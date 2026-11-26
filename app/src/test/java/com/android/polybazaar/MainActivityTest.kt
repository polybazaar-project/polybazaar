// Co-authored-by: Claude Code (Claude Opus 5.5)
// Co-authored-by: Copilot (Auto)
package com.android.polybazaar

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.auth.ui.SignInScreenTags
import com.android.polybazaar.booking.model.ConfirmationAction
import com.android.polybazaar.booking.model.ConfirmationAttempt
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

  @get:Rule val composeTestRule = createEmptyComposeRule()

  @Before
  fun initFirebase() {
    // Robolectric does not always run Firebase's init provider, and MainActivity builds the real
    // Firebase repositories.
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
  }

  @Test
  fun launch_withoutSignedInUser_opensSignIn() {
    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()
    }
  }

  @Test
  fun journalSurvivesActivityRecreation() {
    val attempt = ConfirmationAttempt("renter", "booking", ConfirmationAction.CONFIRM_HANDOVER, "t")
    val application = ApplicationProvider.getApplicationContext<PolyBazaarApplication>()
    val journal = application.confirmationJournal
    try {
      runBlocking { journal.put(attempt) }
      ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        scenario.recreate()
        scenario.onActivity { activity ->
          assertSame(journal, (activity.application as PolyBazaarApplication).confirmationJournal)
          assertTrue(attempt.token in journal.activeTokens.value)
        }
      }
    } finally {
      runBlocking { journal.remove(attempt.token) }
      journal.finish(attempt.token)
    }
  }

  @Test
  fun launch_drawsEdgeToEdgeAndResizesForTheKeyboard() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val window = activity.window
        // Compose only receives the keyboard's insets, and shrinks the screen above it, when the
        // window resizes for the keyboard instead of panning.
        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST,
        )
        // Set by enableEdgeToEdge on API 30+, which the theme does not touch.
        assertEquals(
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
            window.attributes.layoutInDisplayCutoutMode,
        )
        assertTrue(
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
        )
      }
    }
  }

  @Test
  @Config(qualifiers = "+night")
  fun launch_inDarkMode_keepsDarkSystemBarIcons() {
    // The screens stay light in dark mode, so light icons would vanish against them.
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        assertTrue(controller.isAppearanceLightStatusBars)
        assertTrue(controller.isAppearanceLightNavigationBars)
      }
    }
  }
}
