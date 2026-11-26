// Co-authored-by: Copilot (Auto)
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.booking.model

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingTest {
  @Test
  fun existingBookingDefaultsAllConfirmationsToFalse() {
    val booking =
        Booking(
            "listing",
            "renter",
            "lender",
            setOf(LocalDate.of(2026, 12, 1)),
            BookingStatus.CONFIRMED,
            "id",
        )

    assertFalse(booking.handoverNotified)
    assertFalse(booking.renterHandoverConfirmed)
    assertFalse(booking.returnNotified)
    assertFalse(booking.lenderReturnConfirmed)
  }

  @Test
  fun aBookingCanBeCancelled_untilItIsCancelledHandedOverOrOver() {
    val today = LocalDate.of(2026, 11, 27)
    val booking =
        Booking(
            "listing",
            "renter",
            "lender",
            setOf(today.minusDays(1), today),
            BookingStatus.CONFIRMED,
            "id",
        )

    // Its last day is today: not over yet. A request can be cancelled too.
    assertTrue(booking.canBeCancelled(today))
    assertTrue(booking.copy(status = BookingStatus.REQUESTED).canBeCancelled(today))
    assertFalse(booking.copy(status = BookingStatus.CANCELLED).canBeCancelled(today))
    assertFalse(booking.copy(handoverNotified = true).canBeCancelled(today))
    assertFalse(booking.canBeCancelled(today.plusDays(1)))
  }
}
