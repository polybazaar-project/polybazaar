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
import com.android.polybazaar.listing.model.ListingRepository
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
 * @property offeredDays the days the listing still offers, read with the bookings. See [canAccept].
 * @property hasLoaded true once the bookings were read at least once.
 * @property isLoading true while the bookings are being read.
 * @property acceptingId the id of the request being accepted, or null. Nothing else can be accepted
 *   meanwhile.
 * @property cancellingId the id of the accepted booking whose cancellation is being queued, or
 *   null. Nothing else can be accepted or cancelled meanwhile.
 * @property error why the bookings or the listing could not be read, or null.
 * @property acceptError why the last accept failed, or null. Cleared when the lender tries again.
 * @property cancelError why the last cancellation failed, or null. Cleared when the lender tries
 *   again.
 */
data class ReceivedRequestsUiState(
    val requests: List<Booking> = emptyList(),
    val accepted: List<Booking> = emptyList(),
    val renterNames: Map<String, String> = emptyMap(),
    val offeredDays: Set<LocalDate> = emptySet(),
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val acceptingId: String? = null,
    val cancellingId: String? = null,
    val error: AppError? = null,
    val acceptError: AppError? = null,
    val cancelError: AppError? = null,
) {
  /** Whether an accept or a cancellation is running, so that no other one can start. */
  val isAnswering: Boolean
    get() = acceptingId != null || cancellingId != null

  /**
   * Whether [request] can be accepted: every one of its days is still offered on the listing, as
   * the rules require. A request stays listed without Accept when the owner no longer offers one of
   * its days (an edit drops the past ones), or when another accepted booking took one.
   */
  fun canAccept(request: Booking): Boolean = request.days.all { it in offeredDays }

  /** The other waiting requests that accepting [request] cancels: those sharing one of its days. */
  fun conflictsOf(request: Booking): List<Booking> = requests.filter {
    it.id != request.id && it.overlaps(request)
  }
}

/** The booking's earliest day, which orders the lists. */
private val Booking.firstDay: LocalDate
  get() = days.min()

/**
 * Loads the bookings of the listing [listingId] for its owner, accepts the requests they choose and
 * cancels the bookings they no longer want to lend.
 *
 * @param listingRepository reads the days the listing still offers, which decide what can be
 *   accepted.
 * @param today the day the screen is opened on: bookings whose days are all before it are hidden.
 */
class ReceivedRequestsViewModel(
    private val listingId: String,
    private val bookingRepository: BookingRepository,
    private val listingRepository: ListingRepository,
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
   * Reads the bookings and the listing's offered days again, for example after a failure (Retry).
   * Ignored while a read, an accept or a cancellation is running. On failure what is already shown
   * stays shown.
   */
  fun loadRequests() {
    val state = _uiState.value
    if (state.isLoading || state.isAnswering) return
    _uiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      try {
        val bookings = bookingRepository.getBookingsForListing(listingId)
        val offeredDays = listingRepository.getListing(listingId).getOrThrow().availableDates
        show(bookings, offeredDays)
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
   * [BookingRepository.acceptBooking]). Ignored while a read, another accept or a cancellation is
   * running, for a booking that is not waiting for an answer, and for one that
   * [ReceivedRequestsUiState.canAccept] refuses. On failure the requests stay as they were and
   * [ReceivedRequestsUiState.acceptError] says why.
   */
  fun accept(request: Booking) {
    val state = _uiState.value
    if (state.isLoading || state.isAnswering) return
    if (state.requests.none { it.id == request.id } || !state.canAccept(request)) return
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
              // The accept took its days off the listing.
              offeredDays = it.offeredDays - request.days,
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

  /**
   * Cancels [booking], one of the accepted bookings, and takes it off the list as soon as the
   * cancellation is queued, also offline. If the server later refuses it, the booking comes back
   * and [ReceivedRequestsUiState.cancelError] says why. Its days stay off the listing: the lender
   * adds them back in "Edit listing" to lend them again. Ignored while a read, an accept or another
   * cancellation is running, and for a booking that is not accepted.
   */
  fun cancel(booking: Booking) {
    val state = _uiState.value
    if (state.isLoading || state.isAnswering) return
    if (state.accepted.none { it.id == booking.id }) return
    // Set before launching, so a second tap in the same frame is already refused.
    _uiState.update { it.copy(cancellingId = booking.id, cancelError = null) }
    viewModelScope.launch {
      // The failure can arrive while the cancellation is still being queued: then the booking
      // must stay listed rather than be removed after it was put back.
      var rejected = false
      try {
        bookingRepository.cancelBooking(booking.id) { exception ->
          rejected = true
          _uiState.update {
            it.copy(
                accepted =
                    if (it.accepted.any { kept -> kept.id == booking.id }) it.accepted
                    else (it.accepted + booking).sortedBy(Booking::firstDay),
                cancelError = exception.toAppError(),
            )
          }
        }
        if (!rejected) {
          _uiState.update {
            it.copy(accepted = it.accepted.filterNot { kept -> kept.id == booking.id })
          }
        }
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        _uiState.update { it.copy(cancelError = exception.toAppError()) }
      } finally {
        // Also on cancellation, as in loadRequests.
        _uiState.update { it.copy(cancellingId = null) }
      }
    }
  }

  private fun show(bookings: List<Booking>, offeredDays: Set<LocalDate>) {
    val upcoming =
        bookings
            .filter { booking -> booking.days.any { !it.isBefore(today) } }
            .sortedBy(Booking::firstDay)
    _uiState.update {
      it.copy(
          requests = upcoming.filter { booking -> booking.status == BookingStatus.REQUESTED },
          accepted = upcoming.filter { booking -> booking.status == BookingStatus.CONFIRMED },
          offeredDays = offeredDays,
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
