// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * A [ChatRepository] for tests.
 *
 * [observeMyConversations] follows [myConversations]: set it to a new list to emit it, or to a
 * failure to make the flow fail, like Firestore ending a listener with an error. Its
 * `subscriptionCount` tells how many collections are still active. [observeConversation] finds the
 * conversation by id in the current list, [observeMessages] follows [messages], and [sendMessage]
 * only records the call in [sentMessages].
 */
class FakeChatRepository(conversations: List<Conversation> = emptyList()) : ChatRepository {
  val myConversations = MutableStateFlow(Result.success(conversations))

  /** The messages of each conversation, by conversation id. */
  val messages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())

  /** The calls to [sendMessage], as (conversation, text), in order. */
  val sentMessages = mutableListOf<Pair<Conversation, String>>()

  override suspend fun sendMessage(
      conversation: Conversation,
      text: String,
      onWriteFailure: (Exception) -> Unit,
  ) {
    sentMessages += conversation to text
  }

  override fun observeConversation(id: String): Flow<Conversation?> =
      myConversations.map { result ->
        result.getOrThrow().firstOrNull { it.id == id }
      }

  override fun observeMessages(id: String): Flow<List<ChatMessage>> = messages.map {
    it[id].orEmpty()
  }

  override fun observeMyConversations(): Flow<List<Conversation>> = myConversations.map {
    it.getOrThrow()
  }
}
