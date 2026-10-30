// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.auth.ui.SignInScreenTags
import com.google.firebase.FirebaseApp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
}
