// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.booking.viewmodel

import com.android.polybazaar.auth.model.User
import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingStatus
import com.android.polybazaar.booking.model.FakeBookingRepository
import com.android.polybazaar.error.model.AppError
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

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    bookings = FakeBookingRepository(listOf(cy, bob, ann))
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun newViewModel() =
      ReceivedRequestsViewModel("drill", bookings, profiles, today).also {
        testDispatcher.scheduler.advanceUntilIdle()
      }

  @Test
  fun loads_theWaitingRequestsEarliestFirst_withTheRenterNames() = runTest {
    val viewModel = newViewModel()

    val state = viewModel.uiState.value
    assertTrue(state.hasLoaded)
    assertFalse(state.isLoading)
    assertEquals(listOf(ann, bob, cy), state.requests)
    assertEquals(emptyList<Booking>(), state.accepted)
    // Cy has no profile: shown without a name.
    assertEquals(mapOf("uid-ann" to "ann_m", "uid-bob" to "bob.k"), state.renterNames)
  }

  @Test
  fun loads_onlyThisListing_andHidesCancelledAndPastBookings() = runTest {
    val accepted = booking("ok", "uid-ann", days(28..28), BookingStatus.CONFIRMED)
    bookings.bookings =
        listOf(
            ann,
            accepted,
            booking("gone", "uid-bob", days(23..23), BookingStatus.CANCELLED),
            booking("past", "uid-bob", days(10..17)),
            booking("other", "uid-bob", days(20..20), listingId = "tent"),
        )

    val state = newViewModel().uiState.value

    assertEquals(listOf(ann), state.requests)
    assertEquals(listOf(accepted), state.accepted)
  }

  @Test
  fun aRequestStartedBeforeToday_isStillShownWhileItHasDaysToCome() = runTest {
    val ongoing = booking("now", "uid-ann", days(17..19))
    bookings.bookings = listOf(ongoing)

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
    bookings.readFailure = UnknownHostException("offline")
    val viewModel = newViewModel()
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.hasLoaded)

    bookings.readFailure = null
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
    assertEquals(listOf(ann.copy(status = BookingStatus.CONFIRMED)), state.accepted)
    assertNull(state.acceptingId)
    // Read again after the accept, to catch requests sent since the screen loaded.
    assertEquals(2, bookings.reads)
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
    bookings.readFailure = UnknownHostException("offline")

    viewModel.accept(ann)
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(listOf(cy), state.requests)
    assertEquals(listOf(ann.copy(status = BookingStatus.CONFIRMED)), state.accepted)
  }

  @Test
  fun loadRequests_isIgnoredWhileALoadRuns() = runTest {
    val gate = CompletableDeferred<Unit>()
    bookings.readGate = gate
    val viewModel = newViewModel()
    assertTrue(viewModel.uiState.value.isLoading)

    viewModel.loadRequests()
    viewModel.accept(ann)
    gate.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, bookings.reads)
    assertEquals(emptyList<Booking>(), bookings.accepted)
  }
}
