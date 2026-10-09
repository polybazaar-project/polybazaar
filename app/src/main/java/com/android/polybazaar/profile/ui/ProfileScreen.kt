// Portions of this code were generated with the help of Claude Code.
package com.android.polybazaar.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.auth.ui.AuthRoundIconButton
import com.android.polybazaar.auth.ui.AuthTitleGroup

object ProfileScreenTags {
  const val SCREEN = "profile_screen"
  const val SETTINGS_BUTTON = "profile_settings_button"
  const val LOADING_INDICATOR = "profile_loading_indicator"
  const val NAME = "profile_name"
  const val BIO = "profile_bio"
  const val ERROR_MESSAGE = "profile_error_message"
  const val RECENT_ACTIVITY = "profile_recent_activity"
  const val EDIT_PROFILE_BUTTON = "profile_edit_profile_button"
}

/**
 * Profile screen of the signed-in user.
 *
 * @param profile the profile to show, or null while it loads or if it could not be loaded.
 * @param isLoading whether the profile is loading.
 * @param errorMessage the error to show, if any.
 * @param onEditProfile called when the user taps "Edit profile".
 * @param onOpenSettings called when the user taps the settings button.
 */
@Composable
fun ProfileContent(
    profile: User?,
    isLoading: Boolean,
    errorMessage: String?,
    onEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
) {
  BoxWithConstraints(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .testTag(ProfileScreenTags.SCREEN)
  ) {
    // At least screen-high, so "Edit profile" sits at the bottom as in the Figma frame, and the
    // page scrolls instead of overlapping on short screens.
    Column(
        modifier =
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
    ) {
      Header(onOpenSettings = onOpenSettings)
      Column(
          modifier =
              Modifier.weight(1f).padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 14.dp),
          verticalArrangement = Arrangement.SpaceBetween,
      ) {
        IdentityCard(profile = profile, isLoading = isLoading, errorMessage = errorMessage)
        RecentActivityCard(modifier = Modifier.padding(vertical = 24.dp))
        EditProfileButton(onClick = onEditProfile, enabled = profile != null)
      }
    }
  }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 18.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    AuthTitleGroup(title = "My profile")
    AuthRoundIconButton(
        iconRes = R.drawable.ic_settings,
        contentDescription = "Settings",
        onClick = onOpenSettings,
        modifier = Modifier.testTag(ProfileScreenTags.SETTINGS_BUTTON),
    )
  }
}

/** The avatar, name and bio, or a loading indicator or an error while there is no profile. */
@Composable
private fun IdentityCard(profile: User?, isLoading: Boolean, errorMessage: String?) {
  val shape = RoundedCornerShape(20.dp)
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .shadow(
                  elevation = 4.dp,
                  shape = shape,
                  ambientColor = AuthColors.TextPrimary.copy(alpha = 0.04f),
                  spotColor = AuthColors.TextPrimary.copy(alpha = 0.04f),
              )
              .background(AuthColors.CardBackground, shape)
              .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      ProfileAvatar()
      if (isLoading && profile == null) {
        CircularProgressIndicator(
            color = AuthColors.Accent,
            modifier = Modifier.size(24.dp).testTag(ProfileScreenTags.LOADING_INDICATOR),
        )
      } else {
        Text(
            text = profile?.username.orEmpty(),
            color = AuthColors.TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(ProfileScreenTags.NAME),
        )
      }
    }
    profile
        ?.bio
        ?.takeIf { it.isNotBlank() }
        ?.let { bio ->
          Text(
              text = bio,
              color = AuthColors.TextSecondary,
              fontSize = 12.sp,
              lineHeight = 1.35.em,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.testTag(ProfileScreenTags.BIO),
          )
        }
    errorMessage?.let {
      Text(
          text = it,
          color = AuthColors.Danger,
          fontSize = 12.sp,
          modifier = Modifier.testTag(ProfileScreenTags.ERROR_MESSAGE),
      )
    }
  }
}

/** Rentals and loans are not built yet, so the history is always empty for now. */
@Composable
private fun RecentActivityCard(modifier: Modifier = Modifier) {
  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .background(AuthColors.CardBackground, RoundedCornerShape(20.dp))
              .border(1.dp, AuthColors.Outline, RoundedCornerShape(20.dp))
              .padding(16.dp)
              .testTag(ProfileScreenTags.RECENT_ACTIVITY),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
        text = "Recent activity",
        color = AuthColors.TextPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Box(modifier = Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
      Text(
          text = "History is empty!",
          color = AuthColors.TextPrimary,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@Composable
private fun EditProfileButton(onClick: () -> Unit, enabled: Boolean) {
  Button(
      onClick = onClick,
      enabled = enabled,
      modifier =
          Modifier.fillMaxWidth().height(82.dp).testTag(ProfileScreenTags.EDIT_PROFILE_BUTTON),
      shape = RoundedCornerShape(20.dp),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = AuthColors.Accent,
              contentColor = Color.White,
              disabledContainerColor = AuthColors.Accent.copy(alpha = 0.5f),
              disabledContentColor = Color.White,
          ),
  ) {
    Icon(
        painter = painterResource(R.drawable.ic_user_round_pen),
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = Modifier.size(19.dp),
    )
    Spacer(modifier = Modifier.size(8.dp))
    Text(text = "Edit profile", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
  }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 844)
@Composable
private fun ProfileContentPreview() {
  ProfileContent(
      profile =
          User(
              uid = "preview",
              email = "james.richard@epfl.ch",
              username = "James Richard",
              bio = "Weekend maker, careful borrower, and always happy to share tools.",
          ),
      isLoading = false,
      errorMessage = null,
      onEditProfile = {},
      onOpenSettings = {},
  )
}
