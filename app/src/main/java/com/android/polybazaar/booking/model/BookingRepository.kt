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
}
