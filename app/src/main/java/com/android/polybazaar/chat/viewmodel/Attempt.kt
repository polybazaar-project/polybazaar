// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Runs [block] and returns its result, or its failure. A [CancellationException] is rethrown only
 * when the calling coroutine is itself cancelled, so cancelling a load still stops it. One thrown
 * by a call that was cancelled on its own, such as a cancelled Firebase task, is returned as a
 * failure like any other, so it cannot leave a screen loading forever.
 */
internal suspend fun <T> attempt(block: suspend () -> T): Result<T> =
    try {
      Result.success(block())
    } catch (exception: Exception) {
      if (exception is CancellationException) currentCoroutineContext().ensureActive()
      Result.failure(exception)
    }
