// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.auth.model.AuthRules
import com.android.polybazaar.auth.model.EPFL_EMAIL_DOMAIN
import com.android.polybazaar.auth.model.isEpflEmail
import com.android.polybazaar.auth.viewmodel.AuthUiState
import com.android.polybazaar.auth.viewmodel.AuthViewModel

object CreateAccountScreenTags {
  const val SCREEN = "create_account_screen"
  const val BACK_BUTTON = "create_account_back_button"
  const val USERNAME_FIELD = "create_account_username_field"
  const val USERNAME_INVALID_MESSAGE = "create_account_username_invalid_message"
  const val EMAIL_FIELD = "create_account_email_field"
  const val EMAIL_TOO_LONG_MESSAGE = "create_account_email_too_long_message"
  const val EMAIL_NOT_EPFL_MESSAGE = "create_account_email_not_epfl_message"
  const val PASSWORD_FIELD = "create_account_password_field"
  const val CONFIRM_PASSWORD_FIELD = "create_account_confirm_password_field"
  const val PASSWORD_TOO_SHORT_MESSAGE = "create_account_password_too_short_message"
  const val PASSWORD_TOO_LONG_MESSAGE = "create_account_password_too_long_message"
  const val PASSWORD_MISMATCH_MESSAGE = "create_account_password_mismatch_message"
  const val ERROR_MESSAGE = "create_account_error_message"
  const val CANCEL_BUTTON = "create_account_cancel_button"
  const val CREATE_BUTTON = "create_account_create_button"
}

/**
 * Sign-up screen.
 *
 * @param onBack called when the user taps the back arrow or Cancel.
 * @param onSignedIn called once the account is created and the user is signed in.
 */
@Composable
fun CreateAccountScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
) {
  val uiState by authViewModel.uiState.collectAsState()

  LaunchedEffect(uiState.user) { if (uiState.user != null) onSignedIn() }

  CreateAccountContent(
      uiState = uiState,
      onCreateAccount = { username, email, password ->
        authViewModel.signUp(email, password, username)
      },
      onBack = onBack,
  )
}

