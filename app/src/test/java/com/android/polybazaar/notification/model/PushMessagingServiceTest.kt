// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.notification.model

import com.google.firebase.auth.FirebaseUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class PushMessagingServiceTest {
  private val stored = mutableListOf<Pair<String, String>>()
  private val store: (String, String) -> Unit = { uid, token -> stored += uid to token }

  @Test
  fun newToken_withoutASignedInUser_isNotStored() {
    storeNewToken(user = null, token = "token-1", store = store)

    assertTrue(stored.isEmpty())
  }

  @Test
  fun newToken_ofAnUnverifiedUser_isNotStored() {
    storeNewToken(user = user(verified = false), token = "token-1", store = store)

    assertTrue(stored.isEmpty())
  }

  @Test
  fun newToken_ofAVerifiedUser_isStoredForThem() {
    storeNewToken(user = user(verified = true), token = "token-1", store = store)

    assertEquals(listOf("uid-1" to "token-1"), stored)
  }

  private fun user(verified: Boolean) =
      mock<FirebaseUser> {
        on { uid } doReturn "uid-1"
        on { isEmailVerified } doReturn verified
      }
}
