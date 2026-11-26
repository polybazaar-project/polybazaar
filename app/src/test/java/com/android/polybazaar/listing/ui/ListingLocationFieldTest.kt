// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.ui

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.connectivity.model.FakeConnectivityRepository
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.FakePendingListingQueue
import com.android.polybazaar.listing.model.FilePhotoStorage
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingLocation
import com.android.polybazaar.listing.model.ListingRules.MAX_LOCATION_NAME_LENGTH
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.viewmodel.ListingFormUiState
import com.android.polybazaar.listing.viewmodel.ListingFormViewModel
import com.android.polybazaar.map.model.Campus
import com.android.polybazaar.map.model.Coordinates
import com.android.polybazaar.map.model.FakeLocationRepository
import com.android.polybazaar.map.model.FakePlaceNameRepository
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The "Pickup location" / "Meeting place" field of the listing forms and its map picker (#339),
 * with a fake map that records the camera it shows and pans to [panTo] when tapped.
 */
@RunWith(AndroidJUnit4::class)
class ListingLocationFieldTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val today = LocalDate.of(2026, 11, 17)
  private val renens = Coordinates(46.5399, 6.5881)
  private val lausanne = Coordinates(46.5197, 6.6323)
  private val onRenens =
      ListingFormUiState(
          today = today,
          location = ListingLocation(renens.latitude, renens.longitude, "Renens"),
          suggestedPoint = renens,
      )

  /** The camera the map last showed, and whether it showed the device's location. */
  private var shownCamera: CameraPosition? = null
  private var myLocationShown: Boolean? = null
  private var panTo = renens

  /** Whether the map writes back a slightly different target once shown, as GoogleMap does. */
  private var nudge = false

  private val fakeMap: ListingLocationMap = { camera, showMyLocation ->
    shownCamera = camera.position
    myLocationShown = showMyLocation
    if (nudge)
        LaunchedEffect(Unit) {
          val (lat, lng) = camera.position.target.let { it.latitude to it.longitude }
          camera.position = CameraPosition.fromLatLngZoom(LatLng(lat + 1e-9, lng + 1e-9), 16f)
        }
    Box(
        Modifier.size(40.dp).testTag(FAKE_MAP).clickable {
          camera.position =
              CameraPosition.fromLatLngZoom(LatLng(panTo.latitude, panTo.longitude), 16f)
        }
    )
  }

  private fun mapCentre() = shownCamera?.target?.let { Coordinates(it.latitude, it.longitude) }

  /** What the field asked for, in order. */
  private val calls = mutableListOf<Any>()
  private val actions =
      ListingFormActions(
          onConfirmLocation = { calls += it },
          onLocationNameChange = { calls += "name:$it" },
          onClearLocation = { calls += "clear" },
          onLocateLender = { calls += "locate" },
      )

  private var uiState by mutableStateOf(ListingFormUiState(today = today))

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

  private fun setContent(
      registry: FakePermissionRegistry = FakePermissionRegistry(),
      restoration: StateRestorationTester? = null,
      content: @Composable () -> Unit,
  ) {
    val owner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry = registry
        }
    val provided =
        @Composable {
          CompositionLocalProvider(
              LocalActivityResultRegistryOwner provides owner,
              content = content,
          )
        }
    if (restoration != null) restoration.setContent(provided)
    else composeTestRule.setContent(provided)
  }

  /** Shows the field alone, reading [uiState] so tests can change it. */
  private fun showField(
      state: ListingFormUiState,
      registry: FakePermissionRegistry = FakePermissionRegistry(),
      restoration: StateRestorationTester? = null,
      map: ListingLocationMap = fakeMap,
  ) {
    uiState = state
    setContent(registry, restoration) {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        ListingLocationField(uiState = uiState, actions = actions, map = map)
      }
    }
  }

  /**
   * Opens the picker from "Choose on the map", or with a location set from [tag], the pin button.
   */
  private fun openPicker(tag: String = ListingLocationFieldTags.FIELD) {
    node(tag).performScrollTo().performClick()
    node(ListingLocationFieldTags.PICKER).assertIsDisplayed()
  }

  private fun assertNameIs(name: String) {
    node(ListingLocationFieldTags.NAME_FIELD).assertTextContains(name)
  }

  @Test
  fun withoutLocation_offersTheMapWithoutRemoveOrName() {
    showField(ListingFormUiState(today = today))

    node(ListingLocationFieldTags.FIELD)
        .assertContentDescriptionEquals(
            string(
                R.string.listing_location_description,
                string(R.string.listing_location_item_label),
                string(R.string.listing_location_placeholder),
            )
        )
    uiState = uiState.copy(type = ListingType.SERVICE)
    node(ListingLocationFieldTags.FIELD)
        .assertContentDescriptionContains(string(R.string.listing_location_service_label), true)
    node(ListingLocationFieldTags.REMOVE).assertDoesNotExist()
    node(ListingLocationFieldTags.NAME_FIELD).assertDoesNotExist()
    node(ListingLocationFieldTags.NAME_ERROR).assertDoesNotExist()
    node(ListingLocationFieldTags.PICKER).assertDoesNotExist()
    node(ListingLocationFieldTags.OFFLINE_HINT).assertDoesNotExist()
  }

  @Test
  fun withANamedLocation_showsItAndEditsOrRemovesIt() {
    showField(onRenens)

    assertNameIs("Renens")
    node(ListingLocationFieldTags.FIELD).assertDoesNotExist()
    composeTestRule.onNodeWithText("6/$MAX_LOCATION_NAME_LENGTH").assertIsDisplayed()
    node(ListingLocationFieldTags.NAME_ERROR).assertDoesNotExist()
    node(ListingLocationFieldTags.NAME_FIELD).performTextReplacement("Gare")
    node(ListingLocationFieldTags.REMOVE).performClick()

    assertEquals(listOf<Any>("name:Gare", "clear"), calls)
  }

  @Test
  fun withABlankName_asksForANameOnceTheFieldWasLeft() {
    val unnamed = onRenens.copy(location = onRenens.location!!.copy(name = ""))
    showField(unnamed)

    composeTestRule.onNodeWithText("0/$MAX_LOCATION_NAME_LENGTH").assertIsDisplayed()
    node(ListingLocationFieldTags.NAME_ERROR).assertDoesNotExist()

    uiState = unnamed.copy(locationNameTouched = true)
    node(ListingLocationFieldTags.NAME_ERROR)
        .assertTextEquals(string(R.string.listing_location_name_error))
  }

  @Test
  fun withALocation_asksForAPublicPlace_untilTheNameErrorReplacesIt() {
    showField(ListingFormUiState(today = today))
    node(ListingLocationFieldTags.NAME_PRIVACY_HINT).assertDoesNotExist()

    uiState = onRenens
    node(ListingLocationFieldTags.NAME_PRIVACY_HINT)
        .assertTextEquals(string(R.string.listing_location_name_privacy_hint))

    // One message under the field at a time: the missing name matters more.
    uiState =
        onRenens.copy(location = onRenens.location!!.copy(name = ""), locationNameTouched = true)
    node(ListingLocationFieldTags.NAME_ERROR).assertIsDisplayed()
    node(ListingLocationFieldTags.NAME_PRIVACY_HINT).assertDoesNotExist()
  }

  @Test
  fun picker_saysRentersOnlySeeAnArea() {
    showField(onRenens)
    openPicker(ListingLocationFieldTags.MOVE_PIN)

    node(ListingLocationFieldTags.APPROXIMATE_HINT)
        .assertTextEquals(string(R.string.listing_location_approximate_hint))
  }

  @Test
  fun whenTheFormCannotBeEditedIsSavingOrOffline_theFieldIsDisabled() {
    showField(onRenens.copy(loadError = AppError.NETWORK))
    for (state in
        listOf(onRenens.copy(loadError = AppError.NETWORK), onRenens.copy(isSaving = true))) {
      uiState = state
      node(ListingLocationFieldTags.MOVE_PIN).assertIsNotEnabled().performClick()
      node(ListingLocationFieldTags.REMOVE).assertIsNotEnabled().performClick()
      node(ListingLocationFieldTags.PICKER).assertDoesNotExist()
    }
    uiState = ListingFormUiState(today = today, isSaving = true)
    node(ListingLocationFieldTags.FIELD).assertIsNotEnabled().performClick()
    node(ListingLocationFieldTags.PICKER).assertDoesNotExist()
    // Offline the map cannot be opened, but the place can still be removed.
    uiState = onRenens.copy(isOnline = false)
    node(ListingLocationFieldTags.OFFLINE_HINT).assertIsDisplayed()
    node(ListingLocationFieldTags.MOVE_PIN).assertIsNotEnabled()
    node(ListingLocationFieldTags.REMOVE).assertIsEnabled()
    uiState = ListingFormUiState(today = today, isOnline = false)
    node(ListingLocationFieldTags.FIELD).assertIsNotEnabled()
    assertTrue(calls.isEmpty())
  }

  @Test
  fun picker_opensOnTheSuggestedPointAndConfirmsIt() {
    showField(onRenens)
    openPicker(ListingLocationFieldTags.MOVE_PIN)

    assertEquals(renens, mapCentre())
    assertEquals(16f, shownCamera!!.zoom)
    node(ListingLocationFieldTags.CENTRE_PIN).assertIsDisplayed()
    node(ListingLocationFieldTags.CONFIRM).performClick()

    assertEquals(listOf<Any>(renens), calls)
    node(ListingLocationFieldTags.PICKER).assertDoesNotExist()
  }

  @Test
  fun picker_confirmsThePointUnderThePinAfterPanning() {
    showField(ListingFormUiState(today = today))
    openPicker()
    assertEquals(Campus.EPFL.centre, mapCentre())

    node(FAKE_MAP).performClick()
    node(ListingLocationFieldTags.CONFIRM).performClick()

    assertEquals(listOf<Any>(renens), calls)
    node(ListingLocationFieldTags.PICKER).assertDoesNotExist()
  }

  @Test
  fun picker_closeAndSystemBackSaveNothing() {
    showField(ListingFormUiState(today = today))

    openPicker()
    node(FAKE_MAP).performClick()
    node(ListingLocationFieldTags.PICKER_CLOSE).performClick()
    node(ListingLocationFieldTags.PICKER).assertDoesNotExist()

    openPicker()
    node(FAKE_MAP).performClick()
    uiState = uiState.copy(isOnline = false)
    node(ListingLocationFieldTags.CONFIRM).assertIsNotEnabled().performClick()
    Espresso.pressBack()
    node(ListingLocationFieldTags.PICKER).assertDoesNotExist()
    node(ListingLocationFieldTags.FIELD).assertExists()

    assertTrue(calls.isEmpty())
  }

  @Test
  fun picker_staysOpenWhereItWasWhenTheScreenIsRecreated() {
    val restoration = StateRestorationTester(composeTestRule)
    showField(ListingFormUiState(today = today), restoration = restoration)
    openPicker()
    node(FAKE_MAP).performClick()

    restoration.emulateSavedInstanceStateRestore()

    node(ListingLocationFieldTags.PICKER).assertIsDisplayed()
    assertEquals(renens, mapCentre())
  }

  @Test
  fun picker_followsTheSuggestedPointUntilTheMapIsMoved() {
    showField(ListingFormUiState(today = today))
    openPicker()

    uiState = uiState.copy(suggestedPoint = lausanne)
    composeTestRule.waitForIdle()
    assertEquals(lausanne, mapCentre())
    assertEquals(16f, shownCamera!!.zoom)

    node(FAKE_MAP).performClick()
    uiState = uiState.copy(suggestedPoint = Campus.UNIL.centre)
    composeTestRule.waitForIdle()
    assertEquals(renens, mapCentre())
  }

  @Test
  fun picker_followsTheSuggestedPointAfterTheMapNudgesTheCamera() {
    nudge = true
    showField(ListingFormUiState(today = today))
    openPicker()
    val title = string(R.string.listing_location_picker_title)
    composeTestRule.onNode(hasText(title) and isHeading()).assertExists()
    assertNotEquals(Campus.EPFL.centre, mapCentre())

    uiState = uiState.copy(suggestedPoint = lausanne)
    composeTestRule.waitForIdle()
    assertEquals(lausanne, mapCentre())
  }

  @Test
  fun showMyLocation_withoutPermission_asksWhenTappedAndLocatesOnceGranted() {
    val registry = FakePermissionRegistry(ArrayDeque(listOf(false, true)))
    showField(ListingFormUiState(today = today), registry)
    openPicker()
    assertEquals(false, myLocationShown)

    // Refused: nothing changes, and the button stays to ask again.
    node(ListingLocationFieldTags.SHOW_MY_LOCATION).performClick()
    assertEquals(false, myLocationShown)
    assertTrue(calls.isEmpty())

    node(ListingLocationFieldTags.SHOW_MY_LOCATION).performClick()

    assertEquals(List(2) { Manifest.permission.ACCESS_COARSE_LOCATION }, registry.requested)
    node(ListingLocationFieldTags.SHOW_MY_LOCATION).assertDoesNotExist()
    assertEquals(true, myLocationShown)
    assertEquals(listOf<Any>("locate"), calls)
  }

  @Test
  fun showMyLocation_withPermission_locatesWithoutAsking() {
    shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    val registry = FakePermissionRegistry()
    showField(ListingFormUiState(today = today), registry)
    openPicker()

    node(ListingLocationFieldTags.SHOW_MY_LOCATION).assertDoesNotExist()
    assertEquals(true, myLocationShown)
    assertTrue(registry.requested.isEmpty())
    assertEquals(listOf<Any>("locate"), calls)
  }

  // Through a real ListingFormViewModel: the confirmed location is saved with the listing.

  private fun viewModel(repository: FakeListingRepository, listingId: String?) =
      ListingFormViewModel(
          listingId,
          repository,
          FilePhotoStorage(
              context,
              if (listingId == null) FilePhotoStorage.CREATE_FORM else FilePhotoStorage.EDIT_FORM,
          ),
          SavedStateHandle(),
          FakeLocationRepository(lausanne),
          FakePlaceNameRepository(),
          FakeConnectivityRepository(),
          FakePendingListingQueue(),
      ) {
        today
      }

  @Test
  fun create_opensOnTheLenderAndSavesTheNamedLocation() {
    shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    val repository = FakeListingRepository()
    val viewModel = viewModel(repository, listingId = null)
    setContent {
      CreateListingContent(
          uiState = viewModel.uiState.collectAsState().value,
          actions = listingFormActions(viewModel),
          locationMap = fakeMap,
      )
    }
    node(CreateListingScreenTags.TITLE_FIELD).performTextInput("Drill")
    node(CreateListingScreenTags.DESCRIPTION_FIELD).performTextInput("Works well")
    node(CreateListingScreenTags.PRICE_FIELD).performTextInput("5")
    node(CreateListingScreenTags.CATEGORY_FIELD).performClick()
    node(CreateListingScreenTags.categoryOption(ListingCategory.ITEM_TOOLS)).performClick()

    openPicker()
    composeTestRule.waitForIdle()
    assertEquals(lausanne, mapCentre())
    node(ListingLocationFieldTags.CONFIRM).performClick()
    // No error before the lender has had a chance to name the place.
    node(ListingLocationFieldTags.NAME_ERROR).assertDoesNotExist()
    node(ListingLocationFieldTags.NAME_FIELD).performScrollTo().performClick()
    node(CreateListingScreenTags.TITLE_FIELD).performScrollTo().performClick()
    node(ListingLocationFieldTags.NAME_ERROR).performScrollTo().assertIsDisplayed()
    node(ListingLocationFieldTags.NAME_FIELD).performTextReplacement("Flon")
    node(CreateListingScreenTags.CREATE_BUTTON).performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertEquals(
        ListingLocation(lausanne.latitude, lausanne.longitude, "Flon"),
        repository.allListings.single().location,
    )
  }

  @Test
  fun edit_savesTheCampusPickedOnTheMap() {
    val drill =
        ItemListing(
            listingId = "drill",
            category = ListingCategory.ITEM_TOOLS,
            price = 12.0,
            title = "Bosch drill",
            description = "Cordless",
        )
    val repository = FakeListingRepository(myListings = listOf(drill))
    val viewModel = viewModel(repository, listingId = "drill")
    panTo = Campus.UNIL.centre
    setContent {
      EditListingContent(
          uiState = viewModel.uiState.collectAsState().value,
          actions = listingFormActions(viewModel),
          onBack = {},
          onRetry = {},
          locationMap = fakeMap,
      )
    }

    openPicker()
    node(FAKE_MAP).performClick()
    node(ListingLocationFieldTags.CONFIRM).performClick()
    assertNameIs("UNIL campus")
    node(EditListingScreenTags.SAVE_BUTTON).performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertEquals(
        ListingLocation(Campus.UNIL.centre.latitude, Campus.UNIL.centre.longitude, "UNIL campus"),
        repository.updatedListings.single().location,
    )
  }

  /** Stands in for the permission dialog: answers each request with the next of [answers]. */
  private class FakePermissionRegistry(private val answers: ArrayDeque<Boolean> = ArrayDeque()) :
      ActivityResultRegistry() {
    val requested = mutableListOf<Any?>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
      requested += input
      dispatchResult(requestCode, answers.removeFirst())
    }
  }

  private companion object {
    const val FAKE_MAP = "fake_location_map"
  }
}
