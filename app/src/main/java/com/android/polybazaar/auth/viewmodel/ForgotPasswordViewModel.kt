// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.PasswordResetException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the forgot password screen.
 *
 * @property email the address typed by the user, as typed.
 * @property isLoading true while a reset request is in flight.
 * @property emailSent true once a reset link was requested for [email]. The screen shows the same
 *   neutral confirmation whether or not the address has an account.
 * @property error why the last request failed, or null. The screen picks the message to show.
 */
data class ForgotPasswordUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val emailSent: Boolean = false,
    val error: PasswordResetException.Reason? = null,
)

/** Sends a password reset link to the email address the user enters. */
class ForgotPasswordViewModel(private val authRepository: AuthRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(ForgotPasswordUiState())
  val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

  /** Updates the email and clears the result of the previous request, which was for another one. */
  fun onEmailChange(email: String) {
    _uiState.update { it.copy(email = email, emailSent = false, error = null) }
  }

  /**
   * Requests a reset link for the current email. Ignored while a request is already in flight. If
   * the email changes before the request finishes, its result is dropped: it was for another one.
   */
  fun sendResetLink() {
    if (_uiState.value.isLoading) return
    _uiState.update { it.copy(isLoading = true, emailSent = false, error = null) }
    val email = _uiState.value.email.trim()
    viewModelScope.launch {
      authRepository
          .sendPasswordResetEmail(email)
          .fold(
              onSuccess = {
                _uiState.update {
                  if (it.email.trim() == email) it.copy(isLoading = false, emailSent = true)
                  else it.copy(isLoading = false)
                }
              },
              onFailure = { exception ->
                val reason =
                    (exception as? PasswordResetException)?.reason
                        ?: PasswordResetException.Reason.UNKNOWN
                _uiState.update {
                  if (it.email.trim() == email) it.copy(isLoading = false, error = reason)
                  else it.copy(isLoading = false)
                }
              },
          )
    }
  }
}
