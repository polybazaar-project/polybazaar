// Co-authored-by: Claude Haiku 5.5
package com.android.polybazaar.booking.model

import java.time.LocalDate

enum class BookingStatus {
  REQUESTED,
  CONFIRMED,
  CANCELLED,
}

/**
 * A rental or service booking of a listing.
 *
 * @property listingId ID of the booked listing.
 * @property renterUid uid of the user who books the listing.
 * @property lenderUid uid of the listing owner.
 * @property startDate first day of the booking.
 * @property endDate last day of the booking.
 * @property status where the booking is in its lifecycle, starting at [BookingStatus.REQUESTED].
 */
data class Booking(
    val listingId: String,
    val renterUid: String,
    val lenderUid: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: BookingStatus = BookingStatus.REQUESTED,
)
