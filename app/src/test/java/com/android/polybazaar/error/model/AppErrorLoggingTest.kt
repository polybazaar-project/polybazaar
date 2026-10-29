// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.error.model

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseNetworkException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

/** Checks the default logger of [toAppError], which writes to Logcat. */
@RunWith(AndroidJUnit4::class)
class AppErrorLoggingTest {

  @Before
  fun clearLogs() {
    ShadowLog.reset()
  }

  @Test
  fun unknownException_isLoggedAsAnError_withTheOriginalException() {
    val exception = IllegalStateException("Unexpected")

    assertEquals(AppError.UNKNOWN, exception.toAppError())

    val log = ShadowLog.getLogsForTag("AppError").single()
    assertEquals(Log.ERROR, log.type)
    assertSame(exception, log.throwable)
  }

  @Test
  fun knownException_isNotLogged() {
    FirebaseNetworkException("Offline").toAppError()

    assertTrue(ShadowLog.getLogsForTag("AppError").isEmpty())
  }
}
