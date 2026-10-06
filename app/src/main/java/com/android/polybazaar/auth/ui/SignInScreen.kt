// Portions of this code were generated with the help of Claude Code.
package com.android.polybazaar.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.auth.viewmodel.AuthUiState
import com.android.polybazaar.auth.viewmodel.AuthViewModel

object SignInScreenTags {
  const val SCREEN = "sign_in_screen"
  const val EMAIL_FIELD = "sign_in_email_field"
  const val PASSWORD_FIELD = "sign_in_password_field"
  const val FORGOT_PASSWORD_LINK = "sign_in_forgot_password_link"
  const val ERROR_MESSAGE = "sign_in_error_message"
  const val SIGN_IN_BUTTON = "sign_in_sign_in_button"
  const val CREATE_ACCOUNT_LINK = "sign_in_create_account_link"
}

/**
 * Sign-in screen.
 *
 * @param onCreateAccount called when the user taps "Create account".
 * @param onForgotPassword called when the user taps "Forgot password?".
 * @param onSignedIn called once the user is signed in.
 */
@Composable
fun SignInScreen(
    authViewModel: AuthViewModel,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
    onSignedIn: () -> Unit,
) {
  val uiState by authViewModel.uiState.collectAsState()

  LaunchedEffect(uiState.user) { if (uiState.user != null) onSignedIn() }

  SignInContent(
      uiState = uiState,
      onSignIn = { email, password -> authViewModel.signIn(email, password) },
      onCreateAccount = onCreateAccount,
      onForgotPassword = onForgotPassword,
  )
}

@Composable
fun SignInContent(
    uiState: AuthUiState,
    onSignIn: (email: String, password: String) -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
) {
  var email by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  val canSubmit = !uiState.isLoading && email.isNotBlank() && password.isNotEmpty()

  Box(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .testTag(SignInScreenTags.SCREEN)
  ) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
      Row(
          modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 18.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        AuthTitleGroup(title = "Sign in")
      }
      Column(
          modifier =
              Modifier.fillMaxWidth()
                  .padding(horizontal = 18.dp, vertical = 10.dp)
                  .background(AuthColors.CardBackground, RoundedCornerShape(20.dp))
                  .border(1.dp, AuthColors.Outline, RoundedCornerShape(20.dp))
                  .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
            text = "Sign-in details",
            color = AuthColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        LabeledField(
            label = "Email",
            value = email,
            onValueChange = { email = it },
            placeholder = "Enter your email",
            tag = SignInScreenTags.EMAIL_FIELD,
            keyboardType = KeyboardType.Email,
        )
        LabeledField(
            label = "Password",
            value = password,
            onValueChange = { password = it },
            placeholder = "Enter your password",
            tag = SignInScreenTags.PASSWORD_FIELD,
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
          Text(
              text = "Forgot password?",
              color = AuthColors.Accent,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              modifier =
                  Modifier.clickable(onClick = onForgotPassword)
                      .testTag(SignInScreenTags.FORGOT_PASSWORD_LINK),
          )
        }
        uiState.errorMessage?.let {
          Text(
              text = it,
              color = AuthColors.Danger,
              fontSize = 12.sp,
              modifier = Modifier.testTag(SignInScreenTags.ERROR_MESSAGE),
          )
        }
        Button(
            onClick = { onSignIn(email.trim(), password) },
            enabled = canSubmit,
            modifier =
                Modifier.fillMaxWidth().height(48.dp).testTag(SignInScreenTags.SIGN_IN_BUTTON),
            shape = RoundedCornerShape(20.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AuthColors.Accent,
                    contentColor = Color.White,
                    disabledContainerColor = AuthColors.Accent.copy(alpha = 0.5f),
                    disabledContentColor = Color.White,
                ),
        ) {
          Text(text = "Sign in", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(text = "New here?", color = AuthColors.TextSecondary, fontSize = 12.sp)
          Text(
              text = "Create account",
              color = AuthColors.Accent,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              modifier =
                  Modifier.clickable(onClick = onCreateAccount)
                      .testTag(SignInScreenTags.CREATE_ACCOUNT_LINK),
          )
        }
      }
    }
    CommunityReassurance(
        modifier =
            Modifier.align(Alignment.BottomCenter)
                .padding(start = 34.dp, end = 34.dp, bottom = 105.dp)
    )
  }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 924)
@Composable
private fun SignInContentPreview() {
  SignInContent(
      uiState = AuthUiState(),
      onSignIn = { _, _ -> },
      onCreateAccount = {},
      onForgotPassword = {},
  )
}
