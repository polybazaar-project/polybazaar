// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.android.polybazaar.map.model.Campus
import com.android.polybazaar.map.model.Coordinates
import com.android.polybazaar.map.model.FakeLocationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val nearRolex = Coordinates(46.518, 6.568)
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

  private fun viewModel() = MapViewModel(location, handle)

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
    val viewModel = MapViewModel(location, SavedStateHandle(mapOf("map_chosen_campus" to "EPFL")))

    viewModel.onPermissionResult(granted = true)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(MapCentre.OnCampus(Campus.EPFL), viewModel.centre())
  }

  @Test
  fun unknownSavedCampus_readsAsNone() {
    val viewModel = MapViewModel(location, SavedStateHandle(mapOf("map_chosen_campus" to "ETHZ")))

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
}
