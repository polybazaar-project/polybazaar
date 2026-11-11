// Co-authored-by: Claude Code (Claude Fable 5.2)
package com.android.polybazaar.booking.viewmodel

import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.DateRun
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ServiceListing
import java.net.UnknownHostException
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking

@OptIn(ExperimentalCoroutinesApi::class)
class BookingRequestViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private val today = LocalDate.of(2026, 11, 11)

  private fun day(dayOfMonth: Int): LocalDate = LocalDate.of(2026, 11, dayOfMonth)

  private fun days(range: IntRange): Set<LocalDate> = range.mapTo(mutableSetOf()) { day(it) }

  /** Offered on the 9th and 10th (already past), from the 13th to the 16th and on the 20th. */
  private val drill =
      ItemListing(
          category = ListingCategory.ITEM_TOOLS,
          price = 8.0,
          title = "Bosch drill set",
          description = "With bits",
          refundValue = 120.0,
          maxRentalDays = 3,
          listingId = "drill",
          ownerUid = "lender",
          availableDates = days(9..10) + days(13..16) + day(20),
      )

  private val renter =
      User(uid = "renter", email = "renter@epfl.ch", username = "Rita", isEmailVerified = true)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun signedInAs(user: User): AuthRepository = mock {
    onBlocking { getCurrentUser() } doReturn user
  }

  /** Creates the ViewModel for [listing] and runs its first load to the end. */
  private fun loadedViewModel(
      listing: Listing = drill,
      auth: AuthRepository = signedInAs(renter),
      listings: FakeListingRepository = FakeListingRepository(allListings = listOf(listing)),
  ): BookingRequestViewModel {
    val viewModel = BookingRequestViewModel(listing.listingId!!, listings, auth, today)
    testDispatcher.scheduler.advanceUntilIdle()
    return viewModel
  }

  @Test
  fun loading_showsTheListing_andOffersOnlyTheDaysStillToCome() {
    val state = loadedViewModel().uiState.value

    assertEquals(drill, state.listing)
    assertEquals(days(13..16) + day(20), state.selectableDays)
    assertTrue(state.selection.isEmpty())
    assertFalse(state.isLoading)
    assertNull(state.error)
  }

  @Test
  fun isLoading_whileTheListingIsBeingRead() {
    val listings = FakeListingRepository(allListings = listOf(drill))
    listings.gate = CompletableDeferred()
    val viewModel = loadedViewModel(listings = listings)

    assertTrue(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.listing)

    listings.gate?.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(drill, viewModel.uiState.value.listing)
  }

  @Test
  fun aFailedLoad_showsTheError_andRetryLoadsTheListing() {
    val listings = FakeListingRepository(allListings = listOf(drill))
    listings.failure = UnknownHostException()
    val viewModel = loadedViewModel(listings = listings)

    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    assertNull(viewModel.uiState.value.listing)

    listings.failure = null
    viewModel.loadListing()
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.uiState.value.error)
    assertEquals(drill, viewModel.uiState.value.listing)
  }

  @Test
  fun aVerifiedRenter_mayRequestSomeoneElsesListing() {
    val state = loadedViewModel().uiState.value

    assertTrue(state.canRequest)
    assertEquals(renter.uid, state.renterUid)
  }

  @Test
  fun theOwner_mayNotRequestTheirOwnListing() {
    val owner = renter.copy(uid = "lender")

    assertFalse(loadedViewModel(auth = signedInAs(owner)).uiState.value.canRequest)
  }

  @Test
  fun anUnverifiedUser_mayNotRequest() {
    val unverified = renter.copy(isEmailVerified = false)

    assertFalse(loadedViewModel(auth = signedInAs(unverified)).uiState.value.canRequest)
  }

  @Test
  fun aPausedListing_cannotBeRequested() {
    assertFalse(loadedViewModel(listing = drill.copy(isPaused = true)).uiState.value.canRequest)
  }

  @Test
  fun withoutASignedInUser_theListingIsShown_butCannotBeRequested() {
    val signedOut: AuthRepository = mock {
      onBlocking { getCurrentUser() } doThrow IllegalStateException("No user")
    }
    val state = loadedViewModel(auth = signedOut).uiState.value

    assertEquals(drill, state.listing)
    assertNull(state.error)
    assertFalse(state.canRequest)
  }

  @Test
  fun updateSelection_keepsOnlyTheDaysTheLenderOffers() {
    val viewModel = loadedViewModel()

    // The 10th is past, the 17th is not offered.
    viewModel.updateSelection(setOf(day(10), day(13), day(14), day(17)))

    assertEquals(days(13..14), viewModel.uiState.value.selection)
  }

  @Test
  fun daysInARow_makeAPeriod_withItsTotalAndBooking() {
    val viewModel = loadedViewModel()

    viewModel.updateSelection(days(13..15))
    val state = viewModel.uiState.value

    assertEquals(DateRun(day(13), day(15)), state.period)
    assertEquals(3, state.dayCount)
    assertEquals(24.0, state.total!!, 0.0)
    assertFalse(state.isTooLong)
    assertEquals(
        Booking(
            listingId = "drill",
            renterUid = "renter",
            lenderUid = "lender",
            startDate = day(13),
            endDate = day(15),
        ),
        state.booking,
    )
  }

  @Test
  fun aSingleDay_isAPeriodOfOneDay() {
    val viewModel = loadedViewModel()

    viewModel.updateSelection(setOf(day(20)))
    val state = viewModel.uiState.value

    assertEquals(1, state.dayCount)
    assertEquals(8.0, state.total!!, 0.0)
    assertEquals(day(20), state.booking?.startDate)
    assertEquals(day(20), state.booking?.endDate)
  }

  @Test
  fun noDayChosen_isNotAProblem_butGivesNoBooking() {
    val state = loadedViewModel().uiState.value

    assertFalse(state.isTooLong)
    assertNull(state.period)
    assertNull(state.total)
    assertEquals(0, state.dayCount)
    assertNull(state.booking)
  }

  @Test
  fun choosingADayAwayFromThePeriod_startsANewPeriod() {
    val viewModel = loadedViewModel()
    viewModel.updateSelection(days(13..14))

    viewModel.updateSelection(days(13..14) + day(20))

    assertEquals(setOf(day(20)), viewModel.uiState.value.selection)
    assertEquals(DateRun(day(20), day(20)), viewModel.uiState.value.period)
  }

  @Test
  fun choosingTheNextDay_extendsThePeriod() {
    val viewModel = loadedViewModel()
    viewModel.updateSelection(days(13..14))

    viewModel.updateSelection(days(13..15))

    assertEquals(days(13..15), viewModel.uiState.value.selection)
  }

  @Test
  fun unselectingADayInTheMiddle_keepsTheEarliestPeriod() {
    val viewModel = loadedViewModel()
    viewModel.updateSelection(days(13..15))

    viewModel.updateSelection(setOf(day(13), day(15)))

    assertEquals(setOf(day(13)), viewModel.uiState.value.selection)
  }

  @Test
  fun aRangeOverDaysNotOffered_keepsItsFirstPeriod() {
    val viewModel = loadedViewModel()

    // Holding the 13th then tapping the 20th skips the 17th to the 19th, which are not offered.
    viewModel.updateSelection(days(13..16) + day(20))

    assertEquals(days(13..16), viewModel.uiState.value.selection)
  }

  @Test
  fun aPeriodLongerThanMaxRentalDays_cannotBeRequested() {
    val viewModel = loadedViewModel()

    viewModel.updateSelection(days(13..16))
    val state = viewModel.uiState.value

    assertTrue(state.isTooLong)
    assertEquals(3, state.maxRentalDays)
    assertNull(state.total)
    assertNull(state.booking)
  }

  @Test
  fun anItemWithoutMaxRentalDays_hasNoLimit() {
    val viewModel = loadedViewModel(listing = drill.copy(maxRentalDays = null))

    viewModel.updateSelection(days(13..16))

    assertFalse(viewModel.uiState.value.isTooLong)
    assertEquals(day(16), viewModel.uiState.value.booking?.endDate)
  }

  @Test
  fun aService_isPricedByTheDay_withoutALimit() {
    val tutoring =
        ServiceListing(
            category = ListingCategory.SERVICE_TUTORING,
            price = 25.5,
            title = "Analysis tutoring",
            description = "Before the exam",
            listingId = "tutoring",
            ownerUid = "lender",
            availableDates = days(13..16),
        )
    val viewModel = loadedViewModel(listing = tutoring)

    viewModel.updateSelection(days(13..16))
    val state = viewModel.uiState.value

    assertFalse(state.isTooLong)
    assertEquals(102.0, state.total!!, 0.0)
    assertEquals("tutoring", state.booking?.listingId)
  }

  @Test
  fun aUserWhoMayNotRequest_getsNoBooking() {
    val viewModel = loadedViewModel(auth = signedInAs(renter.copy(isEmailVerified = false)))

    viewModel.updateSelection(days(13..14))

    assertEquals(16.0, viewModel.uiState.value.total!!, 0.0)
    assertNull(viewModel.uiState.value.booking)
  }

  @Test
  fun aReload_dropsTheChosenDaysTheLenderTookBack() {
    val listings = FakeListingRepository(allListings = listOf(drill))
    val viewModel = loadedViewModel(listings = listings)
    viewModel.updateSelection(days(13..15))

    listings.allListings = listOf(drill.copy(availableDates = days(13..14)))
    viewModel.loadListing()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(days(13..14), viewModel.uiState.value.selection)
  }

  @Test
  fun aReload_keepsOnePeriod_whenTheLenderTookBackADayInTheMiddle() {
    val listings = FakeListingRepository(allListings = listOf(drill))
    val viewModel = loadedViewModel(listings = listings)
    viewModel.updateSelection(days(13..15))

    listings.allListings = listOf(drill.copy(availableDates = setOf(day(13), day(15))))
    viewModel.loadListing()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(setOf(day(13)), viewModel.uiState.value.selection)
  }

  @Test
  fun aReload_doesNotReadTheSignedInUserAgain() {
    val auth = signedInAs(renter)
    val viewModel = loadedViewModel(auth = auth)

    viewModel.loadListing()
    testDispatcher.scheduler.advanceUntilIdle()

    verifyBlocking(auth, times(1)) { getCurrentUser() }
    assertTrue(viewModel.uiState.value.canRequest)
  }

  @Test
  fun theState_carriesTheDayTheScreenWasOpenedOn() {
    assertEquals(today, loadedViewModel().uiState.value.today)
  }
}
