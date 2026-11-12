// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.time.Instant
import java.util.concurrent.Executor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseChatRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val logWriteFailure: (Exception) -> Unit = {
      Log.e("ChatRepository", "Chat write failed", it)
    },
) : ChatRepository {

  override suspend fun sendMessage(
      conversation: Conversation,
      text: String,
      onWriteFailure: (Exception) -> Unit,
  ) {
    val senderUid =
        auth.currentUser?.uid
            ?: throw FirebaseFirestoreException(
                "Sign in to send a message",
                FirebaseFirestoreException.Code.UNAUTHENTICATED,
            )
    require(senderUid in conversation.participants) { "Only participants can send messages" }
    require(ChatRules.isSendable(text)) {
      "A message must have 1 to ${ChatRules.MAX_MESSAGE_LENGTH} characters"
    }
    val conversationRef = conversationDocument(conversation.id)
    val messageRef = conversationRef.collection(MESSAGES).document()
    val trimmed = text.trim()
    val conversationFields =
        mutableMapOf<String, Any>(
            "listingId" to conversation.listingId,
            "renterUid" to conversation.renterUid,
            "lenderUid" to conversation.lenderUid,
            "participants" to conversation.participants,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "lastMessageId" to messageRef.id,
            "lastMessageText" to trimmed,
            "lastMessageSenderUid" to senderUid,
            // The merge sets only my key, so the other participant's read time is kept, and my own
            // message never shows as unread to me.
            "lastReadAt" to mapOf(senderUid to FieldValue.serverTimestamp()),
        )
    // A reply leaves the title as stored, so an outdated title on this device cannot get the
    // reply rejected.
    if (conversation.lastMessageAt == null) {
      // The rules require the listing's current title, and the one this screen loaded may be
      // outdated.
      conversationFields["listingTitle"] = currentListingTitle(conversation.listingId)
    }
    firestore
        .batch()
        // A merge leaves unchanged fields out of the rules' affected keys, so the same write
        // creates the conversation on the first message and only bumps it on later ones.
        .set(conversationRef, conversationFields, SetOptions.merge())
        .set(
            messageRef,
            mapOf(
                "senderUid" to senderUid,
                "text" to trimmed,
                "sentAt" to FieldValue.serverTimestamp(),
            ),
        )
        .commit()
        .addOnFailureListener(Executor { it.run() }) {
          logWriteFailure(it)
          onWriteFailure(it)
        }
  }

  override fun markRead(conversationId: String) {
    val uid = auth.currentUser?.uid ?: return
    // Not awaited, like sendMessage: the write is queued offline and the listener sees it at once.
    conversationDocument(conversationId)
        .update(FieldPath.of("lastReadAt", uid), FieldValue.serverTimestamp())
        .addOnFailureListener(Executor { it.run() }) { logWriteFailure(it) }
  }

  override fun observeConversation(id: String): Flow<Conversation?> = callbackFlow {
    val listener =
        conversationDocument(id).addSnapshotListener { snapshot, error ->
          when {
            error != null -> close(error)
            snapshot == null -> close(IllegalStateException("Conversation snapshot was null"))
            !snapshot.exists() -> trySend(null)
            else -> {
              try {
                val result = trySend(snapshot.toConversation())
                result.exceptionOrNull()?.let { close(it) }
              } catch (e: Exception) {
                close(e)
              }
            }
          }
        }

    awaitClose { listener.remove() }
  }

  override fun observeMessages(id: String): Flow<List<ChatMessage>> = callbackFlow {
    val listener =
        conversationDocument(id).collection(MESSAGES).orderBy("sentAt").addSnapshotListener {
            snapshot,
            error ->
          when {
            error != null -> close(error)
            snapshot == null -> close(IllegalStateException("Messages snapshot was null"))
            else -> {
              try {
                val result = trySend(snapshot.documents.map { it.toChatMessage() })
                result.exceptionOrNull()?.let { close(it) }
              } catch (e: Exception) {
                close(e)
              }
            }
          }
        }

    awaitClose { listener.remove() }
  }

  override fun observeMyConversations(): Flow<List<Conversation>> = callbackFlow {
    val uid =
        auth.currentUser?.uid
            ?: throw FirebaseFirestoreException(
                "Sign in to read conversations",
                FirebaseFirestoreException.Code.UNAUTHENTICATED,
            )
    val listener =
        firestore
            .collection(CONVERSATIONS)
            .whereArrayContains("participants", uid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
              when {
                error != null -> close(error)
                snapshot == null -> close(IllegalStateException("Conversations snapshot was null"))
                else -> {
                  try {
                    val result = trySend(snapshot.documents.map { it.toConversation() })
                    result.exceptionOrNull()?.let { close(it) }
                  } catch (e: Exception) {
                    close(e)
                  }
                }
              }
            }

    awaitClose { listener.remove() }
  }

  private suspend fun currentListingTitle(listingId: String): String {
    val listing = firestore.collection(LISTINGS).document(listingId).get().await()
    return listing.takeIf { it.exists() }?.getString("title")
        ?: throw FirebaseFirestoreException(
            "Listing $listingId no longer exists",
            FirebaseFirestoreException.Code.NOT_FOUND,
        )
  }

  private fun conversationDocument(id: String) = firestore.collection(CONVERSATIONS).document(id)

  // A write not yet confirmed by the server has no sentAt or lastMessageAt; ESTIMATE gives it the
  // local time.
  private fun DocumentSnapshot.instant(field: String): Instant? =
      getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toInstant()

  private fun DocumentSnapshot.toConversation(): Conversation {
    fun string(field: String): String =
        getString(field) ?: throw IllegalStateException("Conversation $id is missing $field")

    val listingId = string("listingId")
    val listingTitle = string("listingTitle")
    val renterUid = string("renterUid")
    val lenderUid = string("lenderUid")
    // Conversations created before previews existed have none of the last three fields.
    return Conversation(
        listingId = listingId,
        listingTitle = listingTitle,
        renterUid = renterUid,
        lenderUid = lenderUid,
        lastMessageAt = instant("lastMessageAt"),
        lastMessageText = getString("lastMessageText"),
        lastMessageSenderUid = getString("lastMessageSenderUid"),
        lastReadAt =
            listOf(renterUid, lenderUid)
                .mapNotNull { uid -> instant("lastReadAt.$uid")?.let { uid to it } }
                .toMap(),
    )
  }

  private fun DocumentSnapshot.toChatMessage(): ChatMessage {
    fun missing(field: String) = IllegalStateException("Message $id is missing $field")

    return ChatMessage(
        id = id,
        senderUid = getString("senderUid") ?: throw missing("senderUid"),
        text = getString("text") ?: throw missing("text"),
        sentAt = instant("sentAt") ?: throw missing("sentAt"),
    )
  }

  private companion object {
    const val CONVERSATIONS = "conversations"
    const val MESSAGES = "messages"
    const val LISTINGS = "listings"
  }
}
