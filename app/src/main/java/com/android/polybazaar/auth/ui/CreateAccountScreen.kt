// Portions of this code were generated with the help of Claude Code.
package com.android.polybazaar.auth.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.viewmodel.AuthUiState
import com.android.polybazaar.auth.viewmodel.AuthViewModel

object CreateAccountScreenTags {
  const val SCREEN = "create_account_screen"
  const val BACK_BUTTON = "create_account_back_button"
  const val USERNAME_FIELD = "create_account_username_field"
  const val USERNAME_INVALID_MESSAGE = "create_account_username_invalid_message"
  const val EMAIL_FIELD = "create_account_email_field"
  const val PASSWORD_FIELD = "create_account_password_field"
  const val CONFIRM_PASSWORD_FIELD = "create_account_confirm_password_field"
  const val PASSWORD_TOO_SHORT_MESSAGE = "create_account_password_too_short_message"
  const val PASSWORD_MISMATCH_MESSAGE = "create_account_password_mismatch_message"
  const val ERROR_MESSAGE = "create_account_error_message"
  const val CANCEL_BUTTON = "create_account_cancel_button"
  const val CREATE_BUTTON = "create_account_create_button"
}

/** Firebase Auth rejects passwords shorter than this. */
const val MIN_PASSWORD_LENGTH = 6

private val RESERVED_FIRESTORE_ID = Regex("__.*__")

/**
 * Returns why [username] cannot be used, or null if it is valid. The trimmed username becomes a
 * Firestore document ID, so it must follow Firestore's ID rules.
 */
private fun usernameError(username: String): String? {
  val id = username.trim()
  return when {
    id.contains('/') -> "Username cannot contain /"
    id == "." || id == ".." || RESERVED_FIRESTORE_ID.matches(id) -> "This username is not allowed"
    else -> null
  }
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
  val usernameError = usernameError(username)
  val passwordTooShort = password.isNotEmpty() && password.length < MIN_PASSWORD_LENGTH
  val passwordsMatch = password == confirmPassword
  val canSubmit =
      !uiState.isLoading &&
          username.isNotBlank() &&
          usernameError == null &&
          email.isNotBlank() &&
          password.isNotEmpty() &&
          !passwordTooShort &&
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
            onValueChange = { username = it },
            placeholder = "Enter your username",
            tag = CreateAccountScreenTags.USERNAME_FIELD,
        )
        usernameError?.let {
          Text(
              text = it,
              color = AuthColors.Danger,
              fontSize = 12.sp,
              modifier = Modifier.testTag(CreateAccountScreenTags.USERNAME_INVALID_MESSAGE),
          )
        }
        LabeledField(
            label = "Email",
            value = email,
            onValueChange = { email = it },
            placeholder = "Enter your email",
            tag = CreateAccountScreenTags.EMAIL_FIELD,
            keyboardType = KeyboardType.Email,
        )
        LabeledField(
            label = "Password",
            value = password,
            onValueChange = { password = it },
            placeholder = "Create a password",
            tag = CreateAccountScreenTags.PASSWORD_FIELD,
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
        )
        if (passwordTooShort) {
          Text(
              text = "Password must be at least $MIN_PASSWORD_LENGTH characters",
              color = AuthColors.Danger,
              fontSize = 12.sp,
              modifier = Modifier.testTag(CreateAccountScreenTags.PASSWORD_TOO_SHORT_MESSAGE),
          )
        }
        LabeledField(
            label = "Confirm password",
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            placeholder = "Repeat your password",
            tag = CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD,
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
        )
        if (confirmPassword.isNotEmpty() && !passwordsMatch) {
          Text(
              text = "Passwords do not match",
              color = AuthColors.Danger,
              fontSize = 12.sp,
              modifier = Modifier.testTag(CreateAccountScreenTags.PASSWORD_MISMATCH_MESSAGE),
          )
        }
        uiState.errorMessage?.let {
          Text(
              text = it,
              color = AuthColors.Danger,
              fontSize = 12.sp,
              modifier = Modifier.testTag(CreateAccountScreenTags.ERROR_MESSAGE),
          )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
              onClick = onBack,
              modifier =
                  Modifier.weight(1f).fillMaxSize().testTag(CreateAccountScreenTags.CANCEL_BUTTON),
              shape = RoundedCornerShape(20.dp),
              border = BorderStroke(1.dp, AuthColors.Outline),
              colors =
                  ButtonDefaults.outlinedButtonColors(containerColor = AuthColors.CardBackground),
          ) {
            Text(
                text = "Cancel",
                color = AuthColors.Danger,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
          }
          AuthPrimaryButton(
              text = "Create account",
              onClick = { onCreateAccount(username.trim(), email.trim(), password) },
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
private fun Header(onBack: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 18.dp),
      horizontalArrangement = Arrangement.spacedBy(15.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(
        onClick = onBack,
        modifier =
            Modifier.size(38.dp)
                .background(AuthColors.Background, CircleShape)
                .border(1.dp, AuthColors.Outline, CircleShape)
                .testTag(CreateAccountScreenTags.BACK_BUTTON),
    ) {
      Icon(
          painter = painterResource(R.drawable.ic_back_arrow),
          contentDescription = "Back",
          tint = Color.Unspecified,
          modifier = Modifier.size(19.dp),
      )
    }
    AuthTitleGroup(title = "Create account")
  }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 924)
@Composable
private fun CreateAccountContentPreview() {
  CreateAccountContent(uiState = AuthUiState(), onCreateAccount = { _, _, _ -> }, onBack = {})
}
