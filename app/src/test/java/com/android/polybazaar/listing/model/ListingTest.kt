// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import android.net.Uri
import org.junit.Assert.assertEquals
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
  fun listingImagesAreOptional() {
    val listing =
        ServiceListing(
            category = "Tutoring",
            price = 25.0,
            title = "Math tutoring",
            description = "One-hour session",
        )

    assertEquals(emptyList<Uri>(), listing.images)
    // Not saved yet, so not stored anywhere.
    assertEquals(emptyList<String>(), listing.imagePaths)
  }

  @Test
  fun newListingsHaveNoOwnerUntilSaved() {
    val service = ServiceListing("Tutoring", 25.0, "Math tutoring", "One-hour session")
    val item = ItemListing("Tools", 8.0, "Bosch drill set", "With bits")

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
