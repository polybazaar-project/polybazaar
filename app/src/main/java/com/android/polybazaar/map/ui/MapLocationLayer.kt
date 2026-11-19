// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.auth.ui.AuthPrimaryButton
import com.android.polybazaar.map.model.Campus
import com.android.polybazaar.map.viewmodel.MapCentre
import com.android.polybazaar.map.viewmodel.MapViewModel

object MapLocationTags {
  const val REASON_CARD = "map_location_reason_card"
  const val CONTINUE_BUTTON = "map_location_continue_button"
  const val NOT_NOW_BUTTON = "map_location_not_now_button"
  const val CHOOSER_CARD = "map_campus_chooser_card"
  const val CANCEL_BUTTON = "map_campus_cancel_button"
  const val LOCATING_PILL = "map_locating_pill"
  const val CAMPUS_PILL = "map_campus_pill"
  const val CHANGE_BUTTON = "map_change_campus_button"

  /** Tag of the chooser's button for [campus]. */
  fun campusButton(campus: Campus) = "map_campus_button_${campus.name}"
}

/** What the location layer can ask for; every action does nothing by default. */
class MapLocationActions(
    val onContinue: () -> Unit = {},
    val onNotNow: () -> Unit = {},
    val onChooseCampus: (Campus) -> Unit = {},
    val onChangeCampus: () -> Unit = {},
    val onCancelChoice: () -> Unit = {},
)

/** Side margin of the cards and pills, the page margin of the other screens. */
private val PageMargin = 18.dp

/** The dim behind a bottom card, so it stands out from the map (Figma "Map — Location reason"). */
private val Scrim = AuthColors.TextPrimary.copy(alpha = 0.2f)

private fun hasCoarseLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * The location layer drawn over the map (#340): asks for the approximate location once the renter
 * read why, and reports Android's answer to [viewModel], which decides the centre. Already granted,
 * it skips the explanation and reads the location straight away.
 */
@Composable
fun MapLocationLayer(viewModel: MapViewModel, modifier: Modifier = Modifier) {
  val uiState by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val alreadyGranted = hasCoarseLocation(context)
  val requestPermission =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(granted)
      }
  // Keyed on the permission, not run once: if the renter grants it in Android's settings while the
  // explanation is open, the next redraw shows "Finding your location…" below, so the location
  // must be read then too, or the pill would never go away.
  LaunchedEffect(alreadyGranted) {
    if (alreadyGranted && viewModel.uiState.value.centre == MapCentre.Undecided) {
      viewModel.onPermissionResult(true)
    }
  }
  // Remembered: MapLocationActions is a plain class of lambdas, so a new one on every redraw would
  // make Compose redraw the whole content each time the map around it redraws.
  val actions =
      remember(viewModel, requestPermission) {
        MapLocationActions(
            onContinue = { requestPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
            onNotNow = { viewModel.onPermissionResult(false) },
            onChooseCampus = viewModel::chooseCampus,
            onChangeCampus = viewModel::changeCampus,
            onCancelChoice = viewModel::cancelCampusChoice,
        )
      }
  MapLocationContent(
      // Undecided but granted is the frame before the effect above runs: show nothing rather than
      // flash the explanation.
      centre =
          if (uiState.centre == MapCentre.Undecided && alreadyGranted) MapCentre.Locating
          else uiState.centre,
      actions = actions,
      modifier = modifier,
  )
}

/**
 * What the map shows over itself for [centre] (Figma "Map — …" frames): the reason before asking
 * for the location, "Finding your location…", the campus it shows with "Change", or the campus
 * chooser. Nothing once centred on the renter.
 */
@Composable
fun MapLocationContent(
    centre: MapCentre,
    actions: MapLocationActions,
    modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize()) {
    when (centre) {
      MapCentre.Undecided -> BottomCard(MapLocationTags.REASON_CARD) { ReasonContent(actions) }
      MapCentre.Locating -> LocatingPill()
      is MapCentre.Located -> Unit
      is MapCentre.OnCampus -> CampusPill(centre.campus, actions.onChangeCampus)
      is MapCentre.NeedsChoice ->
          BottomCard(MapLocationTags.CHOOSER_CARD) { ChooserContent(centre.current, actions) }
    }
  }
}

