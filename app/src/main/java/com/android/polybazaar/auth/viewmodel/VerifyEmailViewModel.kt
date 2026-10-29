// Co-authored-by: Claude Opus 5.5
package com.android.polybazaar.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.AuthRepository
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
    val errorMessage: String? = null,
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
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
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
                _uiState.update {
                  it.copy(
                      isLoading = false,
                      errorMessage = exception.message ?: "Unable to send verification email",
                  )
                }
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
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      authRepository
          .refreshUser()
          .fold(
              onSuccess = { user ->
                _uiState.update {
                  it.copy(
                      isVerified = user.isEmailVerified,
                      isLoading = false,
                      errorMessage =
                          if (user.isEmailVerified || automatic) null else "Email not verified yet",
                  )
                }
              },
              onFailure = { exception ->
                _uiState.update {
                  it.copy(
                      isLoading = false,
                      errorMessage =
                          if (automatic) null
                          else exception.message ?: "Unable to check verification",
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
