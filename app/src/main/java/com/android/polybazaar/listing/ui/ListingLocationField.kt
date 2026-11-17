// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthBackButton
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.auth.ui.AuthPrimaryButton
import com.android.polybazaar.listing.model.ListingRules.MAX_LOCATION_NAME_LENGTH
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.viewmodel.ListingFormUiState
import com.android.polybazaar.map.model.Coordinates
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.abs

object ListingLocationFieldTags {
  const val FIELD = "listing_location_field"
  const val MOVE_PIN = "listing_location_move_pin"
  const val REMOVE = "listing_location_remove"
  const val NAME_FIELD = "listing_location_name_field"
  const val NAME_ERROR = "listing_location_name_error"
  const val OFFLINE_HINT = "listing_location_offline_hint"
  const val PICKER = "listing_location_picker"
  const val PICKER_CLOSE = "listing_location_picker_close"
  const val CENTRE_PIN = "listing_location_centre_pin"
  const val SHOW_MY_LOCATION = "listing_location_show_my_location"
  const val CONFIRM = "listing_location_confirm"
}

/**
 * The picker's map: it shows and moves `camera`, and shows the device once `showMyLocation` (the
 * approximate location permission is granted). A parameter so tests can replace the Google map.
 */
typealias ListingLocationMap =
    @Composable (camera: CameraPositionState, showMyLocation: Boolean) -> Unit

val DefaultListingLocationMap: ListingLocationMap = { camera, showMyLocation ->
  GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = camera,
      contentDescription = stringResource(R.string.listing_location_map_description),
      properties = MapProperties(isMyLocationEnabled = showMyLocation),
      uiSettings =
          MapUiSettings(
              compassEnabled = false,
              mapToolbarEnabled = false,
              myLocationButtonEnabled = showMyLocation,
              rotationGesturesEnabled = false,
              tiltGesturesEnabled = false,
              zoomControlsEnabled = false,
          ),
  )
}

private const val MAP_ZOOM = 16f
private val PIN_SIZE = 44.dp

/** About 1 m: the map writes back a slightly different target after it lays out. */
private const val FOLLOW_TOLERANCE_DEGREES = 1e-5

private fun LatLng.isNear(other: LatLng) =
    abs(latitude - other.latitude) < FOLLOW_TOLERANCE_DEGREES &&
        abs(longitude - other.longitude) < FOLLOW_TOLERANCE_DEGREES

private fun Coordinates.toLatLng() = LatLng(latitude, longitude)

/**
 * "Pickup location" (items) or "Meeting place" (services): "Choose on the map", which opens
 * [ListingLocationPicker], or once chosen the place's name to edit, with move-pin and remove
 * buttons.
 */
@Composable
internal fun ListingLocationField(
    uiState: ListingFormUiState,
    actions: ListingFormActions,
    map: ListingLocationMap,
) {
  var isPicking by rememberSaveable { mutableStateOf(false) }
  // Not while saving: an edit form then shows the listing as saved, and would lose the change.
  val enabled = uiState.canEdit && !uiState.isSaving
  // The map needs the internet; removing the place or renaming it does not.
  val canPick = enabled && uiState.isOnline
  val location = uiState.location
  val shape = RoundedCornerShape(14.dp)
  val label =
      stringResource(
          if (uiState.type == ListingType.ITEM) R.string.listing_location_item_label
          else R.string.listing_location_service_label
      )
  val placeholder = stringResource(R.string.listing_location_placeholder)
  val description = stringResource(R.string.listing_location_description, label, placeholder)
  FormField(
      label = label,
      counter = location?.let { "${it.name.length}/$MAX_LOCATION_NAME_LENGTH" },
  ) {
    if (location == null) {
      Row(
          modifier =
              Modifier.fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .alpha(if (canPick) 1f else 0.45f)
                  .background(AuthColors.Background, shape)
                  .border(1.dp, AuthColors.Outline, shape)
                  .clickable(enabled = canPick, role = Role.Button) { isPicking = true }
                  .semantics { contentDescription = description }
                  .padding(12.dp)
                  .testTag(ListingLocationFieldTags.FIELD),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = placeholder,
            color = AuthColors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_map_pin),
            contentDescription = null,
            tint = AuthColors.TextSecondary,
            modifier = Modifier.size(16.dp),
        )
      }
    } else {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
          FormTextField(
              value = location.name,
              onValueChange = actions.onLocationNameChange,
              placeholder = stringResource(R.string.listing_location_name_placeholder),
              tag = ListingLocationFieldTags.NAME_FIELD,
          )
        }
        LocationIconButton(
            icon = R.drawable.ic_map_pin,
            description = stringResource(R.string.listing_location_move_pin),
            enabled = canPick,
            tag = ListingLocationFieldTags.MOVE_PIN,
        ) {
          isPicking = true
        }
        LocationIconButton(
            icon = R.drawable.ic_close,
            description = stringResource(R.string.listing_location_remove),
            enabled = enabled,
            tag = ListingLocationFieldTags.REMOVE,
            onClick = actions.onClearLocation,
        )
      }
    }
  }
  if (uiState.showLocationNameError) {
    FieldError(
        text = stringResource(R.string.listing_location_name_error),
        tag = ListingLocationFieldTags.NAME_ERROR,
    )
  }
  if (!uiState.isOnline) {
    Text(
        text = stringResource(R.string.listing_location_offline),
        color = AuthColors.TextSecondary,
        fontSize = 12.sp,
        modifier = Modifier.testTag(ListingLocationFieldTags.OFFLINE_HINT),
    )
  }
  if (isPicking) {
    ListingLocationPicker(
        suggestedPoint = uiState.suggestedPoint,
        isOnline = uiState.isOnline,
        onLocateLender = actions.onLocateLender,
        onConfirm = { point ->
          actions.onConfirmLocation(point)
          isPicking = false
        },
        onClose = { isPicking = false },
        map = map,
    )
  }
}

