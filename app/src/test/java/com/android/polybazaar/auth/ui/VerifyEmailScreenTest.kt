// Co-authored-by: Claude Opus 5.5
package com.android.polybazaar.auth.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.viewmodel.RESEND_COOLDOWN_SECONDS
import com.android.polybazaar.auth.viewmodel.VerifyEmailUiState
import com.android.polybazaar.auth.viewmodel.VerifyEmailViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VerifyEmailScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val email = "ada@epfl.ch"

  private fun setContent(
      uiState: VerifyEmailUiState = VerifyEmailUiState(),
      onCheckVerified: () -> Unit = {},
      onResend: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      VerifyEmailContent(
          uiState = uiState,
          email = email,
          onCheckVerified = onCheckVerified,
          onResend = onResend,
      )
    }
  }

  @Test
  fun displaysEmailAndEnabledButtons() {
    setContent()

    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.EMAIL_TEXT).assertTextEquals(email)
    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.CHECK_BUTTON).assertIsEnabled()
    composeTestRule
        .onNodeWithTag(VerifyEmailScreenTags.RESEND_BUTTON)
        .assertIsEnabled()
        .assert(hasText("Resend email"))
    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun buttons_callTheirCallbacks() {
    var checks = 0
    var resends = 0
    setContent(onCheckVerified = { checks++ }, onResend = { resends++ })

    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.CHECK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.RESEND_BUTTON).performClick()

    assertEquals(1, checks)
    assertEquals(1, resends)
  }

  @Test
  fun duringCooldown_resendIsDisabledAndShowsSecondsLeft() {
    var resent = false
    setContent(
        uiState = VerifyEmailUiState(resendCooldownSeconds = 42),
        onResend = { resent = true },
    )

    composeTestRule
        .onNodeWithTag(VerifyEmailScreenTags.RESEND_BUTTON)
        .assertIsNotEnabled()
        .assert(hasText("Resend email in 42s"))
        .performClick()

    assertFalse(resent)
    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.CHECK_BUTTON).assertIsEnabled()
  }

  @Test
  fun whileLoading_bothButtonsAreDisabled() {
    setContent(uiState = VerifyEmailUiState(isLoading = true))

    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.CHECK_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.RESEND_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun onceVerified_resendIsDisabled() {
    var resent = false
    setContent(uiState = VerifyEmailUiState(isVerified = true), onResend = { resent = true })

    composeTestRule
        .onNodeWithTag(VerifyEmailScreenTags.RESEND_BUTTON)
        .assertIsNotEnabled()
        .performClick()

    assertFalse(resent)
  }

  @Test
  fun errorMessage_isDisplayedWhenPresent() {
    setContent(uiState = VerifyEmailUiState(errorMessage = "Email not verified yet"))

    composeTestRule
        .onNodeWithTag(VerifyEmailScreenTags.ERROR_MESSAGE)
        .assertIsDisplayed()
        .assertTextEquals("Email not verified yet")
  }

  @Test
  fun screen_checkingAVerifiedEmail_callsOnVerified() {
    val repository =
        FakeAuthRepository(
            refreshUserResult =
                Result.success(
                    User(uid = "user-1", email = email, username = "ada", isEmailVerified = true)
                )
        )
    val viewModel = VerifyEmailViewModel(repository)
    var verified = false
    composeTestRule.setContent {
      VerifyEmailScreen(
          verifyEmailViewModel = viewModel,
          email = email,
          onVerified = { verified = true },
      )
    }

    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.CHECK_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertTrue(verified)
  }

  @Test
  fun screen_checkingAnUnverifiedEmail_showsErrorAndDoesNotCallOnVerified() {
    val repository =
        FakeAuthRepository(
            refreshUserResult =
                Result.success(
                    User(uid = "user-1", email = email, username = "ada", isEmailVerified = false)
                )
        )
    val viewModel = VerifyEmailViewModel(repository)
    var verified = false
    composeTestRule.setContent {
      VerifyEmailScreen(
          verifyEmailViewModel = viewModel,
          email = email,
          onVerified = { verified = true },
      )
    }

    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.CHECK_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertFalse(verified)
    composeTestRule
        .onNodeWithTag(VerifyEmailScreenTags.ERROR_MESSAGE)
        .assertIsDisplayed()
        .assertTextEquals("Email not verified yet")
  }

  @Test
  fun screen_resending_sendsEmailAndStartsCooldown() {
    val repository = FakeAuthRepository()
    val viewModel = VerifyEmailViewModel(repository)
    composeTestRule.setContent {
      VerifyEmailScreen(
          verifyEmailViewModel = viewModel,
          email = email,
          onVerified = {},
      )
    }

    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.RESEND_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, repository.sendVerificationEmailCalls)
    composeTestRule
        .onNodeWithTag(VerifyEmailScreenTags.RESEND_BUTTON)
        .assertIsNotEnabled()
        .assert(hasText("Resend email in ${RESEND_COOLDOWN_SECONDS}s"))
  }

  private class FakeAuthRepository(
      private val refreshUserResult: Result<User> =
          Result.failure(IllegalStateException("Not used"))
  ) : AuthRepository {
    var sendVerificationEmailCalls = 0

    override suspend fun signIn(email: String, password: String): Result<User> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun signUp(email: String, password: String, username: String): Result<User> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun signOut() {}

    override suspend fun getCurrentUser(): User = throw IllegalStateException("Not used")

    override suspend fun sendVerificationEmail(): Result<Unit> {
      sendVerificationEmailCalls++
      return Result.success(Unit)
    }

    override suspend fun refreshUser(): Result<User> = refreshUserResult
  }
}
