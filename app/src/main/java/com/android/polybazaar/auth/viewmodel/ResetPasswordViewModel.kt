// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.AuthRules
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the typed passwords were refused before sending. The screen picks the message to show. */
enum class ResetPasswordError {
  /** The new password is empty or only whitespace. */
  EMPTY_PASSWORD,

  /** The new password is longer than [AuthRules.MAX_PASSWORD_LENGTH], as sign-up refuses. */
  PASSWORD_TOO_LONG,

  /** The new password does not meet the rules shown under the field. */
  WEAK_PASSWORD,

  /** The confirmation is not exactly the new password. */
  PASSWORDS_DO_NOT_MATCH,
}

/**
 * Checks the two fields before anything is sent, in this order:
 * - [newPassword] blank: [ResetPasswordError.EMPTY_PASSWORD];
 * - [newPassword] over [AuthRules.MAX_PASSWORD_LENGTH]: [ResetPasswordError.PASSWORD_TOO_LONG];
 * - [newPassword] not meeting [AuthRules.meetsPasswordRules], the same rule as sign-up:
 *   [ResetPasswordError.WEAK_PASSWORD];
 * - [confirmPassword] not exactly equal to [newPassword], blank included:
 *   [ResetPasswordError.PASSWORDS_DO_NOT_MATCH].
 *
 * Nothing is trimmed.
 *
 * @return the first rule broken, or null if the passwords can be sent.
 */
internal fun newPasswordError(newPassword: String, confirmPassword: String): ResetPasswordError? =
    when {
      newPassword.isBlank() -> ResetPasswordError.EMPTY_PASSWORD
      // Before the strength check, so a long password is not called weak.
      AuthRules.passwordLengthError(newPassword) != null -> ResetPasswordError.PASSWORD_TOO_LONG
      !AuthRules.meetsPasswordRules(newPassword) -> ResetPasswordError.WEAK_PASSWORD
      confirmPassword != newPassword -> ResetPasswordError.PASSWORDS_DO_NOT_MATCH
      else -> null
    }

/**
 * State of the reset password screen, reached from the link in the password reset email.
 *
 * @property newPassword the new password, as typed. Never trimmed.
 * @property confirmPassword the confirmation, as typed. Never trimmed.
 * @property isLoading true while the new password is being saved.
 * @property passwordReset true once the new password was saved. The screen then goes back to sign
 *   in.
 * @property error which field check the last attempt failed, or null.
 * @property requestError why the last request failed, or null.
 */
data class ResetPasswordUiState(
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val passwordReset: Boolean = false,
    val error: ResetPasswordError? = null,
    val requestError: AppError? = null,
)

/**
 * Sets a new password for the account that requested the reset email.
 *
 * @param oobCode the one-time code from the reset link, which identifies the account.
 */
class ResetPasswordViewModel(
    private val authRepository: AuthRepository,
    private val oobCode: String,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ResetPasswordUiState())
  val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

  /** Updates the new password and clears the result of the previous attempt. */
  fun onNewPasswordChange(password: String) {
    _uiState.update {
      it.copy(newPassword = password, passwordReset = false, error = null, requestError = null)
    }
  }

  /** Updates the confirmation and clears the result of the previous attempt. */
  fun onConfirmPasswordChange(password: String) {
    _uiState.update {
      it.copy(confirmPassword = password, passwordReset = false, error = null, requestError = null)
    }
  }

  /**
   * Saves the new password. Ignored while a request is already in flight. The fields are first
   * checked with [newPasswordError]; if a rule is broken, its error is set and the repository is
   * not called. Otherwise [ResetPasswordUiState.passwordReset] becomes true on success, or
   * [ResetPasswordUiState.requestError] is set on failure. If either field changes before the
   * request finishes, its result is dropped and only the loading state ends: it was for other
   * input.
   */
  fun resetPassword() {
    if (_uiState.value.isLoading) return
    val newPassword = _uiState.value.newPassword
    val confirmPassword = _uiState.value.confirmPassword
    newPasswordError(newPassword, confirmPassword)?.let { error ->
      _uiState.update { it.copy(error = error) }
      return
    }
    _uiState.update {
      it.copy(isLoading = true, passwordReset = false, error = null, requestError = null)
    }
    viewModelScope.launch {
      val result = authRepository.confirmPasswordReset(oobCode, newPassword)
      // Outside update, which may run its block more than once: an unmapped failure is logged once.
      val requestError = result.exceptionOrNull()?.toAppError()
      _uiState.update {
        if (it.newPassword != newPassword || it.confirmPassword != confirmPassword) {
          it.copy(isLoading = false)
        } else {
          it.copy(
              isLoading = false,
              passwordReset = result.isSuccess,
              requestError = requestError,
          )
        }
      }
    }
  }
}
