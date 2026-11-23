// Co-authored-by: Copilot (Auto)
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.booking.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingRepository
import com.android.polybazaar.booking.model.BookingStatus
import com.android.polybazaar.booking.model.ObservedBookings
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.profile.model.ProfileRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BookingParty {
  LENDER,
  RENTER,
}

enum class ConfirmationAction {
  NOTIFY_HANDOVER,
  CONFIRM_HANDOVER,
  NOTIFY_RETURN,
  CONFIRM_RETURN,
}

data class ConfirmationFailure(val action: ConfirmationAction, val error: AppError)

/** The action available to this party at the current stage, if any. */
fun Booking.confirmationAction(party: BookingParty): ConfirmationAction? {
  if (status != BookingStatus.CONFIRMED) return null
  return when {
    !handoverNotified -> ConfirmationAction.NOTIFY_HANDOVER.takeIf { party == BookingParty.LENDER }
    !renterHandoverConfirmed ->
        ConfirmationAction.CONFIRM_HANDOVER.takeIf { party == BookingParty.RENTER }
    !returnNotified -> ConfirmationAction.NOTIFY_RETURN.takeIf { party == BookingParty.RENTER }
    !lenderReturnConfirmed ->
        ConfirmationAction.CONFIRM_RETURN.takeIf { party == BookingParty.LENDER }
    else -> null
  }
}

private fun ConfirmationAction.isRecorded(booking: Booking): Boolean =
    when (this) {
      ConfirmationAction.NOTIFY_HANDOVER -> booking.handoverNotified
      ConfirmationAction.CONFIRM_HANDOVER -> booking.renterHandoverConfirmed
      ConfirmationAction.NOTIFY_RETURN -> booking.returnNotified
      ConfirmationAction.CONFIRM_RETURN -> booking.lenderReturnConfirmed
    }

data class BookingUiState(
    val isSubmitting: Boolean = false,
    /** True after local queuing; it does not mean the server confirmed or lender accepted it. */
    val isQueued: Boolean = false,
    val bookings: List<Booking> = emptyList(),
    val isLoadingBookings: Boolean = false,
    val cancellingBookingIds: Set<String> = emptySet(),
    val pendingBookingIds: Set<String> = emptySet(),
    val submittingConfirmations: Map<String, ConfirmationAction> = emptyMap(),
    val confirmationFailures: Map<String, ConfirmationFailure> = emptyMap(),
    /** The booking whose cancellation the server refused or that failed, so Retry can redo it. */
    val failedCancelId: String? = null,
    /**
     * Titles of the booked listings by listing id, filled in once read; a missing one is unknown.
     */
    val listingTitles: Map<String, String> = emptyMap(),
    /** Usernames of the renters by uid, filled in once read; a missing one is unknown. */
    val renterNames: Map<String, String> = emptyMap(),
    val error: AppError? = null,
)

/**
 * Submits a booking request through [bookingRepository] and lists bookings. [listingRepository] and
 * [profileRepository] only name the bookings (listing titles for a renter, renter usernames for a
 * lender); when a name cannot be read, the booking is listed without it.
 */
