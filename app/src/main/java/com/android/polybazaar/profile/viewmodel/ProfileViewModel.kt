package com.android.polybazaar.profile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.profile.model.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: User? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class ProfileViewModel(private val profileRepository: ProfileRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(ProfileUiState())
  val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

  private var profileObservation: Job? = null
  private var observedUid: String? = null

  fun observeProfile(uid: String) {
    profileObservation?.cancel()
    val shouldClearProfile = observedUid != null && observedUid != uid
    observedUid = uid
    profileObservation = viewModelScope.launch {
      _uiState.update {
        it.copy(
            profile = if (shouldClearProfile) null else it.profile,
            isLoading = true,
            errorMessage = null,
        )
      }
      try {
        profileRepository.observeProfile(uid).collect { profile ->
          _uiState.update { it.copy(profile = profile, isLoading = false) }
        }
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
}
