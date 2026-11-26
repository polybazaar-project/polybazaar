// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
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
import org.junit.Assert.assertNull
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
  private fun setContent(
      uiState: MapUiState,
      onRetry: () -> Unit = {},
      onOpenListing: (String) -> Unit = {},
      onClosePreview: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      MapContent(
          uiState = uiState,
          onRetry = onRetry,
          map = { Box(Modifier.fillMaxSize().testTag("map")) },
          locationLayer = { Box(Modifier.fillMaxSize().testTag("location_layer")) },
          onOpenListing = onOpenListing,
          onClosePreview = onClosePreview,
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

    composeTestRule.setContent { MapScreen(viewModel, map = { state, _ -> shown = state.markers }) }
    composeTestRule.waitForIdle()
    assertEquals(emptyList<String>(), shown.map { it.id })
    node(MapLocationTags.CAMPUS_PILL).assertIsDisplayed()

    listings.failure = null
    node(MapScreenTags.RETRY_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(listOf("drill"), shown.map { it.id })
    node(MapScreenTags.ERROR).assertDoesNotExist()
  }

  // ---- Marker preview (#342) ----

  private val skill =
      MapMarker(
          "tutor",
          "Calculus tutoring",
          ListingType.SERVICE,
          Coordinates(46.52, 6.56),
          "CHF 25",
      )

  @Test
  fun preview_showsTheSelectedListing_andReplacesTheStatus() {
    setContent(MapUiState(markers = listOf(drill), selectedMarkerId = "drill", isLoading = true))

    node(MapScreenTags.PREVIEW).assertIsDisplayed()
    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule
        .onNodeWithText("CHF 8 ${context.getString(R.string.browse_price_per_day)}")
        .assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.map_preview_item)).assertIsDisplayed()
    // The marker is the snapped point, not the lender's spot (#23).
    node(MapScreenTags.PREVIEW_AREA)
        .assertTextEquals(context.getString(R.string.map_preview_approximate))
    // The bottom of the map holds one thing at a time: the preview, not the loading pill.
    node(MapScreenTags.LOADING).assertDoesNotExist()
  }

  @Test
  fun preview_withAFailedReload_showsTheErrorAboveIt_andRetryStillWorks() {
    var retried = false
    // Offline: the reload failed, but the previewed listing is still on the map.
    setContent(
        MapUiState(markers = listOf(drill), selectedMarkerId = "drill", error = AppError.NETWORK),
        onRetry = { retried = true },
    )

    node(MapScreenTags.PREVIEW).assertIsDisplayed()
    node(MapScreenTags.ERROR).assertIsDisplayed()
    // Stacked, not overlapping: the error pill ends above the card.
    assertTrue(
        node(MapScreenTags.ERROR).getBoundsInRoot().bottom <=
            node(MapScreenTags.PREVIEW).getBoundsInRoot().top
    )
    node(MapScreenTags.RETRY_BUTTON).performClick()
    assertTrue(retried)
  }

  @Test
  fun preview_ofASkill_saysSkill() {
    setContent(MapUiState(markers = listOf(skill), selectedMarkerId = "tutor"))

    composeTestRule
        .onNodeWithText(context.getString(R.string.map_preview_skill))
        .assertIsDisplayed()
  }

  @Test
  fun preview_isNotShown_withoutASelection() {
    setContent(MapUiState(markers = listOf(drill)))

    node(MapScreenTags.PREVIEW).assertDoesNotExist()
  }

  @Test
  fun preview_tapped_opensTheListing_andTalkBackSaysWhich() {
    var opened: String? = null
    setContent(
        MapUiState(markers = listOf(drill), selectedMarkerId = "drill"),
        onOpenListing = { opened = it },
    )

    node(MapScreenTags.PREVIEW)
        // TalkBack says "Open Bosch drill set" rather than just "double tap to activate".
        .assert(
            SemanticsMatcher("click label opens the listing") {
              it.config[SemanticsActions.OnClick].label ==
                  context.getString(R.string.map_preview_open, "Bosch drill set")
            }
        )
        .performClick()

    assertEquals("drill", opened)
  }

  @Test
  fun preview_close_closesItWithoutOpening() {
    var opened: String? = null
    var closed = false
    setContent(
        MapUiState(markers = listOf(drill), selectedMarkerId = "drill"),
        onOpenListing = { opened = it },
        onClosePreview = { closed = true },
    )

    node(MapScreenTags.PREVIEW_CLOSE).assertHeightIsAtLeast(48.dp).performClick()

    assertTrue(closed)
    assertNull(opened)
  }

  @Test
  fun mapScreen_markerTapped_showsItsPreview_mapTapped_closesIt() {
    val listings =
        FakeListingRepository(
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
    val viewModel = MapViewModel(FakeLocationRepository(), listings, "renter", SavedStateHandle())
    viewModel.chooseCampus(Campus.EPFL)
    var opened: String? = null
    composeTestRule.setContent {
      MapScreen(
          viewModel,
          onOpenListing = { opened = it },
          // A stand-in that taps like the Google map: one button per marker, and the map itself.
          map = { state, actions ->
            Column {
              Button(onClick = actions.onMapClick, modifier = Modifier.testTag("map")) {
                Text("map")
              }
              state.markers.forEach { marker ->
                Button(
                    onClick = { actions.onMarkerClick(marker.id) },
                    modifier = Modifier.testTag("marker_${marker.id}"),
                ) {
                  Text(marker.id)
                }
              }
            }
          },
      )
    }
    composeTestRule.waitForIdle()

    node("marker_drill").performClick()
    node(MapScreenTags.PREVIEW).assertIsDisplayed().performClick()
    assertEquals("drill", opened)

    node("map").performClick()
    node(MapScreenTags.PREVIEW).assertDoesNotExist()
  }
}