/** A card at the bottom of the map, with the map dimmed behind it. */
@Composable
private fun BoxScope.BottomCard(tag: String, content: @Composable ColumnScope.() -> Unit) {
  Box(modifier = Modifier.fillMaxSize().background(Scrim))
  val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
  Column(
      modifier =
          Modifier.align(Alignment.BottomCenter)
              .fillMaxWidth()
              .background(AuthColors.CardBackground, shape)
              .border(1.dp, AuthColors.Outline, shape)
              .padding(start = PageMargin, end = PageMargin, top = 12.dp, bottom = PageMargin)
              .testTag(tag),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Drag handle, decorative: the card can't be dragged away, the map needs a centre.
    Box(
        modifier =
            Modifier.size(width = 36.dp, height = 4.dp)
                .background(AuthColors.Outline, RoundedCornerShape(2.dp))
    )
    content()
  }
}

/** Why the map asks for the location, then Continue (Android's dialog) or Not now. */
@Composable
private fun ColumnScope.ReasonContent(actions: MapLocationActions) {
  CardHeader(
      icon = R.drawable.ic_map_pin,
      title = R.string.map_location_reason_title,
      text = R.string.map_location_reason_text,
  )
  AuthPrimaryButton(
      text = stringResource(R.string.map_location_continue),
      onClick = actions.onContinue,
      enabled = true,
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag(MapLocationTags.CONTINUE_BUTTON),
  )
  CardTextButton(
      text = stringResource(R.string.map_location_not_now),
      onClick = actions.onNotNow,
      tag = MapLocationTags.NOT_NOW_BUTTON,
  )
}

/**
 * EPFL and UNIL, with [current] shown selected, and Cancel when there is a campus to go back to
 * (opened from "Change").
 */
@Composable
private fun ColumnScope.ChooserContent(current: Campus?, actions: MapLocationActions) {
  CardHeader(
      icon = R.drawable.ic_campus,
      title = R.string.map_campus_title,
      text = R.string.map_campus_text,
  )
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Campus.entries.forEach { campus ->
      CampusButton(
          campus = campus,
          selected = campus == current,
          onClick = { actions.onChooseCampus(campus) },
      )
    }
  }
  if (current != null) {
    CardTextButton(
        text = stringResource(R.string.map_campus_cancel),
        onClick = actions.onCancelChoice,
        tag = MapLocationTags.CANCEL_BUTTON,
    )
  }
}

/** The tinted icon circle, the title and the explanation at the top of a bottom card. */
@Composable
private fun CardHeader(@DrawableRes icon: Int, @StringRes title: Int, @StringRes text: Int) {
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Box(
        modifier = Modifier.size(40.dp).background(AuthColors.AccentContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      // Decorative: the title says what the card is about.
      Icon(
          painter = painterResource(icon),
          contentDescription = null,
          tint = AuthColors.Accent,
          modifier = Modifier.size(22.dp),
      )
    }
    Text(
        text = stringResource(title),
        color = AuthColors.TextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(text),
        color = AuthColors.TextSecondary,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
  }
}

/** A campus as a full-width outlined button: its name, its area, and a check when selected. */
@Composable
private fun CampusButton(campus: Campus, selected: Boolean, onClick: () -> Unit) {
  val (name, area) =
      when (campus) {
        Campus.EPFL -> R.string.map_campus_epfl to R.string.map_campus_epfl_area
        Campus.UNIL -> R.string.map_campus_unil to R.string.map_campus_unil_area
      }
  OutlinedButton(
      onClick = onClick,
      modifier =
          Modifier.fillMaxWidth()
              .heightIn(min = 48.dp)
              // TalkBack says "selected" on the current campus, as the check shows it.
              .semantics { this.selected = selected }
              .testTag(MapLocationTags.campusButton(campus)),
      shape = RoundedCornerShape(20.dp),
      border = BorderStroke(1.dp, if (selected) AuthColors.Accent else AuthColors.Outline),
      colors =
          ButtonDefaults.outlinedButtonColors(
              containerColor =
                  if (selected) AuthColors.SelectedBackground else AuthColors.CardBackground
          ),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
  ) {
    Icon(
        painter = painterResource(R.drawable.ic_campus),
        contentDescription = null,
        tint = AuthColors.Accent,
        modifier = Modifier.size(17.dp),
    )
    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
      Text(
          text = stringResource(name),
          color = AuthColors.TextPrimary,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
      )
      Text(text = stringResource(area), color = AuthColors.TextSecondary, fontSize = 12.sp)
    }
    if (selected) {
      Icon(
          painter = painterResource(R.drawable.ic_check),
          contentDescription = null,
          tint = AuthColors.Accent,
          modifier = Modifier.size(17.dp),
      )
    }
  }
}

