// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.settings.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var backs = 0
  private var signOuts = 0

  private fun setContent() {
    composeTestRule.setContent { SettingsContent(onBack = { backs++ }, onSignOut = { signOuts++ }) }
  }

  @Test
  fun displaysSignOutWithoutDialog() {
    setContent()

    composeTestRule.onNodeWithTag(SettingsScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_DIALOG).assertDoesNotExist()
  }

  @Test
  fun backButton_callsOnBack() {
    setContent()

    composeTestRule.onNodeWithTag(SettingsScreenTags.BACK_BUTTON).performClick()

    assertEquals(1, backs)
  }

  @Test
  fun signOut_asksForConfirmationBeforeSigningOut() {
    setContent()

    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_BUTTON).performClick()

    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_DIALOG).assertIsDisplayed()
    assertEquals(0, signOuts)
  }

  @Test
  fun confirmingSignOut_callsOnSignOutAndClosesDialog() {
    setContent()

    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_CONFIRM).performClick()

    assertEquals(1, signOuts)
    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_DIALOG).assertDoesNotExist()
  }

  @Test
  fun cancellingSignOut_closesDialogWithoutSigningOut() {
    setContent()

    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_CANCEL).performClick()

    assertEquals(0, signOuts)
    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_DIALOG).assertDoesNotExist()
  }
}
