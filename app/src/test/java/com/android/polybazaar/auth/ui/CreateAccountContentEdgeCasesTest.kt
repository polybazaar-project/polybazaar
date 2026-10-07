// Portions of this code were generated with the help of Claude Code.
package com.android.polybazaar.auth.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.auth.viewmodel.AuthUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [CreateAccountContent] edge cases: static content, masking and blank input. */
@RunWith(AndroidJUnit4::class)
class CreateAccountContentEdgeCasesTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var submitted: Triple<String, String, String>? = null

  private fun setContent(uiState: AuthUiState = AuthUiState()) {
    composeTestRule.setContent {
      CreateAccountContent(
          uiState = uiState,
          onCreateAccount = { username, email, password ->
            submitted = Triple(username, email, password)
          },
          onBack = {},
      )
    }
  }

  private fun type(tag: String, text: String) =
      composeTestRule.onNodeWithTag(tag).performTextInput(text)

  private fun editableText(tag: String): String =
      composeTestRule
          .onNodeWithTag(tag)
          .fetchSemanticsNode()
          .config[SemanticsProperties.EditableText]
          .text

  @Test
  fun displaysTitleLabelsAndPlaceholders() {
    setContent()

    listOf(
            "ACCOUNT",
            "Registration details",
            "USERNAME",
            "EMAIL",
            "PASSWORD",
            "CONFIRM PASSWORD",
            "Enter your username",
            "Enter your email",
            "Create a password",
            "Repeat your password",
            "Cancel",
            "Good tools. Great neighbours.",
        )
        .forEach { composeTestRule.onNodeWithText(it, useUnmergedTree = true).assertExists() }
    // "Create account" is both the header title and the submit button label.
    composeTestRule
        .onAllNodesWithText("Create account", useUnmergedTree = true)
        .assertCountEquals(2)
  }

  @Test
  fun placeholder_disappearsOnceFieldHasText() {
    setContent()

    type(CreateAccountScreenTags.USERNAME_FIELD, "ada")

    composeTestRule.onNodeWithText("Enter your username").assertDoesNotExist()
    assertEquals("ada", editableText(CreateAccountScreenTags.USERNAME_FIELD))
  }

  @Test
  fun passwordFields_storeRawTextButAreMasked() {
    setContent()

    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret")

    // EditableText exposes the transformed (masked) text, never the raw password.
    assertEquals("•".repeat(6), editableText(CreateAccountScreenTags.PASSWORD_FIELD))
    assertEquals("•".repeat(6), editableText(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD))
  }

  @Test
  fun usernameAndEmail_areNotMasked() {
    setContent()

    type(CreateAccountScreenTags.USERNAME_FIELD, "ada_lovelace")
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")

    assertEquals("ada_lovelace", editableText(CreateAccountScreenTags.USERNAME_FIELD))
    assertEquals("ada@epfl.ch", editableText(CreateAccountScreenTags.EMAIL_FIELD))
  }

  @Test
  fun blankUsername_keepsCreateButtonDisabled() {
    setContent()

    type(CreateAccountScreenTags.USERNAME_FIELD, "   ")
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret")
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).performClick()

    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsNotEnabled()
    assertNull(submitted)
  }

  @Test
  fun blankEmail_keepsCreateButtonDisabled() {
    setContent()

    type(CreateAccountScreenTags.USERNAME_FIELD, "ada_lovelace")
    type(CreateAccountScreenTags.EMAIL_FIELD, "  ")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret")

    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun whitespaceOnlyPassword_isAcceptedAndSentUntrimmed() {
    setContent()

    type(CreateAccountScreenTags.USERNAME_FIELD, "ada_lovelace")
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "  pw  ")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "  pw  ")
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).performClick()

    assertEquals(Triple("ada_lovelace", "ada@epfl.ch", "  pw  "), submitted)
  }

  @Test
  fun passwordOneCharTooShort_keepsCreateButtonDisabled() {
    setContent()

    type(CreateAccountScreenTags.USERNAME_FIELD, "ada_lovelace")
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secre")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secre")
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).performClick()

    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsNotEnabled()
    assertNull(submitted)
  }

  @Test
  fun passwordOfExactlyMinLength_isAccepted() {
    setContent()

    type(CreateAccountScreenTags.USERNAME_FIELD, "ada_lovelace")
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret")

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.PASSWORD_TOO_SHORT_MESSAGE)
        .assertDoesNotExist()
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsEnabled()
  }

  private fun fillValidFormExceptUsername(username: String) {
    type(CreateAccountScreenTags.USERNAME_FIELD, username)
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret")
  }

  @Test
  fun usernameWithSlash_showsMessageAndDisablesCreateButton() {
    setContent()

    fillValidFormExceptUsername("ada/lovelace")
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).performClick()

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.USERNAME_INVALID_MESSAGE)
        .assertTextEquals("Username cannot contain /")
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsNotEnabled()
    assertNull(submitted)
  }

  @Test
  fun reservedFirestoreIds_showNotAllowedMessage() {
    setContent()
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret")

    listOf(".", "..", " .. ", "__name__", "____").forEach { username ->
      composeTestRule.onNodeWithTag(CreateAccountScreenTags.USERNAME_FIELD).performTextClearance()
      type(CreateAccountScreenTags.USERNAME_FIELD, username)

      composeTestRule
          .onNodeWithTag(CreateAccountScreenTags.USERNAME_INVALID_MESSAGE)
          .assertTextEquals("This username is not allowed")
      composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsNotEnabled()
    }
  }

  @Test
  fun usernamesCloseToReservedIds_areAccepted() {
    setContent()
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret")

    listOf("ada.lovelace", "...", "__ada", "ada__", "___").forEach { username ->
      composeTestRule.onNodeWithTag(CreateAccountScreenTags.USERNAME_FIELD).performTextClearance()
      type(CreateAccountScreenTags.USERNAME_FIELD, username)

      composeTestRule
          .onNodeWithTag(CreateAccountScreenTags.USERNAME_INVALID_MESSAGE)
          .assertDoesNotExist()
      composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsEnabled()
    }
  }

  @Test
  fun removingSlash_hidesMessageAndEnablesCreateButton() {
    setContent()
    fillValidFormExceptUsername("ada/")
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.USERNAME_INVALID_MESSAGE).assertExists()

    composeTestRule.onNodeWithTag(CreateAccountScreenTags.USERNAME_FIELD).performTextClearance()
    type(CreateAccountScreenTags.USERNAME_FIELD, "ada")

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.USERNAME_INVALID_MESSAGE)
        .assertDoesNotExist()
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsEnabled()
  }

  @Test
  fun confirmPasswordOnly_showsMismatchMessage() {
    setContent()

    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret")

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.PASSWORD_MISMATCH_MESSAGE)
        .assertIsDisplayed()
  }

  @Test
  fun passwordOnly_doesNotShowMismatchMessageUntilConfirmIsTyped() {
    setContent()

    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret")

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.PASSWORD_MISMATCH_MESSAGE)
        .assertDoesNotExist()
  }

  @Test
  fun changingPasswordAfterConfirm_reintroducesMismatch() {
    setContent()
    type(CreateAccountScreenTags.USERNAME_FIELD, "ada_lovelace")
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret")
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsEnabled()

    composeTestRule.onNodeWithTag(CreateAccountScreenTags.PASSWORD_FIELD).performTextClearance()
    type(CreateAccountScreenTags.PASSWORD_FIELD, "other")

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.PASSWORD_MISMATCH_MESSAGE)
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun errorAndMismatchMessages_canBeShownTogether() {
    setContent(uiState = AuthUiState(errorMessage = "Email already exists"))

    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "other")

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.PASSWORD_MISMATCH_MESSAGE)
        .assertTextEquals("Passwords do not match")
    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.ERROR_MESSAGE)
        .assertTextEquals("Email already exists")
  }

  @Test
  fun cancelButton_staysEnabledWhileLoading() {
    setContent(uiState = AuthUiState(isLoading = true))

    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CANCEL_BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.BACK_BUTTON).assertIsEnabled()
  }
}
