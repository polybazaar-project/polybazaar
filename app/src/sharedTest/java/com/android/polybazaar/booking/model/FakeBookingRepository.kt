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

  override suspend fun requestBooking(booking: Booking, onWriteFailure: (Exception) -> Unit) {
    requestJob = currentCoroutineContext()[Job]
    requestedBooking = booking
    requestCount++
    writeFailure = onWriteFailure
    pendingRequest?.await()
    immediateWriteFailure?.let(onWriteFailure)
    failure?.let { throw it }
  }

  override suspend fun getBookingsForListing(listingId: String): List<Booking> = emptyList()
}
