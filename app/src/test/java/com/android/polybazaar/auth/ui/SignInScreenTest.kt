// Portions of this code were generated with the help of Claude Code.
package com.android.polybazaar.auth.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.viewmodel.AuthUiState
import com.android.polybazaar.auth.viewmodel.AuthViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignInScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun setContent(
      uiState: AuthUiState = AuthUiState(),
      onSignIn: (String, String) -> Unit = { _, _ -> },
      onCreateAccount: () -> Unit = {},
      onForgotPassword: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      SignInContent(
          uiState = uiState,
          onSignIn = onSignIn,
          onCreateAccount = onCreateAccount,
          onForgotPassword = onForgotPassword,
      )
    }
  }

  private fun fillForm(email: String, password: String) {
    composeTestRule.onNodeWithTag(SignInScreenTags.EMAIL_FIELD).performTextInput(email)
    composeTestRule.onNodeWithTag(SignInScreenTags.PASSWORD_FIELD).performTextInput(password)
  }

  @Test
  fun displaysAllFieldsAndActions() {
    setContent()

    listOf(
            SignInScreenTags.SCREEN,
            SignInScreenTags.EMAIL_FIELD,
            SignInScreenTags.PASSWORD_FIELD,
            SignInScreenTags.FORGOT_PASSWORD_LINK,
            SignInScreenTags.SIGN_IN_BUTTON,
            SignInScreenTags.CREATE_ACCOUNT_LINK,
        )
        .forEach { composeTestRule.onNodeWithTag(it).assertIsDisplayed() }
    composeTestRule.onNodeWithTag(SignInScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun signInButton_isDisabledUntilBothFieldsAreFilled() {
    setContent()
    val signInButton = composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON)

    signInButton.assertIsNotEnabled()
    composeTestRule.onNodeWithTag(SignInScreenTags.EMAIL_FIELD).performTextInput("ada@epfl.ch")
    signInButton.assertIsNotEnabled()
    composeTestRule.onNodeWithTag(SignInScreenTags.PASSWORD_FIELD).performTextInput("secret")
    signInButton.assertIsEnabled()
  }

  @Test
  fun signInButton_isDisabledForBlankEmail() {
    setContent()

    fillForm("   ", "secret")

    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun signInButton_submitsTrimmedEmailAndUntouchedPassword() {
    var submitted: Pair<String, String>? = null
    setContent(onSignIn = { email, password -> submitted = email to password })

    fillForm(" ada@epfl.ch ", " secret ")
    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).performClick()

    assertEquals("ada@epfl.ch" to " secret ", submitted)
  }

  @Test
  fun signInButton_isDisabledWhileLoading() {
    setContent(uiState = AuthUiState(isLoading = true))

    fillForm("ada@epfl.ch", "secret")

    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun errorMessage_isDisplayedWhenPresent() {
    setContent(uiState = AuthUiState(errorMessage = "Wrong password"))

    composeTestRule
        .onNodeWithTag(SignInScreenTags.ERROR_MESSAGE)
        .assertIsDisplayed()
        .assertTextEquals("Wrong password")
  }

  @Test
  fun links_callTheirCallbacks() {
    var createAccountClicked = false
    var forgotPasswordClicked = false
    setContent(
        onCreateAccount = { createAccountClicked = true },
        onForgotPassword = { forgotPasswordClicked = true },
    )

    composeTestRule.onNodeWithTag(SignInScreenTags.CREATE_ACCOUNT_LINK).performClick()
    assertTrue(createAccountClicked)
    assertFalse(forgotPasswordClicked)

    composeTestRule.onNodeWithTag(SignInScreenTags.FORGOT_PASSWORD_LINK).performClick()
    assertTrue(forgotPasswordClicked)
  }

  @Test
  fun screen_signsInThroughViewModelAndCallsOnSignedIn() {
    val user = User(uid = "user-1", email = "ada@epfl.ch", username = "ada_lovelace")
    val repository = FakeAuthRepository(signInResult = Result.success(user))
    val viewModel = AuthViewModel(repository)
    var signedIn = false
    composeTestRule.setContent {
      SignInScreen(
          authViewModel = viewModel,
          onCreateAccount = {},
          onForgotPassword = {},
          onSignedIn = { signedIn = true },
      )
    }

    fillForm("ada@epfl.ch", "secret")
    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals("ada@epfl.ch" to "secret", repository.signInDetails)
    assertTrue(signedIn)
  }

  @Test
  fun screen_showsErrorAndStaysWhenSignInFails() {
    val repository =
        FakeAuthRepository(signInResult = Result.failure(Exception("Invalid credentials")))
    val viewModel = AuthViewModel(repository)
    var signedIn = false
    composeTestRule.setContent {
      SignInScreen(
          authViewModel = viewModel,
          onCreateAccount = {},
          onForgotPassword = {},
          onSignedIn = { signedIn = true },
      )
    }

    fillForm("ada@epfl.ch", "wrong")
    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).performClick()
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(SignInScreenTags.ERROR_MESSAGE)
        .assertTextEquals("Invalid credentials")
    assertFalse(signedIn)
    assertNull(viewModel.uiState.value.user)
  }

  private class FakeAuthRepository(private val signInResult: Result<User>) : AuthRepository {
    var signInDetails: Pair<String, String>? = null

    override suspend fun signIn(email: String, password: String): Result<User> {
      signInDetails = email to password
      return signInResult
    }

    override suspend fun signUp(email: String, password: String, username: String): Result<User> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun signOut() {}

    override fun getCurrentUser(): User = throw IllegalStateException("Not used")
  }
}
