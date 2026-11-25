// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.notification.model

import kotlinx.coroutines.CompletableDeferred

/**
 * A [PushTokenRepository] that records which uids this device was registered and unregistered for.
 */
class FakePushTokenRepository : PushTokenRepository {
  val registered = mutableListOf<String>()
  val unregistered = mutableListOf<String>()
  var registerException: Exception? = null
  var unregisterException: Exception? = null
  /** When set, [register] waits for it, so a test can act while a registration is running. */
  var registerGate: CompletableDeferred<Unit>? = null
  /** When set, [unregister] waits for it, so a test can act while a sign-out is waiting on it. */
  var unregisterGate: CompletableDeferred<Unit>? = null
  /** Runs at the start of [unregister], so a test can check what had happened before it. */
  var onUnregister: () -> Unit = {}

  override suspend fun register(uid: String) {
    registerGate?.await()
    registerException?.let { throw it }
    registered += uid
  }

  override suspend fun unregister(uid: String) {
    onUnregister()
    unregisterGate?.await()
    unregisterException?.let { throw it }
    unregistered += uid
  }
}
