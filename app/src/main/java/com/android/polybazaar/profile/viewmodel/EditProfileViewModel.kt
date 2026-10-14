package com.android.polybazaar.profile.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.profile.model.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditProfileUiState(
    val profile: User? = null,
    val bio: String = "",
    val selectedPhotoUri: Uri? = null,
    val isPhotoRemoved: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isPartiallySaved: Boolean = false,
    val errorMessage: String? = null,
) {
  val photoUrl: String?
    get() =
        when {
          isPartiallySaved -> profile?.photoUrl
          isPhotoRemoved -> null
          selectedPhotoUri != null -> null
          else -> profile?.photoUrl
        }
}

class EditProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val uid: String,
) : ViewModel() {

  private val _uiState = MutableStateFlow(EditProfileUiState())
  val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

  init {
    observeProfile()
  }

  fun updateBio(bio: String) {
    _uiState.update { it.withUpdatedBio(bio) }
  }

  fun selectProfilePhoto(photoUri: Uri) {
    _uiState.update { it.withSelectedPhoto(photoUri) }
  }

  fun removeProfilePhoto() {
    _uiState.update { it.withRemovedPhoto() }
  }

  fun saveProfile() {
    val state = _uiState.value
    if (!state.canEdit()) return
    if (state.profile == null) {
      _uiState.update { it.copy(errorMessage = "Profile is not available to save") }
      return
    }

    _uiState.update { it.copy(isSaving = true, isSaved = false, errorMessage = null) }

    viewModelScope.launch {
      var profileUpdateSucceeded = false
      try {
        val photoUrl = state.selectedPhotoUri?.let { profileRepository.uploadProfilePhoto(uid, it) }
        profileRepository.updateProfile(uid, photoUrl = photoUrl, bio = state.bio)
        profileUpdateSucceeded = true
        if (state.isPhotoRemoved) {
          profileRepository.removeProfilePhoto(uid)
        }

        _uiState.update { it.withSavedProfile(state, photoUrl) }
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        _uiState.update { it.withSaveFailure(state, exception, profileUpdateSucceeded) }
      }
    }
  }

  private fun observeProfile() {
    viewModelScope.launch {
      try {
        profileRepository.observeProfile(uid).collect { profile ->
          _uiState.update { it.withObservedProfile(profile) }
        }
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        _uiState.update { it.withObservationFailure(exception) }
      }
    }
  }

  private fun EditProfileUiState.canEdit(): Boolean = !isLoading && !isSaving

  private fun EditProfileUiState.withUpdatedBio(bio: String): EditProfileUiState =
      if (!canEdit()) this
      else
          copy(
              bio = bio,
              isSaved = false,
              isPartiallySaved = false,
              errorMessage = null,
          )

  private fun EditProfileUiState.withSelectedPhoto(photoUri: Uri): EditProfileUiState =
      if (!canEdit()) this
      else
          copy(
              selectedPhotoUri = photoUri,
              isPhotoRemoved = false,
              isSaved = false,
              isPartiallySaved = false,
              errorMessage = null,
          )

  private fun EditProfileUiState.withRemovedPhoto(): EditProfileUiState =
      if (!canEdit()) this
      else
          copy(
              selectedPhotoUri = null,
              isPhotoRemoved = true,
              isSaved = false,
              isPartiallySaved = false,
              errorMessage = null,
          )

  private fun EditProfileUiState.withObservedProfile(profile: User?): EditProfileUiState =
      if (profile == null) {
        copy(
            profile = null,
            isLoading = false,
            errorMessage = "Profile not found",
        )
      } else {
        val hasDraftChanges =
            this.profile != null &&
                (this.bio != this.profile.bio ||
                    this.selectedPhotoUri != null ||
                    this.isPhotoRemoved)
        copy(
            profile = profile,
            bio = if (hasDraftChanges) this.bio else profile.bio,
            isLoading = false,
            errorMessage = null,
        )
      }

  private fun EditProfileUiState.withObservationFailure(exception: Exception): EditProfileUiState =
      copy(
          isLoading = false,
          errorMessage = exception.message ?: "Unable to load profile",
      )

  private fun EditProfileUiState.withSavedProfile(
      state: EditProfileUiState,
      photoUrl: String?,
  ): EditProfileUiState =
      copy(
          profile =
              profile?.copy(
                  photoUrl =
                      when {
                        state.isPhotoRemoved -> null
                        photoUrl != null -> photoUrl
                        else -> profile.photoUrl
                      },
                  bio = state.bio,
              ),
          bio = state.bio,
          selectedPhotoUri = null,
          isPhotoRemoved = false,
          isSaving = false,
          isSaved = true,
          isPartiallySaved = false,
      )

  private fun EditProfileUiState.withSaveFailure(
      state: EditProfileUiState,
      exception: Exception,
      profileUpdateSucceeded: Boolean,
  ): EditProfileUiState =
      if (profileUpdateSucceeded && state.isPhotoRemoved) {
        copy(
            profile = profile?.copy(bio = state.bio),
            bio = state.bio,
            isSaving = false,
            isSaved = false,
            isPartiallySaved = true,
            errorMessage =
                "Bio was saved, but the profile photo could not be removed: " +
                    (exception.message ?: "Unknown error"),
        )
      } else {
        copy(
            isSaving = false,
            errorMessage = exception.message ?: "Unable to save profile",
        )
      }
}
