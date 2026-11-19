// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import com.android.polybazaar.map.model.Campus
import com.android.polybazaar.map.model.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingLocationsTest {

  /** About 111 195 m per degree of latitude. */
  private fun Coordinates.northBy(meters: Double) =
      Coordinates(latitude + meters / 111_195.0, longitude)

  /** The point [fraction] of the way from EPFL to UNIL, about 1 040 m apart. */
  private fun betweenCampuses(fraction: Double): Coordinates {
    val epfl = Campus.EPFL.centre
    val unil = Campus.UNIL.centre
    return Coordinates(
        epfl.latitude + (unil.latitude - epfl.latitude) * fraction,
        epfl.longitude + (unil.longitude - epfl.longitude) * fraction,
    )
  }

  @Test
  fun campusNameFor_pointsWithin600Meters_areOnThatCampus() {
    mapOf(Campus.EPFL to "EPFL campus", Campus.UNIL to "UNIL campus").forEach { (campus, name) ->
      assertEquals(name, ListingLocations.campusNameFor(campus.centre))
      assertEquals(name, ListingLocations.campusNameFor(campus.centre.northBy(590.0)))
    }
  }

  @Test
  fun campusNameFor_pointsBeyond600Meters_areOnNoCampus() {
    // North of both campuses, more than 600 m from each.
    assertNull(ListingLocations.campusNameFor(Campus.EPFL.centre.northBy(610.0)))
    assertNull(ListingLocations.campusNameFor(Campus.UNIL.centre.northBy(610.0)))
    // Lausanne station, a few kilometers away.
    assertNull(ListingLocations.campusNameFor(Coordinates(46.5167, 6.6291)))
  }

  @Test
  fun campusNameFor_pointCloseToBothCampuses_isOnTheNearest() {
    // Both points are within 600 m of each campus.
    assertEquals("EPFL campus", ListingLocations.campusNameFor(betweenCampuses(0.45)))
    assertEquals("UNIL campus", ListingLocations.campusNameFor(betweenCampuses(0.55)))
  }

  @Test
  fun isCampusName_onlyForTheNamesGivenToCampuses() {
    assertTrue(ListingLocations.isCampusName("EPFL campus"))
    assertTrue(ListingLocations.isCampusName("UNIL campus"))
    listOf("", "Flat", "epfl campus", "EPFL campus ").forEach {
      assertFalse(it, ListingLocations.isCampusName(it))
    }
  }
}
