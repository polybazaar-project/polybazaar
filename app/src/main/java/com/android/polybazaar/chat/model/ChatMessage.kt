// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

import java.time.Instant

/**
 * A message in a conversation.
 *
 * @property id Firestore document ID of the message.
 * @property senderUid uid of the participant who sent the message.
 * @property text trimmed message text.
 * @property sentAt when the message was sent.
 */
data class ChatMessage(
    val id: String,
    val senderUid: String,
    val text: String,
    val sentAt: Instant,
)
