// Portions of this code were generated with the help of Claude Code.
package com.android.polybazaar.profile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.auth.model.User
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val profile =
      User(
          uid = "uid-1",
          email = "james.richard@epfl.ch",
          username = "james",
          bio = "Weekend maker",
      )

  private fun setContent(
      profile: User? = this.profile,
      isLoading: Boolean = false,
      errorMessage: String? = null,
      onEditProfile: () -> Unit = {},
      onOpenSettings: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      ProfileContent(
          profile = profile,
          isLoading = isLoading,
          errorMessage = errorMessage,
          onEditProfile = onEditProfile,
          onOpenSettings = onOpenSettings,
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  @Test
  fun displaysTheProfile() {
    setContent()

    node(ProfileScreenTags.NAME).assertTextEquals("james")
    node(ProfileScreenTags.BIO).assertTextEquals("Weekend maker")
    node(ProfileComponentTags.AVATAR).assertIsDisplayed()
    node(ProfileScreenTags.SETTINGS_BUTTON).assertIsDisplayed()
    node(ProfileScreenTags.RECENT_ACTIVITY).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("History is empty!").assertExists()
    node(ProfileScreenTags.EDIT_PROFILE_BUTTON).performScrollTo().assertIsEnabled()
    node(ProfileScreenTags.LOADING_INDICATOR).assertDoesNotExist()
    node(ProfileScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun blankBio_isNotShown() {
    setContent(profile = profile.copy(bio = "  "))

    node(ProfileScreenTags.BIO).assertDoesNotExist()
  }

  @Test
  fun editProfileAndSettingsCallTheirActions() {
    var edits = 0
    var settings = 0
    setContent(onEditProfile = { edits++ }, onOpenSettings = { settings++ })

    node(ProfileScreenTags.EDIT_PROFILE_BUTTON).performScrollTo().performClick()
    node(ProfileScreenTags.SETTINGS_BUTTON).performScrollTo().performClick()

    assertEquals(1, edits)
    assertEquals(1, settings)
  }

  @Test
  fun whileLoading_showsTheIndicatorAndDisablesEditing() {
    setContent(profile = null, isLoading = true)

    node(ProfileScreenTags.LOADING_INDICATOR).assertIsDisplayed()
    node(ProfileScreenTags.NAME).assertDoesNotExist()
    node(ProfileScreenTags.EDIT_PROFILE_BUTTON).performScrollTo().assertIsNotEnabled()
  }

  @Test
  fun reloadingAnAlreadyShownProfile_keepsShowingIt() {
    setContent(isLoading = true)

    node(ProfileScreenTags.NAME).assertTextEquals("james")
    node(ProfileScreenTags.LOADING_INDICATOR).assertDoesNotExist()
  }

  @Test
  fun loadFailure_showsTheErrorAndDisablesEditing() {
    setContent(profile = null, errorMessage = "Unable to load profile")

    node(ProfileScreenTags.ERROR_MESSAGE).assertTextEquals("Unable to load profile")
    node(ProfileScreenTags.EDIT_PROFILE_BUTTON).performScrollTo().assertIsNotEnabled()
  }
}