class BookingViewModel(
    private val bookingRepository: BookingRepository,
    private val listingRepository: ListingRepository? = null,
    private val profileRepository: ProfileRepository? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow(BookingUiState())
  val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()
  private var bookingObservation: Job? = null
  private var party: BookingParty? = null

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

  /** Loads bookings made by the signed-in renter, with the titles of their listings. */
  fun loadRenterBookings() =
      loadBookings(
          load = { bookingRepository.getBookingsForRenter() },
          name = { bookings -> nameListings(bookings) },
      )

  /** Loads the bookings of [listingId] for the signed-in lender, with their renters' names. */
  fun loadListingBookings(listingId: String) =
      loadBookings(
          load = { bookingRepository.getBookingsForListing(listingId) },
          name = { bookings -> nameRenters(bookings) },
      )

  fun observeRenterBookings() {
    party = BookingParty.RENTER
    observeBookings(
        observe = bookingRepository::observeBookingsForRenter,
        name = { nameListings(it) },
    )
  }

  fun observeListingBookings(listingId: String) {
    party = BookingParty.LENDER
    observeBookings(
        observe = { bookingRepository.observeBookingsForListing(listingId) },
        name = { nameRenters(it) },
    )
  }

  fun stopObservingBookings() {
    bookingObservation?.cancel()
    bookingObservation = null
  }

  private fun observeBookings(
      observe: () -> Flow<ObservedBookings>,
      name: suspend (List<Booking>) -> Unit,
  ) {
    stopObservingBookings()
    _uiState.update { it.copy(isLoadingBookings = true, failedCancelId = null, error = null) }
    bookingObservation = viewModelScope.launch {
      try {
        observe().collect { observed ->
          val bookings =
              observed.bookings.sortedWith(
                  compareBy({ it.days.minOrNull() ?: LocalDate.MAX }, { it.id })
              )
          _uiState.update { state ->
            state.copy(
                bookings = bookings,
                pendingBookingIds = observed.pendingIds,
                isLoadingBookings = false,
                submittingConfirmations =
                    state.submittingConfirmations.filter { (id, action) ->
                      bookings.any { it.id == id && !action.isRecorded(it) }
                    },
                confirmationFailures =
                    state.confirmationFailures.filter { (id, failure) ->
                      bookings.any {
                        it.id == id && (id in observed.pendingIds || !failure.action.isRecorded(it))
                      }
                    },
            )
          }
          viewModelScope.launch { name(bookings) }
        }
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        _uiState.update { it.copy(isLoadingBookings = false, error = exception.toAppError()) }
      }
    }
  }

  fun confirmBooking(id: String, action: ConfirmationAction) {
    val state = _uiState.value
    val booking = state.bookings.firstOrNull { it.id == id } ?: return
    val currentParty = party ?: return
    if (
        booking.confirmationAction(currentParty) != action ||
            id in state.pendingBookingIds ||
            id in state.submittingConfirmations
    )
        return

    _uiState.update {
      it.copy(
          submittingConfirmations = it.submittingConfirmations + (id to action),
          confirmationFailures = it.confirmationFailures - id,
      )
    }
    fun failed(exception: Exception) {
      _uiState.update {
        it.copy(
            submittingConfirmations = it.submittingConfirmations - id,
            confirmationFailures =
                it.confirmationFailures +
                    (id to ConfirmationFailure(action, exception.toAppError())),
        )
      }
    }
    viewModelScope.launch {
      try {
        when (action) {
          ConfirmationAction.NOTIFY_HANDOVER -> bookingRepository.notifyHandover(id, ::failed)
          ConfirmationAction.CONFIRM_HANDOVER -> bookingRepository.confirmHandover(id, ::failed)
          ConfirmationAction.NOTIFY_RETURN -> bookingRepository.notifyReturn(id, ::failed)
          ConfirmationAction.CONFIRM_RETURN -> bookingRepository.confirmReturn(id, ::failed)
        }
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        failed(exception)
      }
    }
  }

  private fun loadBookings(
      load: suspend () -> List<Booking>,
      name: suspend (List<Booking>) -> Unit,
  ) {
    if (_uiState.value.isLoadingBookings) return
    _uiState.update { it.copy(isLoadingBookings = true, failedCancelId = null, error = null) }
    viewModelScope.launch {
      try {
        // Earliest first; the ID only keeps the order stable between bookings of the same day.
        val bookings =
            load().sortedWith(compareBy({ it.days.minOrNull() ?: LocalDate.MAX }, { it.id }))
        _uiState.update { it.copy(bookings = bookings, isLoadingBookings = false) }
        name(bookings)
      } catch (exception: Exception) {
        if (exception is CancellationException) {
          _uiState.update { it.copy(isLoadingBookings = false) }
          throw exception
        }
        _uiState.update { it.copy(isLoadingBookings = false, error = exception.toAppError()) }
      }
    }
  }

  private suspend fun nameListings(bookings: List<Booking>) {
    val repository = listingRepository ?: return
    val titles = mutableMapOf<String, String>()
    for (listingId in bookings.map { it.listingId }.distinct()) {
      readOrNull { repository.getListing(listingId).getOrNull() }
          ?.let { titles[listingId] = it.title }
    }
    _uiState.update { it.copy(listingTitles = it.listingTitles + titles) }
  }

  private suspend fun nameRenters(bookings: List<Booking>) {
    val repository = profileRepository ?: return
    val names = mutableMapOf<String, String>()
    for (uid in bookings.map { it.renterUid }.distinct()) {
      readOrNull { repository.observeProfile(uid).first() }?.let { names[uid] = it.username }
    }
    _uiState.update { it.copy(renterNames = it.renterNames + names) }
  }

  /** A failed read is no error to show: the booking is simply listed without the name. */
  private suspend fun <T> readOrNull(read: suspend () -> T?): T? =
      try {
        read()
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        null
      }

  /**
   * Queues a cancellation and shows the booking as cancelled at once, also offline. If the server
   * later rejects the write, the booking returns to its previous status and the error is exposed,
   * with the booking in [BookingUiState.failedCancelId] so that the cancellation can be retried.
   * [BookingUiState.cancellingBookingIds] holds the booking only until the write is queued.
   */
  fun cancelBooking(id: String) {
    val previousStatus = _uiState.value.bookings.firstOrNull { it.id == id }?.status
    if (id in _uiState.value.cancellingBookingIds || previousStatus == BookingStatus.CANCELLED) {
      return
    }
    _uiState.update {
      it.copy(
          cancellingBookingIds = it.cancellingBookingIds + id,
          failedCancelId = null,
          error = null,
      )
    }
    viewModelScope.launch {
      var rejected = false
      try {
        bookingRepository.cancelBooking(id) { exception ->
          rejected = true
          _uiState.update {
            it.copy(
                bookings = it.bookings.withStatus(id, previousStatus),
                cancellingBookingIds = it.cancellingBookingIds - id,
                failedCancelId = id,
                error = exception.toAppError(),
            )
          }
        }
        if (!rejected) {
          _uiState.update {
            it.copy(
                bookings = it.bookings.withStatus(id, BookingStatus.CANCELLED),
                cancellingBookingIds = it.cancellingBookingIds - id,
            )
          }
        }
      } catch (exception: Exception) {
        _uiState.update { it.copy(cancellingBookingIds = it.cancellingBookingIds - id) }
        if (exception is CancellationException) throw exception
        _uiState.update { it.copy(failedCancelId = id, error = exception.toAppError()) }
      }
    }
  }

  private fun List<Booking>.withStatus(id: String, status: BookingStatus?): List<Booking> =
      if (status == null) this else map { if (it.id == id) it.copy(status = status) else it }
}
