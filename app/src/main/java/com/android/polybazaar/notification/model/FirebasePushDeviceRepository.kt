// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.notification.model

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.installations.FirebaseInstallations
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Stores each device as `users/{uid}/devices/{fid}`, named by its Firebase Installation ID (FID),
 * which FCM sends to, and holding when it was last stored.
 *
 * @param installations gives this device's FID. FCM registers that same ID on its own at startup,
 *   and reports it to [PushMessagingService.onRegistered].
 * @param unregisterTimeout how long [unregister] waits for the server to confirm the removal.
 */
class FirebasePushDeviceRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val installations: FirebaseInstallations = FirebaseInstallations.getInstance(),
    private val unregisterTimeout: Duration = UNREGISTER_TIMEOUT,
) : PushDeviceRepository {

  override suspend fun register(uid: String) {
    store(uid, installations.id.await())
  }

  /**
   * Stores [fid] as one of [uid]'s devices. The write is not awaited: offline it would only
   * complete once the connection returns, and Firestore sends it then.
   */
  fun store(uid: String, fid: String) {
    device(uid, fid).set(mapOf(UPDATED_AT to FieldValue.serverTimestamp()))
  }

  /**
   * Waits for the server to confirm the removal, because Firestore keeps a write that is still
   * pending at sign-out until the same user signs in again. Offline it gives up after
   * [unregisterTimeout], leaving the document in place.
   */
  override suspend fun unregister(uid: String) {
    withTimeoutOrNull(unregisterTimeout) { device(uid, installations.id.await()).delete().await() }
  }

  private fun device(uid: String, fid: String) =
      firestore.collection(USERS).document(uid).collection(DEVICES).document(fid)

  private companion object {
    const val USERS = "users"
    const val DEVICES = "devices"
    const val UPDATED_AT = "updatedAt"
    val UNREGISTER_TIMEOUT = 3.seconds
  }
}
