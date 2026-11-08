// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.NoSessionException
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
    /** Why the last sign-in, sign-up or sign-out failed, or null. */
    val error: AppError? = null,
    val sessionChecked: Boolean = false,
    /** Set when the persisted session could not be checked, for example offline; retry then. */
    val sessionError: AppError? = null,
)

/**
 * @param logUnexpectedError receives the session check failures nobody expects, such as a missing
 *   username, so they leave a trace before the user is signed out. Logs to Logcat by default.
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val logUnexpectedError: (Throwable) -> Unit = {
      Log.e(TAG, "Session check failed", it)
    },
) : ViewModel() {

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
        when {
          // A signed-out user: expected, so not logged.
          exception is NoSessionException ->
              finishSessionCheck { it.copy(user = null, sessionChecked = true) }
          // The session may still be valid, so only a network failure is worth a retry.
          exception.toAppError(log = logUnexpectedError) == AppError.NETWORK ->
              finishSessionCheck { it.copy(sessionError = AppError.NETWORK) }
          else -> finishSessionCheck { it.copy(user = null, sessionChecked = true) }
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
    _uiState.update { it.copy(error = null) }
  }

  /** Marks the signed-in user's email as verified, once [VerifyEmailViewModel] has confirmed it. */
  fun onEmailVerified() {
    _uiState.update { it.copy(user = it.user?.copy(isEmailVerified = true)) }
  }

  fun signOut() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null) }
      try {
        authRepository.signOut()
        _uiState.update { it.copy(user = null, isLoading = false) }
      } catch (exception: Exception) {
        if (exception is kotlinx.coroutines.CancellationException) throw exception
        _uiState.update { it.copy(isLoading = false, error = exception.toAppError()) }
      }
    }
  }

  private suspend fun authenticate(action: suspend () -> Result<User>) {
    _uiState.update { it.copy(isLoading = true, error = null) }
    action()
        .fold(
            onSuccess = { user ->
              _uiState.update { it.copy(user = user, isLoading = false, sessionChecked = true) }
            },
            onFailure = { exception ->
              _uiState.update { it.copy(isLoading = false, error = exception.toAppError()) }
            },
        )
  }
}

private const val TAG = "AuthViewModel"
