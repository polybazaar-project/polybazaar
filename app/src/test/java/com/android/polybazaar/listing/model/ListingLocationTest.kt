// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ListingLocationTest {

  @Test
  fun approximate_snapsBothCoordinatesToTheGrid_andKeepsTheName() {
    val rolex = ListingLocation(46.5191, 6.5668, "Rolex Learning Center")

    assertEquals(ListingLocation(46.52, 6.56, "Rolex Learning Center"), rolex.approximate())
  }

  @Test
  fun approximate_movesThePointByAtMostHalfAGridCell() {
    val half = 0.5 / ListingLocation.APPROXIMATE_STEPS_PER_DEGREE
    val points =
        listOf(46.5191 to 6.5668, 46.5299 to 6.5701, -33.8568 to 151.2153, 0.0099 to -0.0099)

    for ((latitude, longitude) in points) {
      val snapped = ListingLocation(latitude, longitude, "").approximate()
      // A hair over half a cell for floating point; a cell is 0.02°.
      assertEquals(latitude, snapped.latitude, half + 1e-9)
      assertEquals(longitude, snapped.longitude, half + 1e-9)
    }
  }

  @Test
  fun approximate_handlesNegativeCoordinates() {
    assertEquals(
        ListingLocation(-33.86, 151.22, "Opera House"),
        ListingLocation(-33.8568, 151.2153, "Opera House").approximate(),
    )
    assertEquals(
        ListingLocation(-0.02, -0.06, "Gulf"),
        ListingLocation(-0.0151, -0.0649, "Gulf").approximate(),
    )
  }

  @Test
  fun approximate_keepsTheEdgesOfTheValidRange() {
    // Within the range the rules accept, so a snapped location is still a valid one.
    val edges = ListingLocation(90.0, -180.0, "Edge").approximate()

    assertEquals(90.0, edges.latitude, 0.0)
    assertEquals(-180.0, edges.longitude, 0.0)
    assertEquals(
        true,
        ListingRules.isValidLocation(ListingLocation(89.999, 179.999, "N").approximate()),
    )
  }

  @Test
  fun approximate_twice_changesNothing() {
    // Saving a listing again, after reading its snapped location back, keeps the same point.
    val once = ListingLocation(46.5191, 6.5668, "EPFL").approximate()

    assertEquals(once, once.approximate())
  }
}
