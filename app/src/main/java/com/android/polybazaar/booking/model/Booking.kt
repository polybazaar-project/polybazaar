// Co-authored-by: Claude Haiku 5.5
// Co-authored-by: Copilot (GPT-5.6 Terra)
// Co-authored-by: Claude Code (Claude Opus 5.5)
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
 * @property days the days booked, at least one. They need not follow each other: 1, 4 and 8–10 Nov
 *   can be one booking.
 * @property status where the booking is in its lifecycle, starting at [BookingStatus.REQUESTED].
 * @property id Firestore document ID, populated when the booking is read.
 */
data class Booking(
    val listingId: String,
    val renterUid: String,
    val lenderUid: String,
    val days: Set<LocalDate>,
    val status: BookingStatus = BookingStatus.REQUESTED,
    val id: String = "",
)
