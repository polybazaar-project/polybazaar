// Co-authored-by: Claude Code (Claude Fable 5.2)
package com.android.polybazaar.booking.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingRepository
import com.android.polybazaar.booking.viewmodel.BookingRequestUiState
import com.android.polybazaar.booking.viewmodel.BookingRequestViewModel
import com.android.polybazaar.booking.viewmodel.BookingUiState
import com.android.polybazaar.booking.viewmodel.BookingViewModel
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ServiceListing
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class BookingRequestScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private fun day(dayOfMonth: Int): LocalDate = LocalDate.of(2026, 11, dayOfMonth)

  private fun days(range: IntRange): Set<LocalDate> = range.mapTo(mutableSetOf()) { day(it) }

  private val drill =
      ItemListing(
          category = ListingCategory.ITEM_TOOLS,
          price = 8.0,
          title = "Bosch drill set",
          description = "Cordless drill with two batteries.",
          refundValue = 120.0,
          maxRentalDays = 3,
          listingId = "drill",
          ownerUid = "lender",
          availableDates = days(13..16) + day(20),
      )
  private val tutoring =
      ServiceListing(
          category = ListingCategory.SERVICE_TUTORING,
          price = 25.5,
          title = "Analysis tutoring",
          description = "One hour before the exam.",
          listingId = "tutoring",
          ownerUid = "lender",
          availableDates = days(13..16),
      )

  /** The drill as a renter who may request it sees it, with [selection] chosen. */
  private fun drillState(selection: Set<LocalDate> = emptySet()) =
      BookingRequestUiState(
          listing = drill,
          renterUid = "renter",
          selectableDays = drill.availableDates,
          selection = selection,
      )

  private fun setContent(
      uiState: BookingRequestUiState,
      submitState: BookingUiState = BookingUiState(),
      actions: BookingRequestActions = BookingRequestActions(),
  ) {
    composeTestRule.setContent {
      BookingRequestContent(uiState = uiState, submitState = submitState, actions = actions)
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  @Test
  fun whileLoading_showsTheIndicator() {
    setContent(BookingRequestUiState(isLoading = true))

    node(BookingRequestScreenTags.LOADING_INDICATOR).assertIsDisplayed()
    node(BookingRequestScreenTags.SUBMIT_BUTTON).assertDoesNotExist()
  }

  @Test
  fun aLoadError_isShown_withRetry() {
    var retries = 0
    setContent(
        BookingRequestUiState(error = AppError.NETWORK),
        actions = BookingRequestActions(onRetry = { retries++ }),
    )

    node(BookingRequestScreenTags.ERROR_MESSAGE).assertIsDisplayed()
    node(BookingRequestScreenTags.RETRY_BUTTON).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun back_leavesTheScreen() {
    var backs = 0
    setContent(drillState(), actions = BookingRequestActions(onBack = { backs++ }))

    node(BookingRequestScreenTags.BACK_BUTTON).performClick()

    assertEquals(1, backs)
  }

  @Test
  fun beforeAnyDayIsChosen_asksForDates_andCannotBeRequested() {
    setContent(drillState())

    node(BookingRequestScreenTags.LISTING_TITLE).assertTextEquals("Bosch drill set")
    composeTestRule
        .onNodeWithText(context.getString(R.string.booking_request_dates_placeholder))
        .assertIsDisplayed()
    node(BookingRequestScreenTags.DATES_MESSAGE).assertDoesNotExist()
    node(BookingRequestScreenTags.TOTAL).assertDoesNotExist()
    node(BookingRequestScreenTags.SUBMIT_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun aChosenPeriod_showsTheDatesThePriceAndTheTotal() {
    setContent(drillState(selection = days(13..15)))

    composeTestRule.onNodeWithText("13–15 Nov").assertIsDisplayed()
    node(BookingRequestScreenTags.PRICE_LINE).assertTextEquals("CHF 8 / day × 3 days")
    node(BookingRequestScreenTags.TOTAL).assertTextEquals("CHF 24")
    node(BookingRequestScreenTags.SUBMIT_BUTTON).assertIsEnabled()
  }

  @Test
  fun aSingleDay_isWrittenInTheSingular() {
    setContent(drillState(selection = setOf(day(20))))

    node(BookingRequestScreenTags.PRICE_LINE).assertTextEquals("CHF 8 / day × 1 day")
    node(BookingRequestScreenTags.TOTAL).assertTextEquals("CHF 8")
  }

  @Test
  fun anItem_showsItsDamageCompensation_andHowPaymentWorks() {
    setContent(drillState())

    node(BookingRequestScreenTags.DAMAGE)
        .assertTextEquals("Damage compensation: CHF 120 if the item is damaged.")
    node(BookingRequestScreenTags.PAYMENT)
        .assertTextEquals(context.getString(R.string.booking_request_payment))
  }

  @Test
  fun aService_hasNoDamageCompensation() {
    setContent(
        BookingRequestUiState(
            listing = tutoring,
            renterUid = "renter",
            selectableDays = tutoring.availableDates,
            selection = days(13..14),
        )
    )

    node(BookingRequestScreenTags.DAMAGE).assertDoesNotExist()
    node(BookingRequestScreenTags.TOTAL).assertTextEquals("CHF 51")
    node(BookingRequestScreenTags.PAYMENT).assertIsDisplayed()
  }

  @Test
  fun aListingWithoutDays_saysSo_andTheDatesCannotBeOpened() {
    setContent(drillState().copy(selectableDays = emptySet()))

    node(BookingRequestScreenTags.DATES_MESSAGE)
        .assertTextEquals(context.getString(R.string.booking_request_no_days))
    node(BookingRequestScreenTags.DATES_FIELD).assertIsNotEnabled()
    node(BookingRequestScreenTags.SUBMIT_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun aPeriodLongerThanTheItemAllows_isRefused() {
    setContent(drillState(selection = days(13..16)))

    node(BookingRequestScreenTags.DATES_MESSAGE)
        .assertTextEquals("This item can be kept for at most 3 days.")
    node(BookingRequestScreenTags.PRICE_LINE).assertDoesNotExist()
    node(BookingRequestScreenTags.TOTAL).assertDoesNotExist()
    node(BookingRequestScreenTags.SUBMIT_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun aUserWhoMayNotRequest_isTold_andCannotRequest() {
    setContent(drillState(selection = days(13..14)).copy(renterUid = null))

    node(BookingRequestScreenTags.NOT_ALLOWED).assertIsDisplayed()
    node(BookingRequestScreenTags.SUBMIT_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun aUserWhoMayRequest_isNotWarned() {
    setContent(drillState())

    node(BookingRequestScreenTags.NOT_ALLOWED).assertDoesNotExist()
  }

  @Test
  fun requestBooking_asksForTheRequest() {
    var requests = 0
    setContent(
        drillState(selection = days(13..15)),
        actions = BookingRequestActions(onRequest = { requests++ }),
    )

    node(BookingRequestScreenTags.SUBMIT_BUTTON).performScrollTo().performClick()

    assertEquals(1, requests)
  }

  @Test
  fun whileSubmitting_nothingCanBeChanged() {
    setContent(drillState(selection = days(13..15)), BookingUiState(isSubmitting = true))

    node(BookingRequestScreenTags.SUBMIT_BUTTON).assertIsNotEnabled()
    node(BookingRequestScreenTags.DATES_FIELD).assertIsNotEnabled()
  }

  @Test
  fun aFailedRequest_showsTheError_andCanBeSentAgain() {
    setContent(drillState(selection = days(13..15)), BookingUiState(error = AppError.NETWORK))

    node(BookingRequestScreenTags.SUBMIT_ERROR).assertIsDisplayed()
    node(BookingRequestScreenTags.SUBMIT_BUTTON).assertIsEnabled()
  }

  @Test
  fun onceSent_confirmsTheDays_andDoneLeaves() {
    var dones = 0
    setContent(
        drillState(selection = days(13..15)),
        BookingUiState(isQueued = true),
        BookingRequestActions(onDone = { dones++ }),
    )

    node(BookingRequestScreenTags.SENT_MESSAGE)
        .assertTextEquals("You asked for 13–15 Nov. The lender still has to accept it.")
    node(BookingRequestScreenTags.SUBMIT_BUTTON).assertDoesNotExist()
    node(BookingRequestScreenTags.DONE_BUTTON).performClick()
    assertEquals(1, dones)
  }

  @Test
  fun theDatesField_isNamedForTalkBack() {
    setContent(drillState())

    composeTestRule
        .onNode(hasContentDescription(context.getString(R.string.booking_request_dates_label)))
        .assertIsDisplayed()
  }

  @Test
  fun tappingTheDates_opensTheCalendar_whereOnlyOfferedDaysCanBeChosen() {
    // Today can always be chosen when offered, and is in the month the calendar opens on.
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    var selection: Set<LocalDate>? = null
    setContent(
        drillState().copy(selectableDays = setOf(today)),
        actions = BookingRequestActions(onSelectionChange = { selection = it }),
    )

    node(BookingRequestScreenTags.DATES_FIELD).performClick()
    node(AvailabilityCalendarSheetTags.TITLE)
        .assertTextEquals(context.getString(R.string.booking_request_dates_sheet_title))
    // Tomorrow is not offered; it is only in the month shown when it is in today's month.
    if (tomorrow.month == today.month) {
      node(AvailabilityCalendarTags.day(tomorrow)).performClick()
      assertEquals(null, selection)
    }
    node(AvailabilityCalendarTags.day(today)).performClick()

    // At midnight exactly, the calendar may already be on the next day: nothing to check then.
    assumeTrue(LocalDate.now() == today)
    assertEquals(setOf(today), selection)
  }

  @Test
  fun withItsViewModels_aChosenPeriodIsRequested_thenConfirmed() {
    val renter =
        User(uid = "renter", email = "renter@epfl.ch", username = "Rita", isEmailVerified = true)
    val auth: AuthRepository = mock { onBlocking { getCurrentUser() } doReturn renter }
    val bookings = RecordingBookingRepository()
    val viewModel =
        BookingRequestViewModel(
            listingId = "drill",
            listingRepository = FakeListingRepository(allListings = listOf(drill)),
            authRepository = auth,
            today = day(11),
        )
    val bookingViewModel = BookingViewModel(bookings)
    var backs = 0
    composeTestRule.setContent {
      BookingRequestScreen(viewModel, bookingViewModel, onBack = { backs++ })
    }
    composeTestRule.waitForIdle()

    viewModel.updateSelection(days(13..15))
    node(BookingRequestScreenTags.SUBMIT_BUTTON).performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertEquals(
        listOf(
            Booking(
                listingId = "drill",
                renterUid = "renter",
                lenderUid = "lender",
                startDate = day(13),
                endDate = day(15),
            )
        ),
        bookings.requested,
    )
    node(BookingRequestScreenTags.SENT_MESSAGE).assertIsDisplayed()
    node(BookingRequestScreenTags.DONE_BUTTON).performClick()
    assertEquals(1, backs)
  }

  private class RecordingBookingRepository : BookingRepository {
    val requested = mutableListOf<Booking>()

    override suspend fun requestBooking(booking: Booking, onWriteFailure: (Exception) -> Unit) {
      requested += booking
    }

    override suspend fun getBookingsForListing(listingId: String): List<Booking> = emptyList()
  }
}
