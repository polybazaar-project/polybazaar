// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ListingTest {

  @Test
  fun listingCategoryIdsMatchPersistedIds() {
    assertEquals(
        listOf(
            "item_tools",
            "item_electronics",
            "item_books",
            "item_sports",
            "item_kitchen",
            "item_other",
            "service_tutoring",
            "service_repairs",
            "service_moving_help",
            "service_gardening",
            "service_cleaning",
            "service_other",
        ),
        ListingCategory.entries.map { it.id },
    )
  }

  @Test
  fun serviceListingHasServiceType() {
    val listing =
        ServiceListing(
            category = ListingCategory.SERVICE_TUTORING,
            price = 25.0,
            title = "Math tutoring",
            description = "One-hour session",
        )

    assertEquals(ListingType.SERVICE, listing.type)
  }

  @Test
  fun serviceListingRejectsItemCategory() {
    assertThrows(IllegalArgumentException::class.java) {
      ServiceListing(
          category = ListingCategory.ITEM_TOOLS,
          price = 25.0,
          title = "Math tutoring",
          description = "One-hour session",
      )
    }
  }

  @Test
  fun itemListingDefaultsRefundValueToPrice() {
    val listing =
        ItemListing(
            category = ListingCategory.ITEM_ELECTRONICS,
            price = 100.0,
            title = "Calculator",
            description = "Graphing calculator",
        )

    assertEquals(ListingType.ITEM, listing.type)
    assertEquals(100.0, listing.refundValue, 0.0)
  }

  @Test
  fun itemListingRejectsServiceCategory() {
    assertThrows(IllegalArgumentException::class.java) {
      ItemListing(
          category = ListingCategory.SERVICE_TUTORING,
          price = 100.0,
          title = "Calculator",
          description = "Graphing calculator",
      )
    }
  }

  @Test
  fun listingImagesAreOptional() {
    val listing =
        ServiceListing(
            category = ListingCategory.SERVICE_TUTORING,
            price = 25.0,
            title = "Math tutoring",
            description = "One-hour session",
        )

    assertEquals(emptyList<Uri>(), listing.images)
    // Not saved yet, so not stored anywhere.
    assertEquals(emptyList<String>(), listing.imagePaths)
  }
}
