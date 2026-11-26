// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.booking.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingStatus
import com.android.polybazaar.booking.model.ConfirmationAction
import com.android.polybazaar.booking.model.FakeBookingRepository
import com.android.polybazaar.booking.model.ObservedBookings
import com.android.polybazaar.booking.viewmodel.BookingUiState
import com.android.polybazaar.booking.viewmodel.BookingViewModel
import com.android.polybazaar.booking.viewmodel.ConfirmationFailure
import com.android.polybazaar.booking.viewmodel.ReceivedRequestsUiState
import com.android.polybazaar.booking.viewmodel.ReceivedRequestsViewModel
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.profile.model.FakeProfileRepository
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceivedRequestsScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private fun days(range: IntRange): Set<LocalDate> =
      range.mapTo(mutableSetOf()) { LocalDate.of(2026, 11, it) }

  private fun booking(
      id: String,
      renter: String,
      days: Set<LocalDate>,
      status: BookingStatus = BookingStatus.REQUESTED,
  ) = Booking("drill", renter, "lender", days, status, id)

  // Bob's request shares the 21st with Ann's.
  private val ann = booking("ann", "uid-ann", days(20..21))
  private val bob = booking("bob", "uid-bob", days(21..22))
  private val cy = booking("cy", "uid-cy", days(28..28), BookingStatus.CONFIRMED)

  private val loaded =
      ReceivedRequestsUiState(
          requests = listOf(ann, bob),
          renterNames = mapOf("uid-ann" to "ann_m"),
          offeredDays = days(17..30),
          hasLoaded = true,
      )

  /** Cy's accepted booking, followed live. */
  private val acceptedBookings =
      BookingUiState(bookings = listOf(cy), renterNames = mapOf("uid-cy" to "cy"))

  private val today = LocalDate.of(2026, 11, 18)

  private fun setContent(
      uiState: ReceivedRequestsUiState,
      actions: ReceivedRequestsActions = ReceivedRequestsActions(),
      bookings: BookingUiState = BookingUiState(),
      today: LocalDate = this.today,
  ) {
    composeTestRule.setContent {
      ReceivedRequestsContent(
          uiState = uiState,
          bookings = bookings,
          actions = actions,
          today = today,
      )
    }
  }

  private fun drill(availableDates: Set<LocalDate>) =
      ItemListing(
          category = ListingCategory.ITEM_TOOLS,
          price = 8.0,
          title = "Drill",
          description = "Cordless drill.",
          refundValue = 120.0,
          listingId = "drill",
          ownerUid = "lender",
          availableDates = availableDates,
      )

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  /** The text [text] inside the card tagged [tag]. */
  private fun card(tag: String, text: String) =
      composeTestRule.onNode(
          hasText(text) and hasAnyAncestor(hasTestTag(tag)),
          useUnmergedTree = true,
      )

  @Test
  fun beforeTheFirstLoad_showsTheIndicator() {
    setContent(ReceivedRequestsUiState(isLoading = true))

    node(ReceivedRequestsScreenTags.LOADING_INDICATOR).assertIsDisplayed()
  }

  @Test
  fun aLoadError_isShown_withRetry() {
    var retries = 0
    setContent(
        ReceivedRequestsUiState(error = AppError.NETWORK),
        ReceivedRequestsActions(onRetry = { retries++ }),
    )

    node(ReceivedRequestsScreenTags.ERROR_MESSAGE)
        .assertTextEquals(context.getString(R.string.error_network))
    node(ReceivedRequestsScreenTags.RETRY_BUTTON).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun noBookings_saysSo() {
    setContent(ReceivedRequestsUiState(hasLoaded = true))

    node(ReceivedRequestsScreenTags.EMPTY_MESSAGE).assertIsDisplayed()
  }

  @Test
  fun showsEachRequest_withItsRenterAndDays() {
    setContent(loaded, bookings = acceptedBookings)

    val annCard = ReceivedRequestsScreenTags.request("ann")
    card(annCard, "ann_m").assertIsDisplayed()
    card(annCard, "20–21 Nov").assertIsDisplayed()
    card(annCard, "2 days").assertIsDisplayed()
    // Bob has no name read: shown as a renter.
    card(ReceivedRequestsScreenTags.request("bob"), "A renter").assertIsDisplayed()
    val cyCard = ReceivedRequestsScreenTags.accepted("cy")
    card(cyCard, "cy").assertIsDisplayed()
    card(cyCard, "28 Nov").assertIsDisplayed()
    node(ReceivedRequestsScreenTags.acceptButton("cy")).assertDoesNotExist()
    node(ReceivedRequestsScreenTags.EMPTY_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun accept_asksFirst_sayingWhatItCancels() {
    val accepted = mutableListOf<Booking>()
    setContent(loaded, ReceivedRequestsActions(onAccept = { accepted += it }))

    node(ReceivedRequestsScreenTags.acceptButton("ann")).performClick()

    node(ReceivedRequestsScreenTags.ACCEPT_DIALOG_TEXT)
        .assertTextEquals(
            "ann_m gets 20–21 Nov. No one else can request these days. " +
                "1 other request for these days will be cancelled."
        )
    assertEquals(emptyList<Booking>(), accepted)
    node(ReceivedRequestsScreenTags.ACCEPT_CONFIRM).performClick()

    node(ReceivedRequestsScreenTags.ACCEPT_DIALOG).assertDoesNotExist()
    assertEquals(listOf(ann), accepted)
  }

  @Test
  fun theAcceptDialog_survivesARotation() {
    val restoration = StateRestorationTester(composeTestRule)
    restoration.setContent {
      ReceivedRequestsContent(
          uiState = loaded,
          bookings = BookingUiState(),
          actions = ReceivedRequestsActions(),
          today = today,
      )
    }
    node(ReceivedRequestsScreenTags.acceptButton("ann")).performClick()

    restoration.emulateSavedInstanceStateRestore()

    node(ReceivedRequestsScreenTags.ACCEPT_DIALOG_TEXT)
        .assertTextEquals(
            "ann_m gets 20–21 Nov. No one else can request these days. " +
                "1 other request for these days will be cancelled."
        )
  }

  @Test
  fun cancellingTheDialog_acceptsNothing() {
    val accepted = mutableListOf<Booking>()
    setContent(
        loaded.copy(requests = listOf(ann)),
        ReceivedRequestsActions(onAccept = { accepted += it }),
    )

    node(ReceivedRequestsScreenTags.acceptButton("ann")).performClick()
    node(ReceivedRequestsScreenTags.ACCEPT_DIALOG_TEXT)
        .assertTextEquals("ann_m gets 20–21 Nov. No one else can request these days.")
    node(ReceivedRequestsScreenTags.ACCEPT_CANCEL).performClick()

    node(ReceivedRequestsScreenTags.ACCEPT_DIALOG).assertDoesNotExist()
    assertEquals(emptyList<Booking>(), accepted)
  }

  @Test
  fun whileAnAcceptRuns_theButtonsAreDisabled_andAFailureIsShown() {
    setContent(loaded.copy(acceptingId = "ann", acceptError = AppError.NETWORK))

    node(ReceivedRequestsScreenTags.acceptButton("ann")).assertIsNotEnabled()
    node(ReceivedRequestsScreenTags.acceptButton("bob")).assertIsNotEnabled()
    node(ReceivedRequestsScreenTags.ACCEPT_ERROR)
        .assertTextEquals(context.getString(R.string.error_network))
  }

  @Test
  fun aRequestForADayNoLongerOffered_hasNoAccept_andSaysWhy() {
    setContent(loaded.copy(offeredDays = days(17..21)))

    node(ReceivedRequestsScreenTags.request("bob")).assertIsDisplayed()
    node(ReceivedRequestsScreenTags.acceptButton("bob")).assertDoesNotExist()
    node(ReceivedRequestsScreenTags.unavailable("bob"))
        .assertTextEquals(context.getString(R.string.received_requests_unavailable))
    node(ReceivedRequestsScreenTags.acceptButton("ann")).assertIsEnabled()
    node(ReceivedRequestsScreenTags.unavailable("ann")).assertDoesNotExist()
  }

  @Test
  fun withTheViewModel_acceptingMovesTheRequest_andRemovesTheOverlappingOne() {
    val bookings = FakeBookingRepository().apply { listingBookings = listOf(ann, bob) }
    val listings = FakeListingRepository(listOf(drill(availableDates = days(17..30))))
    val profiles =
        FakeProfileRepository(
            mapOf("uid-ann" to User(uid = "uid-ann", email = "", username = "ann_m"))
        )
    val viewModel = ReceivedRequestsViewModel("drill", bookings, listings, profiles, today)
    val bookingsViewModel = BookingViewModel(bookings, profileRepository = profiles)
    composeTestRule.setContent {
      ReceivedRequestsScreen("drill", viewModel, bookingsViewModel, onBack = {})
    }

    node(ReceivedRequestsScreenTags.acceptButton("ann")).assertIsEnabled().performClick()
    node(ReceivedRequestsScreenTags.ACCEPT_CONFIRM).performClick()
    // As Firestore's listener would after the accept.
    composeTestRule.runOnIdle {
      bookings.bookingUpdates.tryEmit(ObservedBookings(bookings.listingBookings))
    }

    card(ReceivedRequestsScreenTags.accepted("ann"), "ann_m").assertIsDisplayed()
    node(ReceivedRequestsScreenTags.request("ann")).assertDoesNotExist()
    node(ReceivedRequestsScreenTags.request("bob")).assertDoesNotExist()
    assertEquals(BookingStatus.CANCELLED, bookings.listingBookings.single { it.id == "bob" }.status)
  }

  @Test
  fun anAcceptedBooking_offersTheLendersHandoverStep_andCancelAfterAConfirmation() {
    val confirmed = mutableListOf<Pair<String, ConfirmationAction>>()
    val cancelled = mutableListOf<String>()
    setContent(
        loaded,
        ReceivedRequestsActions(
            onConfirm = { id, action -> confirmed += id to action },
            onCancel = { cancelled += it },
        ),
        bookings = acceptedBookings,
    )

    val cyCard = ReceivedRequestsScreenTags.accepted("cy")
    card(cyCard, "Ready for handover").assertIsDisplayed()
    card(cyCard, "Step 1 of 5").assertIsDisplayed()
    node(BookingListScreenTags.CONFIRMATION_ACTION)
        .assertTextEquals("Notify handover")
        .performClick()
    node(BookingListScreenTags.CANCEL_BUTTON).performClick()

    assertEquals(listOf("cy" to ConfirmationAction.NOTIFY_HANDOVER), confirmed)
    node(ReceivedRequestsScreenTags.CANCEL_DIALOG_TEXT)
        .assertTextEquals(
            "cy will no longer have 28 Nov. These days stay off your listing: " +
                "add them back in Edit listing to lend them again."
        )
    assertEquals(emptyList<String>(), cancelled)
    node(ReceivedRequestsScreenTags.CANCEL_CONFIRM).performClick()

    node(ReceivedRequestsScreenTags.CANCEL_DIALOG).assertDoesNotExist()
    assertEquals(listOf("cy"), cancelled)
  }

  @Test
  fun keepingTheBooking_cancelsNothing() {
    val cancelled = mutableListOf<String>()
    setContent(
        ReceivedRequestsUiState(hasLoaded = true),
        ReceivedRequestsActions(onCancel = { cancelled += it }),
        bookings = BookingUiState(bookings = listOf(cy)),
    )

    node(BookingListScreenTags.CANCEL_BUTTON).performClick()
    // No name read: the dialog names a renter.
    node(ReceivedRequestsScreenTags.CANCEL_DIALOG_TEXT)
        .assertTextEquals(
            "A renter will no longer have 28 Nov. These days stay off your listing: " +
                "add them back in Edit listing to lend them again."
        )
    node(ReceivedRequestsScreenTags.CANCEL_KEEP).performClick()

    node(ReceivedRequestsScreenTags.CANCEL_DIALOG).assertDoesNotExist()
    assertEquals(emptyList<String>(), cancelled)
  }

  @Test
  fun theCancelDialog_survivesARotation() {
    val restoration = StateRestorationTester(composeTestRule)
    restoration.setContent {
      ReceivedRequestsContent(
          uiState = loaded,
          bookings = acceptedBookings,
          actions = ReceivedRequestsActions(),
          today = today,
      )
    }
    node(BookingListScreenTags.CANCEL_BUTTON).performClick()

    restoration.emulateSavedInstanceStateRestore()

    node(ReceivedRequestsScreenTags.CANCEL_DIALOG).assertIsDisplayed()
  }

  @Test
  fun theCancelDialog_closes_whenTheBookingIsCancelledMeanwhile() {
    var bookings by mutableStateOf(acceptedBookings)
    composeTestRule.setContent {
      ReceivedRequestsContent(
          uiState = ReceivedRequestsUiState(hasLoaded = true),
          bookings = bookings,
          actions = ReceivedRequestsActions(),
          today = today,
      )
    }
    node(BookingListScreenTags.CANCEL_BUTTON).performClick()
    node(ReceivedRequestsScreenTags.CANCEL_DIALOG).assertIsDisplayed()

    // The renter cancels it first.
    bookings = acceptedBookings.copy(bookings = listOf(cy.copy(status = BookingStatus.CANCELLED)))

    node(ReceivedRequestsScreenTags.CANCEL_DIALOG).assertDoesNotExist()
  }

  @Test
  fun whileAnAcceptRuns_noBookingCanBeCancelled() {
    setContent(loaded.copy(acceptingId = "ann"), bookings = acceptedBookings)

    node(BookingListScreenTags.CANCEL_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun whileACancellationIsQueued_noRequestCanBeAccepted_norAnotherBookingCancelled() {
    val dee = booking("dee", "uid-dee", days(29..29), BookingStatus.CONFIRMED)
    setContent(
        loaded,
        bookings = BookingUiState(bookings = listOf(cy, dee), cancellingBookingIds = setOf("cy")),
    )

    node(ReceivedRequestsScreenTags.acceptButton("ann")).assertIsNotEnabled()
    node(ReceivedRequestsScreenTags.acceptButton("bob")).assertIsNotEnabled()
    composeTestRule
        .onNode(
            hasTestTag(BookingListScreenTags.CANCEL_BUTTON) and
                hasAnyAncestor(hasTestTag(ReceivedRequestsScreenTags.accepted("dee")))
        )
        .assertIsNotEnabled()
  }

  @Test
  fun aPastBooking_staysUntilItsReturnIsCompleted() {
    val returned =
        cy.copy(
            handoverNotified = true,
            renterHandoverConfirmed = true,
            returnNotified = true,
            lenderReturnConfirmed = true,
        )
    val returning = booking("dee", "uid-dee", days(24..25), BookingStatus.CONFIRMED)
    val cancelled = booking("eve", "uid-eve", days(29..29), BookingStatus.CANCELLED)
    val afterward = LocalDate.of(2026, 12, 1)
    setContent(
        ReceivedRequestsUiState(hasLoaded = true),
        bookings = BookingUiState(bookings = listOf(returning, returned, cancelled)),
        today = afterward,
    )

    // Over, but the lender has not confirmed the return yet; no name read: shown as a renter.
    card(ReceivedRequestsScreenTags.accepted("dee"), "A renter").assertIsDisplayed()
    node(ReceivedRequestsScreenTags.accepted("cy")).assertDoesNotExist()
    node(ReceivedRequestsScreenTags.accepted("eve")).assertDoesNotExist()
    // A handed-over booking can no longer be cancelled, nor a past one.
    node(BookingListScreenTags.CANCEL_BUTTON).assertDoesNotExist()
  }

  @Test
  fun aFailedConfirmationOnACancelledBooking_staysUntilDismissed() {
    val cancelled = cy.copy(status = BookingStatus.CANCELLED)
    val dismissed = mutableListOf<String>()
    setContent(
        ReceivedRequestsUiState(hasLoaded = true),
        ReceivedRequestsActions(onDismissConfirmation = { dismissed += it }),
        bookings =
            BookingUiState(
                bookings = listOf(cancelled),
                confirmationFailures =
                    mapOf(
                        "cy" to
                            ConfirmationFailure(
                                ConfirmationAction.NOTIFY_HANDOVER,
                                AppError.PERMISSION_DENIED,
                            )
                    ),
            ),
    )

    card(ReceivedRequestsScreenTags.accepted("cy"), "Cancelled").assertIsDisplayed()
    node(BookingListScreenTags.CONFIRMATION_DISMISS).performClick()
    assertEquals(listOf("cy"), dismissed)
  }

  @Test
  fun liveUpdatesThatStopped_areShownWithRetry_insteadOfTheEmptyMessage() {
    var retries = 0
    setContent(
        ReceivedRequestsUiState(hasLoaded = true),
        ReceivedRequestsActions(onRetryBookings = { retries++ }),
        bookings = BookingUiState(observationError = AppError.NETWORK),
    )

    node(BookingListScreenTags.OBSERVATION_ERROR).assertIsDisplayed()
    node(ReceivedRequestsScreenTags.EMPTY_MESSAGE).assertDoesNotExist()
    node(BookingListScreenTags.OBSERVATION_RETRY).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun whileTheAcceptedBookingsAreRead_theEmptyMessageWaits() {
    setContent(
        ReceivedRequestsUiState(hasLoaded = true),
        bookings = BookingUiState(isLoadingBookings = true),
    )

    node(ReceivedRequestsScreenTags.EMPTY_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun withTheViewModels_liveUpdatesRetry_andAnAcceptedBookingIsCancelled() {
    val upcoming =
        booking("cy", "uid-cy", setOf(LocalDate.now().plusDays(5)), BookingStatus.CONFIRMED)
    val bookings =
        FakeBookingRepository().apply {
          listingBookings = listOf(upcoming)
          bookingUpdateFailure = AppException(AppError.NETWORK)
        }
    val listings = FakeListingRepository(listOf(drill(availableDates = emptySet())))
    val viewModel = ReceivedRequestsViewModel("drill", bookings, listings, FakeProfileRepository())
    val bookingsViewModel = BookingViewModel(bookings)
    composeTestRule.setContent {
      ReceivedRequestsScreen("drill", viewModel, bookingsViewModel, onBack = {})
    }
    node(ReceivedRequestsScreenTags.accepted("cy")).assertIsDisplayed()
    // The next live update fails, which stops them.
    composeTestRule.runOnIdle {
      bookings.bookingUpdates.tryEmit(ObservedBookings(listOf(upcoming)))
    }
    composeTestRule.waitUntil { bookingsViewModel.uiState.value.observationError != null }

    bookings.bookingUpdateFailure = null
    node(BookingListScreenTags.OBSERVATION_RETRY).performClick()
    composeTestRule.waitUntil { bookingsViewModel.uiState.value.observationError == null }
    node(BookingListScreenTags.CANCEL_BUTTON).performClick()
    node(ReceivedRequestsScreenTags.CANCEL_CONFIRM).performClick()

    composeTestRule.waitUntil {
      bookingsViewModel.uiState.value.bookings.single().status == BookingStatus.CANCELLED
    }
    assertEquals(1, bookings.cancellationCount)
    node(ReceivedRequestsScreenTags.accepted("cy")).assertDoesNotExist()
  }

  @Test
  fun withTheViewModels_aCancellationTheServerRefuses_bringsTheBookingBack_withAnError() {
    val upcoming =
        booking("cy", "uid-cy", setOf(LocalDate.now().plusDays(5)), BookingStatus.CONFIRMED)
    val bookings = FakeBookingRepository().apply { listingBookings = listOf(upcoming) }
    val listings = FakeListingRepository(listOf(drill(availableDates = emptySet())))
    val viewModel = ReceivedRequestsViewModel("drill", bookings, listings, FakeProfileRepository())
    val bookingsViewModel = BookingViewModel(bookings)
    composeTestRule.setContent {
      ReceivedRequestsScreen("drill", viewModel, bookingsViewModel, onBack = {})
    }

    node(BookingListScreenTags.CANCEL_BUTTON).performClick()
    node(ReceivedRequestsScreenTags.CANCEL_CONFIRM).performClick()
    // Queued, also offline: the booking leaves Accepted at once.
    node(ReceivedRequestsScreenTags.accepted("cy")).assertDoesNotExist()

    // The server answers after the write was queued locally.
    composeTestRule.runOnIdle {
      bookings.cancelWriteFailure?.invoke(AppException(AppError.PERMISSION_DENIED))
    }

    node(ReceivedRequestsScreenTags.accepted("cy")).assertIsDisplayed()
    node(BookingListScreenTags.ERROR_MESSAGE)
        .assertTextEquals(context.getString(R.string.error_permission_denied))
    // Retry cancels again, without asking a second time.
    node(BookingListScreenTags.RETRY_BUTTON).performClick()
    composeTestRule.waitUntil { bookings.cancellationCount == 2 }
    node(ReceivedRequestsScreenTags.CANCEL_DIALOG).assertDoesNotExist()
  }
}
