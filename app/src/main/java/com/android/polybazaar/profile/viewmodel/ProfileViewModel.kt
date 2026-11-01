// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.profile.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.profile.model.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: User? = null,
    /** The saved profile photo's encoded bytes, or null if there is none or it has not loaded. */
    val photo: ByteArray? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class ProfileViewModel(private val profileRepository: ProfileRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(ProfileUiState())
  val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

  private var profileObservation: Job? = null
  private var observedUid: String? = null
  private val photoReloads = MutableStateFlow(0)

  fun observeProfile(uid: String) {
    // The screen calls this every time it comes back from another screen, but the observation keeps
    // running behind that screen. Only a photo that could not be loaded needs another try.
    if (uid == observedUid && profileObservation?.isActive == true) {
      if (_uiState.value.photo == null) reloadPhoto()
      return
    }
    profileObservation?.cancel()
    val shouldClearProfile = observedUid != null && observedUid != uid
    observedUid = uid
    profileObservation = viewModelScope.launch {
      _uiState.update {
        it.copy(
            profile = if (shouldClearProfile) null else it.profile,
            photo = if (shouldClearProfile) null else it.photo,
            isLoading = true,
            errorMessage = null,
        )
      }
      try {
        profileRepository
            .observeProfile(uid)
            .onEach { profile ->
              _uiState.update {
                it.copy(
                    profile = profile,
                    photo = if (profile?.photoPath == null) null else it.photo,
                    isLoading = false,
                )
              }
            }
            .map { it?.photoPath }
            .distinctUntilChanged()
            .combine(photoReloads) { photoPath, _ -> photoPath }
            .collectLatest { photoPath -> photoPath?.let { loadPhoto(it) } }
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        _uiState.update {
          it.copy(
              isLoading = false,
              errorMessage = exception.message ?: "Unable to load profile",
          )
        }
      }
    }
  }

  /**
   * Downloads the photo again. A new photo overwrites the same Storage path, so the profile
   * document does not change and the observation cannot notice it by itself.
   */
  fun reloadPhoto() {
    photoReloads.update { it + 1 }
  }

  /** On failure (when offline, for example) the avatar keeps what it was showing. */
  private suspend fun loadPhoto(photoPath: String) {
    try {
      val photo = profileRepository.loadProfilePhoto(photoPath)
      _uiState.update { it.copy(photo = photo) }
    } catch (exception: Exception) {
      if (exception is CancellationException) throw exception
      Log.w(TAG, "Could not load the profile photo", exception)
    }
  }

  private companion object {
    const val TAG = "ProfileViewModel"
  }
}
