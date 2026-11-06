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
import com.android.polybazaar.auth.model.FirebaseAuthRepository
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.viewmodel.ForgotPasswordUiState
import com.android.polybazaar.auth.viewmodel.ForgotPasswordViewModel
import com.android.polybazaar.error.model.AppError
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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
  fun eachError_showsTheSameTextAsBeforeAppError() {
    var uiState by mutableStateOf(ForgotPasswordUiState(email = "ada@epfl.ch"))
    composeTestRule.setContent {
      ForgotPasswordContent(
          uiState = uiState,
          onEmailChange = {},
          onSendResetLink = {},
          onBack = {},
      )
    }

    mapOf(
            AppError.INVALID_EMAIL to INVALID_EMAIL_TEXT,
            AppError.NETWORK to NETWORK_TEXT,
            AppError.TOO_MANY_REQUESTS to TOO_MANY_REQUESTS_TEXT,
            AppError.UNKNOWN to UNKNOWN_TEXT,
        )
        .forEach { (error, text) ->
          uiState = uiState.copy(error = error)
          node(ForgotPasswordScreenTags.ERROR_MESSAGE).assertTextEquals(text)
        }
    node(ForgotPasswordScreenTags.SENT_MESSAGE).assertDoesNotExist()
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
  fun withTheRepository_eachFirebaseFailureShowsItsTextAndNeverItsOwn() {
    val auth = mock<FirebaseAuth>()
    val viewModel = ForgotPasswordViewModel(FirebaseAuthRepository(auth, mock<FirebaseFirestore>()))
    composeTestRule.setContent { ForgotPasswordScreen(forgotPasswordViewModel = viewModel) {} }
    node(ForgotPasswordScreenTags.EMAIL_FIELD).performTextInput("ada@epfl.ch")

    mapOf(
            FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", RAW_TEXT) to
                INVALID_EMAIL_TEXT,
            FirebaseNetworkException(RAW_TEXT) to NETWORK_TEXT,
            FirebaseTooManyRequestsException(RAW_TEXT) to TOO_MANY_REQUESTS_TEXT,
            IllegalStateException(RAW_TEXT) to UNKNOWN_TEXT,
        )
        .forEach { (exception, text) ->
          whenever(auth.sendPasswordResetEmail("ada@epfl.ch"))
              .thenReturn(Tasks.forException(exception))

          node(ForgotPasswordScreenTags.SEND_BUTTON).performClick()

          node(ForgotPasswordScreenTags.ERROR_MESSAGE).assertTextEquals(text)
          composeTestRule.onNodeWithText(RAW_TEXT, substring = true).assertDoesNotExist()
          node(ForgotPasswordScreenTags.SENT_MESSAGE).assertDoesNotExist()
        }
  }

  @Test
  fun withTheRepository_unknownOrDisabledAddressShowsTheNeutralConfirmation() {
    val auth = mock<FirebaseAuth>()
    val viewModel = ForgotPasswordViewModel(FirebaseAuthRepository(auth, mock<FirebaseFirestore>()))
    composeTestRule.setContent { ForgotPasswordScreen(forgotPasswordViewModel = viewModel) {} }
    node(ForgotPasswordScreenTags.EMAIL_FIELD).performTextInput("ada@epfl.ch")

    for (code in listOf("ERROR_USER_NOT_FOUND", "ERROR_USER_DISABLED")) {
      whenever(auth.sendPasswordResetEmail("ada@epfl.ch"))
          .thenReturn(Tasks.forException(FirebaseAuthInvalidUserException(code, RAW_TEXT)))

      node(ForgotPasswordScreenTags.SEND_BUTTON).performClick()

      node(ForgotPasswordScreenTags.SENT_MESSAGE)
          .assertTextEquals(string(R.string.forgot_password_sent_message, "ada@epfl.ch"))
      node(ForgotPasswordScreenTags.ERROR_MESSAGE).assertDoesNotExist()
    }
  }

  @Test
  fun withTheRepository_blankEmailShowsTheInvalidEmailTextWithoutCallingFirebase() {
    val auth = mock<FirebaseAuth>()
    val viewModel = ForgotPasswordViewModel(FirebaseAuthRepository(auth, mock<FirebaseFirestore>()))
    composeTestRule.setContent { ForgotPasswordScreen(forgotPasswordViewModel = viewModel) {} }

    // The send button is disabled for a blank email, so call the ViewModel as a stale tap would.
    composeTestRule.runOnIdle { viewModel.sendResetLink() }

    node(ForgotPasswordScreenTags.ERROR_MESSAGE).assertTextEquals(INVALID_EMAIL_TEXT)
    verify(auth, never()).sendPasswordResetEmail(any<String>())
  }

  private class FakeAuthRepository(private val resetResult: Result<Unit>) : AuthRepository {
    val resetEmails = mutableListOf<String>()

    override suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit> =
        Result.failure(IllegalStateException("Not used"))

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

  private companion object {
    // Literal on purpose: the acceptance criterion is that the text did not change.
    const val INVALID_EMAIL_TEXT = "Enter a valid email address."
    const val NETWORK_TEXT = "No connection. Check your internet and try again."
    const val TOO_MANY_REQUESTS_TEXT = "Too many attempts. Wait a few minutes, then try again."
    const val UNKNOWN_TEXT = "Something went wrong. Please try again."
    const val RAW_TEXT = "raw firebase message"
  }
}
