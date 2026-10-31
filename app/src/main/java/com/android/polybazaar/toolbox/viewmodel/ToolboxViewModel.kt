// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.toolbox.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the Toolbox, the part of the profile that shows what the user is responsible for (#20).
 *
 * Only the user's own listings for now; the items they rent will be added once rentals exist.
 *
 * @property listings the signed-in user's listings, in the order the repository returns them.
 * @property isLoading true while the listings are being loaded.
 * @property error why the last load failed, or null.
 */
data class ToolboxUiState(
    val listings: List<Listing> = emptyList(),
    val isLoading: Boolean = false,
    val error: AppError? = null,
)

/** Loads the signed-in user's listings for the Toolbox. */
class ToolboxViewModel(private val listingRepository: ListingRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(ToolboxUiState())
  val uiState: StateFlow<ToolboxUiState> = _uiState.asStateFlow()

  init {
    loadListings()
  }

  /**
   * Loads the user's listings, for example again after a failure (Retry) or after creating one.
   * Ignored while a load is already running. On failure the listings already shown are kept, so a
   * reload while offline does not empty the Toolbox.
   */
  fun loadListings() {
    // Two loads at once could finish in any order, and the older answer would overwrite the newer.
    if (_uiState.value.isLoading) return
    _uiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      try {
        listingRepository
            .getMyListings()
            .onSuccess { listings -> _uiState.update { it.copy(listings = listings) } }
            .onFailure { exception ->
              // Cancellation means the Toolbox was closed: it is not an error to show.
              if (exception is CancellationException) throw exception
              _uiState.update { it.copy(error = exception.toAppError()) }
            }
      } finally {
        // Also on cancellation, otherwise the guard above would block every later load.
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }
}
