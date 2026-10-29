// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.auth.ui.AuthBackButton
import com.android.polybazaar.auth.ui.AuthCard
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.auth.ui.AuthTitleGroup

object SettingsScreenTags {
  const val SCREEN = "settings_screen"
  const val BACK_BUTTON = "settings_back_button"
  const val SIGN_OUT_BUTTON = "settings_sign_out_button"
  const val SIGN_OUT_DIALOG = "settings_sign_out_dialog"
  const val SIGN_OUT_CONFIRM = "settings_sign_out_confirm"
  const val SIGN_OUT_CANCEL = "settings_sign_out_cancel"
}

/**
 * Settings of the signed-in user, opened from the profile. For now it only lets them sign out,
 * after confirming.
 *
 * @param onBack called when the user taps the back arrow.
 * @param onSignOut called once the user has confirmed they want to sign out.
 */
@Composable
fun SettingsContent(onBack: () -> Unit, onSignOut: () -> Unit) {
  var confirmingSignOut by rememberSaveable { mutableStateOf(false) }

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .verticalScroll(rememberScrollState())
              .testTag(SettingsScreenTags.SCREEN)
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      AuthBackButton(onClick = onBack, modifier = Modifier.testTag(SettingsScreenTags.BACK_BUTTON))
      AuthTitleGroup(title = "Settings")
    }
    Spacer(modifier = Modifier.height(7.dp))
    AuthCard {
      Text(
          text = "Account",
          color = AuthColors.TextPrimary,
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
      )
      SettingsRow(
          text = "Sign out",
          onClick = { confirmingSignOut = true },
          tag = SettingsScreenTags.SIGN_OUT_BUTTON,
      )
    }
  }

  if (confirmingSignOut) {
    SignOutDialog(
        onConfirm = {
          confirmingSignOut = false
          onSignOut()
        },
        onDismiss = { confirmingSignOut = false },
    )
  }
}

/** A full-width row of the settings list, in the danger color since its only use is signing out. */
@Composable
private fun SettingsRow(text: String, onClick: () -> Unit, tag: String) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .heightIn(min = 48.dp)
              .clip(RoundedCornerShape(12.dp))
              .clickable(role = Role.Button, onClick = onClick)
              .testTag(tag),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text = text,
        color = AuthColors.Danger,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
private fun SignOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
      onDismissRequest = onDismiss,
      modifier = Modifier.testTag(SettingsScreenTags.SIGN_OUT_DIALOG),
      containerColor = AuthColors.CardBackground,
      title = { Text(text = "Sign out of PolyBazaar?", color = AuthColors.TextPrimary) },
      text = {
        Text(
            text = "You will need your email and password to sign back in.",
            color = AuthColors.TextSecondary,
        )
      },
      confirmButton = {
        TextButton(
            onClick = onConfirm,
            modifier = Modifier.testTag(SettingsScreenTags.SIGN_OUT_CONFIRM),
        ) {
          Text(text = "Sign out", color = AuthColors.Danger)
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(SettingsScreenTags.SIGN_OUT_CANCEL),
        ) {
          Text(text = "Cancel", color = AuthColors.TextSecondary)
        }
      },
  )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 924)
@Composable
private fun SettingsContentPreview() {
  SettingsContent(onBack = {}, onSignOut = {})
}
