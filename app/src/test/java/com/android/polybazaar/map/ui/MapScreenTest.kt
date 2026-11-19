// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingLocation
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.map.model.Campus
import com.android.polybazaar.map.model.Coordinates
import com.android.polybazaar.map.model.FakeLocationRepository
import com.android.polybazaar.map.viewmodel.MapCentre
import com.android.polybazaar.map.viewmodel.MapMarker
import com.android.polybazaar.map.viewmodel.MapUiState
import com.android.polybazaar.map.viewmodel.MapViewModel
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private val drill =
      MapMarker("drill", "Bosch drill set", ListingType.ITEM, Coordinates(46.519, 6.567), "CHF 8")

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  /** The map tab with a stand-in map, since Google Maps can't run in unit tests. */
  private fun setContent(uiState: MapUiState, onRetry: () -> Unit = {}) {
    composeTestRule.setContent {
      MapContent(
          uiState = uiState,
          onRetry = onRetry,
          map = { Box(Modifier.fillMaxSize().testTag("map")) },
          locationLayer = { Box(Modifier.fillMaxSize().testTag("location_layer")) },
      )
    }
  }

  // ---- Camera ----

  @Test
  fun camera_isCloseOnTheRenter() {
    val renter = Coordinates(46.52, 6.57)

    assertEquals(MapCamera(renter, 15f), cameraFor(MapCentre.Located(renter)))
  }

  @Test
  fun camera_isCloseOnTheCampus() {
    assertEquals(MapCamera(Campus.UNIL.centre, 15f), cameraFor(MapCentre.OnCampus(Campus.UNIL)))
  }

  @Test
  fun camera_beforeTheCentreIsKnown_showsBothCampuses() {
    listOf(MapCentre.Undecided, MapCentre.Locating, MapCentre.NeedsChoice()).forEach { centre ->
      val camera = cameraFor(centre)
      assertEquals(14f, camera.zoom)
      // Between the campuses, on both axes.
      val latitudes = Campus.entries.map { it.centre.latitude }
      val longitudes = Campus.entries.map { it.centre.longitude }
      assertTrue(camera.target.latitude in latitudes.min()..latitudes.max())
      assertTrue(camera.target.longitude in longitudes.min()..longitudes.max())
    }
  }

  // ---- Markers ----

  @Test
  fun itemsAndSkills_haveDifferentIconsAndDescriptions() {
    assertNotEquals(markerIcon(ListingType.ITEM), markerIcon(ListingType.SERVICE))
    assertNotEquals(markerDescription(ListingType.ITEM), markerDescription(ListingType.SERVICE))
  }

  @Test
  fun markerLabel_readsTitleTypeAndPrice() {
    var label = ""
    composeTestRule.setContent { label = markerLabel(drill) }
    composeTestRule.waitForIdle()

    assertEquals(context.getString(R.string.map_marker_item, "Bosch drill set", "CHF 8"), label)
  }

  @Test
  fun markerPin_drawsItsIcon() {
    composeTestRule.setContent {
      MarkerPin(icon = markerIcon(ListingType.SERVICE), modifier = Modifier.testTag("pin"))
    }

    node("pin").assertIsDisplayed()
  }

  // ---- Listings state over the map ----

  @Test
  fun mapAndLocationLayer_areBothDrawn() {
    setContent(MapUiState(markers = listOf(drill)))

    node("map").assertIsDisplayed()
    node("location_layer").assertIsDisplayed()
  }

  @Test
  fun firstLoad_showsLoading() {
    setContent(MapUiState(isLoading = true))

    node(MapScreenTags.LOADING).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.map_loading_listings))
        .assertIsDisplayed()
  }

  @Test
  fun reloadWithMarkersShown_showsNothing_soTheMapDoesNotFlicker() {
    setContent(MapUiState(markers = listOf(drill), isLoading = true))

    node(MapScreenTags.LOADING).assertDoesNotExist()
    node(MapScreenTags.EMPTY).assertDoesNotExist()
  }

  @Test
  fun noListingWithALocation_saysSo() {
    setContent(MapUiState())

    node(MapScreenTags.EMPTY).assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.map_no_listings)).assertIsDisplayed()
  }

  @Test
  fun markersShown_showNoStatus() {
    setContent(MapUiState(markers = listOf(drill)))

    node(MapScreenTags.LOADING).assertDoesNotExist()
    node(MapScreenTags.EMPTY).assertDoesNotExist()
    node(MapScreenTags.ERROR).assertDoesNotExist()
  }

  @Test
  fun failedLoad_saysWhy_andRetryReloads() {
    var retries = 0
    setContent(MapUiState(error = AppError.NETWORK), onRetry = { retries++ })

    node(MapScreenTags.ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.error_network)).assertIsDisplayed()
    node(MapScreenTags.RETRY_BUTTON).assertHeightIsAtLeast(48.dp).performClick()

    assertEquals(1, retries)
  }

  @Test
  fun failedReloadWithMarkersShown_stillOffersRetry() {
    setContent(MapUiState(markers = listOf(drill), error = AppError.NETWORK))

    node(MapScreenTags.RETRY_BUTTON).assertIsDisplayed()
  }

  // ---- With the real ViewModel ----

  @Test
  fun screen_showsTheViewModelsMarkers_andRetryReloads() {
    val listings =
        FakeListingRepository(
            allListings =
                listOf(
                    ItemListing(
                        category = ListingCategory.ITEM_TOOLS,
                        price = 8.0,
                        title = "Bosch drill set",
                        description = "With bits",
                        listingId = "drill",
                        ownerUid = "lender",
                        location = ListingLocation(46.519, 6.567, "Rolex Learning Center"),
                    )
                )
        )
    // The first load fails, as offline without a cache.
    listings.failure = UnknownHostException("firestore.googleapis.com")
    val viewModel = MapViewModel(FakeLocationRepository(), listings, "renter", SavedStateHandle())
    // A campus already picked, so the location card doesn't cover the bottom of the map.
    viewModel.chooseCampus(Campus.EPFL)
    var shown = emptyList<MapMarker>()

    composeTestRule.setContent { MapScreen(viewModel, map = { shown = it.markers }) }
    composeTestRule.waitForIdle()
    assertEquals(emptyList<String>(), shown.map { it.id })
    node(MapLocationTags.CAMPUS_PILL).assertIsDisplayed()

    listings.failure = null
    node(MapScreenTags.RETRY_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(listOf("drill"), shown.map { it.id })
    node(MapScreenTags.ERROR).assertDoesNotExist()
  }
}
