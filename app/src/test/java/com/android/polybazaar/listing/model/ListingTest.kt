// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

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
    // Not saved yet, so nobody owns it.
    assertEquals(null, listing.ownerUid)
  }

  @Test
  fun saved_pointsToTheStoredPhotosAndDropsTheLocalOnes() {
    val listing =
        ItemListing(
            category = "Tools",
            price = 8.0,
            title = "Drill",
            description = "Cordless",
            refundValue = 120.0,
            images = listOf(mock<Uri>()),
        )

    val saved = listing.saved("l1", listOf("listings/u1/l1/images/0"), "u1")

    assertEquals(
        listing.copy(
            images = emptyList(),
            imagePaths = listOf("listings/u1/l1/images/0"),
            listingId = "l1",
            ownerUid = "u1",
        ),
        saved,
    )
  }
}
