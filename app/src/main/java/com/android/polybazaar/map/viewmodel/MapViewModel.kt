// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.map.model.Campus
import com.android.polybazaar.map.model.Coordinates
import com.android.polybazaar.map.model.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the map is centred (#340), which also tells the screen what to show over it. */
sealed interface MapCentre {
  /** The screen hasn't reported the location permission yet: nothing to centre on. */
  data object Undecided : MapCentre

  /** The permission is granted and the approximate location is being read. */
  data object Locating : MapCentre

  /** Centred on the renter's approximate location. */
  data class Located(val coordinates: Coordinates) : MapCentre

  /** Centred on the campus the renter picked. */
  data class OnCampus(val campus: Campus) : MapCentre

  /**
   * The renter has to pick a campus: they refused the permission, their location couldn't be read
   * (turned off, no fix offline), or they asked to change campus.
   *
   * @property current the campus picked earlier this session, which the chooser shows as selected
   *   and goes back to on Cancel, or null when there is none.
   */
  data class NeedsChoice(val current: Campus? = null) : MapCentre
}

/**
 * State of the map tab (#21).
 *
 * @property centre where the map is centred, or why it isn't yet.
 */
data class MapUiState(val centre: MapCentre = MapCentre.Undecided)

/**
 * Centres the map on the renter's approximate location, or on a campus they pick when they don't
 * share it (#340). The screen asks Android for the permission and reports the answer with
 * [onPermissionResult]; this ViewModel never sees the permission itself.
 *
 * @param savedStateHandle remembers the campus picked for the session: across tab switches, and
 *   when Android kills the app in the background. Pass `createSavedStateHandle()` from the factory;
 *   it has no default, since a handle made by hand is not restored by Android (and its constructor
 *   is meant for tests).
 */
class MapViewModel(
    private val locationRepository: LocationRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val _uiState = MutableStateFlow(MapUiState())
  val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

  /** The campus picked earlier this session, or null. An unknown saved name reads as none. */
  private val chosenCampus: Campus?
    get() =
        savedStateHandle.get<String>(CHOSEN_CAMPUS)?.let { name ->
          Campus.entries.firstOrNull { it.name == name }
        }

  /** Where to centre without the renter's location: their campus if picked, else ask for one. */
  private fun campusOrChoice(): MapCentre =
      chosenCampus?.let { MapCentre.OnCampus(it) } ?: MapCentre.NeedsChoice()

  /**
   * Called by the screen with the location permission's answer, also when it was already granted.
   * Granted, the approximate location is read; refused, the map goes to the campus picked earlier
   * this session, or the renter has to pick one.
   */
  fun onPermissionResult(granted: Boolean) {
    // A second answer while the location is read (a recomposition, a quick resume) would start a
    // second read whose late answer could overwrite the first.
    if (_uiState.value.centre == MapCentre.Locating) return
    if (!granted) {
      _uiState.update { it.copy(centre = campusOrChoice()) }
      return
    }
    _uiState.update { it.copy(centre = MapCentre.Locating) }
    viewModelScope.launch {
      locationRepository
          .currentLocation()
          .onSuccess { coordinates -> showIfStillLocating(MapCentre.Located(coordinates)) }
          // A location that can't be read is expected (turned off, offline), so it is not logged:
          // the renter just gets their campus, or picks one.
          .onFailure { showIfStillLocating(campusOrChoice()) }
    }
  }

  /** Centres the map on [campus] and remembers it for the session. */
  fun chooseCampus(campus: Campus) {
    savedStateHandle[CHOSEN_CAMPUS] = campus.name
    _uiState.update { it.copy(centre = MapCentre.OnCampus(campus)) }
  }

  /**
   * Opens the campus chooser again ("Change"), with the current campus selected. Only from a
   * campus, where the map shows "Change" (Figma "Map — Centred on campus"): anywhere else, Cancel
   * would have no campus to go back to and the renter would be stuck in the chooser.
   */
  fun changeCampus() {
    _uiState.update {
      val centre = it.centre
      if (centre is MapCentre.OnCampus) {
        it.copy(centre = MapCentre.NeedsChoice(current = centre.campus))
      } else {
        it
      }
    }
  }

  /**
   * Closes the chooser opened with [changeCampus] without changing campus. Ignored when there is no
   * campus to go back to: the map needs a centre, so the renter has to pick one.
   */
  fun cancelCampusChoice() {
    val campus = chosenCampus ?: return
    _uiState.update {
      if (it.centre is MapCentre.NeedsChoice) it.copy(centre = MapCentre.OnCampus(campus)) else it
    }
  }

  /** Shows [centre] unless the renter picked a campus while the location was read. */
  private fun showIfStillLocating(centre: MapCentre) {
    _uiState.update { if (it.centre == MapCentre.Locating) it.copy(centre = centre) else it }
  }

  private companion object {
    /** SavedStateHandle key of the campus picked this session, stored by its enum name. */
    const val CHOSEN_CAMPUS = "map_chosen_campus"
  }
}
