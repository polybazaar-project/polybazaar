// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.map.model.Campus
import com.android.polybazaar.map.model.Coordinates
import com.android.polybazaar.map.ui.ListingsMap
import com.android.polybazaar.map.viewmodel.MapCentre
import com.android.polybazaar.map.viewmodel.MapMarker
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the Google map on a real device (the CI emulator has Google Play services), which unit tests
 * can't: an item and a skill marker, with the renter's circle and then a campus marker. Map tiles
 * need a valid key, so this checks that the map, its markers and the overlays are built without
 * crashing, not what the tiles show.
 */
@RunWith(AndroidJUnit4::class)
class ListingsMapTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val markers =
      listOf(
          MapMarker(
              "drill",
              "Bosch drill set",
              ListingType.ITEM,
              Coordinates(46.519, 6.567),
              "CHF 8",
          ),
          MapMarker(
              "maths",
              "Analysis tutoring",
              ListingType.SERVICE,
              Coordinates(46.522, 6.579),
              "CHF 25",
          ),
      )

  /** Gives the map time to be created, since its content is only composed once it's ready. */
  private fun letTheMapLoad() {
    composeTestRule.waitForIdle()
    Thread.sleep(MAP_LOAD_MILLIS)
    composeTestRule.waitForIdle()
  }

  @Test
  fun mapWithMarkers_aroundTheRenter_thenOnACampus() {
    var centre: MapCentre by mutableStateOf(MapCentre.Located(Coordinates(46.52, 6.57)))
    composeTestRule.setContent { ListingsMap(centre = centre, markers = markers) }
    letTheMapLoad()

    composeTestRule.runOnIdle { centre = MapCentre.OnCampus(Campus.EPFL) }
    letTheMapLoad()

    composeTestRule.runOnIdle { centre = MapCentre.NeedsChoice() }
    letTheMapLoad()
  }

  private companion object {
    const val MAP_LOAD_MILLIS = 3_000L
  }
}
