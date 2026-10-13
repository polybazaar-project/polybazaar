// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

object ProfilePlaceholderTags {
  const val SCREEN = "profile_placeholder_screen"
  const val USERNAME = "profile_placeholder_username"
}

/** Stands in for the profile destination until the real profile screen is merged. */
@Composable
internal fun ProfilePlaceholder(username: String) {
  Box(
      modifier = Modifier.fillMaxSize().testTag(ProfilePlaceholderTags.SCREEN),
      contentAlignment = Alignment.Center,
  ) {
    Text(text = username, modifier = Modifier.testTag(ProfilePlaceholderTags.USERNAME))
  }
}
