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
  private val store: (String, String) -> Unit = { uid, fid -> stored += uid to fid }

  @Test
  fun registration_withoutASignedInUser_isNotStored() {
    storeRegistration(user = null, fid = "fid-1", store = store)

    assertTrue(stored.isEmpty())
  }

  @Test
  fun registration_ofAnUnverifiedUser_isNotStored() {
    storeRegistration(user = user(verified = false), fid = "fid-1", store = store)

    assertTrue(stored.isEmpty())
  }

  @Test
  fun registration_ofAVerifiedUser_isStoredForThem() {
    storeRegistration(user = user(verified = true), fid = "fid-1", store = store)

    assertEquals(listOf("uid-1" to "fid-1"), stored)
  }

  private fun user(verified: Boolean) =
      mock<FirebaseUser> {
        on { uid } doReturn "uid-1"
        on { isEmailVerified } doReturn verified
      }
}
