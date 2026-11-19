// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.model

import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class ViewerCacheTest {

  private val ada =
      User(uid = "uid-ada", email = "ada@epfl.ch", username = "ada", isEmailVerified = true)

  /**
   * A network failure. Thrown from an answer: Mockito refuses to stub a checked exception on a
   * method that does not declare it.
   */
  private fun offline() = UnknownHostException("firestore.googleapis.com")

  @Test
  fun viewer_isReadOnce_andKept() = runTest {
    val auth: AuthRepository = mock { onBlocking { getCurrentUser() } doReturn ada }
    val cache = ViewerCache(auth)

    assertSame(ada, cache.viewer())
    assertSame(ada, cache.viewer())

    verify(auth, times(1)).getCurrentUser()
  }

  @Test
  fun aFailedRead_givesNoViewer_withoutThrowing() = runTest {
    val auth: AuthRepository = mock { onBlocking { getCurrentUser() } doAnswer { throw offline() } }

    assertNull(ViewerCache(auth).viewer())
  }

  @Test
  fun aFailedRead_isReadAgain_andThenKept() = runTest {
    val auth: AuthRepository = mock {
      onBlocking { getCurrentUser() }.doAnswer { throw offline() }.doReturn(ada)
    }
    val cache = ViewerCache(auth)

    assertNull(cache.viewer())
    assertSame(ada, cache.viewer())
    assertSame(ada, cache.viewer())

    verify(auth, times(2)).getCurrentUser()
  }

  @Test
  fun cancellation_isNotSwallowed() = runTest {
    val cancellation = CancellationException("screen closed")
    val auth: AuthRepository = mock { onBlocking { getCurrentUser() } doThrow cancellation }

    try {
      ViewerCache(auth).viewer()
      fail("Expected the cancellation to be rethrown")
    } catch (exception: CancellationException) {
      assertEquals("screen closed", exception.message)
    }
  }
}
