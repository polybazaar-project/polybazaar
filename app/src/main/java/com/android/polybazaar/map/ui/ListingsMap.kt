// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.map.model.Coordinates
import com.android.polybazaar.map.viewmodel.MapCentre
import com.android.polybazaar.map.viewmodel.MapMarker
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

/** Radius of the "approximate location" circle (Figma "Map — Centred on you"), in metres. */
private const val APPROXIMATE_RADIUS_METRES = 150.0

private fun Coordinates.toLatLng() = LatLng(latitude, longitude)

/**
 * The Google map itself: camera from [cameraFor], one marker per listing with its item or skill
 * icon, and the renter's approximate circle or their campus marker. [onMapReady] is called once the
 * map is created (tests wait for it). Kept to drawing only, since Google Maps can't run in unit
 * tests: every decision is in the tested functions of MapScreen.kt.
 */
@OptIn(MapsComposeExperimentalApi::class)
@Composable
internal fun ListingsMap(
    centre: MapCentre,
    markers: List<MapMarker>,
    actions: MapActions = MapActions(),
    onMapReady: () -> Unit = {},
) {
  val camera = cameraFor(centre)
  val cameraState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(camera.target.toLatLng(), camera.zoom)
  }
  // Moves when the centre changes, for example once the location is read or a campus is picked.
  // Setting the position rather than animating: CameraUpdateFactory needs the map to be
  // initialised first, which isn't guaranteed this early on a slow device.
  LaunchedEffect(camera) {
    cameraState.position = CameraPosition.fromLatLngZoom(camera.target.toLatLng(), camera.zoom)
  }
  GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = cameraState,
      // The location layer and the bottom bar already cover the edges: keep the map's own
      // controls out of their way.
      uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
      // A tap outside the markers closes the preview.
      onMapClick = { actions.onMapClick() },
  ) {
    // Once the map exists, whether or not its tiles can load: lets the instrumented test wait for
    // the map instead of sleeping.
    MapEffect(Unit) { onMapReady() }
    markers.forEach { marker ->
      // Keyed by listing, not by its place in the list: after a reload that drops or reorders
      // listings, each marker keeps its own state instead of taking its neighbour's, and a listing
      // its owner moved follows its new position (rememberUpdatedMarkerState).
      key(marker.id) {
        MarkerComposable(
            keys = arrayOf<Any>(marker.id, marker.type),
            state = rememberUpdatedMarkerState(position = marker.coordinates.toLatLng()),
            title = markerLabel(marker),
            // true: handled here, so Google Maps doesn't open its own info bubble or recentre.
            onClick = {
              actions.onMarkerClick(marker.id)
              true
            },
        ) {
          MarkerPin(icon = markerIcon(marker.type))
        }
      }
    }
    when (centre) {
      is MapCentre.Located ->
          Circle(
              center = centre.coordinates.toLatLng(),
              radius = APPROXIMATE_RADIUS_METRES,
              fillColor = AuthColors.Accent.copy(alpha = 0.15f),
              strokeColor = AuthColors.Accent.copy(alpha = 0.4f),
              strokeWidth = 2f,
          )
      is MapCentre.OnCampus ->
          key(centre.campus) {
            MarkerComposable(
                keys = arrayOf<Any>(centre.campus),
                state = rememberUpdatedMarkerState(position = centre.campus.centre.toLatLng()),
                title = centre.campus.name,
            ) {
              MarkerPin(icon = R.drawable.ic_campus)
            }
          }
      else -> Unit
    }
  }
}
