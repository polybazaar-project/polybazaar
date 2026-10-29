// Co-authored-by: Claude Opus 5.5
package com.android.polybazaar.auth.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.auth.viewmodel.VerifyEmailUiState
import com.android.polybazaar.auth.viewmodel.VerifyEmailViewModel

object VerifyEmailScreenTags {
  const val SCREEN = "verify_email_screen"
  const val EMAIL_TEXT = "verify_email_email_text"
  const val ERROR_MESSAGE = "verify_email_error_message"
  const val CHECK_BUTTON = "verify_email_check_button"
  const val RESEND_BUTTON = "verify_email_resend_button"
  const val BACK_BUTTON = "verify_email_back_button"
  const val USE_ANOTHER_ACCOUNT_LINK = "verify_email_use_another_account_link"
}

/**
 * "Check your inbox" screen shown to a signed-in user whose email is not verified yet.
 *
 * @param email the address the verification link was sent to.
 * @param onVerified called once the server confirms the email is verified.
 * @param onUseAnotherAccount called from the back arrow, the system back gesture and the "Use a
 *   different account" link. The user is signed in, so leaving this screen means signing out.
 */
@Composable
fun VerifyEmailScreen(
    verifyEmailViewModel: VerifyEmailViewModel,
    email: String,
    onVerified: () -> Unit,
    onUseAnotherAccount: () -> Unit,
) {
  val uiState by verifyEmailViewModel.uiState.collectAsState()

  LaunchedEffect(uiState.isVerified) { if (uiState.isVerified) onVerified() }
  BackHandler(onBack = onUseAnotherAccount)

  VerifyEmailContent(
      uiState = uiState,
      email = email,
      onCheckVerified = verifyEmailViewModel::refresh,
      onResend = verifyEmailViewModel::resend,
      onUseAnotherAccount = onUseAnotherAccount,
  )
}

@Composable
fun VerifyEmailContent(
    uiState: VerifyEmailUiState,
    email: String,
    onCheckVerified: () -> Unit,
    onResend: () -> Unit,
    onUseAnotherAccount: () -> Unit,
) {
  val canResend = !uiState.isLoading && !uiState.isVerified && uiState.resendCooldownSeconds == 0

  BoxWithConstraints(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .testTag(VerifyEmailScreenTags.SCREEN)
  ) {
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
            onClick = onUseAnotherAccount,
            modifier = Modifier.testTag(VerifyEmailScreenTags.BACK_BUTTON),
        )
        AuthTitleGroup(title = "Verify your email")
      }
      AuthCard {
        Text(
            text = "Check your inbox",
            color = AuthColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "We sent a verification link to",
            color = AuthColors.TextSecondary,
            fontSize = 14.sp,
        )
        Text(
            text = email,
            color = AuthColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag(VerifyEmailScreenTags.EMAIL_TEXT),
        )
        Text(
            text = "Open the link, then come back and tap \"I've verified\".",
            color = AuthColors.TextSecondary,
            fontSize = 14.sp,
        )
        uiState.errorMessage?.let {
          Text(
              text = it,
              color = AuthColors.Danger,
              fontSize = 12.sp,
              modifier = Modifier.testTag(VerifyEmailScreenTags.ERROR_MESSAGE),
          )
        }
        AuthPrimaryButton(
            text = "I've verified",
            onClick = onCheckVerified,
            enabled = !uiState.isLoading,
            modifier =
                Modifier.fillMaxWidth().height(48.dp).testTag(VerifyEmailScreenTags.CHECK_BUTTON),
        )
        OutlinedButton(
            onClick = onResend,
            enabled = canResend,
            modifier =
                Modifier.fillMaxWidth().height(48.dp).testTag(VerifyEmailScreenTags.RESEND_BUTTON),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, AuthColors.Outline),
            colors =
                ButtonDefaults.outlinedButtonColors(containerColor = AuthColors.CardBackground),
        ) {
          Text(
              text =
                  if (uiState.resendCooldownSeconds > 0)
                      "Resend email in ${uiState.resendCooldownSeconds}s"
                  else "Resend email",
              color = if (canResend) AuthColors.Accent else AuthColors.TextSecondary,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
          )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(text = "Wrong email?", color = AuthColors.TextSecondary, fontSize = 12.sp)
          AuthLinkButton(
              text = "Use a different account",
              onClick = onUseAnotherAccount,
              tag = VerifyEmailScreenTags.USE_ANOTHER_ACCOUNT_LINK,
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

@Preview(showBackground = true, widthDp = 412, heightDp = 924)
@Composable
private fun VerifyEmailContentPreview() {
  VerifyEmailContent(
      uiState = VerifyEmailUiState(resendCooldownSeconds = 42),
      email = "ada.lovelace@epfl.ch",
      onCheckVerified = {},
      onResend = {},
      onUseAnotherAccount = {},
  )
}
