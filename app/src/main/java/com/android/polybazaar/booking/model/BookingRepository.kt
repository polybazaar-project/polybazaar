// Co-authored-by: Copilot (Auto)
package com.android.polybazaar.booking.model

interface BookingRepository {
  /**
   * Queues a booking request without waiting for server confirmation. Firestore may later reject
   * the write and report the failure through [onWriteFailure].
   *
   * @param onWriteFailure receives a write failure that arrives after local queuing.
   */
  suspend fun requestBooking(
      booking: Booking,
      onWriteFailure: (Exception) -> Unit = {},
  )

  /** Returns bookings for this listing owned by the signed-in lender. */
  suspend fun getBookingsForListing(listingId: String): List<Booking>

  /** Returns bookings made by the signed-in renter. */
  suspend fun getBookingsForRenter(): List<Booking>

  /**
   * Queues a cancellation without waiting for server confirmation; offline, the write stays queued
   * until the connection returns. If the server later rejects it, for example because the booking
   * is no longer requested or confirmed, the failure reaches [onWriteFailure].
   *
   * @param onWriteFailure receives a write failure that arrives after local queuing.
   */
  suspend fun cancelBooking(id: String, onWriteFailure: (Exception) -> Unit = {})
}