@Composable
private fun LocationIconButton(
    icon: Int,
    description: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
  IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.testTag(tag)) {
    Icon(
        painter = painterResource(icon),
        contentDescription = description,
        tint = AuthColors.TextSecondary,
        modifier = Modifier.size(20.dp),
    )
  }
}

/**
 * A full-screen map under a pin fixed at its centre: the user pans the map, then confirms the point
 * under the pin. Closing it saves nothing. A dialog rather than a route, so it shares the form's
 * ViewModel.
 */
@Composable
private fun ListingLocationPicker(
    suggestedPoint: Coordinates,
    isOnline: Boolean,
    onLocateLender: () -> Unit,
    onConfirm: (Coordinates) -> Unit,
    onClose: () -> Unit,
    map: ListingLocationMap,
) {
  val camera = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(suggestedPoint.toLatLng(), MAP_ZOOM)
  }
  // The camera follows the suggested point (the lender's position arriving, say) until the user
  // moves the map away from the last point it followed.
  var followed by remember { mutableStateOf(suggestedPoint.toLatLng()) }
  LaunchedEffect(suggestedPoint) {
    if (camera.position.target.isNear(followed)) {
      followed = suggestedPoint.toLatLng()
      camera.position = CameraPosition.fromLatLngZoom(followed, camera.position.zoom)
    }
  }
  val context = LocalContext.current
  var showMyLocation by remember {
    mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    )
  }
  val requestPermission =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        showMyLocation = it
      }
  LaunchedEffect(showMyLocation) { if (showMyLocation) onLocateLender() }
  Dialog(
      onDismissRequest = onClose,
      properties =
          DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(AuthColors.Background)
                .safeDrawingPadding()
                .testTag(ListingLocationFieldTags.PICKER)
    ) {
      Row(
          modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 18.dp),
          horizontalArrangement = Arrangement.spacedBy(15.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        AuthBackButton(
            onClick = onClose,
            modifier = Modifier.testTag(ListingLocationFieldTags.PICKER_CLOSE),
        )
        Text(
            text = stringResource(R.string.listing_location_picker_title),
            modifier = Modifier.semantics { heading() },
            color = AuthColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
      }
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        map(camera, showMyLocation)
        Icon(
            painter = painterResource(R.drawable.ic_map_pin),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier =
                Modifier.align(Alignment.Center)
                    // Lifts the pin so its tip, at 22/24 of its height, marks the centre.
                    .offset(y = -PIN_SIZE * 5 / 12)
                    .size(PIN_SIZE)
                    .testTag(ListingLocationFieldTags.CENTRE_PIN),
        )
      }
      Column(
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
            text = stringResource(R.string.listing_location_picker_hint),
            color = AuthColors.TextSecondary,
            fontSize = 13.sp,
        )
        if (!showMyLocation) {
          TextButton(
              onClick = { requestPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
              modifier = Modifier.fillMaxWidth().testTag(ListingLocationFieldTags.SHOW_MY_LOCATION),
          ) {
            Text(
                text = stringResource(R.string.listing_location_show_mine),
                color = AuthColors.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
          }
        }
        AuthPrimaryButton(
            text = stringResource(R.string.listing_location_confirm),
            onClick = {
              // Read now, so it is what is under the pin at this moment, even mid-fling.
              val target = camera.position.target
              onConfirm(Coordinates(target.latitude, target.longitude))
            },
            enabled = isOnline,
            modifier =
                Modifier.fillMaxWidth().height(48.dp).testTag(ListingLocationFieldTags.CONFIRM),
        )
      }
    }
  }
}
