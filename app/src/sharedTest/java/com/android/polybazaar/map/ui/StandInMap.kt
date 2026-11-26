// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.android.polybazaar.map.viewmodel.MapUiState

/** Tags of [standInMap]'s buttons. */
object StandInMapTags {
  /** The button standing for a tap on the map outside the markers. */
  const val MAP = "stand_in_map"

  /** The button standing for the marker of the listing [id]. */
  fun marker(id: String) = "stand_in_map_marker_$id"
}

/**
 * Drawn instead of the Google map, which can't run under Robolectric (#435): a button for the map
 * itself and one per marker, so a test taps them as it would the real map. [onState] receives each
 * state the map is given, for tests that check the markers.
 */
fun standInMap(onState: (MapUiState) -> Unit = {}): MapSlot = { state, actions ->
  onState(state)
  Column {
    Button(onClick = actions.onMapClick, modifier = Modifier.testTag(StandInMapTags.MAP)) {
      Text("map")
    }
    state.markers.forEach { marker ->
      Button(
          onClick = { actions.onMarkerClick(marker.id) },
          modifier = Modifier.testTag(StandInMapTags.marker(marker.id)),
      ) {
        Text(marker.id)
      }
    }
  }
}
