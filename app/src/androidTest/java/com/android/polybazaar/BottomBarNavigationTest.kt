// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.screens.BottomBarPage
import com.android.polybazaar.screens.BrowsePage
import com.android.polybazaar.screens.CreateListingPage
import com.android.polybazaar.screens.ProfilePage
import io.github.kakaocup.compose.node.element.ComposeScreen.Companion.onComposeScreen
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomBarNavigationTest : PolyBazaarTestCase() {

  @Test
  fun signedInUser_movesBetweenBrowseCreateListingAndProfile() = run {
    step("Launch the app on Browse") {
      signInAda()
      launchApp()
      onComposeScreen<BrowsePage>(composeTestRule) { assertIsDisplayed() }
      onComposeScreen<BottomBarPage>(composeTestRule) { assertIsDisplayed() }
    }
    step("Open Create listing") {
      onComposeScreen<BottomBarPage>(composeTestRule) { createListingTab.performClick() }
      onComposeScreen<CreateListingPage>(composeTestRule) { assertIsDisplayed() }
    }
    step("Open Profile") {
      onComposeScreen<BottomBarPage>(composeTestRule) { profileTab.performClick() }
      onComposeScreen<ProfilePage>(composeTestRule) {
        assertIsDisplayed()
        name.assertTextEquals("ada")
      }
    }
    step("Go back to Browse") {
      onComposeScreen<BottomBarPage>(composeTestRule) { browseTab.performClick() }
      onComposeScreen<BrowsePage>(composeTestRule) { assertIsDisplayed() }
    }
  }
}
