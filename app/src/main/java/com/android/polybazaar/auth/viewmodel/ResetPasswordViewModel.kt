// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.viewmodel

import androidx.lifecycle.ViewModel
import com.android.polybazaar.auth.model.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Why choosing a new password failed. The screen picks the message to show. */
enum class ResetPasswordError {
  /** The new password is empty or only whitespace. */
  EMPTY_PASSWORD,

  /** The new password does not meet the rules shown under the field. */
  WEAK_PASSWORD,

  /** The confirmation is not exactly the new password. */
  PASSWORDS_DO_NOT_MATCH,
}

/** Shortest new password accepted, as stated under the new password field. */
internal const val MIN_PASSWORD_LENGTH = 8

/**
 * Checks the two fields before anything is sent, in this order:
 * - [newPassword] blank: [ResetPasswordError.EMPTY_PASSWORD];
 * - [newPassword] shorter than [MIN_PASSWORD_LENGTH], or without a letter or a digit:
 *   [ResetPasswordError.WEAK_PASSWORD];
 * - [confirmPassword] not exactly equal to [newPassword], blank included:
 *   [ResetPasswordError.PASSWORDS_DO_NOT_MATCH].
 *
 * Letters and digits are any Unicode ones ([Char.isLetter], [Char.isDigit]). Nothing is trimmed.
 *
 * @return the first rule broken, or null if the passwords can be sent.
 */
internal fun newPasswordError(newPassword: String, confirmPassword: String): ResetPasswordError? =
    when {
      newPassword.isBlank() -> ResetPasswordError.EMPTY_PASSWORD
      newPassword.length < MIN_PASSWORD_LENGTH ||
          newPassword.none { it.isLetter() } ||
          newPassword.none { it.isDigit() } -> ResetPasswordError.WEAK_PASSWORD
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
}
