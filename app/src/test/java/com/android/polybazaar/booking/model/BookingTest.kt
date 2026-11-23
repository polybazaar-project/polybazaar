// Co-authored-by: Copilot (Auto)
package com.android.polybazaar.booking.model

import java.time.LocalDate
import org.junit.Assert.assertFalse
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
}
