// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.booking.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingRepository
import com.android.polybazaar.booking.model.BookingStatus
import com.android.polybazaar.booking.model.overlaps
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import com.android.polybazaar.profile.model.ProfileRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the screen where a lender answers the booking requests on one of their listings.
 *
 * Only bookings with a day from today on are shown: a request whose days have all passed can no
 * longer be lent, and an accepted one is over.
 *
 * @property requests the requests waiting for an answer, earliest first day first.
 * @property accepted the requests the lender accepted, earliest first day first.
 * @property renterNames the username of each renter whose profile could be read, by uid. A renter
 *   missing here is shown without a name.
 * @property hasLoaded true once the bookings were read at least once.
 * @property isLoading true while the bookings are being read.
 * @property acceptingId the id of the request being accepted, or null. Nothing else can be accepted
 *   meanwhile.
 * @property error why the bookings could not be read, or null.
 * @property acceptError why the last accept failed, or null. Cleared when the lender tries again.
 */
data class ReceivedRequestsUiState(
    val requests: List<Booking> = emptyList(),
    val accepted: List<Booking> = emptyList(),
    val renterNames: Map<String, String> = emptyMap(),
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val acceptingId: String? = null,
    val error: AppError? = null,
    val acceptError: AppError? = null,
) {
  /** The other waiting requests that accepting [request] cancels: those sharing one of its days. */
  fun conflictsOf(request: Booking): List<Booking> = requests.filter {
    it.id != request.id && it.overlaps(request)
  }
}

/** The booking's earliest day, which orders the lists. */
private val Booking.firstDay: LocalDate
  get() = days.min()

/**
 * Loads the bookings of the listing [listingId] for its owner, and accepts the one they choose.
 *
 * @param today the day the screen is opened on: bookings whose days are all before it are hidden.
 */
class ReceivedRequestsViewModel(
    private val listingId: String,
    private val bookingRepository: BookingRepository,
    private val profileRepository: ProfileRepository,
    private val today: LocalDate = LocalDate.now(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(ReceivedRequestsUiState())
  val uiState: StateFlow<ReceivedRequestsUiState> = _uiState.asStateFlow()

  /** The renters whose name was read or is being read, so that each is read once. */
  private val namesRequested = mutableSetOf<String>()

  init {
    loadRequests()
  }

  /**
   * Reads the bookings again, for example after a failure (Retry). Ignored while a read or an
   * accept is running. On failure the bookings already shown stay shown.
   */
  fun loadRequests() {
    val state = _uiState.value
    if (state.isLoading || state.acceptingId != null) return
    _uiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      try {
        show(bookingRepository.getBookingsForListing(listingId))
      } catch (exception: Exception) {
        // Cancellation means the screen was closed: it is not an error to show.
        if (exception is CancellationException) throw exception
        _uiState.update { it.copy(error = exception.toAppError()) }
      } finally {
        // Also on cancellation, otherwise the guard above would block every later read.
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  /**
   * Accepts [request], which cancels the other requests sharing one of its days (see
   * [BookingRepository.acceptBooking]). Ignored while a read or another accept is running, and for
   * a booking that is not waiting for an answer. On failure the requests stay as they were and
   * [ReceivedRequestsUiState.acceptError] says why.
   */
  fun accept(request: Booking) {
    val state = _uiState.value
    if (state.isLoading || state.acceptingId != null) return
    if (state.requests.none { it.id == request.id }) return
    // Set before launching, so a second tap in the same frame is already refused.
    _uiState.update { it.copy(acceptingId = request.id, acceptError = null) }
    viewModelScope.launch {
      try {
        bookingRepository.acceptBooking(request)
        // Shown from what was saved, then read again below: the repository may also have cancelled
        // a request sent since the screen loaded. The request shares its own days, so it leaves
        // the waiting list with those it cancels.
        _uiState.update {
          it.copy(
              requests = it.requests.filterNot { booking -> booking.overlaps(request) },
              accepted =
                  (it.accepted + request.copy(status = BookingStatus.CONFIRMED)).sortedBy(
                      Booking::firstDay
                  ),
          )
        }
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        _uiState.update { it.copy(acceptError = exception.toAppError()) }
        return@launch
      } finally {
        // Also on cancellation, as in loadRequests.
        _uiState.update { it.copy(acceptingId = null) }
      }
      loadRequests()
    }
  }

  private fun show(bookings: List<Booking>) {
    val upcoming =
        bookings
            .filter { booking -> booking.days.any { !it.isBefore(today) } }
            .sortedBy(Booking::firstDay)
    _uiState.update {
      it.copy(
          requests = upcoming.filter { booking -> booking.status == BookingStatus.REQUESTED },
          accepted = upcoming.filter { booking -> booking.status == BookingStatus.CONFIRMED },
          hasLoaded = true,
      )
    }
    upcoming.map { it.renterUid }.distinct().forEach { loadRenterName(it) }
  }

  /**
   * Reads the username of [renterUid] into [ReceivedRequestsUiState.renterNames], in its own
   * coroutine so the list never waits for it. On failure the renter is shown without a name, and
   * the next read of the bookings tries again.
   */
  private fun loadRenterName(renterUid: String) {
    if (!namesRequested.add(renterUid)) return
    viewModelScope.launch {
      try {
        val renter = profileRepository.observeProfile(renterUid).first()
        if (renter != null) {
          _uiState.update { it.copy(renterNames = it.renterNames + (renterUid to renter.username)) }
        } else {
          namesRequested.remove(renterUid)
        }
      } catch (exception: Exception) {
        namesRequested.remove(renterUid)
        if (exception is CancellationException) throw exception
        // Logged when unexpected, but not shown: a name is not worth an error.
        exception.toAppError()
      }
    }
  }
}
