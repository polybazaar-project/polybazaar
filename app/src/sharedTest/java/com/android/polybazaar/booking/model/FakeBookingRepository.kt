// Co-authored-by: Copilot (Auto)
// Co-authored-by: Claude Code (Claude Fable 5.2)
package com.android.polybazaar.booking.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

/**
 * A [BookingRepository] in memory, for tests. It records the last booking requested and how many
 * were, and can hold a request back ([pendingRequest]), make it throw ([failure]) or report a write
 * failure, at once ([immediateWriteFailure]) or later through [writeFailure].
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
  var pendingLoad: CompletableDeferred<Unit>? = null
  var loadFailure: Exception? = null
  var loadCount = 0
  var cancellationCount = 0
  var cancelFailure: Exception? = null
  var pendingCancel: CompletableDeferred<Unit>? = null
  var cancelWriteFailure: ((Exception) -> Unit)? = null
  var immediateCancelWriteFailure: Exception? = null

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
    return listingBookings
  }

  override suspend fun getBookingsForRenter(): List<Booking> {
    loadCount++
    pendingLoad?.await()
    loadFailure?.let { throw it }
    return renterBookings
  }

  override suspend fun cancelBooking(id: String, onWriteFailure: (Exception) -> Unit) {
    cancellationCount++
    cancelWriteFailure = onWriteFailure
    pendingCancel?.await()
    immediateCancelWriteFailure?.let(onWriteFailure)
    cancelFailure?.let { throw it }
  }
}
