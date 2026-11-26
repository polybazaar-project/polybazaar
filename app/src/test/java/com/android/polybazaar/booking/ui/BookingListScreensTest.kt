// Co-authored-by: Copilot (Auto)
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.booking.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingStatus
import com.android.polybazaar.booking.model.FakeBookingRepository
import com.android.polybazaar.booking.viewmodel.BookingUiState
import com.android.polybazaar.booking.viewmodel.BookingViewModel
import com.android.polybazaar.booking.viewmodel.ConfirmationAction
import com.android.polybazaar.booking.viewmodel.ConfirmationFailure
import com.android.polybazaar.booking.viewmodel.ReceivedRequestsUiState
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookingListScreensTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun booking(id: String, status: BookingStatus = BookingStatus.REQUESTED) =
      Booking(
          listingId = "listing",
          renterUid = "renter",
          lenderUid = "lender",
          days = setOf(LocalDate.now().plusDays(5)),
          status = status,
          id = id,
      )

  private fun show(
      state: BookingUiState,
      onBack: () -> Unit = {},
      onRetry: () -> Unit = {},
      onCancel: (String) -> Unit = {},
      asLender: Boolean = false,
      onConfirm: (String, ConfirmationAction) -> Unit = { _, _ -> },
  ) {
    composeTestRule.setContent { Bookings(state, asLender, onBack, onRetry, onCancel, onConfirm) }
  }

  /**
   * [state] as the renter sees it on "My bookings", or with [asLender] as the lender sees its
   * confirmed bookings in the Accepted section of their listing's booking requests.
   */
  @Composable
  private fun Bookings(
      state: BookingUiState,
      asLender: Boolean,
      onBack: () -> Unit = {},
      onRetry: () -> Unit = {},
      onCancel: (String) -> Unit = {},
      onConfirm: (String, ConfirmationAction) -> Unit = { _, _ -> },
  ) {
    if (asLender) {
      ReceivedRequestsContent(
          uiState = ReceivedRequestsUiState(hasLoaded = true),
          bookings = state,
          actions =
              ReceivedRequestsActions(
                  onBack = onBack,
                  onRetryBookings = onRetry,
                  onCancel = onCancel,
                  onConfirm = onConfirm,
              ),
      )
    } else {
      BookingListContent(
          title = "My bookings",
          uiState = state,
          onBack = onBack,
          onRetry = onRetry,
          onCancel = onCancel,
          onConfirm = onConfirm,
      )
    }
  }

  @Test
  fun finishedBooking_hasNoCancelButton() {
    val finished = booking("a").copy(days = setOf(LocalDate.now().minusDays(1)))
    show(BookingUiState(bookings = listOf(finished)))

    statuses().assertCountEquals(1)
    composeTestRule.onAllNodesWithTag(BookingListScreenTags.CANCEL_BUTTON).assertCountEquals(0)
  }

  @Test
  fun bookingWithNotifiedHandover_hasNoCancelButton() {
    val handedOver = booking("a", BookingStatus.CONFIRMED).copy(handoverNotified = true)
    val awaitingHandover = booking("b", BookingStatus.CONFIRMED)
    show(BookingUiState(bookings = listOf(handedOver, awaitingHandover)))

    statuses().assertCountEquals(2)
    composeTestRule.onAllNodesWithTag(BookingListScreenTags.CANCEL_BUTTON).assertCountEquals(1)
  }

  @Test
  fun renterCanConfirmNotifiedHandover() {
    val handover = booking("a", BookingStatus.CONFIRMED).copy(handoverNotified = true)
    val actions = mutableListOf<Pair<String, ConfirmationAction>>()
    show(
        BookingUiState(bookings = listOf(handover)),
        onConfirm = { id, action -> actions += id to action },
    )

    composeTestRule
        .onNodeWithTag(BookingListScreenTags.CONFIRMATION_STATUS)
        .assertTextEquals("Handover notified")
    composeTestRule.onNodeWithTag(BookingListScreenTags.CONFIRMATION_ACTION).performClick()
    assertEquals(listOf("a" to ConfirmationAction.CONFIRM_HANDOVER), actions)
  }

  @Test
  fun lenderCanConfirmNotifiedReturn() {
    val returning =
        booking("a", BookingStatus.CONFIRMED)
            .copy(handoverNotified = true, renterHandoverConfirmed = true, returnNotified = true)
    val actions = mutableListOf<Pair<String, ConfirmationAction>>()
    show(
        BookingUiState(bookings = listOf(returning)),
        asLender = true,
        onConfirm = { id, action -> actions += id to action },
    )

    composeTestRule
        .onNodeWithTag(BookingListScreenTags.CONFIRMATION_STATUS)
        .assertTextEquals("Return notified")
    composeTestRule.onNodeWithTag(BookingListScreenTags.CONFIRMATION_ACTION).performClick()
    assertEquals(listOf("a" to ConfirmationAction.CONFIRM_RETURN), actions)
  }

  @Test
  fun bothPartiesSeeTheCorrectStatusAndActionAtEveryStage() {
    data class Scenario(
        val booking: Booking,
        val lender: Boolean,
        val step: Int,
        val status: String,
        val label: String? = null,
        val action: ConfirmationAction? = null,
    )

    val confirmed = booking("a", BookingStatus.CONFIRMED)
    val handover = confirmed.copy(handoverNotified = true)
    val received = handover.copy(renterHandoverConfirmed = true)
    val returning = received.copy(returnNotified = true)
    val completed = returning.copy(lenderReturnConfirmed = true)
    val scenarios =
        listOf(
            Scenario(
                confirmed,
                true,
                1,
                "Ready for handover",
                "Notify handover",
                ConfirmationAction.NOTIFY_HANDOVER,
            ),
            Scenario(confirmed, false, 1, "Waiting for handover"),
            Scenario(handover, true, 2, "Waiting for renter"),
            Scenario(
                handover,
                false,
                2,
                "Handover notified",
                "Confirm handover",
                ConfirmationAction.CONFIRM_HANDOVER,
            ),
            Scenario(received, true, 3, "Waiting for return"),
            Scenario(
                received,
                false,
                3,
                "Ready to return",
                "Notify return",
                ConfirmationAction.NOTIFY_RETURN,
            ),
            Scenario(
                returning,
                true,
                4,
                "Return notified",
                "Confirm return",
                ConfirmationAction.CONFIRM_RETURN,
            ),
            Scenario(returning, false, 4, "Waiting for lender"),
            Scenario(completed, true, 5, "Return completed"),
            Scenario(completed, false, 5, "Return completed"),
        )
    var current by mutableStateOf(scenarios.first())
    val actions = mutableListOf<Pair<String, ConfirmationAction>>()
    composeTestRule.setContent {
      Bookings(
          state = BookingUiState(bookings = listOf(current.booking)),
          asLender = current.lender,
          onConfirm = { id, action -> actions += id to action },
      )
    }

    for (scenario in scenarios) {
      composeTestRule.runOnIdle { current = scenario }
      composeTestRule
          .onNodeWithTag(BookingListScreenTags.CONFIRMATION_STATUS)
          .assertTextEquals(scenario.status)
      composeTestRule.onNodeWithText("Step ${scenario.step} of 5").assertIsDisplayed()
      if (scenario.action == null) {
        composeTestRule
            .onAllNodesWithTag(BookingListScreenTags.CONFIRMATION_ACTION)
            .assertCountEquals(0)
      } else {
        composeTestRule
            .onNodeWithTag(BookingListScreenTags.CONFIRMATION_ACTION)
            .assertTextEquals(scenario.label!!)
            .performClick()
      }
    }
    assertEquals(
        listOf(
            "a" to ConfirmationAction.NOTIFY_HANDOVER,
            "a" to ConfirmationAction.CONFIRM_HANDOVER,
            "a" to ConfirmationAction.NOTIFY_RETURN,
            "a" to ConfirmationAction.CONFIRM_RETURN,
        ),
        actions,
    )
  }

  @Test
  fun pendingConfirmationDoesNotOfferAnotherActionOrClaimCompletion() {
    val returning =
        booking("a", BookingStatus.CONFIRMED)
            .copy(
                handoverNotified = true,
                renterHandoverConfirmed = true,
                returnNotified = true,
                lenderReturnConfirmed = true,
            )
    show(BookingUiState(bookings = listOf(returning), pendingBookingIds = setOf("a")))

    composeTestRule
        .onNodeWithTag(BookingListScreenTags.CONFIRMATION_STATUS)
        .assertTextEquals("Pending sync")
    composeTestRule.onNodeWithTag(BookingListScreenTags.CONFIRMATION_PENDING).assertIsDisplayed()
    composeTestRule
        .onAllNodesWithTag(BookingListScreenTags.CONFIRMATION_ACTION)
        .assertCountEquals(0)
  }

  @Test
  fun submissionIsNotDescribedAsSavedBeforeItIsQueued() {
    val handover = booking("a", BookingStatus.CONFIRMED).copy(handoverNotified = true)
    show(
        BookingUiState(
            bookings = listOf(handover),
            submittingConfirmations = mapOf("a" to ConfirmationAction.CONFIRM_HANDOVER),
        )
    )

    composeTestRule
        .onNodeWithTag(BookingListScreenTags.CONFIRMATION_STATUS)
        .assertTextEquals("Saving…")
    composeTestRule
        .onAllNodesWithTag(BookingListScreenTags.CONFIRMATION_ACTION)
        .assertCountEquals(0)
  }

  @Test
  fun reloadingKeepsStatusVisibleButHidesConfirmationUntilSnapshotArrives() {
    val handover = booking("a", BookingStatus.CONFIRMED).copy(handoverNotified = true)
    var loading by mutableStateOf(true)
    val actions = mutableListOf<Pair<String, ConfirmationAction>>()
    composeTestRule.setContent {
      BookingListContent(
          title = "Bookings",
          uiState = BookingUiState(bookings = listOf(handover), isLoadingBookings = loading),
          onBack = {},
          onRetry = {},
          onCancel = {},
          onConfirm = { id, action -> actions += id to action },
      )
    }

    composeTestRule
        .onNodeWithTag(BookingListScreenTags.CONFIRMATION_STATUS)
        .assertTextEquals("Handover notified")
    composeTestRule.onNodeWithText("Step 2 of 5").assertIsDisplayed()
    composeTestRule
        .onAllNodesWithTag(BookingListScreenTags.CONFIRMATION_ACTION)
        .assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(BookingListScreenTags.OBSERVATION_ERROR).assertCountEquals(0)

    composeTestRule.runOnIdle { loading = false }
    composeTestRule.onNodeWithTag(BookingListScreenTags.CONFIRMATION_ACTION).performClick()
    assertEquals(listOf("a" to ConfirmationAction.CONFIRM_HANDOVER), actions)
  }

  @Test
  fun rejectedConfirmationShowsRetryForTheSameAction() {
    val handover = booking("a", BookingStatus.CONFIRMED).copy(handoverNotified = true)
    val actions = mutableListOf<Pair<String, ConfirmationAction>>()
    show(
        BookingUiState(
            bookings = listOf(handover),
            confirmationFailures =
                mapOf(
                    "a" to
                        ConfirmationFailure(
                            ConfirmationAction.CONFIRM_HANDOVER,
                            AppError.PERMISSION_DENIED,
                        )
                ),
        ),
        onConfirm = { id, action -> actions += id to action },
    )

    composeTestRule.onNodeWithTag(BookingListScreenTags.CONFIRMATION_ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(BookingListScreenTags.CONFIRMATION_RETRY).performClick()
    assertEquals(listOf("a" to ConfirmationAction.CONFIRM_HANDOVER), actions)
  }

  @Test
  fun renterScreen_namesBookingsAfterTheirListing() {
    show(
        BookingUiState(
            bookings = listOf(booking("a")),
            listingTitles = mapOf("listing" to "Bosch drill set"),
            renterNames = mapOf("renter" to "James Richard"),
        )
    )

    composeTestRule.onNodeWithTag(BookingListScreenTags.LABEL).assertTextEquals("Bosch drill set")
  }

  @Test
  fun lenderScreen_namesBookingsAfterTheirRenter() {
    show(
        BookingUiState(
            // The lender's screen lists only accepted bookings here; requests have their own cards.
            bookings = listOf(booking("a", BookingStatus.CONFIRMED)),
            listingTitles = mapOf("listing" to "Bosch drill set"),
            renterNames = mapOf("renter" to "James Richard"),
        ),
        asLender = true,
    )

    composeTestRule.onNodeWithTag(BookingListScreenTags.LABEL).assertTextEquals("James Richard")
  }

  @Test
  fun bookingWithoutKnownName_isListedWithoutLabel() {
    show(BookingUiState(bookings = listOf(booking("a"))))

    composeTestRule.onAllNodesWithTag(BookingListScreenTags.LABEL).assertCountEquals(0)
  }

  @Test
  fun retryAfterRefusedCancellation_cancelsAgainInsteadOfReloading() {
    val cancelled = mutableListOf<String>()
    var reloads = 0
    show(
        BookingUiState(
            bookings = listOf(booking("a")),
            failedCancelId = "a",
            error = AppError.PERMISSION_DENIED,
        ),
        onRetry = { reloads++ },
        onCancel = { cancelled += it },
    )

    composeTestRule.onNodeWithTag(BookingListScreenTags.RETRY_BUTTON).performClick()

    assertEquals(listOf("a"), cancelled)
    assertEquals(0, reloads)
  }

  @Test
  fun retryAfterLoadError_reloads() {
    var reloads = 0
    show(BookingUiState(observationError = AppError.NETWORK), onRetry = { reloads++ })

    composeTestRule.onNodeWithTag(BookingListScreenTags.OBSERVATION_RETRY).performClick()

    assertEquals(1, reloads)
  }

  @Test
  fun listenerAndCancellationErrorsHaveSeparateRetriesAndHideStaleConfirmation() {
    val cancelled = mutableListOf<String>()
    var reloads = 0
    val stale = booking("a", BookingStatus.CONFIRMED).copy(handoverNotified = true)
    show(
        BookingUiState(
            bookings = listOf(stale),
            observationError = AppError.NETWORK,
            error = AppError.PERMISSION_DENIED,
            failedCancelId = "a",
        ),
        onRetry = { reloads++ },
        onCancel = { cancelled += it },
    )

    composeTestRule.onNodeWithTag(BookingListScreenTags.OBSERVATION_ERROR).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(BookingListScreenTags.CONFIRMATION_STATUS)
        .assertTextEquals("Updates unavailable")
    composeTestRule
        .onAllNodesWithTag(BookingListScreenTags.CONFIRMATION_ACTION)
        .assertCountEquals(0)
    composeTestRule.onNodeWithTag(BookingListScreenTags.OBSERVATION_RETRY).performClick()
    composeTestRule.onNodeWithTag(BookingListScreenTags.RETRY_BUTTON).performClick()
    assertEquals(1, reloads)
    assertEquals(listOf("a"), cancelled)
  }

  private fun statuses() = composeTestRule.onAllNodesWithTag(BookingListScreenTags.STATUS)

  @Test
  fun cancelButton_callsOnCancelWithTheBookingId() {
    val cancelled = mutableListOf<String>()
    show(BookingUiState(bookings = listOf(booking("a"))), onCancel = { cancelled += it })

    composeTestRule.onNodeWithTag(BookingListScreenTags.CANCEL_BUTTON).performClick()

    assertEquals(listOf("a"), cancelled)
  }

  @Test
  fun cancellingBooking_showsProgressInsteadOfTheButton() {
    show(BookingUiState(bookings = listOf(booking("a")), cancellingBookingIds = setOf("a")))

    composeTestRule.onNodeWithTag(BookingListScreenTags.CANCELLING).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(BookingListScreenTags.CANCEL_BUTTON).assertCountEquals(0)
  }

  @Test
  fun cancelledBooking_hasNoCancelButton() {
    show(BookingUiState(bookings = listOf(booking("a", BookingStatus.CANCELLED))))

    composeTestRule.onAllNodesWithTag(BookingListScreenTags.CANCEL_BUTTON).assertCountEquals(0)
    statuses().assertCountEquals(1)
  }

  @Test
  fun loadingAndErrorStates_areShown() {
    show(BookingUiState(isLoadingBookings = true))
    composeTestRule.onNodeWithTag(BookingListScreenTags.LOADING_INDICATOR).assertIsDisplayed()
  }

  @Test
  fun loadError_showsMessageAndRetry() {
    show(BookingUiState(observationError = AppError.NETWORK))

    composeTestRule.onNodeWithTag(BookingListScreenTags.OBSERVATION_ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(BookingListScreenTags.OBSERVATION_RETRY).assertIsDisplayed()
  }

  @Test
  fun emptyBookings_showsEmptyMessageAndBackCallsCallback() {
    var backs = 0
    show(BookingUiState(), onBack = { backs++ })

    composeTestRule.onNodeWithTag(BookingListScreenTags.EMPTY).assertIsDisplayed()
    composeTestRule.onNodeWithTag(BookingListScreenTags.BACK_BUTTON).performClick()

    assertEquals(1, backs)
  }

  @Test
  fun offlineCancellation_isShownCancelledThenRestoredWhenServerRejectsIt() {
    val repository = FakeBookingRepository()
    repository.renterBookings = listOf(booking("a"))
    val viewModel = BookingViewModel(repository)
    composeTestRule.setContent { RenterBookingsScreen(viewModel = viewModel, onBack = {}) }
    composeTestRule.waitUntil { viewModel.uiState.value.bookings.isNotEmpty() }

    composeTestRule.onNodeWithTag(BookingListScreenTags.CANCEL_BUTTON).performClick()
    composeTestRule.waitUntil {
      viewModel.uiState.value.bookings[0].status == BookingStatus.CANCELLED
    }
    composeTestRule.onAllNodesWithTag(BookingListScreenTags.CANCEL_BUTTON).assertCountEquals(0)

    // The server answers after the write was queued locally.
    composeTestRule.runOnIdle {
      repository.cancelWriteFailure?.invoke(AppException(AppError.PERMISSION_DENIED))
    }

    composeTestRule.onNodeWithTag(BookingListScreenTags.ERROR_MESSAGE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(BookingListScreenTags.CANCEL_BUTTON).assertIsDisplayed()
    assertEquals(BookingStatus.REQUESTED, viewModel.uiState.value.bookings[0].status)
  }

  @Test
  fun pendingCancellation_showsProgressUntilQueued() {
    val repository = FakeBookingRepository()
    val gate = CompletableDeferred<Unit>()
    repository.renterBookings = listOf(booking("a"))
    val viewModel = BookingViewModel(repository)
    composeTestRule.setContent { RenterBookingsScreen(viewModel = viewModel, onBack = {}) }
    composeTestRule.waitUntil { viewModel.uiState.value.bookings.isNotEmpty() }
    repository.pendingCancel = gate

    composeTestRule.onNodeWithTag(BookingListScreenTags.CANCEL_BUTTON).performClick()
    composeTestRule.onNodeWithTag(BookingListScreenTags.CANCELLING).assertIsDisplayed()

    gate.complete(Unit)
    composeTestRule.waitUntil { viewModel.uiState.value.cancellingBookingIds.isEmpty() }
    composeTestRule.onAllNodesWithTag(BookingListScreenTags.CANCELLING).assertCountEquals(0)
  }
}
