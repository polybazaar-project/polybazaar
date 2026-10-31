// Co-authored-by: Claude Code (Claude Opus 5.5, Claude Sonnet 5.5)
package com.android.polybazaar.listing.model

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.listing.model.ListingRules.buildListing
import com.android.polybazaar.listing.model.ListingRules.isValidPriceInput
import com.android.polybazaar.listing.model.ListingRules.isValidRefundInput
import com.android.polybazaar.listing.model.ListingRules.parsePrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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

  private fun uris(count: Int) = (1..count).map { Uri.parse("content://photos/$it") }

  @Test
  fun isValidPriceInput_allowsOneSeparatorAndTwoDecimals() {
    listOf("", "5", "12.5", "12,50", "0.", ".5").forEach { assertTrue(it, isValidPriceInput(it)) }
    listOf("10000.00", "99999").forEach { assertTrue(it, isValidPriceInput(it)) }
    listOf("1.2.3", "1,000.50", "1.234", "abc", "-5", "5 ", "100000").forEach {
      assertFalse(it, isValidPriceInput(it))
    }
  }

  @Test
  fun isValidRefundInput_allowsASixthIntegerDigit() {
    assertTrue(isValidRefundInput("100000.00"))
    assertFalse(isValidRefundInput("1000000"))
  }

  @Test
  fun parsePrice_respectsTheGivenMaximum() {
    assertEquals(10_000.0, parsePrice("10000")!!, 0.0)
    assertNull(parsePrice("10000.01"))
    assertEquals(100_000.0, parsePrice("100000", ListingRules.MAX_REFUND_VALUE)!!, 0.0)
    assertNull(parsePrice("100000.01", ListingRules.MAX_REFUND_VALUE))
  }

  @Test
  fun buildListing_putsPhotosOnBothTypes() {
    val photos = uris(3)

    val item = buildListing(ListingType.ITEM, "t", "d", "5", "Tools", photos)
    val service = buildListing(ListingType.SERVICE, "t", "d", "5", "Tutoring", photos)

    assertEquals(photos, item?.images)
    assertEquals(photos, service?.images)
  }

  @Test
  fun buildListing_refundValueDefaultsToPrice() {
    val blank = buildListing(ListingType.ITEM, "t", "d", "5", "Tools", emptyList(), " ")
    val given = buildListing(ListingType.ITEM, "t", "d", "5", "Tools", emptyList(), "80,50")

    assertEquals(5.0, (blank as ItemListing).refundValue, 0.0)
    assertEquals(80.5, (given as ItemListing).refundValue, 0.0)
    assertNull(buildListing(ListingType.ITEM, "t", "d", "5", "Tools", emptyList(), "0"))
  }

  @Test
  fun buildListing_returnsNullForIncompleteOrInvalidInput() {
    assertNull(buildListing(ListingType.ITEM, "", "d", "5", "Tools", emptyList()))
    assertNull(buildListing(ListingType.ITEM, "t", " ", "5", "Tools", emptyList()))
    assertNull(buildListing(ListingType.ITEM, "t", "d", "0", "Tools", emptyList()))
    assertNull(buildListing(ListingType.ITEM, "t", "d", "1.2.3", "Tools", emptyList()))
    assertNull(buildListing(ListingType.ITEM, "t", "d", "5", null, emptyList()))
    assertNull(buildListing(ListingType.ITEM, "t", "d", "10000.01", "Tools", emptyList()))
    assertNull(buildListing(ListingType.ITEM, "t", "d", "5", "Tools", emptyList(), "100000.01"))
    assertNotNull(buildListing(ListingType.ITEM, "t", "d", "5", "Tools", emptyList(), "100000"))
    assertNull(buildListing(ListingType.SERVICE, "t", "d", "10000.01", "Tutoring", emptyList()))
  }
}
