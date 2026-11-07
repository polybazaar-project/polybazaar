// Co-authored-by: Copilot (Auto)
package com.android.polybazaar.booking.viewmodel

import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingRepository
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    Dispatchers.setMain(UnconfinedTestDispatcher())
    repository = FakeBookingRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun requestBooking_submitsAndExposesSuccess() = runTest {
    val viewModel = BookingViewModel(repository)

    viewModel.requestBooking(booking)

    assertEquals(booking, repository.requestedBooking)
    assertEquals(BookingUiState(isSubmitted = true), viewModel.uiState.value)
  }

  @Test
  fun requestBooking_showsPendingAndIgnoresDuplicateRequest() = runTest {
    val gate = CompletableDeferred<Unit>()
    repository.pendingRequest = gate
    val viewModel = BookingViewModel(repository)

    viewModel.requestBooking(booking)
    viewModel.requestBooking(booking.copy(listingId = "other-listing"))

    assertTrue(viewModel.uiState.value.isSubmitting)
    assertEquals(1, repository.requestCount)

    gate.complete(Unit)

    assertTrue(viewModel.uiState.value.isSubmitted)
    assertFalse(viewModel.uiState.value.isSubmitting)
  }

  @Test
  fun requestBooking_exposesFailureAndAllowsRetry() = runTest {
    repository.failure = AppException(AppError.NETWORK)
    val viewModel = BookingViewModel(repository)

    viewModel.requestBooking(booking)

    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isSubmitting)

    repository.failure = null
    viewModel.requestBooking(booking)

    assertTrue(viewModel.uiState.value.isSubmitted)
    assertEquals(null, viewModel.uiState.value.error)
  }
}

private class FakeBookingRepository : BookingRepository {
  var requestedBooking: Booking? = null
  var requestCount = 0
  var pendingRequest: CompletableDeferred<Unit>? = null
  var failure: Exception? = null

  override suspend fun requestBooking(booking: Booking) {
    requestedBooking = booking
    requestCount++
    pendingRequest?.await()
    failure?.let { throw it }
  }

  override suspend fun getBookingsForListing(listingId: String): List<Booking> = emptyList()
}
