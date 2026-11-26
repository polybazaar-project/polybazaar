// Co-authored-by: Copilot (Auto)
// Co-authored-by: Claude Code (Claude Fable 5.2)
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.booking.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

/**
 * A [BookingRepository] in memory, for tests. It records the last booking requested and how many
 * were, and can hold a request back ([pendingRequest]), make it throw ([failure]) or report a write
 * failure, at once ([immediateWriteFailure]) or later through [writeFailure].
 *
 * [getBookingsForListing] returns the [listingBookings] of that listing. Like
 * [getBookingsForRenter], it is counted in [loadCount], waits for [pendingLoad] when set and fails
 * with [loadFailure] when set. [acceptBooking] changes [listingBookings] as the real repository
 * does: the booking is confirmed and the other requests sharing a day with it cancelled. It waits
 * for [acceptGate] when set and fails with [acceptFailure] when set, leaving [listingBookings]
 * unchanged.
 *
 * [getBookingsForLender] returns [lenderBookings], or throws [lenderLoadFailure], after
 * [pendingLenderLoad]; it has its own fields so that a screen loading both kinds of bookings can
 * fail one alone. Handover and return operations record their booking IDs without enforcing rules.
 */
class FakeBookingRepository : BookingRepository {
  var requestedBooking: Booking? = null
  var requestCount = 0
  var pendingRequest: CompletableDeferred<Unit>? = null
  var failure: Exception? = null
  var immediateWriteFailure: Exception? = null
  var writeFailure: ((Exception) -> Unit)? = null
  var requestJob: Job? = null
  var renterBookings: List<Booking> = emptyList()
  var listingBookings: List<Booking> = emptyList()
  var lenderBookings: List<Booking> = emptyList()
  var pendingLenderLoad: CompletableDeferred<Unit>? = null
  var lenderLoadFailure: Exception? = null
  var lenderLoadCount = 0
  var pendingLoad: CompletableDeferred<Unit>? = null
  var loadFailure: Exception? = null
  var loadCount = 0
  var cancellationCount = 0
  var cancelFailure: Exception? = null
  var pendingCancel: CompletableDeferred<Unit>? = null
  var cancelWriteFailure: ((Exception) -> Unit)? = null
  var immediateCancelWriteFailure: Exception? = null
  val handoverNotifications = mutableListOf<String>()
  val handoverConfirmations = mutableListOf<String>()
  val returnNotifications = mutableListOf<String>()
  val returnConfirmations = mutableListOf<String>()
  val bookingUpdates = MutableSharedFlow<ObservedBookings>(extraBufferCapacity = 1)
  var bookingUpdateFailure: Exception? = null
  var confirmationWriteFailure: ((Exception) -> Unit)? = null
  var immediateConfirmationFailure: Exception? = null
  val dismissedConfirmations = mutableListOf<String>()
  var dismissConfirmationError: Exception? = null

  val accepted = mutableListOf<Booking>()
  var acceptGate: CompletableDeferred<Unit>? = null
  var acceptFailure: Exception? = null

  override suspend fun requestBooking(booking: Booking, onWriteFailure: (Exception) -> Unit) {
    requestJob = currentCoroutineContext()[Job]
    requestedBooking = booking
    requestCount++
    writeFailure = onWriteFailure
    pendingRequest?.await()
    immediateWriteFailure?.let(onWriteFailure)
    failure?.let { throw it }
  }

  override suspend fun getBookingsForListing(listingId: String): List<Booking> {
    loadCount++
    pendingLoad?.await()
    loadFailure?.let { throw it }
    return listingBookings.filter { it.listingId == listingId }
  }

  override suspend fun getBookingsForLender(): List<Booking> {
    lenderLoadCount++
    pendingLenderLoad?.await()
    lenderLoadFailure?.let { throw it }
    return lenderBookings
  }

  override suspend fun acceptBooking(booking: Booking) {
    accepted += booking
    acceptGate?.await()
    acceptFailure?.let { throw it }
    listingBookings = listingBookings.map {
      when {
        it.id == booking.id -> it.copy(status = BookingStatus.CONFIRMED)
        it.status == BookingStatus.REQUESTED &&
            it.listingId == booking.listingId &&
            it.overlaps(booking) -> it.copy(status = BookingStatus.CANCELLED)
        else -> it
      }
    }
  }

  override suspend fun getBookingsForRenter(): List<Booking> {
    loadCount++
    pendingLoad?.await()
    loadFailure?.let { throw it }
    return renterBookings
  }

  override fun observeBookingsForListing(listingId: String): Flow<ObservedBookings> = flow {
    emit(ObservedBookings(getBookingsForListing(listingId)))
    emitAll(bookingUpdates.onEach { bookingUpdateFailure?.let { throw it } })
  }

  override fun observeBookingsForRenter(): Flow<ObservedBookings> = flow {
    emit(ObservedBookings(getBookingsForRenter()))
    emitAll(bookingUpdates.onEach { bookingUpdateFailure?.let { throw it } })
  }

  override suspend fun cancelBooking(id: String, onWriteFailure: (Exception) -> Unit) {
    cancellationCount++
    cancelWriteFailure = onWriteFailure
    pendingCancel?.await()
    immediateCancelWriteFailure?.let(onWriteFailure)
    cancelFailure?.let { throw it }
  }

  override suspend fun notifyHandover(id: String, onWriteFailure: (Exception) -> Unit) {
    handoverNotifications += id
    recordConfirmationFailure(onWriteFailure)
  }

  override suspend fun confirmHandover(id: String, onWriteFailure: (Exception) -> Unit) {
    handoverConfirmations += id
    recordConfirmationFailure(onWriteFailure)
  }

  override suspend fun notifyReturn(id: String, onWriteFailure: (Exception) -> Unit) {
    returnNotifications += id
    recordConfirmationFailure(onWriteFailure)
  }

  override suspend fun confirmReturn(id: String, onWriteFailure: (Exception) -> Unit) {
    returnConfirmations += id
    recordConfirmationFailure(onWriteFailure)
  }

  override suspend fun dismissConfirmationFailure(id: String) {
    dismissedConfirmations += id
    dismissConfirmationError?.let { throw it }
  }

  private fun recordConfirmationFailure(onWriteFailure: (Exception) -> Unit) {
    confirmationWriteFailure = onWriteFailure
    immediateConfirmationFailure?.let(onWriteFailure)
  }
}
