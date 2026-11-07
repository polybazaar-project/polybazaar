// Co-authored-by: Copilot (Auto)
package com.android.polybazaar.booking.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingRepository
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
    val isSubmitted: Boolean = false,
    val error: AppError? = null,
)

/** Submits a booking request through [bookingRepository]. */
class BookingViewModel(private val bookingRepository: BookingRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(BookingUiState())
  val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()

  /**
   * Submits [booking] once. A successful return means the request was queued locally, not that the
   * lender accepted it or the server confirmed it.
   */
  fun requestBooking(booking: Booking) {
    if (_uiState.value.isSubmitting || _uiState.value.isSubmitted) return
    _uiState.update { it.copy(isSubmitting = true, error = null) }
    viewModelScope.launch {
      try {
        bookingRepository.requestBooking(booking)
        _uiState.update { it.copy(isSubmitting = false, isSubmitted = true) }
      } catch (exception: Exception) {
        if (exception is CancellationException) {
          _uiState.update { it.copy(isSubmitting = false) }
          throw exception
        }
        _uiState.update { it.copy(isSubmitting = false, error = exception.toAppError()) }
      }
    }
  }
}
