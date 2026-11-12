// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

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
   * Marks the conversation with this ID as read by the signed-in user: sets only their lastReadAt
   * to the server time. Like [sendMessage], the write is queued without waiting for the server, so
   * it works offline. It never throws: a failure is only logged, and when signed out nothing is
   * written.
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
