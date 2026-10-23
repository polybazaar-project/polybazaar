// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.error.ui

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.error.model.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppErrorMessagesTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun everyError_hasANonBlankMessage() {
    AppError.entries.forEach { error ->
      assertTrue("$error has a blank message", context.getString(error.messageRes).isNotBlank())
    }
  }

  @Test
  fun everyError_hasItsOwnMessage() {
    val messages = AppError.entries.map { context.getString(it.messageRes) }

    assertEquals(messages.size, messages.toSet().size)
  }

  @Test
  fun message_showsTheLocalizedText() {
    composeTestRule.setContent { Text(AppError.NETWORK.message()) }

    composeTestRule.onNodeWithText(context.getString(R.string.error_network)).assertExists()
  }
}
