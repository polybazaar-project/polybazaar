// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.screens

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import com.android.polybazaar.profile.ui.EditProfileScreenTags
import com.android.polybazaar.profile.ui.ProfileScreenTags
import com.android.polybazaar.settings.ui.SettingsScreenTags
import com.kaspersky.components.composesupport.core.KNode
import io.github.kakaocup.compose.node.element.ComposeScreen

class ProfilePage(semanticsProvider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<ProfilePage>(
        semanticsProvider,
        viewBuilderAction = { hasTestTag(ProfileScreenTags.SCREEN) },
    ) {
  val name = KNode(semanticsProvider) { hasTestTag(ProfileScreenTags.NAME) }
  val bio = KNode(semanticsProvider) { hasTestTag(ProfileScreenTags.BIO) }
  val editProfileButton =
      KNode(semanticsProvider) { hasTestTag(ProfileScreenTags.EDIT_PROFILE_BUTTON) }
  val settingsButton = KNode(semanticsProvider) { hasTestTag(ProfileScreenTags.SETTINGS_BUTTON) }
}

class EditProfilePage(semanticsProvider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<EditProfilePage>(
        semanticsProvider,
        viewBuilderAction = { hasTestTag(EditProfileScreenTags.SCREEN) },
    ) {
  val bioField = KNode(semanticsProvider) { hasTestTag(EditProfileScreenTags.BIO_FIELD) }
  val saveButton = KNode(semanticsProvider) { hasTestTag(EditProfileScreenTags.SAVE_BUTTON) }
}

class SettingsPage(semanticsProvider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<SettingsPage>(
        semanticsProvider,
        viewBuilderAction = { hasTestTag(SettingsScreenTags.SCREEN) },
    ) {
  val signOutButton = KNode(semanticsProvider) { hasTestTag(SettingsScreenTags.SIGN_OUT_BUTTON) }
  val signOutConfirm = KNode(semanticsProvider) { hasTestTag(SettingsScreenTags.SIGN_OUT_CONFIRM) }
}
