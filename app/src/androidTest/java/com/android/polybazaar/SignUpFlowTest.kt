// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.screens.BrowsePage
import com.android.polybazaar.screens.CreateAccountPage
import com.android.polybazaar.screens.SignInPage
import com.android.polybazaar.screens.VerifyEmailPage
import io.github.kakaocup.compose.node.element.ComposeScreen.Companion.onComposeScreen
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignUpFlowTest : PolyBazaarTestCase() {

  @Test
  fun newUser_signsUpVerifiesEmailAndLandsOnBrowse() = run {
    step("Launch the app signed out") {
      launchApp()
      onComposeScreen<SignInPage>(composeTestRule) { assertIsDisplayed() }
    }
    step("Create an account") {
      onComposeScreen<SignInPage>(composeTestRule) { createAccountLink.performClick() }
      onComposeScreen<CreateAccountPage>(composeTestRule) {
        usernameField.performTextInput("ada_lovelace")
        emailField.performTextInput("ada@epfl.ch")
        passwordField.performTextInput("secret123")
        confirmPasswordField.performTextInput("secret123")
        createButton.performClick()
      }
    }
    step("Ask to verify the email address") {
      onComposeScreen<VerifyEmailPage>(composeTestRule) {
        assertIsDisplayed()
        emailText.assertTextContains("ada@epfl.ch")
      }
    }
    step("Verify the email and land on Browse") {
      backend.openVerificationLink("ada@epfl.ch")
      onComposeScreen<VerifyEmailPage>(composeTestRule) { checkButton.performClick() }
      onComposeScreen<BrowsePage>(composeTestRule) { assertIsDisplayed() }
    }
  }
}
