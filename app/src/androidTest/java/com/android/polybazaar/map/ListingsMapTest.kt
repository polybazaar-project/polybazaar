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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the Google map on a real device (the CI emulator has Google Play services), which unit tests
 * can't: an item and a skill marker, with the renter's circle and then a campus marker. It waits
 * for the map to be created (onMapReady) rather than loaded: tiles need a key that accepts this
 * build, which debug builds don't have, so onMapLoaded might never fire.
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

  @Test
  fun mapWithMarkers_isCreated_aroundTheRenter_onACampus_andWhileChoosing() {
    var centre: MapCentre by mutableStateOf(MapCentre.Located(Coordinates(46.52, 6.57)))
    var ready = false
    composeTestRule.setContent {
      ListingsMap(centre = centre, markers = markers, onMapReady = { ready = true })
    }

    // The map exists, with its markers and the renter's circle: no crash so far.
    composeTestRule.waitUntil(MAP_READY_MILLIS) { ready }

    // Moving to a campus marker, then to the overview, recomposes without crashing.
    composeTestRule.runOnIdle { centre = MapCentre.OnCampus(Campus.EPFL) }
    composeTestRule.waitForIdle()
    composeTestRule.runOnIdle { centre = MapCentre.NeedsChoice() }
    composeTestRule.waitForIdle()
    assertTrue(ready)
  }

  private companion object {
    /** Generous for a cold emulator; the map is usually created within a second or two. */
    const val MAP_READY_MILLIS = 20_000L
  }
}
