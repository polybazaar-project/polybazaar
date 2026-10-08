package com.android.polybazaar.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.User
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
)

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(AuthUiState())
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  /** Restores the persisted session, if any. Runs once; later calls are no-ops. */
  fun restoreSession() {
    if (_uiState.value.sessionChecked || _uiState.value.isLoading) return
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      val user =
          try {
            authRepository.getCurrentUser()
          } catch (exception: Exception) {
            if (exception is kotlinx.coroutines.CancellationException) throw exception
            null
          }
      _uiState.update {
        if (it.sessionChecked) it
        else it.copy(user = user, isLoading = false, sessionChecked = true)
      }
    }
  }

  fun signIn(email: String, password: String) {
    viewModelScope.launch { authenticate { authRepository.signIn(email, password) } }
  }

  fun signUp(email: String, password: String, username: String) {
    viewModelScope.launch { authenticate { authRepository.signUp(email, password, username) } }
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
