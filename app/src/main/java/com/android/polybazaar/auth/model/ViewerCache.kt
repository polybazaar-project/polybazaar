// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.model

import com.android.polybazaar.error.model.toAppError
import kotlinx.coroutines.CancellationException

/**
 * The signed-in user as a screen sees them: read once through [authRepository], then kept for the
 * rest of the screen's life. Every [AuthRepository.getCurrentUser] also writes the user's profile
 * document, so a screen that loads again must not read them again.
 *
 * A failed read is kept as unknown, not as an error, so the next [viewer] reads again. Not safe to
 * call from two coroutines at once: each screen calls it from its own load, which never runs twice.
 */
class ViewerCache(private val authRepository: AuthRepository) {

  private var known: User? = null

  /**
   * The signed-in user, or null when there is none or they cannot be read, for example offline
   * before the username is cached. The screen still shows, with its actions disabled.
   */
  suspend fun viewer(): User? {
    known?.let {
      return it
    }
    return try {
      authRepository.getCurrentUser().also { known = it }
    } catch (exception: Exception) {
      if (exception is CancellationException) throw exception
      exception.toAppError()
      null
    }
  }
}
