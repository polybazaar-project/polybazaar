// Co-authored-by: Copilot (Auto)
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

    assertFalse(booking.lenderHandoverConfirmed)
    assertFalse(booking.renterHandoverConfirmed)
    assertFalse(booking.renterReturnConfirmed)
    assertFalse(booking.lenderReturnConfirmed)
  }

  @Test
  fun confirmationsCanBeRecordedIndependently() {
    val booking =
        Booking("listing", "renter", "lender", setOf(LocalDate.of(2026, 12, 1)))
            .copy(lenderHandoverConfirmed = true, renterReturnConfirmed = true)

    assertTrue(booking.lenderHandoverConfirmed)
    assertFalse(booking.renterHandoverConfirmed)
    assertTrue(booking.renterReturnConfirmed)
    assertFalse(booking.lenderReturnConfirmed)

    val confirmed = booking.copy(renterHandoverConfirmed = true, lenderReturnConfirmed = true)
    assertTrue(confirmed.lenderHandoverConfirmed)
    assertTrue(confirmed.renterHandoverConfirmed)
    assertTrue(confirmed.renterReturnConfirmed)
    assertTrue(confirmed.lenderReturnConfirmed)
  }
}
