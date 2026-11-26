// Portions of this code were generated with the help of Claude Code.
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.profile.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.error.model.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private val profile =
      User(
          uid = "uid-1",
          email = "james.richard@epfl.ch",
          username = "james",
          bio = "Weekend maker",
      )

  private fun setContent(
      profile: User? = this.profile,
      photo: ByteArray? = null,
      isLoading: Boolean = false,
      error: AppError? = null,
      onEditProfile: () -> Unit = {},
      onOpenSettings: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      ProfileContent(
          profile = profile,
          photo = photo,
          isLoading = isLoading,
          error = error,
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
    node(ProfileScreenTags.EDIT_PROFILE_BUTTON).performScrollTo().assertIsEnabled()
    node(ProfileScreenTags.LOADING_INDICATOR).assertDoesNotExist()
    node(ProfileScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun toolbox_fillsTheHeightDownToMyBookings() {
    // A phone-sized page, so the Toolbox gets whatever the fixed parts leave.
    composeTestRule.setContent {
      Box(modifier = Modifier.requiredSize(width = 412.dp, height = 844.dp)) {
        ProfileContent(
            profile = profile,
            isLoading = false,
            error = null,
            onEditProfile = {},
            onOpenSettings = {},
            toolbox = { Box(modifier = Modifier.fillMaxSize().testTag(TOOLBOX_TAG)) },
        )
      }
    }

    val toolbox = node(TOOLBOX_TAG).getUnclippedBoundsInRoot()
    val bookings = node(ProfileScreenTags.MY_BOOKINGS_BUTTON).getUnclippedBoundsInRoot()
    // Nothing sits between the Toolbox and "My bookings" any more. The Material3 TextButton's 48dp
    // minimum touch target leaves a few dp above its 40dp bounds, so allow a small gap.
    val gap = bookings.top - toolbox.bottom
    assertTrue("Gap between Toolbox and My bookings is $gap", gap >= 0.dp && gap <= 8.dp)
    // With the activity card it was about 350dp high; the freed height now goes to the Toolbox.
    val height = toolbox.bottom - toolbox.top
    assertTrue("Toolbox is only $height high", height > 400.dp)
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE) // Decoding and drawing the photo need real bitmaps.
  fun savedPhoto_largerThanTheDecodeSize_isDrawnInTheAvatar() {
    setContent(photo = redPhoto(sizePx = 600))

    composeTestRule.waitForAvatar(isRed = true)
  }

  @Test
  fun shortScreen_scrollsToTheFullSizeEditButton() {
    // 320dp is shorter than the header, the identity card and the 82dp button together, so the page
    // has to scroll. A squeezed layout would shrink or hide the button instead.
    composeTestRule.setContent {
      Box(modifier = Modifier.size(width = 360.dp, height = 320.dp)) {
        ProfileContent(
            profile = profile,
            isLoading = false,
            error = null,
            onEditProfile = {},
            onOpenSettings = {},
        )
      }
    }

    node(ProfileScreenTags.EDIT_PROFILE_BUTTON)
        .performScrollTo()
        .assertIsDisplayed()
        .assertHeightIsEqualTo(82.dp)
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
  fun loadFailure_showsTheErrorTextAndDisablesEditing() {
    setContent(profile = null, error = AppError.NETWORK)

    node(ProfileScreenTags.ERROR_MESSAGE)
        .assertTextEquals(context.getString(R.string.error_network))
    node(ProfileScreenTags.EDIT_PROFILE_BUTTON).performScrollTo().assertIsNotEnabled()
  }

  @Test
  fun notFound_showsTheNotFoundText() {
    setContent(profile = null, error = AppError.NOT_FOUND)

    node(ProfileScreenTags.ERROR_MESSAGE)
        .assertTextEquals(context.getString(R.string.error_not_found))
  }

  private companion object {
    const val TOOLBOX_TAG = "toolbox"
  }
}
