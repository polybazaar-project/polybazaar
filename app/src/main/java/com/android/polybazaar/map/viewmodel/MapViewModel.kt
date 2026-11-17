// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.error.model.toAppError
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
   * The renter has to pick a campus: they refused the permission, or their location couldn't be
   * read (turned off, no fix offline).
   */
  data object NeedsChoice : MapCentre
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
 */
class MapViewModel(private val locationRepository: LocationRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(MapUiState())
  val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

  /**
   * Called by the screen with the location permission's answer, also when it was already granted.
   * Granted, the approximate location is read; refused, the renter has to pick a campus.
   */
  fun onPermissionResult(granted: Boolean) {
    // A second answer while the location is read (a recomposition, a quick resume) would start a
    // second read whose late answer could overwrite the first.
    if (_uiState.value.centre == MapCentre.Locating) return
    if (!granted) {
      _uiState.update { it.copy(centre = MapCentre.NeedsChoice) }
      return
    }
    _uiState.update { it.copy(centre = MapCentre.Locating) }
    viewModelScope.launch {
      locationRepository
          .currentLocation()
          .onSuccess { coordinates ->
            _uiState.update { it.copy(centre = MapCentre.Located(coordinates)) }
          }
          .onFailure { exception ->
            // Only for the log: toAppError() logs exceptions it does not recognise. A location that
            // can't be read is expected (turned off, offline), so the renter just picks a campus.
            exception.toAppError()
            _uiState.update { it.copy(centre = MapCentre.NeedsChoice) }
          }
    }
  }
}
