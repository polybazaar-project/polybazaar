// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

import java.time.Instant

/**
 * A message in a conversation.
 *
 * @property id Firestore document ID of the message.
 * @property senderUid uid of the participant who sent the message.
 * @property text trimmed message text, or "" for a photo-only message.
 * @property sentAt when the message was sent.
 * @property imagePath Storage path of the message's photo, or null for a text message.
 * @property photoPending true while the sender has not finished uploading the photo: it can't be
 *   downloaded yet. It stays true if the upload never ends, as when the sender's app was closed.
 * @property sending true while this device has written the message but the server doesn't have it
 *   yet, as when it was sent offline. It stays true until the device is back online. Unlike
 *   [photoPending], it is about the message document itself, and only on the sender's device.
 */
data class ChatMessage(
    val id: String,
    val senderUid: String,
    val text: String,
    val sentAt: Instant,
    val imagePath: String? = null,
    val photoPending: Boolean = false,
    val sending: Boolean = false,
)