@Composable
fun CreateAccountContent(
    uiState: AuthUiState,
    onCreateAccount: (username: String, email: String, password: String) -> Unit,
    onBack: () -> Unit,
) {
  var username by rememberSaveable { mutableStateOf("") }
  var email by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var confirmPassword by rememberSaveable { mutableStateOf("") }
  val usernameError = AuthRules.usernameError(username)
  val emailLengthError = AuthRules.emailLengthError(email)
  // Skipped while the email is too long, so one field never shows two errors.
  val emailNotEpfl = email.isNotBlank() && emailLengthError == null && !isEpflEmail(email)
  val passwordTooShort = password.isNotEmpty() && password.length < AuthRules.MIN_PASSWORD_LENGTH
  val passwordLengthError = AuthRules.passwordLengthError(password)
  val passwordsMatch = password == confirmPassword
  val canSubmit =
      !uiState.isLoading &&
          username.isNotBlank() &&
          usernameError == null &&
          email.isNotBlank() &&
          !emailNotEpfl &&
          emailLengthError == null &&
          password.isNotEmpty() &&
          !passwordTooShort &&
          passwordLengthError == null &&
          passwordsMatch

  BoxWithConstraints(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .testTag(CreateAccountScreenTags.SCREEN)
  ) {
    // At least screen-high, so the reassurance sits at the bottom on tall screens and scrolls
    // below the card on short ones (e.g. with the keyboard open) instead of covering it.
    Column(
        modifier =
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
    ) {
      Header(onBack = onBack)
      AuthCard {
        Text(
            text = "Registration details",
            color = AuthColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        LabeledField(
            label = "Username",
            value = username,
            onValueChange = {
              username = AuthRules.limitInput(it, AuthRules.MAX_TEXT_INPUT_LENGTH)
            },
            placeholder = "Enter your username",
            tag = CreateAccountScreenTags.USERNAME_FIELD,
        )
        usernameError?.let { FieldError(it, CreateAccountScreenTags.USERNAME_INVALID_MESSAGE) }
        LabeledField(
            label = "Email",
            value = email,
            onValueChange = { email = AuthRules.limitInput(it, AuthRules.MAX_TEXT_INPUT_LENGTH) },
            placeholder = "Enter your email",
            tag = CreateAccountScreenTags.EMAIL_FIELD,
            keyboardType = KeyboardType.Email,
        )
        emailLengthError?.let { FieldError(it, CreateAccountScreenTags.EMAIL_TOO_LONG_MESSAGE) }
        if (emailNotEpfl) {
          FieldError(
              "Use your EPFL email (ending in @$EPFL_EMAIL_DOMAIN)",
              CreateAccountScreenTags.EMAIL_NOT_EPFL_MESSAGE,
          )
        }
        LabeledField(
            label = "Password",
            value = password,
            onValueChange = {
              password = AuthRules.limitTo(it, AuthRules.MAX_PASSWORD_INPUT_LENGTH)
            },
            placeholder = "Create a password",
            tag = CreateAccountScreenTags.PASSWORD_FIELD,
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
        )
        if (passwordTooShort) {
          FieldError(
              "Password must be at least ${AuthRules.MIN_PASSWORD_LENGTH} characters",
              CreateAccountScreenTags.PASSWORD_TOO_SHORT_MESSAGE,
          )
        }
        passwordLengthError?.let {
          FieldError(it, CreateAccountScreenTags.PASSWORD_TOO_LONG_MESSAGE)
        }
        LabeledField(
            label = "Confirm password",
            value = confirmPassword,
            onValueChange = {
              confirmPassword = AuthRules.limitTo(it, AuthRules.MAX_PASSWORD_INPUT_LENGTH)
            },
            placeholder = "Repeat your password",
            tag = CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD,
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
        )
        if (confirmPassword.isNotEmpty() && !passwordsMatch) {
          FieldError("Passwords do not match", CreateAccountScreenTags.PASSWORD_MISMATCH_MESSAGE)
        }
        uiState.errorMessage?.let { FieldError(it, CreateAccountScreenTags.ERROR_MESSAGE) }
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          AuthSecondaryButton(
              text = "Cancel",
              onClick = onBack,
              modifier =
                  Modifier.weight(1f).fillMaxSize().testTag(CreateAccountScreenTags.CANCEL_BUTTON),
          )
          AuthPrimaryButton(
              text = "Create account",
              onClick = {
                onCreateAccount(AuthRules.normalizeUsername(username), email.trim(), password)
              },
              enabled = canSubmit,
              modifier =
                  Modifier.weight(1f).fillMaxSize().testTag(CreateAccountScreenTags.CREATE_BUTTON),
          )
        }
      }
      Spacer(modifier = Modifier.weight(1f))
      CommunityReassurance(
          modifier =
              Modifier.align(Alignment.CenterHorizontally)
                  .padding(start = 34.dp, end = 34.dp, top = 16.dp, bottom = 105.dp)
      )
    }
  }
}

@Composable
private fun FieldError(text: String, tag: String) {
  Text(
      text = text,
      color = AuthColors.Danger,
      fontSize = 12.sp,
      modifier = Modifier.testTag(tag),
  )
}

@Composable
private fun Header(onBack: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 18.dp),
      horizontalArrangement = Arrangement.spacedBy(15.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    AuthBackButton(
        onClick = onBack,
        modifier = Modifier.testTag(CreateAccountScreenTags.BACK_BUTTON),
    )
    AuthTitleGroup(title = "Create account")
  }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 924)
@Composable
private fun CreateAccountContentPreview() {
  CreateAccountContent(uiState = AuthUiState(), onCreateAccount = { _, _, _ -> }, onBack = {})
}
