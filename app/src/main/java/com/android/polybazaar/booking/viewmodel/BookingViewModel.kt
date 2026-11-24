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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

enum class ConfirmationStage(
    val step: Int,
    val party: BookingParty?,
    val action: ConfirmationAction?,
) {
  HANDOVER(1, BookingParty.LENDER, ConfirmationAction.NOTIFY_HANDOVER),
  HANDOVER_CONFIRMATION(2, BookingParty.RENTER, ConfirmationAction.CONFIRM_HANDOVER),
  RETURN(3, BookingParty.RENTER, ConfirmationAction.NOTIFY_RETURN),
  RETURN_CONFIRMATION(4, BookingParty.LENDER, ConfirmationAction.CONFIRM_RETURN),
  COMPLETED(5, null, null),
}

data class ConfirmationFailure(val action: ConfirmationAction, val error: AppError)

val Booking.confirmationStage: ConfirmationStage
  get() =
      when {
        !handoverNotified -> ConfirmationStage.HANDOVER
        !renterHandoverConfirmed -> ConfirmationStage.HANDOVER_CONFIRMATION
        !returnNotified -> ConfirmationStage.RETURN
        !lenderReturnConfirmed -> ConfirmationStage.RETURN_CONFIRMATION
        else -> ConfirmationStage.COMPLETED
      }

/** The action available to this party at the current stage, if any. */
fun Booking.confirmationAction(party: BookingParty): ConfirmationAction? {
  if (status != BookingStatus.CONFIRMED) return null
  val stage = confirmationStage
  return stage.action.takeIf { stage.party == party }
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
    val observationError: AppError? = null,
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
  private val namingMutex = Mutex()

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
  fun observeRenterBookings() {
    party = BookingParty.RENTER
    observeBookings(
        observe = bookingRepository::observeBookingsForRenter,
        nameId = { it.listingId },
        name = { nameListings(it) },
    )
  }

  fun observeListingBookings(listingId: String) {
    party = BookingParty.LENDER
    observeBookings(
        observe = { bookingRepository.observeBookingsForListing(listingId) },
        nameId = { it.renterUid },
        name = { nameRenters(it) },
    )
  }

  fun stopObservingBookings() {
    bookingObservation?.cancel()
    bookingObservation = null
  }

  private fun observeBookings(
      observe: () -> Flow<ObservedBookings>,
      nameId: (Booking) -> String,
      name: suspend (Set<String>) -> Unit,
  ) {
    stopObservingBookings()
    _uiState.update { it.copy(isLoadingBookings = true) }
    bookingObservation = viewModelScope.launch {
      try {
        coroutineScope {
          val ids = MutableStateFlow<Set<String>>(emptySet())
          val nameJob = launch { ids.collect { name(it) } }
          try {
            observe().collect { observed ->
              val bookings =
                  observed.bookings.sortedWith(
                      compareBy({ it.days.minOrNull() ?: LocalDate.MAX }, { it.id })
                  )
              _uiState.update { state ->
                val cancellationObsolete =
                    state.failedCancelId != null &&
                        bookings.any {
                          it.id == state.failedCancelId &&
                              (it.status == BookingStatus.CANCELLED || it.handoverNotified)
                        }
                state.copy(
                    bookings = bookings,
                    pendingBookingIds = observed.pendingIds,
                    isLoadingBookings = false,
                    observationError = null,
                    failedCancelId = if (cancellationObsolete) null else state.failedCancelId,
                    error = if (cancellationObsolete) null else state.error,
                    submittingConfirmations =
                        state.submittingConfirmations.filter { (id, action) ->
                          bookings.any { it.id == id && !action.isRecorded(it) }
                        },
                    confirmationFailures =
                        state.confirmationFailures.filter { (id, failure) ->
                          bookings.any {
                            it.id == id &&
                                (id in observed.pendingIds || !failure.action.isRecorded(it))
                          }
                        },
                )
              }
              ids.value = bookings.mapTo(mutableSetOf(), nameId)
            }
          } finally {
            nameJob.cancel()
          }
        }
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        _uiState.update {
          it.copy(
              isLoadingBookings = false,
              observationError = exception.toAppError(),
              pendingBookingIds = emptySet(),
              submittingConfirmations = emptyMap(),
          )
        }
      }
    }
  }

  fun confirmBooking(id: String, action: ConfirmationAction) {
    val state = _uiState.value
    val booking = state.bookings.firstOrNull { it.id == id } ?: return
    val currentParty = party ?: return
    if (
        booking.confirmationAction(currentParty) != action ||
            state.observationError != null ||
            state.isLoadingBookings ||
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

  private suspend fun nameListings(ids: Set<String>): Unit = namingMutex.withLock {
    val repository = listingRepository ?: return
    for (listingId in ids) {
      if (listingId in _uiState.value.listingTitles) continue
      readOrNull { repository.getListing(listingId).getOrNull() }
          ?.let { listing ->
            _uiState.update {
              it.copy(listingTitles = it.listingTitles + (listingId to listing.title))
            }
          }
    }
  }

  private suspend fun nameRenters(ids: Set<String>): Unit = namingMutex.withLock {
    val repository = profileRepository ?: return
    for (uid in ids) {
      if (uid in _uiState.value.renterNames) continue
      readOrNull { repository.observeProfile(uid).first() }
          ?.let { user ->
            _uiState.update { it.copy(renterNames = it.renterNames + (uid to user.username)) }
          }
    }
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
