// Co-authored-by: Copilot (Auto)
package com.android.polybazaar.booking.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingStatus
import com.android.polybazaar.booking.model.FakeBookingRepository
import com.android.polybazaar.booking.viewmodel.BookingUiState
import com.android.polybazaar.booking.viewmodel.BookingViewModel
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
      showRenter: Boolean = false,
  ) {
    composeTestRule.setContent {
      BookingListContent(
          title = "My bookings",
          uiState = state,
          onBack = onBack,
          onRetry = onRetry,
          onCancel = onCancel,
          showRenter = showRenter,
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
            bookings = listOf(booking("a")),
            listingTitles = mapOf("listing" to "Bosch drill set"),
            renterNames = mapOf("renter" to "James Richard"),
        ),
        showRenter = true,
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
    show(BookingUiState(error = AppError.NETWORK), onRetry = { reloads++ })

    composeTestRule.onNodeWithTag(BookingListScreenTags.RETRY_BUTTON).performClick()

    assertEquals(1, reloads)
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
    show(BookingUiState(error = AppError.NETWORK))

    composeTestRule.onNodeWithTag(BookingListScreenTags.ERROR_MESSAGE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(BookingListScreenTags.RETRY_BUTTON).assertIsDisplayed()
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
  fun lenderBookingsScreen_retriesLoadAndCancelsBooking() {
    val repository =
        FakeBookingRepository().apply {
          loadFailure = AppException(AppError.NETWORK)
          listingBookings = listOf(booking("a"))
        }
    val viewModel = BookingViewModel(repository)
    var backs = 0
    composeTestRule.setContent {
      LenderBookingsScreen(
          listingId = "listing",
          viewModel = viewModel,
          onBack = { backs++ },
      )
    }
    composeTestRule.waitUntil { viewModel.uiState.value.error != null }
    repository.loadFailure = null
    composeTestRule.onNodeWithTag(BookingListScreenTags.RETRY_BUTTON).performClick()

    composeTestRule.waitUntil { viewModel.uiState.value.bookings.isNotEmpty() }
    assertEquals(2, repository.loadCount)

    composeTestRule.onNodeWithTag(BookingListScreenTags.CANCEL_BUTTON).performClick()
    composeTestRule.waitUntil {
      viewModel.uiState.value.bookings.single().status == BookingStatus.CANCELLED
    }
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
