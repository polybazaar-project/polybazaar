// Co-authored-by: Claude Opus 5.5
package com.android.polybazaar.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val RESEND_COOLDOWN_SECONDS = 60

data class VerifyEmailUiState(
    val isVerified: Boolean = false,
    val isLoading: Boolean = false,
    val resendCooldownSeconds: Int = 0,
    /** Why the last resend or check the user asked for failed, or null. */
    val error: AppError? = null,
    /**
     * Set when a check the user asked for found the email still unverified. Not a failure, so it is
     * kept apart from [error].
     */
    val isNotVerifiedYet: Boolean = false,
)

/**
 * @param initialCooldownSeconds how long Resend stays disabled when the screen opens. Sign-up has
 *   just sent an email, so by default the user waits for it instead of sending a second one.
 */
class VerifyEmailViewModel(
    private val authRepository: AuthRepository,
    initialCooldownSeconds: Int = RESEND_COOLDOWN_SECONDS,
) : ViewModel() {

  private val _uiState =
      MutableStateFlow(VerifyEmailUiState(resendCooldownSeconds = initialCooldownSeconds))
  val uiState: StateFlow<VerifyEmailUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch { countDownResendCooldown() }
  }

  /**
   * Sends the verification email again. Ignored once verified, while loading, or during the
   * cooldown.
   */
  fun resend() {
    val state = _uiState.value
    if (state.isVerified || state.isLoading || state.resendCooldownSeconds > 0) return
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null, isNotVerifiedYet = false) }
      authRepository
          .sendVerificationEmail()
          .fold(
              onSuccess = {
                _uiState.update {
                  it.copy(isLoading = false, resendCooldownSeconds = RESEND_COOLDOWN_SECONDS)
                }
                countDownResendCooldown()
              },
              onFailure = { exception ->
                _uiState.update { it.copy(isLoading = false, error = exception.toAppError()) }
              },
          )
    }
  }

  /**
   * Checks with the server whether the email has been verified since the last check.
   *
   * @param automatic true for checks the user did not ask for, such as on resume. They show no
   *   message, neither "Email not verified yet" nor a failure, since the next check retries anyway.
   */
  fun refresh(automatic: Boolean = false) {
    if (_uiState.value.isLoading) return
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null, isNotVerifiedYet = false) }
      authRepository
          .refreshUser()
          .fold(
              onSuccess = { user ->
                _uiState.update {
                  it.copy(
                      isVerified = user.isEmailVerified,
                      isLoading = false,
                      isNotVerifiedYet = !user.isEmailVerified && !automatic,
                  )
                }
              },
              onFailure = { exception ->
                _uiState.update {
                  it.copy(
                      isLoading = false,
                      error = if (automatic) null else exception.toAppError(),
                  )
                }
              },
          )
    }
  }

  private suspend fun countDownResendCooldown() {
    while (_uiState.value.resendCooldownSeconds > 0) {
      delay(1_000)
      _uiState.update { it.copy(resendCooldownSeconds = it.resendCooldownSeconds - 1) }
    }
  }
}
