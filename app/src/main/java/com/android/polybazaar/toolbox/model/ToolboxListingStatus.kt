package com.android.polybazaar.toolbox.model

import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingStatus
import com.android.polybazaar.listing.model.Listing
import java.time.LocalDate

/**
 * What the owner's Toolbox says about one of their listings (#196), derived from the listing and
 * its bookings rather than stored.
 *
 * Lenders cannot approve or reject a request yet, so any booking that is not cancelled and covers
 * today makes the listing [BookedToday]. Once requests can be approved, it should only count
 * approved bookings. Bookings on later days do not show: the listing is still [Available] today.
 */
sealed interface ToolboxListingStatus {
  /** Hidden from everyone else by its owner; shown whatever its bookings. */
  data object Paused : ToolboxListingStatus

  /** A booking that is not cancelled covers today. */
  data object BookedToday : ToolboxListingStatus

  /** Not paused, and no booking that is not cancelled covers today. */
  data object Available : ToolboxListingStatus
}

/**
 * The status of [listing] on [today], from [bookings], which may include those of other listings.
 */
fun toolboxListingStatus(
    listing: Listing,
    bookings: List<Booking>,
    today: LocalDate,
): ToolboxListingStatus {
  if (listing.isPaused) return ToolboxListingStatus.Paused
  val bookedToday = bookings.any {
    it.listingId == listing.listingId && it.status != BookingStatus.CANCELLED && today in it.days
  }
  return if (bookedToday) ToolboxListingStatus.BookedToday else ToolboxListingStatus.Available
}
