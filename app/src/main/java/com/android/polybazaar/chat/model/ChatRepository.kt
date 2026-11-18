// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
  /**
   * Queues a message from the signed-in participant without waiting for server confirmation. The
   * first message creates the conversation, and fails with NOT_FOUND when the listing no longer
   * exists; later ones update it. Firestore may later reject the write and report the failure
   * through [onWriteFailure].
   *
   * @param conversation for the first message, a new [Conversation] (no lastMessageAt); its
   *   listingTitle is not used, the listing's current title is sent instead. For a reply, the
   *   conversation from [observeConversation], whose title is left as stored.
   * @param onWriteFailure receives a write failure that arrives after local queuing. It may run on
   *   a Firestore background thread and must not throw.
   */
  suspend fun sendMessage(
      conversation: Conversation,
      text: String,
      onWriteFailure: (Exception) -> Unit = {},
  )

  /**
   * Returns a new message ID for the conversation with this ID, without any network access. Keep it
   * to retry a failed [sendPhoto] with the same ID.
   */
  fun newMessageId(conversationId: String): String

  /**
   * Sends a photo message from the signed-in participant, and returns once the server has both the
   * message and the photo, so it needs a connection. Storage only accepts the photo once its
   * message is on the server, so the message and the conversation update are written first, then
   * the photo is compressed and uploaded. Until the upload ends, the other participant may see the
   * message before its photo can be downloaded.
   *
   * Throws when signed out (UNAUTHENTICATED), when offline or when the send takes too long (an
   * error that maps to NETWORK with toAppError), when the server rejects the message (a Firestore
   * error) or the upload (a Storage error), when the photo can't be read, as when it was deleted or
   * is not an image (an AppException with NOT_FOUND), and IllegalArgumentException when the user is
   * not a participant, when the conversation has no message yet (a photo cannot open a
   * conversation), or when the caption is not sendable. Signed out, offline, for an unreadable
   * photo, or for an IllegalArgumentException, nothing is written. After a failed upload the
   * message stays: retrying with the same [messageId] only uploads the photo.
   *
   * @param conversation the conversation from [observeConversation].
   * @param messageId from [newMessageId]; pass the same one to retry a failed send.
   * @param caption optional text sent with the photo; null or blank sends the photo alone, which
   *   previews as an empty lastMessageText.
   */
  suspend fun sendPhoto(
      conversation: Conversation,
      messageId: String,
      photo: Uri,
      caption: String? = null,
  )

  /**
   * Downloads the photo of a message, from its [ChatMessage.imagePath], as the signed-in user.
   * Fails with PERMISSION_DENIED for a user who is not a participant of the conversation, and with
   * a Storage error to map with toAppError otherwise.
   */
  suspend fun loadMessagePhoto(imagePath: String): ByteArray

  /**
   * Marks the conversation with this ID as read by the signed-in user: sets only their lastReadAt
   * to the server time. Like [sendMessage], the write is queued without waiting for the server, so
   * it works offline. It never throws: a failure is only logged, and when signed out nothing is
   * written. Until the server confirms the write, the read time is estimated from the device's
   * clock, so on a device whose clock is behind, the conversation can still read as unread until
   * then.
   */
  fun markRead(conversationId: String)

  /**
   * Emits the conversation with this ID. For the renter it emits null while the conversation does
   * not exist yet; for the lender the flow fails with PERMISSION_DENIED until the first message
   * exists.
   */
  fun observeConversation(id: String): Flow<Conversation?>

  /**
   * Emits the messages of the conversation with this ID, ordered by sentAt, oldest first. The
   * lender can only read an existing conversation's messages, so for them the flow fails with
   * PERMISSION_DENIED until the first message exists.
   */
  fun observeMessages(id: String): Flow<List<ChatMessage>>

  /**
   * Emits the signed-in user's conversations, most recent message first. Fails with UNAUTHENTICATED
   * when signed out.
   */
  fun observeMyConversations(): Flow<List<Conversation>>
}
