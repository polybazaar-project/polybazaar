// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.PasswordResetException
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.viewmodel.ForgotPasswordUiState
import com.android.polybazaar.auth.viewmodel.ForgotPasswordViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForgotPasswordScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private fun setContent(
      uiState: ForgotPasswordUiState = ForgotPasswordUiState(),
      onEmailChange: (String) -> Unit = {},
      onSendResetLink: () -> Unit = {},
      onBack: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      ForgotPasswordContent(
          uiState = uiState,
          onEmailChange = onEmailChange,
          onSendResetLink = onSendResetLink,
          onBack = onBack,
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

  @Test
  fun displaysTheFigmaTextsAndNoResult() {
    setContent()

    node(ForgotPasswordScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.forgot_password_title)).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.forgot_password_heading)).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.forgot_password_guidance)).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(string(R.string.forgot_password_email_placeholder))
        .assertIsDisplayed()
    node(ForgotPasswordScreenTags.SEND_BUTTON)
        .assert(hasText(string(R.string.forgot_password_send_button)))
    node(ForgotPasswordScreenTags.BACK_TO_SIGN_IN_LINK).assertIsDisplayed()
    node(ForgotPasswordScreenTags.SENT_MESSAGE).assertDoesNotExist()
    node(ForgotPasswordScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun blankEmail_disablesTheSendButton() {
    setContent(uiState = ForgotPasswordUiState(email = "   "))

    node(ForgotPasswordScreenTags.SEND_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun typing_reportsTheEmailAndSendCallsTheAction() {
    var uiState by mutableStateOf(ForgotPasswordUiState())
    var sends = 0
    composeTestRule.setContent {
      ForgotPasswordContent(
          uiState = uiState,
          onEmailChange = { uiState = uiState.copy(email = it) },
          onSendResetLink = { sends++ },
          onBack = {},
      )
    }

    node(ForgotPasswordScreenTags.EMAIL_FIELD).performTextInput("ada@epfl.ch")
    node(ForgotPasswordScreenTags.SEND_BUTTON).assertIsEnabled().performClick()

    assertEquals("ada@epfl.ch", uiState.email)
    assertEquals(1, sends)
  }

  @Test
  fun whileSending_disablesTheButtonAndSaysSending() {
    setContent(uiState = ForgotPasswordUiState(email = "ada@epfl.ch", isLoading = true))

    node(ForgotPasswordScreenTags.SEND_BUTTON)
        .assertIsNotEnabled()
        .assert(hasText(string(R.string.forgot_password_sending_button)))
  }

  @Test
  fun emailSent_showsTheNeutralConfirmationAndOffersToResend() {
    setContent(uiState = ForgotPasswordUiState(email = " ada@epfl.ch ", emailSent = true))

    node(ForgotPasswordScreenTags.SENT_MESSAGE)
        .assertTextEquals(string(R.string.forgot_password_sent_message, "ada@epfl.ch"))
    node(ForgotPasswordScreenTags.SEND_BUTTON)
        .assertIsEnabled()
        .assert(hasText(string(R.string.forgot_password_resend_button)))
    node(ForgotPasswordScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun eachFailureReason_showsItsOwnMessage() {
    var uiState by mutableStateOf(ForgotPasswordUiState(email = "ada@epfl.ch"))
    composeTestRule.setContent {
      ForgotPasswordContent(
          uiState = uiState,
          onEmailChange = {},
          onSendResetLink = {},
          onBack = {},
      )
    }

    for (reason in PasswordResetException.Reason.entries) {
      uiState = uiState.copy(error = reason)
      node(ForgotPasswordScreenTags.ERROR_MESSAGE)
          .assertTextEquals(string(passwordResetErrorText(reason)))
    }
    node(ForgotPasswordScreenTags.SENT_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun failureReasons_mapToDistinctMessages() {
    val messages = PasswordResetException.Reason.entries.map { passwordResetErrorText(it) }

    assertEquals(messages.size, messages.toSet().size)
  }

  @Test
  fun backArrowAndBackToSignIn_callOnBack() {
    var backs = 0
    setContent(onBack = { backs++ })

    node(ForgotPasswordScreenTags.BACK_BUTTON).performClick()
    node(ForgotPasswordScreenTags.BACK_TO_SIGN_IN_LINK).performClick()

    assertEquals(2, backs)
  }

  @Test
  fun shortScreen_scrollsToTheSendButton() {
    // 360x300dp is shorter than the header and the card, so the content has to scroll instead of
    // being cut off or covered by the tagline.
    composeTestRule.setContent {
      Box(modifier = Modifier.size(width = 360.dp, height = 300.dp)) {
        ForgotPasswordContent(
            uiState = ForgotPasswordUiState(email = "ada@epfl.ch"),
            onEmailChange = {},
            onSendResetLink = {},
            onBack = {},
        )
      }
    }

    node(ForgotPasswordScreenTags.BACK_TO_SIGN_IN_LINK).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun withTheViewModel_sendingShowsTheConfirmation() {
    val repository = FakeAuthRepository(Result.success(Unit))
    val viewModel = ForgotPasswordViewModel(repository)
    composeTestRule.setContent { ForgotPasswordScreen(forgotPasswordViewModel = viewModel) {} }

    node(ForgotPasswordScreenTags.EMAIL_FIELD).performTextInput("ada@epfl.ch")
    node(ForgotPasswordScreenTags.SEND_BUTTON).performClick()

    node(ForgotPasswordScreenTags.SENT_MESSAGE)
        .assertTextEquals(string(R.string.forgot_password_sent_message, "ada@epfl.ch"))
    assertEquals(listOf("ada@epfl.ch"), repository.resetEmails)
  }

  @Test
  fun withTheViewModel_networkFailureShowsTheNetworkMessage() {
    val repository =
        FakeAuthRepository(
            Result.failure(PasswordResetException(PasswordResetException.Reason.NETWORK))
        )
    val viewModel = ForgotPasswordViewModel(repository)
    composeTestRule.setContent { ForgotPasswordScreen(forgotPasswordViewModel = viewModel) {} }

    node(ForgotPasswordScreenTags.EMAIL_FIELD).performTextInput("ada@epfl.ch")
    node(ForgotPasswordScreenTags.SEND_BUTTON).performClick()

    node(ForgotPasswordScreenTags.ERROR_MESSAGE)
        .assertTextEquals(string(R.string.forgot_password_error_network))
    node(ForgotPasswordScreenTags.SENT_MESSAGE).assertDoesNotExist()
  }

  private class FakeAuthRepository(private val resetResult: Result<Unit>) : AuthRepository {
    val resetEmails = mutableListOf<String>()

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
      resetEmails += email
      return resetResult
    }

    override suspend fun signIn(email: String, password: String): Result<User> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun signUp(email: String, password: String, username: String): Result<User> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun signOut() = Unit

    override suspend fun getCurrentUser(): User = throw IllegalStateException("Not used")

    override suspend fun sendVerificationEmail(): Result<Unit> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun refreshUser(): Result<User> =
        Result.failure(IllegalStateException("Not used"))
  }
}
