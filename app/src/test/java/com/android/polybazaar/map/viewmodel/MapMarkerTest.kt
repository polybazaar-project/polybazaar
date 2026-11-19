// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.viewmodel

import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingLocation
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.model.ServiceListing
import com.android.polybazaar.map.model.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapMarkerTest {

  private val rolex = ListingLocation(46.5185, 6.5683, "Rolex Learning Center")

  private val drill =
      ItemListing(
          category = ListingCategory.ITEM_TOOLS,
          price = 8.0,
          title = "Bosch drill set",
          description = "With bits",
          listingId = "drill",
          ownerUid = "lender",
          location = rolex,
      )

  @Test
  fun item_becomesAnItemMarkerAtItsLocation() {
    assertEquals(
        MapMarker(
            id = "drill",
            title = "Bosch drill set",
            type = ListingType.ITEM,
            coordinates = Coordinates(46.5185, 6.5683),
            price = "CHF 8",
        ),
        drill.toMapMarker(),
    )
  }

  @Test
  fun service_becomesAServiceMarker() {
    val tutoring =
        ServiceListing(
            category = ListingCategory.SERVICE_TUTORING,
            price = 25.5,
            title = "Analysis tutoring",
            description = "Before the exam",
            listingId = "tutoring",
            location = rolex,
        )

    assertEquals(ListingType.SERVICE, tutoring.toMapMarker()?.type)
  }

  @Test
  fun listingWithoutALocation_hasNoMarker() {
    assertNull(drill.copy(location = null).toMapMarker())
  }

  @Test
  fun unsavedListing_hasNoMarker() {
    assertNull(drill.copy(listingId = null).toMapMarker())
  }
}
