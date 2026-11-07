// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ListingPriceTest {

  private val defaultLocale = Locale.getDefault()

  @After
  fun restoreLocale() {
    Locale.setDefault(defaultLocale)
  }

  @Test
  fun formatChf_wholeAmount_hasNoDecimals() {
    assertEquals("CHF 8", formatChf(8.0))
  }

  @Test
  fun formatChf_fractionalAmount_keepsTwoDecimals() {
    assertEquals("CHF 8.50", formatChf(8.5))
  }

  @Test
  fun formatChf_onADeviceUsingADecimalComma_stillUsesAPoint() {
    Locale.setDefault(Locale.FRANCE)

    assertEquals("CHF 12.25", formatChf(12.25))
  }
}
