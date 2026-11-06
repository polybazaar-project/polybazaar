// Co-authored-by: Copilot (Auto)
package com.android.polybazaar.booking.model

interface BookingRepository {
  /** Creates a requested booking. Firestore rejects requests not made by the renter. */
  suspend fun requestBooking(booking: Booking)

  /** Returns bookings for this listing owned by the signed-in lender. */
  suspend fun getBookingsForListing(listingId: String): List<Booking>
}
