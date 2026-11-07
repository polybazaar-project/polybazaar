// Co-authored-by: Copilot (Auto)
package com.android.polybazaar.booking.model

interface BookingRepository {
  /**
   * Submits a booking request without waiting for server confirmation. A successful return does not
   * confirm local persistence or acceptance; Firestore can later reject the request.
   */
  suspend fun requestBooking(booking: Booking)

  /** Returns bookings for this listing owned by the signed-in lender. */
  suspend fun getBookingsForListing(listingId: String): List<Booking>
}
