// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.AuthRules
import com.android.polybazaar.auth.viewmodel.ResetPasswordError
import com.android.polybazaar.auth.viewmodel.ResetPasswordUiState
import com.android.polybazaar.auth.viewmodel.ResetPasswordViewModel

object ResetPasswordScreenTags {
  const val SCREEN = "reset_password_screen"
  const val BACK_BUTTON = "reset_password_back_button"
  const val NEW_PASSWORD_FIELD = "reset_password_new_password_field"
  const val CONFIRM_PASSWORD_FIELD = "reset_password_confirm_password_field"
  const val ERROR_MESSAGE = "reset_password_error_message"
  const val RESET_BUTTON = "reset_password_reset_button"
  const val BACK_TO_SIGN_IN_LINK = "reset_password_back_to_sign_in_link"
}

/** What the user can do on the reset password screen. */
class ResetPasswordActions(
    val onNewPasswordChange: (String) -> Unit = {},
    val onConfirmPasswordChange: (String) -> Unit = {},
    val onResetPassword: () -> Unit = {},
    val onBackToSignIn: () -> Unit = {},
)

/**
 * Reset password screen, opened from the link in the password reset email.
 *
 * @param onBackToSignIn called when the user taps the back arrow or "Back to sign in".
 * @param onPasswordReset called once the new password is saved, to go back to sign in.
 */
@Composable
fun ResetPasswordScreen(
    resetPasswordViewModel: ResetPasswordViewModel,
    onBackToSignIn: () -> Unit,
    onPasswordReset: () -> Unit,
) {
  val uiState by resetPasswordViewModel.uiState.collectAsState()

  // Keyed on the flag so the callback runs once per success, not on every recomposition.
  LaunchedEffect(uiState.passwordReset) { if (uiState.passwordReset) onPasswordReset() }

  ResetPasswordContent(
      uiState = uiState,
      actions =
          ResetPasswordActions(
              // Capped like sign-up, so a huge paste never reaches the state.
              onNewPasswordChange = {
                resetPasswordViewModel.onNewPasswordChange(capPasswordInput(it))
              },
              onConfirmPasswordChange = {
                resetPasswordViewModel.onConfirmPasswordChange(capPasswordInput(it))
              },
              onResetPassword = resetPasswordViewModel::resetPassword,
              onBackToSignIn = onBackToSignIn,
          ),
  )
}

/**
 * Stateless content of the reset password screen, following the "Reset password S2" Figma frame.
 *
 * The button stays enabled with empty fields: the ViewModel checks them and the error tells the
 * user what is missing, which a greyed-out button would not.
 */
@Composable
fun ResetPasswordContent(uiState: ResetPasswordUiState, actions: ResetPasswordActions) {
  BoxWithConstraints(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .testTag(ResetPasswordScreenTags.SCREEN)
  ) {
    // heightIn(min = maxHeight) lets the weighted Spacer push the tagline to the bottom on tall
    // screens, while short screens (or an open keyboard) scroll instead of overlapping the card.
    Column(
        modifier =
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
    ) {
      Row(
          modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 18.dp),
          horizontalArrangement = Arrangement.spacedBy(15.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        AuthBackButton(
            onClick = actions.onBackToSignIn,
            modifier = Modifier.testTag(ResetPasswordScreenTags.BACK_BUTTON),
        )
        AuthTitleGroup(title = stringResource(R.string.reset_password_title))
      }
      AuthCard {
        Text(
            text = stringResource(R.string.reset_password_heading),
            color = AuthColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        GuidanceText(stringResource(R.string.reset_password_guidance))
        // 6dp between the field and its rules, as in the Figma, instead of the card's 12dp gap.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          PasswordField(
              label = stringResource(R.string.reset_password_new_password_label),
              value = uiState.newPassword,
              onValueChange = actions.onNewPasswordChange,
              placeholder = stringResource(R.string.reset_password_new_password_placeholder),
              tag = ResetPasswordScreenTags.NEW_PASSWORD_FIELD,
          )
          GuidanceText(
              pluralStringResource(
                  R.plurals.reset_password_rules,
                  AuthRules.MIN_PASSWORD_LENGTH,
                  AuthRules.MIN_PASSWORD_LENGTH,
              )
          )
        }
        PasswordField(
            label = stringResource(R.string.reset_password_confirm_label),
            value = uiState.confirmPassword,
            onValueChange = actions.onConfirmPasswordChange,
            placeholder = stringResource(R.string.reset_password_confirm_placeholder),
            tag = ResetPasswordScreenTags.CONFIRM_PASSWORD_FIELD,
        )
        uiState.error?.let {
          Text(
              text = stringResource(resetPasswordErrorText(it)),
              color = AuthColors.Danger,
              fontSize = 12.sp,
              modifier = Modifier.testTag(ResetPasswordScreenTags.ERROR_MESSAGE),
          )
        }
        AuthPrimaryButton(
            text =
                stringResource(
                    if (uiState.isLoading) R.string.reset_password_resetting_button
                    else R.string.reset_password_button
                ),
            onClick = actions.onResetPassword,
            enabled = !uiState.isLoading,
            modifier =
                Modifier.fillMaxWidth().height(48.dp).testTag(ResetPasswordScreenTags.RESET_BUTTON),
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          AuthLinkButton(
              text = stringResource(R.string.reset_password_back_to_sign_in),
              onClick = actions.onBackToSignIn,
              tag = ResetPasswordScreenTags.BACK_TO_SIGN_IN_LINK,
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

/** Cuts [input] to [AuthRules.MAX_PASSWORD_INPUT_LENGTH], as sign-up does. */
private fun capPasswordInput(input: String): String =
    AuthRules.limitTo(input, AuthRules.MAX_PASSWORD_INPUT_LENGTH)

/** A masked password field with the password keyboard. */
@Composable
private fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    tag: String,
) {
  LabeledField(
      label = label,
      value = value,
      onValueChange = onValueChange,
      placeholder = placeholder,
      tag = tag,
      keyboardType = KeyboardType.Password,
      visualTransformation = PasswordVisualTransformation(),
  )
}

/** Small grey explanatory text, used for the guidance and the password rules. */
@Composable
private fun GuidanceText(text: String) {
  Text(text = text, color = AuthColors.TextSecondary, fontSize = 12.sp, lineHeight = 1.4.em)
}

/**
 * Message shown for each [ResetPasswordError]. Errors that every screen can hit (network, rate
 * limit, unknown, weak password) reuse the shared messages of errors.xml so the wording stays the
 * same across the app.
 */
@StringRes
internal fun resetPasswordErrorText(error: ResetPasswordError): Int =
    when (error) {
      ResetPasswordError.EMPTY_PASSWORD -> R.string.reset_password_error_empty
      ResetPasswordError.PASSWORD_TOO_LONG -> R.string.reset_password_error_too_long
      ResetPasswordError.WEAK_PASSWORD -> R.string.error_weak_password
      ResetPasswordError.PASSWORDS_DO_NOT_MATCH -> R.string.reset_password_error_mismatch
      ResetPasswordError.INVALID_LINK -> R.string.reset_password_error_invalid_link
      ResetPasswordError.NETWORK -> R.string.error_network
      ResetPasswordError.TOO_MANY_REQUESTS -> R.string.error_too_many_requests
      ResetPasswordError.UNKNOWN -> R.string.error_unknown
    }

@Preview(showBackground = true, widthDp = 412, heightDp = 924)
@Composable
private fun ResetPasswordContentPreview() {
  ResetPasswordContent(uiState = ResetPasswordUiState(), actions = ResetPasswordActions())
}
