// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.notification.model

/** The devices a user is signed in on, where the backend sends that user's push notifications. */
interface PushDeviceRepository {
  /** Adds this device to [uid]'s devices, so that [uid]'s notifications reach it. */
  suspend fun register(uid: String)

  /**
   * Removes this device from [uid]'s devices. Call it before signing [uid] out: only [uid] may
   * remove it.
   */
  suspend fun unregister(uid: String)

  /** Does nothing, for tests that do not concern push notifications. */
  object None : PushDeviceRepository {
    override suspend fun register(uid: String) = Unit

    override suspend fun unregister(uid: String) = Unit
  }
}
