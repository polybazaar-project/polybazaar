// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.model

import org.junit.Assert.assertTrue
import org.junit.Test

class CampusTest {

  @Test
  fun everyCampus_isInLausanneOuest() {
    // A typo or a swapped latitude and longitude would put a campus far from Lausanne.
    Campus.entries.forEach { campus ->
      assertTrue("$campus latitude", campus.centre.latitude in 46.50..46.54)
      assertTrue("$campus longitude", campus.centre.longitude in 6.54..6.60)
    }
  }

  @Test
  fun epflAndUnil_haveDifferentCentres() {
    assertTrue(Campus.EPFL.centre != Campus.UNIL.centre)
  }
}
