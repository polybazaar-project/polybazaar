// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.ui.message
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.map.model.Campus
import com.android.polybazaar.map.model.Coordinates
import com.android.polybazaar.map.viewmodel.MapCentre
import com.android.polybazaar.map.viewmodel.MapMarker
import com.android.polybazaar.map.viewmodel.MapUiState
import com.android.polybazaar.map.viewmodel.MapViewModel

object MapScreenTags {
  const val SCREEN = "map_screen"
  const val LOADING = "map_loading_listings"
  const val ERROR = "map_listings_error"
  const val RETRY_BUTTON = "map_retry_button"
  const val EMPTY = "map_no_listings"
  const val PREVIEW = "map_marker_preview"
  const val PREVIEW_CLOSE = "map_marker_preview_close"
}

/**
 * What the map reports: a marker tapped, or the map tapped outside the markers. Both do nothing by
 * default.
 */
class MapActions(val onMarkerClick: (String) -> Unit = {}, val onMapClick: () -> Unit = {})

/**
 * Draws the map for the state and reports taps through the actions: the Google map, or a stand-in.
 */
typealias MapSlot = @Composable (uiState: MapUiState, actions: MapActions) -> Unit

/** The Google map, the map used in the app. */
val GoogleListingsMap: MapSlot = { uiState, actions ->
  ListingsMap(centre = uiState.centre, markers = uiState.markers, actions = actions)
}

/** Where the map's camera points and how close. */
internal data class MapCamera(val target: Coordinates, val zoom: Float)

/** Street level: a few hundred metres around the renter or the campus. */
private const val CLOSE_ZOOM = 15f

/** Both campuses at once, before the renter's centre is known. */
private const val OVERVIEW_ZOOM = 14f

/**
 * The camera for [centre]: close on the renter or their campus, and an overview of EPFL and UNIL
 * while the centre isn't known yet (asking, locating, choosing).
 */
internal fun cameraFor(centre: MapCentre): MapCamera =
    when (centre) {
      is MapCentre.Located -> MapCamera(centre.coordinates, CLOSE_ZOOM)
      is MapCentre.OnCampus -> MapCamera(centre.campus.centre, CLOSE_ZOOM)
      MapCentre.Undecided,
      MapCentre.Locating,
      is MapCentre.NeedsChoice -> MapCamera(campusesMidpoint(), OVERVIEW_ZOOM)
    }

/** Halfway between the campuses, so the overview shows both. */
private fun campusesMidpoint(): Coordinates =
    Coordinates(
        latitude = Campus.entries.map { it.centre.latitude }.average(),
        longitude = Campus.entries.map { it.centre.longitude }.average(),
    )

/** The marker icon of a listing: items and skills differ by icon, not only colour (#353). */
@DrawableRes
internal fun markerIcon(type: ListingType): Int =
    when (type) {
      ListingType.ITEM -> R.drawable.ic_marker_item
      ListingType.SERVICE -> R.drawable.ic_marker_service
    }

/** What TalkBack reads for a marker: its title, whether it's an item or a skill, and its price. */
@StringRes
internal fun markerDescription(type: ListingType): Int =
    when (type) {
      ListingType.ITEM -> R.string.map_marker_item
      ListingType.SERVICE -> R.string.map_marker_service
    }

/**
 * The Map tab (#341): the listings around the renter or their campus on a map, item and skill
 * markers drawn differently, with the location layer (#340) over it. Tapping a marker previews its
 * listing at the bottom (#342), and the preview opens it.
 *
 * @param onOpenListing opens the detail of the listing with this id.
 * @param map draws the map; the Google map by default, a stand-in in tests.
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    modifier: Modifier = Modifier,
    onOpenListing: (String) -> Unit = {},
    map: MapSlot = GoogleListingsMap,
) {
  val uiState by viewModel.uiState.collectAsState()
  // Remembered: a plain class of lambdas, so a new one on every redraw would redraw the map too.
  val actions =
      remember(viewModel) {
        MapActions(onMarkerClick = viewModel::selectMarker, onMapClick = viewModel::clearSelection)
      }
  MapContent(
      uiState = uiState,
      onRetry = viewModel::loadListings,
      map = { map(uiState, actions) },
      locationLayer = { MapLocationLayer(viewModel) },
      onOpenListing = onOpenListing,
      onClosePreview = viewModel::clearSelection,
      modifier = modifier,
  )
}

/**
 * The map tab without its ViewModel: [map] draws the map (the Google map in the app, a stand-in in
 * tests, where Google Maps can't run), then what the listings' state needs over it, then
 * [locationLayer] on top.
 *
 * @param onRetry reloads the listings after a failure.
 * @param onOpenListing opens the previewed listing's detail.
 * @param onClosePreview closes the preview.
 */
