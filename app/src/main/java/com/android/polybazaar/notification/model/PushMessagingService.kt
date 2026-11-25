// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.notification.model

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.FirebaseMessagingService

/** Receives this device's FCM events. */
class PushMessagingService : FirebaseMessagingService() {

  override fun onNewToken(token: String) {
    storeNewToken(FirebaseAuth.getInstance().currentUser, token) { uid, newToken ->
      FirebasePushTokenRepository().store(uid, newToken)
    }
  }
}

/**
 * FCM replaced this device's token, so [user]'s devices must name the new one. Only a verified user
 * may store a device; anyone else has it stored when their verified session starts.
 */
internal fun storeNewToken(
    user: FirebaseUser?,
    token: String,
    store: (uid: String, token: String) -> Unit,
) {
  if (user != null && user.isEmailVerified) store(user.uid, token)
}
