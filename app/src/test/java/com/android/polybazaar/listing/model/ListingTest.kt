// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock

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
  fun withPaused_changesOnlyThePauseState_forBothTypes() {
    val service =
        ServiceListing(
            category = ListingCategory.SERVICE_TUTORING,
            price = 25.0,
            title = "Math tutoring",
            description = "One-hour session",
            listingId = "tutoring",
        )
    val item =
        ItemListing(
            category = ListingCategory.ITEM_TOOLS,
            price = 8.0,
            title = "Drill",
            description = "With bits",
            listingId = "drill",
        )

    assertEquals(service.copy(isPaused = true), service.withPaused(true))
    assertEquals(item.copy(isPaused = true), item.withPaused(true))
    assertEquals(service, service.withPaused(true).withPaused(false))
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
    // Not saved yet, so nobody owns it.
    assertEquals(null, listing.ownerUid)
  }

  @Test
  fun saved_pointsToTheStoredPhotosAndDropsTheLocalOnes() {
    val listing =
        ItemListing(
            category = ListingCategory.ITEM_TOOLS,
            price = 8.0,
            title = "Drill",
            description = "Cordless",
            refundValue = 120.0,
            images = listOf(mock<Uri>()),
        )

    val saved = listing.saved("l1", listOf("listings/u1/l1/images/0"), "u1", isPaused = true)

    assertEquals(
        listing.copy(
            images = emptyList(),
            imagePaths = listOf("listings/u1/l1/images/0"),
            listingId = "l1",
            ownerUid = "u1",
            isPaused = true,
        ),
        saved,
    )
  }

  @Test
  fun newListingsHaveNoOwnerUntilSaved() {
    val service =
        ServiceListing(ListingCategory.SERVICE_TUTORING, 25.0, "Math tutoring", "One-hour session")
    val item = ItemListing(ListingCategory.ITEM_TOOLS, 8.0, "Bosch drill set", "With bits")

    assertEquals(null, service.ownerUid)
    assertEquals(null, item.ownerUid)
  }

  @Test
  fun pendingDetailsAreEmptyUntilStored() {
    val details = ListingPendingDetails()

    // Nothing stores these fields yet, so nothing is made up for the screen.
    assertEquals(null, details.condition)
    assertEquals(null, details.location)
    assertEquals(emptyList<String>(), details.specs)
    assertEquals(emptyList<String>(), details.includedItems)
    assertEquals(null, details.pickupTerms)
    assertEquals(null, details.returnTerms)
    assertEquals(null, details.cancellationPolicy)
  }
}
