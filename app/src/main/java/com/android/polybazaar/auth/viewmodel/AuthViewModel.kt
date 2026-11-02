// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val sessionChecked: Boolean = false,
    /** Set when the persisted session could not be checked, for example offline; retry then. */
    val sessionError: AppError? = null,
)

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(AuthUiState())
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  /**
   * Restores the persisted session, if any. Once it has been checked, later calls are no-ops.
   *
   * A network failure does not sign the user out: their session may still be valid, so
   * [AuthUiState.sessionError] is set and the session stays unchecked until this is called again.
   * Any other failure, or no persisted session, leaves the user signed out.
   */
  fun restoreSession() {
    if (_uiState.value.sessionChecked || _uiState.value.isLoading) return
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, sessionError = null) }
      try {
        val user = authRepository.getCurrentUser()
        finishSessionCheck { it.copy(user = user, sessionChecked = true) }
      } catch (exception: Exception) {
        if (exception is kotlinx.coroutines.CancellationException) throw exception
        // No persisted session also ends up here, so only a network failure is worth a retry.
        if (exception.toAppError(log = {}) == AppError.NETWORK) {
          finishSessionCheck { it.copy(sessionError = AppError.NETWORK) }
        } else {
          finishSessionCheck { it.copy(user = null, sessionChecked = true) }
        }
      }
    }
  }

  /** Applies the result of [restoreSession], unless the user signed in while it was running. */
  private fun finishSessionCheck(result: (AuthUiState) -> AuthUiState) {
    _uiState.update { if (it.sessionChecked) it else result(it.copy(isLoading = false)) }
  }

  fun signIn(email: String, password: String) {
    viewModelScope.launch { authenticate { authRepository.signIn(email, password) } }
  }

  fun signUp(email: String, password: String, username: String) {
    viewModelScope.launch { authenticate { authRepository.signUp(email, password, username) } }
  }

  /** Forgets the error once a screen has shown it, so it is not shown again. */
  fun clearError() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  /** Marks the signed-in user's email as verified, once [VerifyEmailViewModel] has confirmed it. */
  fun onEmailVerified() {
    _uiState.update { it.copy(user = it.user?.copy(isEmailVerified = true)) }
  }

  fun signOut() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      try {
        authRepository.signOut()
        _uiState.update { it.copy(user = null, isLoading = false) }
      } catch (exception: Exception) {
        if (exception is kotlinx.coroutines.CancellationException) throw exception
        _uiState.update {
          it.copy(isLoading = false, errorMessage = exception.message ?: "Unable to sign out")
        }
      }
    }
  }

  private suspend fun authenticate(action: suspend () -> Result<User>) {
    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
    action()
        .fold(
            onSuccess = { user ->
              _uiState.update { it.copy(user = user, isLoading = false, sessionChecked = true) }
            },
            onFailure = { exception ->
              _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = exception.message ?: "Authentication failed",
                )
              }
            },
        )
  }
}
