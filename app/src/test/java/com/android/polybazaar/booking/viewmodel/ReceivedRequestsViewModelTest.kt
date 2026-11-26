// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.booking.viewmodel

import com.android.polybazaar.auth.model.User
import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingStatus
import com.android.polybazaar.booking.model.FakeBookingRepository
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.profile.model.FakeProfileRepository
import java.net.UnknownHostException
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReceivedRequestsViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val today = LocalDate.of(2026, 11, 18)

  private fun days(range: IntRange): Set<LocalDate> =
      range.mapTo(mutableSetOf()) { LocalDate.of(2026, 11, it) }

  private fun booking(
      id: String,
      renter: String,
      days: Set<LocalDate>,
      status: BookingStatus = BookingStatus.REQUESTED,
      listingId: String = "drill",
  ) = Booking(listingId, renter, "lender", days, status, id)

  // Bob's request shares the 21st with Ann's; Cy's asks for other days.
  private val ann = booking("ann", "uid-ann", days(20..21))
  private val bob = booking("bob", "uid-bob", days(21..22))
  private val cy = booking("cy", "uid-cy", days(25..26))

  private val profiles =
      FakeProfileRepository(
          mapOf(
              "uid-ann" to User(uid = "uid-ann", email = "", username = "ann_m"),
              "uid-bob" to User(uid = "uid-bob", email = "", username = "bob.k"),
          )
      )
  private lateinit var bookings: FakeBookingRepository

  /** The listing "drill", offering [days]: by default every day the requests above ask for. */
  private fun drill(days: Set<LocalDate> = days(17..30)) =
      ItemListing(
          category = ListingCategory.ITEM_TOOLS,
          price = 8.0,
          title = "Drill",
          description = "Cordless drill.",
          refundValue = 120.0,
          listingId = "drill",
          ownerUid = "lender",
          availableDates = days,
      )

  private val listings = FakeListingRepository(listOf(drill()))

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    bookings = FakeBookingRepository().apply { listingBookings = listOf(cy, bob, ann) }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun newViewModel() =
      ReceivedRequestsViewModel("drill", bookings, listings, profiles, today).also {
        testDispatcher.scheduler.advanceUntilIdle()
      }

  @Test
  fun loads_theWaitingRequestsEarliestFirst_withTheRenterNames() = runTest {
    val viewModel = newViewModel()

    val state = viewModel.uiState.value
    assertTrue(state.hasLoaded)
    assertFalse(state.isLoading)
    assertEquals(listOf(ann, bob, cy), state.requests)
    // Cy has no profile: shown without a name.
    assertEquals(mapOf("uid-ann" to "ann_m", "uid-bob" to "bob.k"), state.renterNames)
  }

  @Test
  fun loads_onlyThisListingsWaitingRequests_andHidesPastOnes() = runTest {
    // An accepted booking is not a request: BookingViewModel follows it on the screen instead.
    val accepted = booking("ok", "uid-ann", days(28..28), BookingStatus.CONFIRMED)
    bookings.listingBookings =
        listOf(
            ann,
            accepted,
            booking("gone", "uid-bob", days(23..23), BookingStatus.CANCELLED),
            booking("past", "uid-bob", days(10..17)),
            booking("other", "uid-bob", days(20..20), listingId = "tent"),
        )

    val state = newViewModel().uiState.value

    assertEquals(listOf(ann), state.requests)
  }

  @Test
  fun aRequestStartedBeforeToday_isStillShownWhileItHasDaysToCome() = runTest {
    val ongoing = booking("now", "uid-ann", days(17..19))
    bookings.listingBookings = listOf(ongoing)

    assertEquals(listOf(ongoing), newViewModel().uiState.value.requests)
  }

  @Test
  fun conflictsOf_areTheOtherWaitingRequestsSharingADay() = runTest {
    val state = newViewModel().uiState.value

    assertEquals(listOf(bob), state.conflictsOf(ann))
    assertEquals(listOf(ann), state.conflictsOf(bob))
    assertEquals(emptyList<Booking>(), state.conflictsOf(cy))
  }

  @Test
  fun aLoadFailure_isShown_andRetryLoads() = runTest {
    bookings.loadFailure = UnknownHostException("offline")
    val viewModel = newViewModel()
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.hasLoaded)

    bookings.loadFailure = null
    viewModel.loadRequests()
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.uiState.value.error)
    assertEquals(listOf(ann, bob, cy), viewModel.uiState.value.requests)
  }

  @Test
  fun aFailedName_isReadAgainOnTheNextLoad() = runTest {
    profiles.failingUids += "uid-ann"
    val viewModel = newViewModel()
    assertNull(viewModel.uiState.value.renterNames["uid-ann"])

    profiles.failingUids.clear()
    viewModel.loadRequests()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals("ann_m", viewModel.uiState.value.renterNames["uid-ann"])
    // Bob's name was read the first time, and is not read again.
    assertEquals(1, profiles.lookups["uid-bob"])
  }

  @Test
  fun accept_confirmsTheRequest_andRemovesTheRequestsSharingItsDays() = runTest {
    val viewModel = newViewModel()

    viewModel.accept(ann)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf(ann), bookings.accepted)
    val state = viewModel.uiState.value
    assertEquals(listOf(cy), state.requests)
    assertNull(state.acceptingId)
    // Read again after the accept, to catch requests sent since the screen loaded.
    assertEquals(2, bookings.loadCount)
  }

  @Test
  fun accept_whileRunning_disablesAnotherAccept() = runTest {
    val gate = CompletableDeferred<Unit>()
    bookings.acceptGate = gate
    val viewModel = newViewModel()

    viewModel.accept(ann)
    viewModel.accept(cy)
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals("ann", viewModel.uiState.value.acceptingId)

    gate.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(listOf(ann), bookings.accepted)
    assertNull(viewModel.uiState.value.acceptingId)
  }

  @Test
  fun accept_ignoresABookingThatIsNotWaiting() = runTest {
    val viewModel = newViewModel()

    viewModel.accept(booking("unknown", "uid-ann", days(20..20)))
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(emptyList<Booking>(), bookings.accepted)
  }

  @Test
  fun aFailedAccept_keepsTheRequests_andSaysWhy() = runTest {
    bookings.acceptFailure = UnknownHostException("offline")
    val viewModel = newViewModel()

    viewModel.accept(ann)
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(AppError.NETWORK, state.acceptError)
    assertEquals(listOf(ann, bob, cy), state.requests)
    assertNull(state.acceptingId)

    bookings.acceptFailure = null
    viewModel.accept(ann)
    testDispatcher.scheduler.advanceUntilIdle()
    assertNull(viewModel.uiState.value.acceptError)
  }

  @Test
  fun aFailedReloadAfterAnAccept_keepsTheAcceptShown() = runTest {
    val viewModel = newViewModel()
    bookings.loadFailure = UnknownHostException("offline")

    viewModel.accept(ann)
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(listOf(cy), state.requests)
    // Its days stay taken off the listing.
    assertFalse(state.canAccept(bob))
  }

  @Test
  fun aRequestWithADayNoLongerOffered_isListedButCannotBeAccepted() = runTest {
    // The owner edited the listing after the request was sent: saving dropped the past days, so
    // the 17th, the first day of this ongoing request, is no longer offered.
    val ongoing = booking("now", "uid-ann", days(17..19))
    bookings.listingBookings = listOf(ongoing, cy)
    listings.allListings = listOf(drill(days(18..30)))
    val viewModel = newViewModel()

    val state = viewModel.uiState.value
    assertEquals(listOf(ongoing, cy), state.requests)
    assertFalse(state.canAccept(ongoing))
    assertTrue(state.canAccept(cy))

    viewModel.accept(ongoing)
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(emptyList<Booking>(), bookings.accepted)
    assertNull(viewModel.uiState.value.acceptingId)
  }

  @Test
  fun aRequestSharingADayWithAnAcceptedBooking_cannotBeAccepted() = runTest {
    // Left waiting by the accept race: the accept took the 21st and 22nd off the listing.
    val confirmed = booking("ok", "uid-cy", days(21..22), BookingStatus.CONFIRMED)
    bookings.listingBookings = listOf(confirmed, ann, cy)
    listings.allListings = listOf(drill(days(17..20) + days(23..30)))

    val state = newViewModel().uiState.value

    assertEquals(listOf(ann, cy), state.requests)
    assertFalse(state.canAccept(ann))
    assertTrue(state.canAccept(cy))
  }

  @Test
  fun accept_takesTheAcceptedDaysOffTheOfferedDays() = runTest {
    val gate = CompletableDeferred<Unit>()
    val viewModel = newViewModel()
    // Hold back the reload, to see the state right after the accept.
    bookings.pendingLoad = gate

    viewModel.accept(ann)
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.offeredDays.any { it in ann.days })
    assertTrue(viewModel.uiState.value.canAccept(cy))
    gate.complete(Unit)
  }

  @Test
  fun aListingReadFailure_isShownAsALoadFailure_andRetryLoads() = runTest {
    listings.failure = UnknownHostException("offline")
    val viewModel = newViewModel()
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.hasLoaded)

    listings.failure = null
    viewModel.loadRequests()
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.canAccept(ann))
  }

  @Test
  fun loadRequests_isIgnoredWhileALoadRuns() = runTest {
    val gate = CompletableDeferred<Unit>()
    bookings.pendingLoad = gate
    val viewModel = newViewModel()
    assertTrue(viewModel.uiState.value.isLoading)

    viewModel.loadRequests()
    viewModel.accept(ann)
    gate.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, bookings.loadCount)
    assertEquals(emptyList<Booking>(), bookings.accepted)
  }
}
