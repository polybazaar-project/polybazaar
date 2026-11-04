// Co-authored-by: GitHub Copilot CLI (Claude Haiku 5.5)
package com.android.polybazaar.profile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.browse.viewmodel.BrowseListing
import com.android.polybazaar.browse.viewmodel.toBrowseListing
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.profile.model.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LenderProfileUiState(
    val profile: User? = null,
    val listings: List<BrowseListing> = emptyList(),
    val isLoading: Boolean = false,
    val error: AppError? = null,
)

/** Shows a lender's profile and the listings they offer, reached by tapping their name. */
class LenderProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val listingRepository: ListingRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(LenderProfileUiState())
  val uiState: StateFlow<LenderProfileUiState> = _uiState.asStateFlow()

  private var profileJob: Job? = null
  private var listingsJob: Job? = null

  // A cancelled job can still resume and finish, so each job writes state only while it is the
  // latest request.
  private var latestRequest = 0

  /**
   * Loads the profile and listings of [uid], dropping whatever was shown for a previous lender. The
   * profile keeps updating while the screen is open.
   */
  fun loadLender(uid: String) {
    profileJob?.cancel()
    listingsJob?.cancel()
    val request = ++latestRequest
    _uiState.value = LenderProfileUiState(isLoading = true)
    profileJob = viewModelScope.launch { loadProfile(request, uid) }
    listingsJob = viewModelScope.launch { loadListings(request, uid) }
  }

  private suspend fun loadProfile(request: Int, uid: String) {
    try {
      profileRepository.observeProfile(uid).collect { profile ->
        updateIfLatest(request) { it.copy(profile = profile) }
      }
    } catch (exception: Exception) {
      if (exception is CancellationException) throw exception
      updateIfLatest(request) { it.copy(error = exception.toAppError()) }
    }
  }

  private suspend fun loadListings(request: Int, uid: String) {
    listingRepository
        .getListingsByOwner(uid)
        .onSuccess { listings ->
          updateIfLatest(request) {
            it.copy(
                listings = listings.mapNotNull { listing -> listing.toBrowseListing() },
                isLoading = false,
            )
          }
        }
        .onFailure { exception ->
          if (exception is CancellationException) throw exception
          updateIfLatest(request) { it.copy(error = exception.toAppError(), isLoading = false) }
        }
  }

  private fun updateIfLatest(
      request: Int,
      transform: (LenderProfileUiState) -> LenderProfileUiState,
  ) {
    if (request == latestRequest) _uiState.update(transform)
  }
}
