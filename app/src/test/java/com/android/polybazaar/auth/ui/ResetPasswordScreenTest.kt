// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import com.android.polybazaar.auth.model.AuthRules
import com.android.polybazaar.auth.model.ConfirmPasswordResetException
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.viewmodel.ResetPasswordError
import com.android.polybazaar.auth.viewmodel.ResetPasswordUiState
import com.android.polybazaar.auth.viewmodel.ResetPasswordViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResetPasswordScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private fun setContent(
      uiState: ResetPasswordUiState = ResetPasswordUiState(),
      actions: ResetPasswordActions = ResetPasswordActions(),
  ) {
    composeTestRule.setContent { ResetPasswordContent(uiState = uiState, actions = actions) }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

  @Test
  fun displaysTheFigmaTextsAndNoError() {
    setContent()

    node(ResetPasswordScreenTags.SCREEN).assertIsDisplayed()
    // The title and the button both say "Reset password", as in the Figma.
    composeTestRule.onAllNodesWithText(string(R.string.reset_password_title)).assertCountEquals(2)
    composeTestRule.onNodeWithText(string(R.string.reset_password_heading)).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.reset_password_guidance)).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(string(R.string.reset_password_rules, AuthRules.MIN_PASSWORD_LENGTH))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(string(R.string.reset_password_new_password_placeholder))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(string(R.string.reset_password_confirm_placeholder))
        .assertIsDisplayed()
    node(ResetPasswordScreenTags.RESET_BUTTON)
        .assertIsEnabled()
        .assert(hasText(string(R.string.reset_password_button)))
    node(ResetPasswordScreenTags.BACK_TO_SIGN_IN_LINK).assertIsDisplayed()
    node(ResetPasswordScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun passwordFields_areMarkedAsPasswords() {
    setContent()

    // Password semantics are what mask the text on screen and keep it out of TalkBack.
    node(ResetPasswordScreenTags.NEW_PASSWORD_FIELD)
        .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
    node(ResetPasswordScreenTags.CONFIRM_PASSWORD_FIELD)
        .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
  }

  @Test
  fun typing_reportsBothFieldsAndResetCallsTheAction() {
    var uiState by mutableStateOf(ResetPasswordUiState())
    var resets = 0
    composeTestRule.setContent {
      ResetPasswordContent(
          uiState = uiState,
          actions =
              ResetPasswordActions(
                  onNewPasswordChange = { uiState = uiState.copy(newPassword = it) },
                  onConfirmPasswordChange = { uiState = uiState.copy(confirmPassword = it) },
                  onResetPassword = { resets++ },
              ),
      )
    }

    node(ResetPasswordScreenTags.NEW_PASSWORD_FIELD).performTextInput("secret12")
    node(ResetPasswordScreenTags.CONFIRM_PASSWORD_FIELD).performTextInput("secret13")
    node(ResetPasswordScreenTags.RESET_BUTTON).performClick()

    assertEquals("secret12", uiState.newPassword)
    assertEquals("secret13", uiState.confirmPassword)
    assertEquals(1, resets)
  }

  @Test
  fun whileResetting_disablesTheButtonAndSaysResetting() {
    setContent(uiState = ResetPasswordUiState(isLoading = true))

    node(ResetPasswordScreenTags.RESET_BUTTON)
        .assertIsNotEnabled()
        .assert(hasText(string(R.string.reset_password_resetting_button)))
  }

  @Test
  fun eachError_showsItsOwnMessage() {
    var uiState by mutableStateOf(ResetPasswordUiState())
    composeTestRule.setContent {
      ResetPasswordContent(uiState = uiState, actions = ResetPasswordActions())
    }

    for (error in ResetPasswordError.entries) {
      uiState = uiState.copy(error = error)
      node(ResetPasswordScreenTags.ERROR_MESSAGE)
          .assertTextEquals(string(resetPasswordErrorText(error)))
    }
  }

  @Test
  fun errors_mapToDistinctMessages() {
    val messages = ResetPasswordError.entries.map { resetPasswordErrorText(it) }

    assertEquals(messages.size, messages.toSet().size)
  }

  @Test
  fun backArrowAndBackToSignIn_callOnBackToSignIn() {
    var backs = 0
    setContent(actions = ResetPasswordActions(onBackToSignIn = { backs++ }))

    node(ResetPasswordScreenTags.BACK_BUTTON).performClick()
    node(ResetPasswordScreenTags.BACK_TO_SIGN_IN_LINK).performClick()

    assertEquals(2, backs)
  }

  @Test
  fun shortScreen_scrollsToTheResetButton() {
    // 360x300dp is shorter than the header and the card, so the content has to scroll instead of
    // being cut off or covered by the tagline.
    composeTestRule.setContent {
      Box(modifier = Modifier.size(width = 360.dp, height = 300.dp)) {
        ResetPasswordContent(uiState = ResetPasswordUiState(), actions = ResetPasswordActions())
      }
    }

    node(ResetPasswordScreenTags.BACK_TO_SIGN_IN_LINK).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun withTheViewModel_successCallsOnPasswordResetOnce() {
    val repository = FakeAuthRepository(Result.success(Unit))
    val viewModel = ResetPasswordViewModel(repository, oobCode = "code-1")
    var resets = 0
    composeTestRule.setContent {
      ResetPasswordScreen(
          resetPasswordViewModel = viewModel,
          onBackToSignIn = {},
          onPasswordReset = { resets++ },
      )
    }

    node(ResetPasswordScreenTags.NEW_PASSWORD_FIELD).performTextInput("secret12")
    node(ResetPasswordScreenTags.CONFIRM_PASSWORD_FIELD).performTextInput("secret12")
    node(ResetPasswordScreenTags.RESET_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, resets)
    assertEquals(listOf("code-1" to "secret12"), repository.resets)
  }

  @Test
  fun withTheViewModel_mismatchShowsTheErrorWithoutCallingTheRepository() {
    val repository = FakeAuthRepository(Result.success(Unit))
    val viewModel = ResetPasswordViewModel(repository, oobCode = "code-1")
    composeTestRule.setContent {
      ResetPasswordScreen(
          resetPasswordViewModel = viewModel,
          onBackToSignIn = {},
          onPasswordReset = {},
      )
    }

    node(ResetPasswordScreenTags.NEW_PASSWORD_FIELD).performTextInput("secret12")
    node(ResetPasswordScreenTags.CONFIRM_PASSWORD_FIELD).performTextInput("secret13")
    node(ResetPasswordScreenTags.RESET_BUTTON).performClick()

    node(ResetPasswordScreenTags.ERROR_MESSAGE)
        .assertTextEquals(string(R.string.reset_password_error_mismatch))
    assertEquals(emptyList<Pair<String, String>>(), repository.resets)
  }

  @Test
  fun withTheViewModel_expiredLinkShowsTheInvalidLinkMessage() {
    val repository =
        FakeAuthRepository(
            Result.failure(
                ConfirmPasswordResetException(ConfirmPasswordResetException.Reason.INVALID_LINK)
            )
        )
    val viewModel = ResetPasswordViewModel(repository, oobCode = "expired")
    var resets = 0
    composeTestRule.setContent {
      ResetPasswordScreen(
          resetPasswordViewModel = viewModel,
          onBackToSignIn = {},
          onPasswordReset = { resets++ },
      )
    }

    node(ResetPasswordScreenTags.NEW_PASSWORD_FIELD).performTextInput("secret12")
    node(ResetPasswordScreenTags.CONFIRM_PASSWORD_FIELD).performTextInput("secret12")
    node(ResetPasswordScreenTags.RESET_BUTTON).performClick()

    node(ResetPasswordScreenTags.ERROR_MESSAGE)
        .assertTextEquals(string(R.string.reset_password_error_invalid_link))
    assertEquals(0, resets)
  }

  private class FakeAuthRepository(private val confirmResult: Result<Unit>) : AuthRepository {
    val resets = mutableListOf<Pair<String, String>>()

    override suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit> {
      resets += code to newPassword
      return confirmResult
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

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        Result.failure(IllegalStateException("Not used"))
  }
}
