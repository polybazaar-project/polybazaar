// Co-authored-by: Claude Code (Claude Sonnet 5.5, Claude Opus 5.5)
package com.android.polybazaar.auth.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.auth.model.AuthRules
import com.android.polybazaar.auth.viewmodel.AuthUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
  fun mixedCaseUsername_isSubmittedTrimmedAndLowercased() {
    setContent()

    type(CreateAccountScreenTags.USERNAME_FIELD, "  Ada_Lovelace ")
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret12")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret12")
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).performClick()

    assertEquals("ada_lovelace", submitted?.first)
  }

  @Test
  fun whitespaceOnlyPassword_isAcceptedAndSentUntrimmed() {
    setContent()

    type(CreateAccountScreenTags.USERNAME_FIELD, "ada_lovelace")
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "  secret12  ")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "  secret12  ")
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).performClick()

    assertEquals(Triple("ada_lovelace", "ada@epfl.ch", "  secret12  "), submitted)
  }

  @Test
  fun passwordOneCharTooShort_keepsCreateButtonDisabled() {
    setContent()

    type(CreateAccountScreenTags.USERNAME_FIELD, "ada_lovelace")
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret1")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret1")
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).performClick()

    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsNotEnabled()
    assertNull(submitted)
  }

  @Test
  fun passwordOfExactlyMinLength_isAccepted() {
    setContent()

    type(CreateAccountScreenTags.USERNAME_FIELD, "ada_lovelace")
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret12")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret12")

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.PASSWORD_TOO_SHORT_MESSAGE)
        .assertDoesNotExist()
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsEnabled()
  }

  @Test
  fun nonEpflEmail_showsMessageAndDisablesCreateButton() {
    setContent()

    type(CreateAccountScreenTags.USERNAME_FIELD, "ada_lovelace")
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@gmail.com")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret")
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).performClick()

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.EMAIL_NOT_EPFL_MESSAGE)
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsNotEnabled()
    assertNull(submitted)
  }

  @Test
  fun epflEmail_showsNoEmailMessage() {
    setContent()

    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.EMAIL_NOT_EPFL_MESSAGE)
        .assertDoesNotExist()
  }

  private fun fillValidFormExceptUsername(username: String) {
    type(CreateAccountScreenTags.USERNAME_FIELD, username)
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret12")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret12")
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
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret12")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret12")

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
  fun usernameBelowMinimum_showsMessageAndDisablesCreateButton() {
    setContent()
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret")

    listOf("a", "ab", " ab ").forEach { username ->
      composeTestRule.onNodeWithTag(CreateAccountScreenTags.USERNAME_FIELD).performTextClearance()
      type(CreateAccountScreenTags.USERNAME_FIELD, username)

      composeTestRule
          .onNodeWithTag(CreateAccountScreenTags.USERNAME_INVALID_MESSAGE)
          .assertTextEquals("Username must be at least ${AuthRules.MIN_USERNAME_LENGTH} characters")
      composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsNotEnabled()
    }
  }

  @Test
  fun emptyForm_showsNoUsernameMessage() {
    setContent()

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.USERNAME_INVALID_MESSAGE)
        .assertDoesNotExist()
  }

  @Test
  fun usernamesAtMinAndMaxLength_areAccepted() {
    setContent()
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret12")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret12")

    listOf(AuthRules.MIN_USERNAME_LENGTH, AuthRules.MAX_USERNAME_LENGTH).forEach { length ->
      composeTestRule.onNodeWithTag(CreateAccountScreenTags.USERNAME_FIELD).performTextClearance()
      type(CreateAccountScreenTags.USERNAME_FIELD, "a".repeat(length))

      composeTestRule
          .onNodeWithTag(CreateAccountScreenTags.USERNAME_INVALID_MESSAGE)
          .assertDoesNotExist()
      composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsEnabled()
    }
  }

  @Test
  fun overlongUsername_isKeptAndShowsMessageAndDisablesCreateButton() {
    setContent()
    val username = "u".repeat(AuthRules.MAX_USERNAME_LENGTH + 5)
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret")

    type(CreateAccountScreenTags.USERNAME_FIELD, username)

    assertEquals(username, editableText(CreateAccountScreenTags.USERNAME_FIELD))
    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.USERNAME_INVALID_MESSAGE)
        .assertTextEquals(AuthRules.usernameError(username)!!)
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun typingIntoFullUsername_keepsTheCharacterAndShowsMessage() {
    setContent()
    type(CreateAccountScreenTags.USERNAME_FIELD, "a".repeat(AuthRules.MAX_USERNAME_LENGTH))
    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.USERNAME_FIELD)
        .performTextInputSelection(TextRange(5))

    type(CreateAccountScreenTags.USERNAME_FIELD, "b")

    assertEquals(
        "a".repeat(5) + "b" + "a".repeat(AuthRules.MAX_USERNAME_LENGTH - 5),
        editableText(CreateAccountScreenTags.USERNAME_FIELD),
    )
    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.USERNAME_INVALID_MESSAGE)
        .assertTextEquals("Username must be at most ${AuthRules.MAX_USERNAME_LENGTH} characters")
  }

  @Test
  fun overlongEmail_showsOnlyTooLongMessageAndDisablesCreateButton() {
    setContent()
    val email = "e".repeat(AuthRules.MAX_EMAIL_LENGTH + 1 - "@epfl.ch".length) + "@epfl.ch"
    type(CreateAccountScreenTags.USERNAME_FIELD, "ada")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret")

    type(CreateAccountScreenTags.EMAIL_FIELD, email)

    assertEquals(email, editableText(CreateAccountScreenTags.EMAIL_FIELD))
    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.EMAIL_TOO_LONG_MESSAGE)
        .assertTextEquals(AuthRules.emailLengthError(email)!!)
    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.EMAIL_NOT_EPFL_MESSAGE)
        .assertDoesNotExist()
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun usernameAndEmailInput_areHeldAtHardCap() {
    setContent()

    type(CreateAccountScreenTags.USERNAME_FIELD, "u".repeat(2000))
    type(CreateAccountScreenTags.EMAIL_FIELD, "e".repeat(2000))

    assertEquals(
        AuthRules.MAX_TEXT_INPUT_LENGTH,
        editableText(CreateAccountScreenTags.USERNAME_FIELD).length,
    )
    assertEquals(
        AuthRules.MAX_TEXT_INPUT_LENGTH,
        editableText(CreateAccountScreenTags.EMAIL_FIELD).length,
    )
  }

  @Test
  fun overlongPassword_isNotTruncatedAndShowsMessage() {
    setContent()
    val tooLong = "p".repeat(AuthRules.MAX_PASSWORD_LENGTH + 1)
    type(CreateAccountScreenTags.USERNAME_FIELD, "ada")
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(CreateAccountScreenTags.PASSWORD_FIELD, tooLong)
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, tooLong)

    assertEquals(tooLong.length, editableText(CreateAccountScreenTags.PASSWORD_FIELD).length)
    assertEquals(
        tooLong.length,
        editableText(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD).length,
    )
    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.PASSWORD_TOO_LONG_MESSAGE)
        .assertTextEquals(AuthRules.passwordLengthError(tooLong)!!)
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun emailAndPasswordAtMaxLength_areAccepted() {
    setContent()
    val email = "e".repeat(AuthRules.MAX_EMAIL_LENGTH - "@epfl.ch".length) + "@epfl.ch"
    // A digit at the end, so the password also meets the letter and digit rule.
    val password = "p".repeat(AuthRules.MAX_PASSWORD_LENGTH - 1) + "1"
    type(CreateAccountScreenTags.USERNAME_FIELD, "ada")
    type(CreateAccountScreenTags.EMAIL_FIELD, email)
    type(CreateAccountScreenTags.PASSWORD_FIELD, password)
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, password)

    assertEquals(email, editableText(CreateAccountScreenTags.EMAIL_FIELD))
    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.PASSWORD_TOO_LONG_MESSAGE)
        .assertDoesNotExist()
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsEnabled()
  }

  @Test
  fun emojiAtHardCapBoundary_isNotSplit() {
    setContent()

    type(
        CreateAccountScreenTags.USERNAME_FIELD,
        "a".repeat(AuthRules.MAX_TEXT_INPUT_LENGTH - 1) + "\uD83D\uDE00",
    )

    val text = editableText(CreateAccountScreenTags.USERNAME_FIELD)
    assertEquals(AuthRules.MAX_TEXT_INPUT_LENGTH - 1, text.length)
    assertFalse(text.last().isHighSurrogate())
  }

  @Test
  fun leadingWhitespaceInUsername_isDroppedSoNoRealCharactersAreLost() {
    setContent()
    val name = "a".repeat(AuthRules.MAX_USERNAME_LENGTH)

    type(CreateAccountScreenTags.USERNAME_FIELD, "  $name")

    assertEquals(name, editableText(CreateAccountScreenTags.USERNAME_FIELD))
  }

  @Test
  fun leadingWhitespaceInEmail_isDroppedSoNoRealCharactersAreLost() {
    setContent()
    val email = "e".repeat(AuthRules.MAX_EMAIL_LENGTH - "@epfl.ch".length) + "@epfl.ch"

    type(CreateAccountScreenTags.EMAIL_FIELD, "  $email")

    assertEquals(email, editableText(CreateAccountScreenTags.EMAIL_FIELD))
  }

  @Test
  fun usernameThatLengthensWhenLowercased_isValidatedOnNormalizedForm() {
    setContent()
    type(CreateAccountScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret")

    type(CreateAccountScreenTags.USERNAME_FIELD, "\u0130".repeat(20))

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.USERNAME_INVALID_MESSAGE)
        .assertTextEquals("Username must be at most ${AuthRules.MAX_USERNAME_LENGTH} characters")
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun passwordInput_isHardCappedAndStillShowsTooLongMessage() {
    setContent()

    type(CreateAccountScreenTags.PASSWORD_FIELD, "p".repeat(5000))

    assertEquals(
        AuthRules.MAX_PASSWORD_INPUT_LENGTH,
        editableText(CreateAccountScreenTags.PASSWORD_FIELD).length,
    )
    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.PASSWORD_TOO_LONG_MESSAGE)
        .assertIsDisplayed()
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
    type(CreateAccountScreenTags.PASSWORD_FIELD, "secret12")
    type(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD, "secret12")
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
  fun communityReassurance_staysBelowCardOnSmallScreen() {
    composeTestRule.setContent {
      Box(Modifier.size(width = 360.dp, height = 480.dp)) {
        CreateAccountContent(uiState = AuthUiState(), onCreateAccount = { _, _, _ -> }, onBack = {})
      }
    }

    val cardBottom =
        composeTestRule
            .onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON)
            .getUnclippedBoundsInRoot()
            .bottom
    val reassuranceTop =
        composeTestRule
            .onNodeWithText("Good tools. Great neighbours.")
            .getUnclippedBoundsInRoot()
            .top

    assertTrue(
        "Reassurance (top=$reassuranceTop) overlaps the card (bottom=$cardBottom)",
        reassuranceTop >= cardBottom,
    )
  }

  @Test
  fun cancelButton_staysEnabledWhileLoading() {
    setContent(uiState = AuthUiState(isLoading = true))

    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CANCEL_BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.BACK_BUTTON).assertIsEnabled()
  }
}
