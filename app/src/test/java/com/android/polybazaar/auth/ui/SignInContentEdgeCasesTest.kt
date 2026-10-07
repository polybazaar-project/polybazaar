// Portions of this code were generated with the help of Claude Code.
package com.android.polybazaar.auth.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.auth.viewmodel.AuthUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [SignInContent] edge cases: static content, masking and blank input. */
@RunWith(AndroidJUnit4::class)
class SignInContentEdgeCasesTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var submitted: Pair<String, String>? = null
  private var createAccountCount = 0
  private var forgotPasswordCount = 0

  private fun setContent(uiState: AuthUiState = AuthUiState()) {
    composeTestRule.setContent {
      SignInContent(
          uiState = uiState,
          onSignIn = { email, password -> submitted = email to password },
          onCreateAccount = { createAccountCount++ },
          onForgotPassword = { forgotPasswordCount++ },
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
            "Sign-in details",
            "EMAIL",
            "PASSWORD",
            "Enter your email",
            "Enter your password",
            "Forgot password?",
            "New here?",
            "Create account",
            "Good tools. Great neighbours.",
        )
        .forEach { composeTestRule.onNodeWithText(it, useUnmergedTree = true).assertExists() }
    // "Sign in" is both the header title and the submit button label.
    composeTestRule.onAllNodesWithText("Sign in", useUnmergedTree = true).assertCountEquals(2)
  }

  @Test
  fun placeholder_disappearsOnceFieldHasText() {
    setContent()

    type(SignInScreenTags.EMAIL_FIELD, "ada@epfl.ch")

    composeTestRule.onNodeWithText("Enter your email").assertDoesNotExist()
    composeTestRule.onNodeWithText("Enter your password").assertExists()
  }

  @Test
  fun passwordField_isMaskedButEmailIsNot() {
    setContent()

    type(SignInScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(SignInScreenTags.PASSWORD_FIELD, "secret")

    assertEquals("ada@epfl.ch", editableText(SignInScreenTags.EMAIL_FIELD))
    // EditableText exposes the transformed (masked) text, never the raw password.
    assertEquals("•".repeat(6), editableText(SignInScreenTags.PASSWORD_FIELD))
  }

  @Test
  fun disabledSignInButton_doesNotSubmit() {
    setContent()

    type(SignInScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).performClick()

    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).assertIsNotEnabled()
    assertNull(submitted)
  }

  @Test
  fun loadingSignInButton_doesNotSubmit() {
    setContent(uiState = AuthUiState(isLoading = true))

    type(SignInScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(SignInScreenTags.PASSWORD_FIELD, "secret")
    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).performClick()

    assertNull(submitted)
  }

  @Test
  fun whitespaceOnlyPassword_isAcceptedAndSentUntrimmed() {
    setContent()

    type(SignInScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(SignInScreenTags.PASSWORD_FIELD, "   ")
    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).performClick()

    assertEquals("ada@epfl.ch" to "   ", submitted)
  }

  @Test
  fun links_stillWorkWhileLoading() {
    setContent(uiState = AuthUiState(isLoading = true))

    composeTestRule.onNodeWithTag(SignInScreenTags.CREATE_ACCOUNT_LINK).performClick()
    composeTestRule.onNodeWithTag(SignInScreenTags.FORGOT_PASSWORD_LINK).performClick()

    assertEquals(1, createAccountCount)
    assertEquals(1, forgotPasswordCount)
  }

  @Test
  fun links_areButtonsWithMinimumTouchTarget() {
    setContent()

    listOf(SignInScreenTags.FORGOT_PASSWORD_LINK, SignInScreenTags.CREATE_ACCOUNT_LINK).forEach {
      val link =
          composeTestRule
              .onNodeWithTag(it)
              .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
              .fetchSemanticsNode()
      // Material 3 widens the touch target, not the layout, so check the touch bounds.
      val touchBounds = link.touchBoundsInRoot
      val minTouchPx = 48 * link.layoutInfo.density.density
      assertTrue("$it touch target is $touchBounds", touchBounds.height >= minTouchPx)
      assertTrue("$it touch target is $touchBounds", touchBounds.width >= minTouchPx)
    }
  }

  @Test
  fun links_doNotSubmitTheForm() {
    setContent()

    type(SignInScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(SignInScreenTags.PASSWORD_FIELD, "secret")
    composeTestRule.onNodeWithTag(SignInScreenTags.FORGOT_PASSWORD_LINK).performClick()
    composeTestRule.onNodeWithTag(SignInScreenTags.CREATE_ACCOUNT_LINK).performClick()

    assertNull(submitted)
  }

  @Test
  fun eachSignInClick_submitsTheCurrentValues() {
    val submissions = mutableListOf<Pair<String, String>>()
    composeTestRule.setContent {
      SignInContent(
          uiState = AuthUiState(),
          onSignIn = { email, password -> submissions += email to password },
          onCreateAccount = {},
          onForgotPassword = {},
      )
    }

    type(SignInScreenTags.EMAIL_FIELD, "ada@epfl.ch")
    type(SignInScreenTags.PASSWORD_FIELD, "wrong")
    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).performClick()
    type(SignInScreenTags.PASSWORD_FIELD, "!")
    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).performClick()

    assertEquals(listOf("ada@epfl.ch" to "wrong", "ada@epfl.ch" to "wrong!"), submissions)
  }
}
