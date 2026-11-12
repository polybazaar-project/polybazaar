// Co-authored-by: Copilot (Auto)
// Co-authored-by: Claude Code (Claude Fable 5.2)
package com.android.polybazaar.booking.viewmodel

import com.android.polybazaar.booking.model.Booking
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
          startDate = LocalDate.of(2026, 12, 1),
          endDate = LocalDate.of(2026, 12, 3),
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
    viewModel.requestBooking(booking.copy(startDate = LocalDate.of(2026, 12, 4)))
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
}
