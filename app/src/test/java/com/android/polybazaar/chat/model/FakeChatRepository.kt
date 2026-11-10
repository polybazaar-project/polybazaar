// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * A [ChatRepository] for tests.
 *
 * [observeMyConversations] follows [myConversations]: set it to a new list to emit it, or to a
 * failure to make the flow fail, like Firestore ending a listener with an error. Its
 * `subscriptionCount` tells how many collections are still active. [observeConversation] finds the
 * conversation by id in the current list, and fails along with it. [observeMessages] follows
 * [messages] and fails while [messagesFailure] is set. Set [conversationGate] or [messagesGate] to
 * hold back the first emission of the collections started after it until the test completes it.
 * [observeConversationCalls] and [observeMessagesCalls] count the subscriptions started.
 *
 * [sendMessage] records every call in [sentMessages] and its callback in [writeFailureCallbacks],
 * which a test invokes to simulate a later server rejection. It then waits for [gate] if set, to
 * look at a send in progress, and throws [sendFailure] if set, like a send refused before queuing;
 * such a call is still recorded.
 */
class FakeChatRepository(conversations: List<Conversation> = emptyList()) : ChatRepository {
  val myConversations = MutableStateFlow(Result.success(conversations))

  /** The messages of each conversation, by conversation id. */
  val messages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())

  /** When set, every active and new [observeMessages] collection fails with it. */
  val messagesFailure = MutableStateFlow<Exception?>(null)

  /** The calls to [sendMessage], as (conversation, text), in order. */
  val sentMessages = mutableListOf<Pair<Conversation, String>>()

  /** The `onWriteFailure` callbacks passed to [sendMessage], in order. */
  val writeFailureCallbacks = mutableListOf<(Exception) -> Unit>()

  var sendFailure: Exception? = null
  var gate: CompletableDeferred<Unit>? = null
  var conversationGate: CompletableDeferred<Unit>? = null
  var messagesGate: CompletableDeferred<Unit>? = null

  var observeConversationCalls = 0
    private set

  var observeMessagesCalls = 0
    private set

  override suspend fun sendMessage(
      conversation: Conversation,
      text: String,
      onWriteFailure: (Exception) -> Unit,
  ) {
    sentMessages += conversation to text
    writeFailureCallbacks += onWriteFailure
    gate?.await()
    sendFailure?.let { throw it }
  }

  override fun observeConversation(id: String): Flow<Conversation?> {
    observeConversationCalls++
    return gated(conversationGate) {
      myConversations.map { result -> result.getOrThrow().firstOrNull { it.id == id } }
    }
  }

  override fun observeMessages(id: String): Flow<List<ChatMessage>> {
    observeMessagesCalls++
    return gated(messagesGate) {
      combine(messages, messagesFailure) { messages, failure ->
        failure?.let { throw it }
        messages[id].orEmpty()
      }
    }
  }

  override fun observeMyConversations(): Flow<List<Conversation>> = myConversations.map {
    it.getOrThrow()
  }

  private fun <T> gated(gate: CompletableDeferred<Unit>?, upstream: () -> Flow<T>): Flow<T> =
      if (gate == null) upstream()
      else
          flow {
            gate.await()
            emitAll(upstream())
          }
}
