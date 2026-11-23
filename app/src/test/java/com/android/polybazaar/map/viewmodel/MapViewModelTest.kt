// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingLocation
import com.android.polybazaar.map.model.Campus
import com.android.polybazaar.map.model.Coordinates
import com.android.polybazaar.map.model.FakeLocationRepository
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val nearRolex = Coordinates(46.518, 6.568)
  private val listings = FakeListingRepository()
  private val location = FakeLocationRepository(nearRolex)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private val handle = SavedStateHandle()

  private fun viewModel() = MapViewModel(location, listings, "renter", handle)

  private fun MapViewModel.centre() = uiState.value.centre

  @Test
  fun beforeThePermissionIsKnown_theCentreIsUndecided() {
    val viewModel = viewModel()

    assertEquals(MapCentre.Undecided, viewModel.centre())
    assertEquals(0, location.calls)
  }

  @Test
  fun permissionGranted_readsTheLocation_andCentresOnIt() {
    val viewModel = viewModel()

    viewModel.onPermissionResult(granted = true)
    assertEquals(MapCentre.Locating, viewModel.centre())
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(MapCentre.Located(nearRolex), viewModel.centre())
    assertEquals(1, location.calls)
  }

  @Test
  fun permissionRefused_asksForACampus_withoutReadingTheLocation() {
    val viewModel = viewModel()

    viewModel.onPermissionResult(granted = false)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(MapCentre.NeedsChoice(), viewModel.centre())
    assertEquals(0, location.calls)
  }

  @Test
  fun locationUnavailable_asksForACampus() {
    // Location turned off, or no fix offline.
    location.failure = IllegalStateException("No location available")
    val viewModel = viewModel()

    viewModel.onPermissionResult(granted = true)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(MapCentre.NeedsChoice(), viewModel.centre())
  }

  @Test
  fun secondAnswerWhileLocating_readsTheLocationOnce() {
    location.gate = CompletableDeferred()
    val viewModel = viewModel()

    viewModel.onPermissionResult(granted = true)
    testDispatcher.scheduler.runCurrent()
    viewModel.onPermissionResult(granted = true)
    viewModel.onPermissionResult(granted = false)
    location.gate?.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, location.calls)
    assertEquals(MapCentre.Located(nearRolex), viewModel.centre())
  }

  @Test
  fun permissionGrantedLater_readsTheLocation() {
    // For example, refused at first, then allowed in the system settings and the map reopened.
    val viewModel = viewModel()
    viewModel.onPermissionResult(granted = false)

    viewModel.onPermissionResult(granted = true)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(MapCentre.Located(nearRolex), viewModel.centre())
  }

  @Test
  fun choosingACampus_centresOnIt() {
    val viewModel = viewModel()
    viewModel.onPermissionResult(granted = false)

    viewModel.chooseCampus(Campus.UNIL)

    assertEquals(MapCentre.OnCampus(Campus.UNIL), viewModel.centre())
  }

  @Test
  fun chosenCampus_isRememberedForTheSession() {
    // A new ViewModel with the same handle: the tab rebuilt, or the app restored by Android.
    viewModel().chooseCampus(Campus.UNIL)

    val reopened = viewModel()
    reopened.onPermissionResult(granted = false)

    assertEquals(MapCentre.OnCampus(Campus.UNIL), reopened.centre())
  }

  @Test
  fun locationUnavailable_withACampusChosen_goesBackToIt() {
    location.failure = IllegalStateException("No location available")
    val viewModel =
        MapViewModel(
            location,
            listings,
            "renter",
            SavedStateHandle(mapOf("map_chosen_campus" to "EPFL")),
        )

    viewModel.onPermissionResult(granted = true)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(MapCentre.OnCampus(Campus.EPFL), viewModel.centre())
  }

  @Test
  fun unknownSavedCampus_readsAsNone() {
    val viewModel =
        MapViewModel(
            location,
            listings,
            "renter",
            SavedStateHandle(mapOf("map_chosen_campus" to "ETHZ")),
        )

    viewModel.onPermissionResult(granted = false)

    assertEquals(MapCentre.NeedsChoice(), viewModel.centre())
  }

  @Test
  fun changeCampus_opensTheChooserWithTheCurrentCampus_andCancelGoesBack() {
    val viewModel = viewModel()
    viewModel.chooseCampus(Campus.EPFL)

    viewModel.changeCampus()
    assertEquals(MapCentre.NeedsChoice(current = Campus.EPFL), viewModel.centre())

    viewModel.cancelCampusChoice()
    assertEquals(MapCentre.OnCampus(Campus.EPFL), viewModel.centre())
  }

  @Test
  fun changeCampus_fromTheRenterLocation_doesNothing() {
    // There is no campus to cancel back to, so opening the chooser would trap the renter in it.
    val viewModel = viewModel()
    viewModel.onPermissionResult(granted = true)
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.changeCampus()
    viewModel.cancelCampusChoice()

    assertEquals(MapCentre.Located(nearRolex), viewModel.centre())
  }

  @Test
  fun cancelWithoutACampus_keepsTheChooser() {
    val viewModel = viewModel()
    viewModel.onPermissionResult(granted = false)

    viewModel.cancelCampusChoice()

    assertEquals(MapCentre.NeedsChoice(), viewModel.centre())
  }

  @Test
  fun cancelOutsideTheChooser_changesNothing() {
    val viewModel = viewModel()
    viewModel.chooseCampus(Campus.EPFL)
    viewModel.onPermissionResult(granted = true)

    viewModel.cancelCampusChoice()

    assertEquals(MapCentre.Locating, viewModel.centre())
  }

  @Test
  fun campusPickedWhileLocating_isKept_whenTheLocationArrivesLate() {
    location.gate = CompletableDeferred()
    val viewModel = viewModel()
    viewModel.onPermissionResult(granted = true)
    testDispatcher.scheduler.runCurrent()

    viewModel.chooseCampus(Campus.UNIL)
    location.gate?.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(MapCentre.OnCampus(Campus.UNIL), viewModel.centre())
  }

  // ---- Listings shown as markers (#341) ----

  private val rolex = ListingLocation(46.5185, 6.5683, "Rolex Learning Center")

  private fun item(id: String, ownerUid: String, location: ListingLocation? = rolex) =
      ItemListing(
          category = ListingCategory.ITEM_TOOLS,
          price = 8.0,
          title = "Item $id",
          description = "Description",
          listingId = id,
          ownerUid = ownerUid,
          location = location,
      )

  private fun loadedViewModel(): MapViewModel {
    val viewModel = viewModel()
    testDispatcher.scheduler.advanceUntilIdle()
    return viewModel
  }

  @Test
  fun init_showsOtherUsersListingsWithALocation_asMarkers() {
    listings.allListings =
        listOf(
            item("drill", ownerUid = "lender"),
            item("mine", ownerUid = "renter"),
            item("nowhere", ownerUid = "lender", location = null),
            item("paused", ownerUid = "lender").copy(isPaused = true),
        )

    val state = loadedViewModel().uiState.value

    assertEquals(listOf("drill"), state.markers.map { it.id })
    assertFalse(state.isLoading)
    assertNull(state.error)
  }

  @Test
  fun loadListings_isLoadingOnlyWhileTheRepositoryAnswers() {
    val gate = CompletableDeferred<Unit>()
    listings.gate = gate

    val viewModel = viewModel()
    testDispatcher.scheduler.runCurrent()
    assertTrue(viewModel.uiState.value.isLoading)

    gate.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun failedLoad_saysWhy_andRetryClearsIt() {
    listings.failure = UnknownHostException("firestore.googleapis.com")
    val viewModel = loadedViewModel()
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)

    listings.failure = null
    listings.allListings = listOf(item("drill", ownerUid = "lender"))
    viewModel.loadListings()
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.uiState.value.error)
    assertEquals(listOf("drill"), viewModel.uiState.value.markers.map { it.id })
  }

  @Test
  fun failedReload_keepsTheMarkersShown() {
    listings.allListings = listOf(item("drill", ownerUid = "lender"))
    val viewModel = loadedViewModel()

    listings.failure = UnknownHostException("firestore.googleapis.com")
    viewModel.loadListings()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("drill"), viewModel.uiState.value.markers.map { it.id })
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
  }

  @Test
  fun failedReload_keepsThePreview() {
    listings.allListings = listOf(item("drill", ownerUid = "lender"))
    val viewModel = loadedViewModel()
    viewModel.selectMarker("drill")

    // Offline: the reload fails, but the listing is still on the map, so its preview stays.
    listings.failure = UnknownHostException("firestore.googleapis.com")
    viewModel.loadListings()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals("drill", viewModel.uiState.value.selectedMarker?.id)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
  }

  @Test
  fun loadListings_whileALoadIsRunning_isIgnored() {
    listings.gate = CompletableDeferred()
    val viewModel = viewModel()
    testDispatcher.scheduler.runCurrent()

    viewModel.loadListings()
    listings.gate?.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, listings.getAllListingsCalls)
  }

  @Test
  fun cancelledLoad_isNotAnError_andAllowsTheNextLoad() {
    listings.failure = CancellationException("Map closed")
    val viewModel = loadedViewModel()

    assertNull(viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  // ---- Marker preview (#342) ----

  @Test
  fun selectMarker_previewsThatListing() {
    listings.allListings =
        listOf(item("drill", ownerUid = "lender"), item("tent", ownerUid = "lender"))
    val viewModel = loadedViewModel()

    viewModel.selectMarker("tent")

    assertEquals("tent", viewModel.uiState.value.selectedMarker?.id)
  }

  @Test
  fun selectMarker_withAnIdTheMapDoesNotShow_isIgnored() {
    listings.allListings = listOf(item("drill", ownerUid = "lender"))
    val viewModel = loadedViewModel()

    viewModel.selectMarker("mine")

    assertNull(viewModel.uiState.value.selectedMarkerId)
  }

  @Test
  fun clearSelection_closesThePreview() {
    listings.allListings = listOf(item("drill", ownerUid = "lender"))
    val viewModel = loadedViewModel()
    viewModel.selectMarker("drill")

    viewModel.clearSelection()

    assertNull(viewModel.uiState.value.selectedMarker)
  }

  @Test
  fun reload_withoutTheSelectedListing_closesThePreview_forGood() {
    listings.allListings = listOf(item("drill", ownerUid = "lender"))
    val viewModel = loadedViewModel()
    viewModel.selectMarker("drill")

    // Paused or deleted meanwhile.
    listings.allListings = emptyList()
    viewModel.loadListings()
    testDispatcher.scheduler.advanceUntilIdle()
    assertNull(viewModel.uiState.value.selectedMarkerId)

    // Back later: the preview doesn't reopen by itself.
    listings.allListings = listOf(item("drill", ownerUid = "lender"))
    viewModel.loadListings()
    testDispatcher.scheduler.advanceUntilIdle()
    assertNull(viewModel.uiState.value.selectedMarker)
  }

  @Test
  fun reload_keepingTheSelectedListing_keepsThePreview() {
    listings.allListings = listOf(item("drill", ownerUid = "lender"))
    val viewModel = loadedViewModel()
    viewModel.selectMarker("drill")

    viewModel.loadListings()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals("drill", viewModel.uiState.value.selectedMarker?.id)
  }
}
