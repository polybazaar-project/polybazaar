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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.PasswordResetException
import com.android.polybazaar.auth.viewmodel.ForgotPasswordUiState
import com.android.polybazaar.auth.viewmodel.ForgotPasswordViewModel

object ForgotPasswordScreenTags {
  const val SCREEN = "forgot_password_screen"
  const val BACK_BUTTON = "forgot_password_back_button"
  const val EMAIL_FIELD = "forgot_password_email_field"
  const val SENT_MESSAGE = "forgot_password_sent_message"
  const val ERROR_MESSAGE = "forgot_password_error_message"
  const val SEND_BUTTON = "forgot_password_send_button"
  const val BACK_TO_SIGN_IN_LINK = "forgot_password_back_to_sign_in_link"
}

/**
 * Forgot password screen: the user enters their email and gets a link to reset their password.
 *
 * @param onBack called when the user taps the back arrow or "Back to sign in".
 */
@Composable
fun ForgotPasswordScreen(forgotPasswordViewModel: ForgotPasswordViewModel, onBack: () -> Unit) {
  val uiState by forgotPasswordViewModel.uiState.collectAsState()

  ForgotPasswordContent(
      uiState = uiState,
      onEmailChange = forgotPasswordViewModel::onEmailChange,
      onSendResetLink = forgotPasswordViewModel::sendResetLink,
      onBack = onBack,
  )
}

/**
 * Stateless content of the forgot password screen, following the "Forgot password S2" Figma frame.
 *
 * After a successful request, the card shows a neutral confirmation (the same whether or not the
 * address has an account, #13) and the button becomes "Resend link".
 */
@Composable
fun ForgotPasswordContent(
    uiState: ForgotPasswordUiState,
    onEmailChange: (String) -> Unit,
    onSendResetLink: () -> Unit,
    onBack: () -> Unit,
) {
  // A blank email is also rejected by the ViewModel; disabling the button matches sign-in.
  val canSend = !uiState.isLoading && uiState.email.isNotBlank()

  BoxWithConstraints(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .testTag(ForgotPasswordScreenTags.SCREEN)
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
            onClick = onBack,
            modifier = Modifier.testTag(ForgotPasswordScreenTags.BACK_BUTTON),
        )
        AuthTitleGroup(title = stringResource(R.string.forgot_password_title))
      }
      AuthCard {
        Text(
            text = stringResource(R.string.forgot_password_heading),
            color = AuthColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.forgot_password_guidance),
            color = AuthColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 1.4.em,
        )
        LabeledField(
            label = stringResource(R.string.forgot_password_email_label),
            value = uiState.email,
            onValueChange = onEmailChange,
            placeholder = stringResource(R.string.forgot_password_email_placeholder),
            tag = ForgotPasswordScreenTags.EMAIL_FIELD,
            keyboardType = KeyboardType.Email,
        )
        ResultMessage(uiState)
        AuthPrimaryButton(
            text = stringResource(sendButtonText(uiState)),
            onClick = onSendResetLink,
            enabled = canSend,
            modifier =
                Modifier.fillMaxWidth().height(48.dp).testTag(ForgotPasswordScreenTags.SEND_BUTTON),
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          AuthLinkButton(
              text = stringResource(R.string.forgot_password_back_to_sign_in),
              onClick = onBack,
              tag = ForgotPasswordScreenTags.BACK_TO_SIGN_IN_LINK,
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

/** The confirmation after a sent link, or the error of the last request. Nothing otherwise. */
@Composable
private fun ResultMessage(uiState: ForgotPasswordUiState) {
  val error = uiState.error
  when {
    error != null ->
        Text(
            text = stringResource(passwordResetErrorText(error)),
            color = AuthColors.Danger,
            fontSize = 12.sp,
            modifier = Modifier.testTag(ForgotPasswordScreenTags.ERROR_MESSAGE),
        )
    uiState.emailSent ->
        Text(
            // The ViewModel sends the trimmed email, so show that one rather than what was typed.
            text = stringResource(R.string.forgot_password_sent_message, uiState.email.trim()),
            color = AuthColors.TextPrimary,
            fontSize = 12.sp,
            lineHeight = 1.4.em,
            modifier = Modifier.testTag(ForgotPasswordScreenTags.SENT_MESSAGE),
        )
  }
}

/** Label of the send button: "Sending…" while in flight, "Resend link" once a link was sent. */
@StringRes
internal fun sendButtonText(uiState: ForgotPasswordUiState): Int =
    when {
      uiState.isLoading -> R.string.forgot_password_sending_button
      uiState.emailSent -> R.string.forgot_password_resend_button
      else -> R.string.forgot_password_send_button
    }

/** Message shown for each reason a reset request can fail, never Firebase's own text. */
@StringRes
internal fun passwordResetErrorText(reason: PasswordResetException.Reason): Int =
    when (reason) {
      PasswordResetException.Reason.INVALID_EMAIL -> R.string.forgot_password_error_invalid_email
      PasswordResetException.Reason.NETWORK -> R.string.forgot_password_error_network
      PasswordResetException.Reason.TOO_MANY_REQUESTS ->
          R.string.forgot_password_error_too_many_requests
      PasswordResetException.Reason.UNKNOWN -> R.string.forgot_password_error_unknown
    }

@Preview(showBackground = true, widthDp = 412, heightDp = 924)
@Composable
private fun ForgotPasswordContentPreview() {
  ForgotPasswordContent(
      uiState = ForgotPasswordUiState(),
      onEmailChange = {},
      onSendResetLink = {},
      onBack = {},
  )
}
