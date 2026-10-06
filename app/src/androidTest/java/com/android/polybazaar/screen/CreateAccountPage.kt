// Portions of this code were generated with the help of Claude Code.
package com.android.polybazaar.screen

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import com.android.polybazaar.auth.ui.CreateAccountScreenTags
import io.github.kakaocup.compose.node.element.ComposeScreen
import io.github.kakaocup.compose.node.element.KNode

/** Kaspresso page object for the Create account screen. */
class CreateAccountPage(semanticsProvider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<CreateAccountPage>(
        semanticsProvider = semanticsProvider,
        viewBuilderAction = { hasTestTag(CreateAccountScreenTags.SCREEN) },
    ) {

  val backButton: KNode = child { hasTestTag(CreateAccountScreenTags.BACK_BUTTON) }
  val usernameField: KNode = child { hasTestTag(CreateAccountScreenTags.USERNAME_FIELD) }
  val emailField: KNode = child { hasTestTag(CreateAccountScreenTags.EMAIL_FIELD) }
  val passwordField: KNode = child { hasTestTag(CreateAccountScreenTags.PASSWORD_FIELD) }
  val confirmPasswordField: KNode = child {
    hasTestTag(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD)
  }
  val mismatchMessage: KNode = child {
    hasTestTag(CreateAccountScreenTags.PASSWORD_MISMATCH_MESSAGE)
  }
  val errorMessage: KNode = child { hasTestTag(CreateAccountScreenTags.ERROR_MESSAGE) }
  val cancelButton: KNode = child { hasTestTag(CreateAccountScreenTags.CANCEL_BUTTON) }
  val createButton: KNode = child { hasTestTag(CreateAccountScreenTags.CREATE_BUTTON) }
}