/** A centred accent text button with a full-width, 48dp touch target. */
@Composable
private fun CardTextButton(text: String, onClick: () -> Unit, tag: String) {
  TextButton(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag),
  ) {
    Text(text = text, color = AuthColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
  }
}

/** A pill at the top of the map, over it, with a soft shadow so it reads on any map. */
@Composable
private fun BoxScope.TopPill(tag: String, content: @Composable () -> Unit) {
  Row(
      modifier =
          Modifier.align(Alignment.TopCenter)
              .padding(top = 16.dp)
              .shadow(2.dp, CircleShape)
              .background(AuthColors.CardBackground, CircleShape)
              .border(1.dp, AuthColors.Outline, CircleShape)
              .padding(start = 12.dp, end = 4.dp)
              .heightIn(min = 32.dp)
              .testTag(tag),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    content()
  }
}

/** "Finding your location…", announced by TalkBack when it appears. */
@Composable
private fun BoxScope.LocatingPill() {
  TopPill(MapLocationTags.LOCATING_PILL) {
    CircularProgressIndicator(
        color = AuthColors.Accent,
        strokeWidth = 2.dp,
        modifier = Modifier.size(16.dp),
    )
    Text(
        text = stringResource(R.string.map_locating),
        color = AuthColors.TextSecondary,
        fontSize = 12.sp,
        modifier =
            Modifier.padding(end = 8.dp, top = 8.dp, bottom = 8.dp).semantics {
              liveRegion = LiveRegionMode.Polite
            },
    )
  }
}

/** "Showing EPFL" with Change, which reopens the chooser. */
@Composable
private fun BoxScope.CampusPill(campus: Campus, onChange: () -> Unit) {
  val name =
      stringResource(
          when (campus) {
            Campus.EPFL -> R.string.map_campus_epfl
            Campus.UNIL -> R.string.map_campus_unil
          }
      )
  TopPill(MapLocationTags.CAMPUS_PILL) {
    Icon(
        painter = painterResource(R.drawable.ic_campus),
        contentDescription = null,
        tint = AuthColors.Accent,
        modifier = Modifier.size(16.dp),
    )
    Text(
        text = stringResource(R.string.map_showing_campus, name),
        color = AuthColors.TextPrimary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Box(modifier = Modifier.width(1.dp).height(16.dp).background(AuthColors.Outline))
    val changeDescription = stringResource(R.string.map_change_campus_description)
    // 48dp touch target although the pill looks smaller; read as "Change campus".
    TextButton(
        onClick = onChange,
        modifier =
            Modifier.heightIn(min = 48.dp)
                .semantics { contentDescription = changeDescription }
                .testTag(MapLocationTags.CHANGE_BUTTON),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
      Text(
          text = stringResource(R.string.map_change_campus),
          color = AuthColors.Accent,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
      )
    }
  }
}
