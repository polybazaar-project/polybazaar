// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.AuthRules
import com.android.polybazaar.auth.model.ConfirmPasswordResetException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why choosing a new password failed. The screen picks the message to show. */
enum class ResetPasswordError {
  /** The new password is empty or only whitespace. */
  EMPTY_PASSWORD,

  /** The new password does not meet the rules shown under the field, or Firebase rejected it. */
  WEAK_PASSWORD,

  /** The confirmation is not exactly the new password. */
  PASSWORDS_DO_NOT_MATCH,

  /** The reset link is malformed, expired or was already used. A new one must be requested. */
  INVALID_LINK,

  /** The device could not reach Firebase. */
  NETWORK,

  /** Firebase blocked the request because too many were sent from this device. */
  TOO_MANY_REQUESTS,

  /** Any other error. */
  UNKNOWN,
}

/**
 * Checks the two fields before anything is sent, in this order:
 * - [newPassword] blank: [ResetPasswordError.EMPTY_PASSWORD];
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
 * @property error why the last attempt failed, or null.
 */
data class ResetPasswordUiState(
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val passwordReset: Boolean = false,
    val error: ResetPasswordError? = null,
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
    _uiState.update { it.copy(newPassword = password, passwordReset = false, error = null) }
  }

  /** Updates the confirmation and clears the result of the previous attempt. */
  fun onConfirmPasswordChange(password: String) {
    _uiState.update { it.copy(confirmPassword = password, passwordReset = false, error = null) }
  }

  /**
   * Saves the new password. Ignored while a request is already in flight. The fields are first
   * checked with [newPasswordError]; if a rule is broken, its error is set and the repository is
   * not called. Otherwise [ResetPasswordUiState.passwordReset] becomes true on success, or
   * [ResetPasswordUiState.error] is set from the [ConfirmPasswordResetException] reason
   * ([ResetPasswordError.UNKNOWN] for any other failure). If either field changes before the
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
    _uiState.update { it.copy(isLoading = true, passwordReset = false, error = null) }
    viewModelScope.launch {
      val result = authRepository.confirmPasswordReset(oobCode, newPassword)
      _uiState.update {
        if (it.newPassword != newPassword || it.confirmPassword != confirmPassword) {
          it.copy(isLoading = false)
        } else {
          result.fold(
              onSuccess = { _ -> it.copy(isLoading = false, passwordReset = true) },
              onFailure = { exception ->
                it.copy(isLoading = false, error = resetPasswordError(exception))
              },
          )
        }
      }
    }
  }
}

/** Maps a repository failure to the error shown on the reset password screen. */
private fun resetPasswordError(exception: Throwable): ResetPasswordError =
    when ((exception as? ConfirmPasswordResetException)?.reason) {
      ConfirmPasswordResetException.Reason.INVALID_LINK -> ResetPasswordError.INVALID_LINK
      ConfirmPasswordResetException.Reason.WEAK_PASSWORD -> ResetPasswordError.WEAK_PASSWORD
      ConfirmPasswordResetException.Reason.NETWORK -> ResetPasswordError.NETWORK
      ConfirmPasswordResetException.Reason.TOO_MANY_REQUESTS -> ResetPasswordError.TOO_MANY_REQUESTS
      ConfirmPasswordResetException.Reason.UNKNOWN,
      null -> ResetPasswordError.UNKNOWN
    }
