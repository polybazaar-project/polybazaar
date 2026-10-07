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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateAccountScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun setContent(
      uiState: AuthUiState = AuthUiState(),
      onCreateAccount: (String, String, String) -> Unit = { _, _, _ -> },
      onBack: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      CreateAccountContent(uiState = uiState, onCreateAccount = onCreateAccount, onBack = onBack)
    }
  }

  private fun fillForm(
      username: String,
      email: String,
      password: String,
      confirmPassword: String = password,
  ) {
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.USERNAME_FIELD).performTextInput(username)
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.EMAIL_FIELD).performTextInput(email)
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.PASSWORD_FIELD).performTextInput(password)
    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput(confirmPassword)
  }

  @Test
  fun displaysAllFieldsAndButtons() {
    setContent()

    listOf(
            CreateAccountScreenTags.SCREEN,
            CreateAccountScreenTags.BACK_BUTTON,
            CreateAccountScreenTags.USERNAME_FIELD,
            CreateAccountScreenTags.EMAIL_FIELD,
            CreateAccountScreenTags.PASSWORD_FIELD,
            CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD,
            CreateAccountScreenTags.CANCEL_BUTTON,
            CreateAccountScreenTags.CREATE_BUTTON,
        )
        .forEach { composeTestRule.onNodeWithTag(it).assertIsDisplayed() }
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.ERROR_MESSAGE).assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.PASSWORD_MISMATCH_MESSAGE)
        .assertDoesNotExist()
  }

  @Test
  fun mismatchedPasswords_showMessageAndDisableCreateButton() {
    var submitted = false
    setContent(onCreateAccount = { _, _, _ -> submitted = true })

    fillForm("ada_lovelace", "ada@epfl.ch", "secret", confirmPassword = "different")
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).performClick()

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.PASSWORD_MISMATCH_MESSAGE)
        .assertIsDisplayed()
        .assertTextEquals("Passwords do not match")
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsNotEnabled()
    assertFalse(submitted)
  }

  @Test
  fun fixingConfirmPassword_hidesMessageAndEnablesCreateButton() {
    setContent()
    fillForm("ada_lovelace", "ada@epfl.ch", "secret", confirmPassword = "secre")

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("t")

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.PASSWORD_MISMATCH_MESSAGE)
        .assertDoesNotExist()
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsEnabled()
  }

  @Test
  fun createButton_isDisabledUntilAllFieldsAreFilled() {
    setContent()
    val createButton = composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON)

    createButton.assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.USERNAME_FIELD)
        .performTextInput("ada_lovelace")
    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.EMAIL_FIELD)
        .performTextInput("ada@epfl.ch")
    createButton.assertIsNotEnabled()
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.PASSWORD_FIELD).performTextInput("secret")
    createButton.assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput("secret")
    createButton.assertIsEnabled()
  }

  @Test
  fun createButton_sendsTrimmedFormValues() {
    var submitted: Triple<String, String, String>? = null
    setContent(
        onCreateAccount = { name, email, password -> submitted = Triple(name, email, password) }
    )

    fillForm(" ada_lovelace ", " ada@epfl.ch ", "secret")
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).performClick()

    assertEquals(Triple("ada_lovelace", "ada@epfl.ch", "secret"), submitted)
  }

  @Test
  fun createButton_isDisabledWhileLoading() {
    setContent(uiState = AuthUiState(isLoading = true))

    fillForm("ada_lovelace", "ada@epfl.ch", "secret")

    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun errorMessage_isDisplayedWhenPresent() {
    setContent(uiState = AuthUiState(errorMessage = "Email already exists"))

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.ERROR_MESSAGE)
        .assertIsDisplayed()
        .assertTextEquals("Email already exists")
  }

  @Test
  fun cancelAndBackButtons_callOnBack() {
    var backCount = 0
    setContent(onBack = { backCount++ })

    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CANCEL_BUTTON).performClick()
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.BACK_BUTTON).performClick()

    assertEquals(2, backCount)
  }

  @Test
  fun screen_signsUpThroughViewModelAndCallsOnSignedIn() {
    val user = User(uid = "user-1", email = "ada@epfl.ch", username = "ada_lovelace")
    val repository = FakeAuthRepository(signUpResult = Result.success(user))
    val viewModel = AuthViewModel(repository)
    var signedIn = false
    composeTestRule.setContent {
      CreateAccountScreen(
          authViewModel = viewModel,
          onBack = {},
          onSignedIn = { signedIn = true },
      )
    }

    fillForm("ada_lovelace", "ada@epfl.ch", "secret")
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(Triple("ada@epfl.ch", "secret", "ada_lovelace"), repository.signUpDetails)
    assertTrue(signedIn)
  }

  private class FakeAuthRepository(private val signUpResult: Result<User>) : AuthRepository {
    var signUpDetails: Triple<String, String, String>? = null

    override suspend fun signIn(email: String, password: String): Result<User> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun signUp(email: String, password: String, username: String): Result<User> {
      signUpDetails = Triple(email, password, username)
      return signUpResult
    }

    override suspend fun signOut() {}

    override suspend fun getCurrentUser(): User = throw IllegalStateException("Not used")
  }
}
