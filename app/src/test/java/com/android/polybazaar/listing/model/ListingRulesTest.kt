// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.listing.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingRulesTest {

  private fun item(
      title: String = "Drill",
      description: String = "Works well",
      price: Double = 5.0,
      refundValue: Double = price,
  ) = ItemListing("Tools", price, title, description, refundValue)

  private fun service(
      title: String = "Tutoring",
      description: String = "One hour",
      price: Double = 25.0,
  ) = ServiceListing("Tutoring", price, title, description)

  @Test
  fun errorFor_validListings_returnNull() {
    assertNull(ListingRules.errorFor(item()))
    assertNull(ListingRules.errorFor(service()))
  }

  @Test
  fun errorFor_titleBoundary() {
    assertNull(ListingRules.errorFor(item(title = "a".repeat(80))))
    assertEquals(
        "Title must be at most 80 characters",
        ListingRules.errorFor(item(title = "a".repeat(81))),
    )
  }

  @Test
  fun errorFor_descriptionBoundary() {
    assertNull(ListingRules.errorFor(service(description = "a".repeat(1000))))
    assertEquals(
        "Description must be at most 1000 characters",
        ListingRules.errorFor(service(description = "a".repeat(1001))),
    )
  }

  @Test
  fun errorFor_measuresTrimmedText() {
    assertNull(ListingRules.errorFor(item(title = "  " + "a".repeat(80) + "  ")))
    assertNull(ListingRules.errorFor(item(description = " " + "a".repeat(1000) + " ")))
  }

  @Test
  fun errorFor_blankTitleOrDescription() {
    assertEquals("Title is required", ListingRules.errorFor(item(title = "  ")))
    assertEquals("Description is required", ListingRules.errorFor(item(description = "")))
  }

  @Test
  fun errorFor_priceBounds() {
    val message = "Enter a price between CHF 0 and CHF 10,000"
    assertEquals(message, ListingRules.errorFor(service(price = 0.0)))
    assertEquals(message, ListingRules.errorFor(service(price = -1.0)))
    assertEquals(message, ListingRules.errorFor(service(price = 10_000.01)))
    assertNull(ListingRules.errorFor(service(price = 0.01)))
    assertNull(ListingRules.errorFor(service(price = 10_000.0)))
  }

  @Test
  fun errorFor_refundBoundsApplyToItemsOnly() {
    val message = "Enter a refund value between CHF 0 and CHF 100,000"
    assertEquals(message, ListingRules.errorFor(item(refundValue = 0.0)))
    assertEquals(message, ListingRules.errorFor(item(refundValue = 100_000.01)))
    assertNull(ListingRules.errorFor(item(refundValue = 10_000.01)))
    assertNull(ListingRules.errorFor(item(refundValue = 100_000.0)))
    assertEquals(
        "Enter a price between CHF 0 and CHF 10,000",
        ListingRules.errorFor(item(price = 10_000.01, refundValue = 100_000.0)),
    )
  }

  @Test
  fun isValidAmount_rejectsNonFiniteValues() {
    assertFalse(ListingRules.isValidAmount(Double.NaN))
    assertFalse(ListingRules.isValidAmount(Double.POSITIVE_INFINITY))
    assertTrue(ListingRules.isValidAmount(1.0))
  }

  @Test
  fun limitTo_doesNotSplitSurrogatePairs() {
    assertEquals("abc", ListingRules.limitTo("abc", 3))
    assertEquals("ab", ListingRules.limitTo("abcd", 2))
    assertEquals("a", ListingRules.limitTo("a😀", 2))
    assertEquals("a😀", ListingRules.limitTo("a😀b", 3))
  }
}
