// Co-authored-by: Copilot (Auto)
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.ui.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutesTest {

  @Test
  fun receivedRequests_encodesTheListingId() {
    assertEquals("received_requests/a%2Fb%3Fc%20d", Routes.receivedRequests("a/b?c d"))
  }
}
