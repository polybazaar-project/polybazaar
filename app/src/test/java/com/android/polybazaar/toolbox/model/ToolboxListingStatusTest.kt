package com.android.polybazaar.toolbox.model

import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingStatus
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolboxListingStatusTest {

  private val today = LocalDate.of(2026, 11, 19)
  private val drill =
      ItemListing(ListingCategory.ITEM_TOOLS, 8.0, "Bosch drill", "", listingId = "drill")

  private fun booking(
      vararg days: LocalDate,
      status: BookingStatus = BookingStatus.REQUESTED,
      listingId: String = "drill",
  ) = Booking(listingId, "renter", "lender", days.toSet(), status)

  private fun status(vararg bookings: Booking) =
      toolboxListingStatus(drill, bookings.toList(), today)

  @Test
  fun noBookings_isAvailable() {
    assertEquals(ToolboxListingStatus.Available, status())
  }

  @Test
  fun paused_winsOverABookingToday() {
    val paused = drill.copy(isPaused = true)

    assertEquals(
        ToolboxListingStatus.Paused,
        toolboxListingStatus(paused, listOf(booking(today)), today),
    )
  }

  @Test
  fun aRequestCoveringToday_isBookedToday() {
    assertEquals(ToolboxListingStatus.BookedToday, status(booking(today.minusDays(1), today)))
  }

  @Test
  fun aSeparateDayBookingWhoseDaysSkipToday_isAvailable() {
    // 18 and 20 Nov in one booking: today is between its days, not one of them.
    assertEquals(
        ToolboxListingStatus.Available,
        status(booking(today.minusDays(1), today.plusDays(1))),
    )
  }

  @Test
  fun bookedToday_winsOverLaterRequests() {
    assertEquals(
        ToolboxListingStatus.BookedToday,
        status(booking(today.plusDays(3)), booking(today)),
    )
  }

  @Test
  fun onlyLaterBookings_isAvailable() {
    assertEquals(
        ToolboxListingStatus.Available,
        status(booking(today.plusDays(1)), booking(today.plusDays(5), today.plusDays(6))),
    )
  }

  @Test
  fun aConfirmedBooking_countsLikeARequest() {
    assertEquals(
        ToolboxListingStatus.BookedToday,
        status(booking(today, status = BookingStatus.CONFIRMED)),
    )
  }

  @Test
  fun cancelledBookings_areIgnored() {
    assertEquals(
        ToolboxListingStatus.Available,
        status(
            booking(today, status = BookingStatus.CANCELLED),
            booking(today.plusDays(1), status = BookingStatus.CANCELLED),
        ),
    )
  }

  @Test
  fun pastBookings_areIgnored() {
    assertEquals(
        ToolboxListingStatus.Available,
        status(booking(today.minusDays(3), today.minusDays(1))),
    )
  }

  @Test
  fun bookingsOfOtherListings_areIgnored() {
    assertEquals(
        ToolboxListingStatus.Available,
        status(booking(today, listingId = "tent"), booking(today.plusDays(1), listingId = "tent")),
    )
  }
}
