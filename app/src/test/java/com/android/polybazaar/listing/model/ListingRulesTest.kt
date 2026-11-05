// Co-authored-by: Claude Code (Claude Opus 5.5, Claude Sonnet 5.5)
// Co-authored-by: Copilot GPT-5.6 Terra
package com.android.polybazaar.listing.model

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.listing.model.ListingRules.buildListing
import com.android.polybazaar.listing.model.ListingRules.canContactLender
import com.android.polybazaar.listing.model.ListingRules.formatAmountInput
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
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class ListingRulesTest {

  private fun item(
      title: String = "Drill",
      description: String = "Works well",
      price: Double = 5.0,
      refundValue: Double = price,
      maxRentalDays: Int? = null,
  ) = ItemListing(ListingCategory.ITEM_TOOLS, price, title, description, refundValue, maxRentalDays)

  private fun service(
      title: String = "Tutoring",
      description: String = "One hour",
      price: Double = 25.0,
  ) = ServiceListing(ListingCategory.SERVICE_TUTORING, price, title, description)

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
  fun errorFor_countsStoredAndNewPhotosTogether() {
    val paths = List(3) { "listings/uid/l1/images/$it" }
    val photos = List(2) { mock<Uri>() }

    assertNull(ListingRules.errorFor(service().copy(imagePaths = paths, images = photos)))
    assertEquals(
        "A listing can have at most 5 photos",
        ListingRules.errorFor(service().copy(imagePaths = paths, images = photos + photos)),
    )
  }

  @Test
  fun errorFor_rentalDaysBounds() {
    val message = ListingRules.RENTAL_DAYS_RANGE_MESSAGE
    assertEquals("Enter a rental duration between 1 and 180 days", message)
    // Null means the lender set no limit.
    assertNull(ListingRules.errorFor(item(maxRentalDays = null)))
    assertNull(ListingRules.errorFor(item(maxRentalDays = 1)))
    assertNull(ListingRules.errorFor(item(maxRentalDays = ListingRules.MAX_RENTAL_DAYS)))
    assertEquals(message, ListingRules.errorFor(item(maxRentalDays = 0)))
    assertEquals(message, ListingRules.errorFor(item(maxRentalDays = -3)))
    assertEquals(
        message,
        ListingRules.errorFor(item(maxRentalDays = ListingRules.MAX_RENTAL_DAYS + 1)),
    )
  }

  private fun viewer(verified: Boolean) =
      User(
          uid = "viewer",
          email = "viewer@epfl.ch",
          username = "viewer",
          isEmailVerified = verified,
      )

  private val lendersListing = item().copy(ownerUid = "lender")
  private val viewersListing = item().copy(ownerUid = "viewer")

  @Test
  fun canContactLender_verifiedViewerOnSomeoneElsesListing() {
    assertTrue(canContactLender(viewer(verified = true), lendersListing))
    // Same rule for services.
    assertTrue(canContactLender(viewer(verified = true), service().copy(ownerUid = "lender")))
  }

  @Test
  fun canContactLender_refusesUnverifiedViewersAndOwnListings() {
    assertFalse(canContactLender(viewer(verified = false), lendersListing))
    assertFalse(canContactLender(viewer(verified = true), viewersListing))
    assertFalse(canContactLender(viewer(verified = false), viewersListing))
    // Not saved yet, so there is nobody to contact.
    assertFalse(canContactLender(viewer(verified = true), item()))
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
  fun categoriesFor_returnsCategoriesForOnlyThatListingType() {
    val itemCategories = ListingRules.categoriesFor(ListingType.ITEM)
    val serviceCategories = ListingRules.categoriesFor(ListingType.SERVICE)

    assertTrue(itemCategories.all { it.type == ListingType.ITEM })
    assertTrue(serviceCategories.all { it.type == ListingType.SERVICE })
    assertEquals(ListingCategory.entries.toSet(), (itemCategories + serviceCategories).toSet())
  }

  @Test
  fun buildListing_putsPhotosOnBothTypes() {
    val photos = uris(3)

    val item = buildListing(ListingType.ITEM, "t", "d", "5", ListingCategory.ITEM_TOOLS, photos)
    val service =
        buildListing(ListingType.SERVICE, "t", "d", "5", ListingCategory.SERVICE_TUTORING, photos)

    assertEquals(photos, item?.images)
    assertEquals(photos, service?.images)
  }

  @Test
  fun buildListing_refundValueDefaultsToPrice() {
    val blank =
        buildListing(ListingType.ITEM, "t", "d", "5", ListingCategory.ITEM_TOOLS, emptyList(), " ")
    val given =
        buildListing(
            ListingType.ITEM,
            "t",
            "d",
            "5",
            ListingCategory.ITEM_TOOLS,
            emptyList(),
            "80,50",
        )

    assertEquals(5.0, (blank as ItemListing).refundValue, 0.0)
    assertEquals(80.5, (given as ItemListing).refundValue, 0.0)
    assertNull(
        buildListing(
            ListingType.ITEM,
            "t",
            "d",
            "5",
            ListingCategory.ITEM_TOOLS,
            emptyList(),
            "0",
        )
    )
  }

  @Test
  fun buildListing_returnsNullForIncompleteOrInvalidInput() {
    assertNull(
        buildListing(ListingType.ITEM, "", "d", "5", ListingCategory.ITEM_TOOLS, emptyList())
    )
    assertNull(
        buildListing(ListingType.ITEM, "t", " ", "5", ListingCategory.ITEM_TOOLS, emptyList())
    )
    assertNull(
        buildListing(ListingType.ITEM, "t", "d", "0", ListingCategory.ITEM_TOOLS, emptyList())
    )
    assertNull(
        buildListing(ListingType.ITEM, "t", "d", "1.2.3", ListingCategory.ITEM_TOOLS, emptyList())
    )
    assertNull(buildListing(ListingType.ITEM, "t", "d", "5", null, emptyList()))
    assertNull(
        buildListing(
            ListingType.ITEM,
            "t",
            "d",
            "5",
            ListingCategory.SERVICE_TUTORING,
            emptyList(),
        )
    )
    assertNull(
        buildListing(
            ListingType.ITEM,
            "t",
            "d",
            "10000.01",
            ListingCategory.ITEM_TOOLS,
            emptyList(),
        )
    )
    assertNull(
        buildListing(
            ListingType.ITEM,
            "t",
            "d",
            "5",
            ListingCategory.ITEM_TOOLS,
            emptyList(),
            "100000.01",
        )
    )
    assertNotNull(
        buildListing(
            ListingType.ITEM,
            "t",
            "d",
            "5",
            ListingCategory.ITEM_TOOLS,
            emptyList(),
            "100000",
        )
    )
    assertNull(
        buildListing(
            ListingType.SERVICE,
            "t",
            "d",
            "10000.01",
            ListingCategory.SERVICE_TUTORING,
            emptyList(),
        )
    )
  }

  // Editing a listing must not erase what the form does not show: its id, kept photos and the
  // item's rental duration.
  @Test
  fun buildListing_keepsTheEditedListingsSavedFields() {
    val paths = listOf("listings/u/l/images/0")

    val item =
        buildListing(
            ListingType.ITEM,
            "t",
            "d",
            "5",
            ListingCategory.ITEM_TOOLS,
            uris(1),
            imagePaths = paths,
            listingId = "l",
            maxRentalDays = 7,
        )
            as ItemListing
    val service =
        buildListing(
            ListingType.SERVICE,
            "t",
            "d",
            "5",
            ListingCategory.SERVICE_TUTORING,
            emptyList(),
            imagePaths = paths,
            listingId = "l",
            maxRentalDays = 7,
        )

    assertEquals(paths, item.imagePaths)
    assertEquals("l", item.listingId)
    assertEquals(7, item.maxRentalDays)
    assertEquals(uris(1), item.images)
    assertEquals(paths, service?.imagePaths)
    assertEquals("l", service?.listingId)
  }

  @Test
  fun formatAmountInput_writesAmountsAsTheFieldsAcceptThem() {
    assertEquals("25", formatAmountInput(25.0))
    assertEquals("12.5", formatAmountInput(12.5))
    assertEquals("0.05", formatAmountInput(0.05))
    assertEquals("10000", formatAmountInput(10_000.0))
    assertEquals("100000", formatAmountInput(100_000.0))
    // What the edit form shows must read back as the same amount.
    listOf(25.0, 12.5, 0.05, 10_000.0).forEach {
      assertEquals(it, parsePrice(formatAmountInput(it))!!, 0.0)
      assertTrue(isValidPriceInput(formatAmountInput(it)))
    }
    assertTrue(isValidRefundInput(formatAmountInput(100_000.0)))
  }
}
