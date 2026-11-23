// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.map.model.Campus
import com.android.polybazaar.map.model.Coordinates
import com.android.polybazaar.map.model.LocationRepository
import kotlinx.coroutines.CancellationException
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
 * @property markers the listings to show on the map: the ones Browse shows (other users', not
 *   paused) that have a location.
 * @property isLoading true while the listings are being loaded.
 * @property error why the listings could not be loaded, or null. The markers already shown are
 *   kept.
 * @property selectedMarkerId the listing whose marker the renter tapped (#342), or null. The
 *   ViewModel's record of the selection: the screen reads [selectedMarker] instead.
 */
data class MapUiState(
    val centre: MapCentre = MapCentre.Undecided,
    val markers: List<MapMarker> = emptyList(),
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val selectedMarkerId: String? = null,
) {
  /**
   * The listing to preview at the bottom of the map, or null: what the screen reads.
   * [MapViewModel.loadListings] already clears an id the map no longer shows; looking the id up in
   * [markers] is a safety net, so the screen can never preview a listing that isn't on the map.
   */
  val selectedMarker: MapMarker?
    get() = selectedMarkerId?.let { id -> markers.firstOrNull { it.id == id } }
}

/**
 * Centres the map on the renter's approximate location, or on a campus they pick when they don't
 * share it (#340), and loads the listings to show on it (#341). The screen asks Android for the
 * permission and reports the answer with [onPermissionResult]; this ViewModel never sees the
 * permission itself.
 *
 * @param currentUid the signed-in user, whose own listings are left out, as in Browse.
 * @param savedStateHandle remembers the campus picked for the session: across tab switches, and
 *   when Android kills the app in the background. Pass `createSavedStateHandle()` from the factory;
 *   it has no default, since a handle made by hand is not restored by Android (and its constructor
 *   is meant for tests).
 */
class MapViewModel(
    private val locationRepository: LocationRepository,
    private val listingRepository: ListingRepository,
    private val currentUid: String,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val _uiState = MutableStateFlow(MapUiState())
  val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

  init {
    loadListings()
  }

  /**
   * Loads the listings to show as markers, for example again after a failure (Retry) or when the
   * tab comes back. Same listings as Browse: every listing that isn't paused, except the signed-in
   * user's own, and only those with a location can be placed. Offline, Firestore answers from its
   * cache; on failure the markers already shown are kept. Ignored while a load is already running.
   */
  fun loadListings() {
    // Two loads at once could finish in any order, and the older answer would overwrite the newer.
    if (_uiState.value.isLoading) return
    _uiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      try {
        listingRepository
            .getAllListings()
            .onSuccess { listings ->
              val markers =
                  listings.filter { it.ownerUid != currentUid }.mapNotNull { it.toMapMarker() }
              _uiState.update {
                it.copy(
                    markers = markers,
                    // A listing deleted or paused since it was selected loses its preview, and it
                    // doesn't come back by itself if the listing reappears later.
                    selectedMarkerId =
                        it.selectedMarkerId?.takeIf { id -> markers.any { m -> m.id == id } },
                )
              }
            }
            .onFailure { exception ->
              // Cancellation means the map was closed: it is not an error to show.
              if (exception is CancellationException) throw exception
              _uiState.update { it.copy(error = exception.toAppError()) }
            }
      } finally {
        // Also on cancellation, otherwise the guard above would block every later load.
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

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

  /** Previews the listing whose marker was tapped (#342); an id the map doesn't show is ignored. */
  fun selectMarker(id: String) {
    _uiState.update { state ->
      if (state.markers.any { it.id == id }) state.copy(selectedMarkerId = id) else state
    }
  }

  /** Closes the preview: its close button, or a tap on the map outside the markers. */
  fun clearSelection() {
    _uiState.update { it.copy(selectedMarkerId = null) }
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
