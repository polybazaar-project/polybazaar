// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.screens

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import com.android.polybazaar.auth.ui.CreateAccountScreenTags
import com.android.polybazaar.auth.ui.SignInScreenTags
import com.android.polybazaar.auth.ui.VerifyEmailScreenTags
import com.kaspersky.components.composesupport.core.KNode
import io.github.kakaocup.compose.node.element.ComposeScreen

class SignInPage(semanticsProvider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<SignInPage>(
        semanticsProvider,
        viewBuilderAction = { hasTestTag(SignInScreenTags.SCREEN) },
    ) {
  val createAccountLink =
      KNode(semanticsProvider) { hasTestTag(SignInScreenTags.CREATE_ACCOUNT_LINK) }
}

class CreateAccountPage(semanticsProvider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<CreateAccountPage>(
        semanticsProvider,
        viewBuilderAction = { hasTestTag(CreateAccountScreenTags.SCREEN) },
    ) {
  val usernameField =
      KNode(semanticsProvider) { hasTestTag(CreateAccountScreenTags.USERNAME_FIELD) }
  val emailField = KNode(semanticsProvider) { hasTestTag(CreateAccountScreenTags.EMAIL_FIELD) }
  val passwordField =
      KNode(semanticsProvider) { hasTestTag(CreateAccountScreenTags.PASSWORD_FIELD) }
  val confirmPasswordField =
      KNode(semanticsProvider) { hasTestTag(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD) }
  val createButton = KNode(semanticsProvider) { hasTestTag(CreateAccountScreenTags.CREATE_BUTTON) }
}

class VerifyEmailPage(semanticsProvider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<VerifyEmailPage>(
        semanticsProvider,
        viewBuilderAction = { hasTestTag(VerifyEmailScreenTags.SCREEN) },
    ) {
  val emailText = KNode(semanticsProvider) { hasTestTag(VerifyEmailScreenTags.EMAIL_TEXT) }
  val checkButton = KNode(semanticsProvider) { hasTestTag(VerifyEmailScreenTags.CHECK_BUTTON) }
}
