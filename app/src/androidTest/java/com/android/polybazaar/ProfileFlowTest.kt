// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.screens.BottomBarPage
import com.android.polybazaar.screens.EditProfilePage
import com.android.polybazaar.screens.ProfilePage
import com.android.polybazaar.screens.SettingsPage
import com.android.polybazaar.screens.SignInPage
import io.github.kakaocup.compose.node.element.ComposeScreen.Companion.onComposeScreen
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileFlowTest : PolyBazaarTestCase() {

  @Test
  fun signedInUser_editsBioThenSignsOut() = run {
    step("Open the profile") {
      signInAda()
      launchApp()
      onComposeScreen<BottomBarPage>(composeTestRule) { profileTab.performClick() }
      onComposeScreen<ProfilePage>(composeTestRule) { assertIsDisplayed() }
    }
    step("Edit the bio") {
      onComposeScreen<ProfilePage>(composeTestRule) { editProfileButton.performClick() }
      onComposeScreen<EditProfilePage>(composeTestRule) {
        bioField.performTextInput("Mathematician")
        saveButton.performClick()
      }
    }
    step("See the new bio on the profile") {
      onComposeScreen<ProfilePage>(composeTestRule) {
        assertIsDisplayed()
        bio.assertTextEquals("Mathematician")
      }
    }
    step("Sign out from the settings") {
      onComposeScreen<ProfilePage>(composeTestRule) { settingsButton.performClick() }
      onComposeScreen<SettingsPage>(composeTestRule) {
        signOutButton.performClick()
        signOutConfirm.performClick()
      }
      onComposeScreen<SignInPage>(composeTestRule) { assertIsDisplayed() }
    }
  }
}
