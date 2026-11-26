// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.notification.model

import android.annotation.SuppressLint
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.FirebaseMessagingService

/**
 * Receives this device's FCM events.
 *
 * Lint still asks for the deprecated `onNewToken`, but with installation IDs turned on in the
 * manifest, [onRegistered] replaces it.
 */
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class PushMessagingService : FirebaseMessagingService() {

  override fun onRegistered(installationId: String) {
    storeRegistration(FirebaseAuth.getInstance().currentUser, installationId) { uid, fid ->
      FirebasePushDeviceRepository().store(uid, fid)
    }
  }
}

/**
 * FCM registered this device under [fid], at install, at a routine sync, or when the ID changed, so
 * [user]'s devices must name it and say when it was last seen. Only a verified user may store a
 * device; anyone else has it stored when their verified session starts.
 */
internal fun storeRegistration(
    user: FirebaseUser?,
    fid: String,
    store: (uid: String, fid: String) -> Unit,
) {
  if (user != null && user.isEmailVerified) store(user.uid, fid)
}