@Composable
fun MapContent(
    uiState: MapUiState,
    onRetry: () -> Unit,
    map: @Composable () -> Unit,
    locationLayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onOpenListing: (String) -> Unit = {},
    onClosePreview: () -> Unit = {},
) {
  Box(modifier = modifier.fillMaxSize().testTag(MapScreenTags.SCREEN)) {
    map()
    // One column at the bottom of the map, so the preview and the error pill stack instead of
    // overlapping.
    Column(
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      val selected = uiState.selectedMarker
      if (selected != null) {
        // A reload that fails (offline) keeps the previewed listing, as it is still on the map,
        // but the renter must still see the error and be able to retry: the pill goes above it.
        uiState.error?.let { ErrorPill(it, onRetry) }
        MarkerPreview(
            marker = selected,
            onOpen = { onOpenListing(selected.id) },
            onClose = onClosePreview,
        )
      } else {
        ListingsStatus(uiState, onRetry)
      }
    }
    // Last, so its cards and their dim cover the status too.
    locationLayer()
  }
}

/**
 * Why there are no markers, or a load in progress, at the bottom of the map. A reload while markers
 * are shown (the tab coming back) shows nothing, so the map doesn't flicker.
 */
@Composable
private fun ListingsStatus(uiState: MapUiState, onRetry: () -> Unit) {
  val error = uiState.error
  when {
    error != null -> ErrorPill(error, onRetry)
    uiState.isLoading && uiState.markers.isEmpty() ->
        BottomPill(MapScreenTags.LOADING) {
          CircularProgressIndicator(
              color = AuthColors.Accent,
              strokeWidth = 2.dp,
              modifier = Modifier.size(16.dp),
          )
          Text(
              text = stringResource(R.string.map_loading_listings),
              color = AuthColors.TextSecondary,
              fontSize = 12.sp,
              modifier = Modifier.padding(vertical = 12.dp),
          )
        }
    !uiState.isLoading && uiState.markers.isEmpty() ->
        BottomPill(MapScreenTags.EMPTY) {
          Text(
              text = stringResource(R.string.map_no_listings),
              color = AuthColors.TextSecondary,
              fontSize = 12.sp,
              modifier = Modifier.padding(vertical = 12.dp),
          )
        }
  }
}

/** Why the listings could not be loaded, with Retry. */
@Composable
private fun ErrorPill(error: AppError, onRetry: () -> Unit) {
  BottomPill(MapScreenTags.ERROR) {
    Text(
        text = error.message(),
        color = AuthColors.Danger,
        fontSize = 12.sp,
        modifier = Modifier.weight(1f, fill = false),
    )
    TextButton(
        onClick = onRetry,
        modifier = Modifier.heightIn(min = 48.dp).testTag(MapScreenTags.RETRY_BUTTON),
    ) {
      Text(
          text = stringResource(R.string.map_retry),
          color = AuthColors.Accent,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

/** A pill at the bottom of the map, styled like the location pills at the top. */
@Composable
private fun BottomPill(tag: String, content: @Composable RowScope.() -> Unit) {
  Row(
      modifier =
          Modifier.padding(horizontal = 18.dp)
              .shadow(2.dp, RoundedCornerShape(24.dp))
              .background(AuthColors.CardBackground, RoundedCornerShape(24.dp))
              .border(1.dp, AuthColors.Outline, RoundedCornerShape(24.dp))
              .padding(horizontal = 14.dp)
              .testTag(tag),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    content()
  }
}

/** The round accent pin with a white icon, drawn for listing and campus markers. */
@Composable
internal fun MarkerPin(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
  Box(
      modifier =
          modifier
              .size(32.dp)
              .background(AuthColors.Accent, CircleShape)
              .border(2.dp, AuthColors.CardBackground, CircleShape),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = AuthColors.CardBackground,
        modifier = Modifier.size(16.dp),
    )
  }
}

/** What TalkBack reads for [marker], for example "Bosch drill set, item, CHF 8 per day". */
@Composable
internal fun markerLabel(marker: MapMarker): String =
    stringResource(markerDescription(marker.type), marker.title, marker.price)
