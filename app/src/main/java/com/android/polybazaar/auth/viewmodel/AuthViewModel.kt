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
)

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(AuthUiState())
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

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
        _uiState.value = AuthUiState()
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
            onSuccess = { user -> _uiState.value = AuthUiState(user = user) },
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
