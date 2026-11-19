// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.ui

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.map.model.Campus
import com.android.polybazaar.map.model.Coordinates
import com.android.polybazaar.map.model.FakeLocationRepository
import com.android.polybazaar.map.viewmodel.MapCentre
import com.android.polybazaar.map.viewmodel.MapViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class MapLocationLayerTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun setContent(centre: MapCentre, actions: MapLocationActions = MapLocationActions()) {
    composeTestRule.setContent { MapLocationContent(centre = centre, actions = actions) }
  }

  @Test
  fun undecided_explainsWhy_andContinueOrNotNowCallBack() {
    val calls = mutableListOf<String>()
    setContent(
        MapCentre.Undecided,
        MapLocationActions(onContinue = { calls += "continue" }, onNotNow = { calls += "not now" }),
    )

    composeTestRule
        .onNodeWithText(context.getString(R.string.map_location_reason_title))
        .assertIsDisplayed()
    node(MapLocationTags.CONTINUE_BUTTON).performClick()
    node(MapLocationTags.NOT_NOW_BUTTON).assertHeightIsAtLeast(48.dp).performClick()

    assertEquals(listOf("continue", "not now"), calls)
  }

  @Test
  fun locating_showsThePill() {
    setContent(MapCentre.Locating)

    node(MapLocationTags.LOCATING_PILL).assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.map_locating)).assertIsDisplayed()
  }

  @Test
  fun located_showsNothingOverTheMap() {
    setContent(MapCentre.Located(Coordinates(46.52, 6.57)))

    node(MapLocationTags.REASON_CARD).assertDoesNotExist()
    node(MapLocationTags.LOCATING_PILL).assertDoesNotExist()
    node(MapLocationTags.CAMPUS_PILL).assertDoesNotExist()
    node(MapLocationTags.CHOOSER_CARD).assertDoesNotExist()
  }

  @Test
  fun onCampus_saysWhichOne_andChangeCallsBack() {
    var changes = 0
    setContent(MapCentre.OnCampus(Campus.UNIL), MapLocationActions(onChangeCampus = { changes++ }))

    composeTestRule
        .onNodeWithText(context.getString(R.string.map_showing_campus, "UNIL"))
        .assertIsDisplayed()
    node(MapLocationTags.CHANGE_BUTTON).assertHeightIsAtLeast(48.dp).performClick()

    assertEquals(1, changes)
  }

  @Test
  fun needsChoice_offersBothCampuses_withoutCancel() {
    val chosen = mutableListOf<Campus>()
    setContent(MapCentre.NeedsChoice(), MapLocationActions(onChooseCampus = { chosen += it }))

    node(MapLocationTags.campusButton(Campus.EPFL)).assertIsNotSelected()
    node(MapLocationTags.campusButton(Campus.UNIL)).assertHeightIsAtLeast(48.dp).performClick()
    // The map needs a centre, so there is nothing to cancel to.
    node(MapLocationTags.CANCEL_BUTTON).assertDoesNotExist()

    assertEquals(listOf(Campus.UNIL), chosen)
  }

  @Test
  fun changingCampus_showsTheCurrentOneSelected_andCancelCallsBack() {
    var cancels = 0
    setContent(
        MapCentre.NeedsChoice(current = Campus.EPFL),
        MapLocationActions(onCancelChoice = { cancels++ }),
    )

    node(MapLocationTags.campusButton(Campus.EPFL)).assertIsSelected()
    node(MapLocationTags.campusButton(Campus.UNIL)).assertIsNotSelected()
    node(MapLocationTags.CANCEL_BUTTON).performClick()

    assertEquals(1, cancels)
  }

  // ---- With the real ViewModel and Android's permission ----

  private val listings = FakeListingRepository()
  private val location = FakeLocationRepository(Coordinates(46.518, 6.568))

  /** Stands in for Android's permission dialog: every request is answered [granted]. */
  private fun permissionDialog(granted: Boolean, onAsk: (String) -> Unit = {}) =
      object : ActivityResultRegistryOwner {
        override val activityResultRegistry =
            object : ActivityResultRegistry() {
              override fun <I, O> onLaunch(
                  requestCode: Int,
                  contract: ActivityResultContract<I, O>,
                  input: I,
                  options: ActivityOptionsCompat?,
              ) {
                onAsk(input as String)
                dispatchResult(requestCode, granted)
              }
            }
      }

  private fun showLayer(viewModel: MapViewModel, owner: ActivityResultRegistryOwner? = null) {
    composeTestRule.setContent {
      if (owner == null) {
        MapLocationLayer(viewModel)
      } else {
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
          MapLocationLayer(viewModel)
        }
      }
    }
  }

  @Test
  fun permissionAlreadyGranted_skipsTheExplanation_andCentresOnTheRenter() {
    shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    val viewModel = MapViewModel(location, listings, "renter", SavedStateHandle())

    showLayer(viewModel)
    composeTestRule.waitForIdle()

    node(MapLocationTags.REASON_CARD).assertDoesNotExist()
    assertEquals(MapCentre.Located(Coordinates(46.518, 6.568)), viewModel.uiState.value.centre)
  }

  @Test
  fun permissionGrantedInSettingsWhileExplaining_readsTheLocationOnTheNextRedraw() {
    val viewModel = MapViewModel(location, listings, "renter", SavedStateHandle())
    var redraws by mutableIntStateOf(0)
    // A new modifier on each redraw, like a parent recomposing around the layer.
    composeTestRule.setContent { MapLocationLayer(viewModel, Modifier.testTag("layer_$redraws")) }
    node(MapLocationTags.REASON_CARD).assertIsDisplayed()

    // The renter grants the permission in Android's settings and comes back.
    shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    redraws++
    composeTestRule.waitForIdle()

    assertEquals(1, location.calls)
    assertEquals(MapCentre.Located(Coordinates(46.518, 6.568)), viewModel.uiState.value.centre)
  }

  @Test
  fun continue_asksForApproximateLocationOnly_andAGrantCentresOnTheRenter() {
    val asked = mutableListOf<String>()
    val viewModel = MapViewModel(location, listings, "renter", SavedStateHandle())
    showLayer(viewModel, permissionDialog(granted = true) { asked += it })

    node(MapLocationTags.CONTINUE_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(listOf(Manifest.permission.ACCESS_COARSE_LOCATION), asked)
    assertEquals(MapCentre.Located(Coordinates(46.518, 6.568)), viewModel.uiState.value.centre)
  }

  @Test
  fun aRefusal_showsTheChooser_andPickingACampusCentresOnIt() {
    val viewModel = MapViewModel(location, listings, "renter", SavedStateHandle())
    showLayer(viewModel, permissionDialog(granted = false))

    node(MapLocationTags.CONTINUE_BUTTON).performClick()
    node(MapLocationTags.campusButton(Campus.EPFL)).performClick()

    node(MapLocationTags.CAMPUS_PILL).assertIsDisplayed()
    assertEquals(MapCentre.OnCampus(Campus.EPFL), viewModel.uiState.value.centre)
    assertEquals(0, location.calls)
  }

  @Test
  fun notNow_showsTheChooser_withoutAskingAndroid() {
    val asked = mutableListOf<String>()
    val viewModel = MapViewModel(location, listings, "renter", SavedStateHandle())
    showLayer(viewModel, permissionDialog(granted = true) { asked += it })

    node(MapLocationTags.NOT_NOW_BUTTON).performClick()

    node(MapLocationTags.CHOOSER_CARD).assertIsDisplayed()
    assertEquals(emptyList<String>(), asked)
  }

  @Test
  fun changeThenCancel_goesBackToTheCampus() {
    val viewModel = MapViewModel(location, listings, "renter", SavedStateHandle())
    viewModel.chooseCampus(Campus.UNIL)
    showLayer(viewModel)

    node(MapLocationTags.CHANGE_BUTTON).performClick()
    node(MapLocationTags.campusButton(Campus.UNIL)).assertIsSelected()
    node(MapLocationTags.CANCEL_BUTTON).performClick()

    node(MapLocationTags.CAMPUS_PILL).assertIsDisplayed()
    assertEquals(MapCentre.OnCampus(Campus.UNIL), viewModel.uiState.value.centre)
  }
}
