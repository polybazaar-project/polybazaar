// Co-authored-by: Copilot (Auto)
// Co-authored-by: Claude Code (Claude Fable 5.2)
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.booking.viewmodel

import androidx.lifecycle.ViewModelStore
import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingStatus
import com.android.polybazaar.booking.model.FakeBookingRepository
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookingViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val booking =
      Booking(
          listingId = "listing",
          renterUid = "renter",
          lenderUid = "lender",
          days = setOf(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3)),
      )

  private lateinit var repository: FakeBookingRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = FakeBookingRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun requestBooking_queuesAndExposesQueuedState() = runTest {
    val viewModel = BookingViewModel(repository)

    viewModel.requestBooking(booking)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(booking, repository.requestedBooking)
    assertEquals(BookingUiState(isQueued = true), viewModel.uiState.value)
  }

  @Test
  fun acknowledgeQueuedBooking_allowsAnotherRequest() = runTest {
    val viewModel = BookingViewModel(repository)

    viewModel.requestBooking(booking)
    testDispatcher.scheduler.advanceUntilIdle()
    viewModel.acknowledgeQueuedBooking()
    viewModel.requestBooking(booking.copy(days = setOf(LocalDate.of(2026, 12, 4))))
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(2, repository.requestCount)
    assertTrue(viewModel.uiState.value.isQueued)
  }

  @Test
  fun requestBooking_showsPendingAndIgnoresDuplicateRequest() = runTest {
    val gate = CompletableDeferred<Unit>()
    repository.pendingRequest = gate
    val viewModel = BookingViewModel(repository)

    viewModel.requestBooking(booking)
    testDispatcher.scheduler.runCurrent()
    viewModel.requestBooking(booking.copy(listingId = "other-listing"))

    assertTrue(viewModel.uiState.value.isSubmitting)
    assertEquals(1, repository.requestCount)

    gate.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value.isQueued)
    assertFalse(viewModel.uiState.value.isSubmitting)
  }

  @Test
  fun requestBooking_reportsFailureAfterLocalQueueAndAllowsRetry() = runTest {
    val viewModel = BookingViewModel(repository)

    viewModel.requestBooking(booking)
    testDispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value.isQueued)

    repository.writeFailure?.invoke(AppException(AppError.NETWORK))

    assertFalse(viewModel.uiState.value.isQueued)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)

    repository.writeFailure = null
    viewModel.requestBooking(booking)
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value.isQueued)
    assertEquals(null, viewModel.uiState.value.error)
  }

  @Test
  fun requestBooking_reportsWriteFailureBeforeRepositoryReturns() = runTest {
    repository.immediateWriteFailure = AppException(AppError.PERMISSION_DENIED)
    val viewModel = BookingViewModel(repository)

    viewModel.requestBooking(booking)
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isSubmitting)
    assertFalse(viewModel.uiState.value.isQueued)
    assertEquals(AppError.PERMISSION_DENIED, viewModel.uiState.value.error)
  }

  @Test
  fun requestBooking_exposesFailureAndAllowsRetry() = runTest {
    repository.failure = AppException(AppError.NETWORK)
    val viewModel = BookingViewModel(repository)

    viewModel.requestBooking(booking)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isSubmitting)

    repository.failure = null
    viewModel.requestBooking(booking)
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value.isQueued)
    assertEquals(null, viewModel.uiState.value.error)
  }

  @Test
  fun requestBooking_cancellationIsNotExposedAsAnAppError() = runTest {
    repository.failure = CancellationException("screen closed")
    val viewModel = BookingViewModel(repository)

    viewModel.requestBooking(booking)
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isSubmitting)
    assertFalse(viewModel.uiState.value.isQueued)
    assertEquals(null, viewModel.uiState.value.error)
    assertTrue(repository.requestJob?.isCancelled == true)
  }

  @Test
  fun loadRenterBookings_exposesLoadingAndResults() = runTest {
    val expected = listOf(booking.copy(id = "booking-id"))
    val gate = CompletableDeferred<Unit>()
    repository.renterBookings = expected
    repository.pendingLoad = gate
    val viewModel = BookingViewModel(repository)

    viewModel.loadRenterBookings()
    testDispatcher.scheduler.runCurrent()

    assertTrue(viewModel.uiState.value.isLoadingBookings)
    gate.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(expected, viewModel.uiState.value.bookings)
    assertFalse(viewModel.uiState.value.isLoadingBookings)
  }

  @Test
  fun loadRenterBookings_exposesFailures() = runTest {
    repository.loadFailure = AppException(AppError.NETWORK)
    val viewModel = BookingViewModel(repository)

    viewModel.loadRenterBookings()
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoadingBookings)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
  }

  @Test
  fun cancelBooking_showsCancelledOnceQueuedAndPreventsDuplicates() = runTest {
    repository.renterBookings = listOf(booking.copy(id = "booking-id"))
    repository.pendingCancel = CompletableDeferred()
    val viewModel = BookingViewModel(repository)
    viewModel.loadRenterBookings()
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.runCurrent()
    viewModel.cancelBooking("booking-id")

    assertEquals(1, repository.cancellationCount)
    assertTrue("booking-id" in viewModel.uiState.value.cancellingBookingIds)

    repository.pendingCancel?.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value.cancellingBookingIds.isEmpty())
    assertEquals(BookingStatus.CANCELLED, viewModel.uiState.value.bookings.single().status)
  }

  @Test
  fun cancelBooking_restoresStatusAndExposesErrorWhenWriteIsRejectedLater() = runTest {
    repository.renterBookings = listOf(booking.copy(id = "booking-id"))
    val viewModel = BookingViewModel(repository)
    viewModel.loadRenterBookings()
    testDispatcher.scheduler.advanceUntilIdle()
    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(BookingStatus.CANCELLED, viewModel.uiState.value.bookings.single().status)

    repository.cancelWriteFailure?.invoke(AppException(AppError.PERMISSION_DENIED))

    assertEquals(BookingStatus.REQUESTED, viewModel.uiState.value.bookings.single().status)
    assertEquals(AppError.PERMISSION_DENIED, viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.cancellingBookingIds.isEmpty())

    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(2, repository.cancellationCount)
  }

  @Test
  fun cancelBooking_keepsStatusWhenWriteFailsBeforeReturning() = runTest {
    repository.renterBookings = listOf(booking.copy(id = "booking-id"))
    repository.immediateCancelWriteFailure = AppException(AppError.NETWORK)
    val viewModel = BookingViewModel(repository)
    viewModel.loadRenterBookings()
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(BookingStatus.REQUESTED, viewModel.uiState.value.bookings.single().status)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.cancellingBookingIds.isEmpty())
  }

  @Test
  fun cancelBooking_exposesLateWriteFailureAfterViewModelIsCleared() = runTest {
    val viewModel = BookingViewModel(repository)
    val viewModelStore = ViewModelStore()
    viewModelStore.put("booking", viewModel)

    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.advanceUntilIdle()

    viewModelStore.clear()
    repository.cancelWriteFailure?.invoke(AppException(AppError.NETWORK))

    assertTrue(viewModel.uiState.value.cancellingBookingIds.isEmpty())
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
  }

  @Test
  fun loadRenterBookings_ignoresDuplicateLoadWhileLoading() = runTest {
    repository.pendingLoad = CompletableDeferred()
    val viewModel = BookingViewModel(repository)

    viewModel.loadRenterBookings()
    testDispatcher.scheduler.runCurrent()
    viewModel.loadRenterBookings()
    testDispatcher.scheduler.runCurrent()

    assertEquals(1, repository.loadCount)
  }

  @Test
  fun loadRenterBookings_cancellationIsNotExposedAsAnAppError() = runTest {
    repository.loadFailure = CancellationException("screen closed")
    val viewModel = BookingViewModel(repository)

    viewModel.loadRenterBookings()
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoadingBookings)
    assertEquals(null, viewModel.uiState.value.error)
  }

  @Test
  fun cancelBooking_ignoresAlreadyCancelledBooking() = runTest {
    repository.renterBookings =
        listOf(booking.copy(id = "booking-id", status = BookingStatus.CANCELLED))
    val viewModel = BookingViewModel(repository)
    viewModel.loadRenterBookings()
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(0, repository.cancellationCount)
    assertTrue(viewModel.uiState.value.cancellingBookingIds.isEmpty())
  }

  @Test
  fun cancelBooking_onlyCancelsTheMatchingBooking() = runTest {
    val other = booking.copy(id = "other-id", status = BookingStatus.CONFIRMED)
    repository.renterBookings = listOf(booking.copy(id = "booking-id"), other)
    val viewModel = BookingViewModel(repository)
    viewModel.loadRenterBookings()
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.advanceUntilIdle()

    val bookings = viewModel.uiState.value.bookings
    assertEquals(BookingStatus.CANCELLED, bookings.first { it.id == "booking-id" }.status)
    assertEquals(other, bookings.first { it.id == "other-id" })
  }

  @Test
  fun cancelBooking_exposesRepositoryFailureAndAllowsRetry() = runTest {
    repository.cancelFailure = AppException(AppError.PERMISSION_DENIED)
    val viewModel = BookingViewModel(repository)

    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(AppError.PERMISSION_DENIED, viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.cancellingBookingIds.isEmpty())

    repository.cancelFailure = null
    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(2, repository.cancellationCount)
    assertEquals(null, viewModel.uiState.value.error)
  }

  @Test
  fun cancelBooking_cancellationIsNotExposedAsAnAppError() = runTest {
    repository.cancelFailure = CancellationException("screen closed")
    val viewModel = BookingViewModel(repository)

    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value.cancellingBookingIds.isEmpty())
    assertEquals(null, viewModel.uiState.value.error)
  }
}
