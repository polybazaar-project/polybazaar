// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

import kotlinx.coroutines.flow.Flow

interface ChatRepository {
  /**
   * Queues a message from the signed-in participant without waiting for server confirmation. The
   * first message creates the conversation; later ones update it. Firestore may later reject the
   * write and report the failure through [onWriteFailure].
   *
   * @param conversation its [Conversation.listingTitle] must be the listing's current title: the
   *   rules accept only the current title on the first message, and the stored or current one
   *   after.
   * @param onWriteFailure receives a write failure that arrives after local queuing. It may run on
   *   a Firestore background thread and must not throw.
   */
  suspend fun sendMessage(
      conversation: Conversation,
      text: String,
      onWriteFailure: (Exception) -> Unit = {},
  )

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
}
