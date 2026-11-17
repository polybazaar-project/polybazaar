// Co-authored-by: Copilot (Auto)
package com.android.polybazaar.booking.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingRepository
import com.android.polybazaar.booking.model.BookingStatus
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookingUiState(
    val isSubmitting: Boolean = false,
    /** True after local queuing; it does not mean the server confirmed or lender accepted it. */
    val isQueued: Boolean = false,
    val bookings: List<Booking> = emptyList(),
    val isLoadingBookings: Boolean = false,
    val cancellingBookingIds: Set<String> = emptySet(),
    val error: AppError? = null,
)

/** Submits a booking request through [bookingRepository]. */
class BookingViewModel(private val bookingRepository: BookingRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(BookingUiState())
  val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()

  /**
   * Queues [booking] once. A queued request is not yet confirmed by the server or accepted by the
   * lender. A later Firestore write failure moves the state to an error and allows retry.
   */
  fun requestBooking(booking: Booking) {
    if (_uiState.value.isSubmitting || _uiState.value.isQueued) return
    _uiState.update { it.copy(isSubmitting = true, error = null) }
    viewModelScope.launch {
      try {
        bookingRepository.requestBooking(booking) { exception ->
          _uiState.update {
            it.copy(isSubmitting = false, isQueued = false, error = exception.toAppError())
          }
        }
        _uiState.update {
          if (it.isSubmitting) it.copy(isSubmitting = false, isQueued = true) else it
        }
      } catch (exception: Exception) {
        if (exception is CancellationException) {
          _uiState.update { it.copy(isSubmitting = false) }
          throw exception
        }
        _uiState.update { it.copy(isSubmitting = false, error = exception.toAppError()) }
      }
    }
  }

  /** Marks the queued submission as handled, allowing the user to request another booking. */
  fun acknowledgeQueuedBooking() {
    _uiState.update { it.copy(isQueued = false) }
  }

  /** Loads bookings made by the signed-in renter. */
  fun loadRenterBookings() {
    if (_uiState.value.isLoadingBookings) return
    _uiState.update { it.copy(isLoadingBookings = true, error = null) }
    viewModelScope.launch {
      try {
        val bookings = bookingRepository.getBookingsForRenter()
        _uiState.update { it.copy(bookings = bookings, isLoadingBookings = false) }
      } catch (exception: Exception) {
        if (exception is CancellationException) {
          _uiState.update { it.copy(isLoadingBookings = false) }
          throw exception
        }
        _uiState.update { it.copy(isLoadingBookings = false, error = exception.toAppError()) }
      }
    }
  }

  /** Queues one cancellation at a time per booking and waits for server acceptance or rejection. */
  fun cancelBooking(id: String) {
    val state = _uiState.value
    if (id in state.cancellingBookingIds) return
    if (state.bookings.any { it.id == id && it.status == BookingStatus.CANCELLED }) return
    _uiState.update {
      it.copy(cancellingBookingIds = it.cancellingBookingIds + id, error = null)
    }
    viewModelScope.launch {
      try {
        bookingRepository.cancelBooking(
            id = id,
            onWriteFailure = { exception ->
              _uiState.update {
                it.copy(
                    cancellingBookingIds = it.cancellingBookingIds - id,
                    error = exception.toAppError(),
                )
              }
            },
            onWriteSuccess = {
              _uiState.update {
                it.copy(
                    bookings =
                        it.bookings.map { booking ->
                          if (booking.id == id) booking.copy(status = BookingStatus.CANCELLED)
                          else booking
                        },
                    cancellingBookingIds = it.cancellingBookingIds - id,
                )
              }
            },
        )
      } catch (exception: Exception) {
        _uiState.update { it.copy(cancellingBookingIds = it.cancellingBookingIds - id) }
        if (exception is CancellationException) throw exception
        _uiState.update { it.copy(error = exception.toAppError()) }
      }
    }
  }
}
