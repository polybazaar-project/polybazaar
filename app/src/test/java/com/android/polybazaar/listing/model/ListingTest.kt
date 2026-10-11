// Co-authored-by: Copilot GPT-5.6 Terra
package com.android.polybazaar.listing.model

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ListingTest {

  @Test
  fun serviceListingHasServiceType() {
    val listing =
        ServiceListing(
            category = "Tutoring",
            price = 25.0,
            title = "Math tutoring",
            description = "One-hour session",
        )

    assertEquals(ListingType.SERVICE, listing.type)
  }

  @Test
  fun itemListingDefaultsRefundValueToPrice() {
    val listing =
        ItemListing(
            category = "Electronics",
            price = 100.0,
            title = "Calculator",
            description = "Graphing calculator",
        )

    assertEquals(ListingType.ITEM, listing.type)
    assertEquals(100.0, listing.refundValue, 0.0)
  }

  @Test
  fun listingImagesAreOptionalAndLimitedToTen() {
    val listing =
        ServiceListing(
            category = "Tutoring",
            price = 25.0,
            title = "Math tutoring",
            description = "One-hour session",
            images = List(Listing.MAXIMUM_IMAGES) { Uri.parse("content://images/$it") },
        )

    assertEquals(Listing.MAXIMUM_IMAGES, listing.images.size)
    assertThrows(IllegalArgumentException::class.java) {
      ServiceListing(
          category = "Tutoring",
          price = 25.0,
          title = "Math tutoring",
          description = "One-hour session",
          images = List(Listing.MAXIMUM_IMAGES + 1) { Uri.parse("content://images/$it") },
      )
    }
  }
}
