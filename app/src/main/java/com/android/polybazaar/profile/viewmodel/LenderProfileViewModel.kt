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
    val profileLoaded: Boolean = false,
    val listingsLoaded: Boolean = false,
    val error: AppError? = null,
) {
  // Loading ends only when both parts have answered, so a missing profile is not shown before it
  // arrives.
  val isLoading: Boolean
    get() = !(profileLoaded && listingsLoaded)
}

/** Shows a lender's profile and the listings they offer, reached by tapping their name. */
class LenderProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val listingRepository: ListingRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(LenderProfileUiState())
  val uiState: StateFlow<LenderProfileUiState> = _uiState.asStateFlow()

  private var profileJob: Job? = null
  private var listingsJob: Job? = null

  /**
   * Loads the profile and listings of [uid], dropping whatever was shown for a previous lender. The
   * profile keeps updating while the screen is open.
   */
  fun loadLender(uid: String) {
    profileJob?.cancel()
    listingsJob?.cancel()
    _uiState.value = LenderProfileUiState()
    profileJob = viewModelScope.launch { loadProfile(uid) }
    listingsJob = viewModelScope.launch { loadListings(uid) }
  }

  private suspend fun loadProfile(uid: String) {
    try {
      profileRepository.observeProfile(uid).collect { profile ->
        _uiState.update { it.copy(profile = profile, profileLoaded = true) }
      }
    } catch (exception: Exception) {
      if (exception is CancellationException) throw exception
      _uiState.update { it.copy(error = exception.toAppError(), profileLoaded = true) }
    }
  }

  private suspend fun loadListings(uid: String) {
    listingRepository
        .getListingsByOwner(uid)
        .onSuccess { listings ->
          _uiState.update {
            it.copy(
                listings = listings.mapNotNull { listing -> listing.toBrowseListing() },
                listingsLoaded = true,
            )
          }
        }
        .onFailure { exception ->
          if (exception is CancellationException) throw exception
          _uiState.update { it.copy(error = exception.toAppError(), listingsLoaded = true) }
        }
  }
}
