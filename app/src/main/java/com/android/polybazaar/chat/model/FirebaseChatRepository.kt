// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

import android.net.Uri
import android.util.Log
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import com.android.polybazaar.photo.PhotoCompressor
import com.android.polybazaar.photo.PhotoStorage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import java.io.IOException
import java.time.Instant
import java.util.concurrent.Executor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * @param photoTimeout how long sending a photo (its message, then its upload) or loading one may
 *   take before it fails with NETWORK, rather than the Storage SDK retrying for minutes while
 *   offline.
 */
class FirebaseChatRepository(
    private val photoCompressor: PhotoCompressor,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val photoTimeout: Duration = 30.seconds,
    private val logWriteFailure: (Exception) -> Unit = {
      Log.e("ChatRepository", "Chat write failed", it)
    },
) : ChatRepository {

  override suspend fun sendMessage(
      conversation: Conversation,
      text: String,
      onWriteFailure: (Exception) -> Unit,
  ) {
    val senderUid = participantUid(conversation)
    require(ChatRules.isSendable(text)) {
      "A message must have 1 to ${ChatRules.MAX_MESSAGE_LENGTH} characters"
    }
    val messageRef = conversationDocument(conversation.id).collection(MESSAGES).document()
    val trimmed = text.trim()
    // Not awaited: the write is queued, so a text can be sent offline.
    messageBatch(
            conversation,
            messageRef,
            senderUid,
            preview = trimmed,
            messageFields = mapOf("text" to trimmed),
        )
        .commit()
        .addOnFailureListener(Executor { it.run() }) {
          logWriteFailure(it)
          onWriteFailure(it)
        }
  }

  override fun newMessageId(conversationId: String): String =
      conversationDocument(conversationId).collection(MESSAGES).document().id

  override suspend fun sendPhoto(
      conversation: Conversation,
      messageId: String,
      photo: Uri,
      caption: String?,
  ) {
    val senderUid = participantUid(conversation)
    // firestore.rules only accept a message with a photo in a conversation that already exists.
    require(conversation.lastMessageAt != null) {
      "A photo cannot be the first message of a conversation"
    }
    val trimmedCaption =
        caption
            ?.takeIf { it.isNotBlank() }
            ?.also {
              require(ChatRules.isSendable(it)) {
                "A caption must have 1 to ${ChatRules.MAX_MESSAGE_LENGTH} characters"
              }
            }
            ?.trim()
    // First, so a photo that can't be compressed sends nothing. Outside the timeout, which is for
    // the network: a large photo compressing slowly must not be reported as offline.
    val compressed =
        try {
          photoCompressor.compress(photo)
        } catch (e: IOException) {
          // Deleted, on storage that's gone, or not an image.
          throw AppException(AppError.NOT_FOUND, e)
        } catch (e: SecurityException) {
          // The app lost its permission to read the photo.
          throw AppException(AppError.NOT_FOUND, e)
        }
    val path = photoPath(conversation.id, messageId)
    val messageRef = conversationDocument(conversation.id).collection(MESSAGES).document(messageId)
    val photoRef = storage.reference.child(path)
    // storage.rules only accept the upload once the server has the message that names the photo,
    // so the message is written and confirmed first. Offline, the Storage SDK keeps retrying for
    // minutes, so the user is told sooner that the photo was not sent.
    withNetworkTimeout {
      // Read from the server: offline it fails at once with UNAVAILABLE, rather than queuing a
      // message that would reach the server later without its photo. A retry finds the message
      // already written, and only uploads.
      if (!messageRef.get(Source.SERVER).await().exists()) {
        val batch =
            messageBatch(
                conversation,
                messageRef,
                senderUid,
                // A photo alone previews as an empty text.
                preview = trimmedCaption ?: "",
                messageFields =
                    buildMap {
                      put("imagePath", path)
                      trimmedCaption?.let { put("text", it) }
                    },
            )
        try {
          batch.commit().await()
        } catch (e: FirebaseFirestoreException) {
          logWriteFailure(e)
          throw e
        }
      }
      if (!exists(photoRef)) upload(photoRef, compressed)
    }
  }

  override suspend fun loadMessagePhoto(imagePath: String): ByteArray = withNetworkTimeout {
    storage.reference.child(imagePath).getBytes(PhotoStorage.MAX_PHOTO_BYTES).await()
  }

  override fun markRead(conversationId: String) {
    val uid = auth.currentUser?.uid ?: return
    // Not awaited, like sendMessage: the write is queued offline and the listener sees it at once.
    // Until the server confirms it, the read time is this device's clock, so with a clock behind
    // the server's lastMessageAt the conversation still shows unread until then.
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

  /** The signed-in user's uid, who must be one of the [conversation]'s participants. */
  private fun participantUid(conversation: Conversation): String {
    val senderUid =
        auth.currentUser?.uid
            ?: throw FirebaseFirestoreException(
                "Sign in to send a message",
                FirebaseFirestoreException.Code.UNAUTHENTICATED,
            )
    require(senderUid in conversation.participants) { "Only participants can send messages" }
    return senderUid
  }

  /**
   * The batch that writes the message at [messageRef] with [messageFields], plus its sender and
   * time, and updates the conversation's preview to [preview]. Not committed yet.
   */
  private suspend fun messageBatch(
      conversation: Conversation,
      messageRef: DocumentReference,
      senderUid: String,
      preview: String,
      messageFields: Map<String, Any>,
  ): WriteBatch {
    val conversationFields =
        mutableMapOf<String, Any>(
            "listingId" to conversation.listingId,
            "renterUid" to conversation.renterUid,
            "lenderUid" to conversation.lenderUid,
            "participants" to conversation.participants,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "lastMessageId" to messageRef.id,
            "lastMessageText" to preview,
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
    return firestore
        .batch()
        // A merge leaves unchanged fields out of the rules' affected keys, so the same write
        // creates the conversation on the first message and only bumps it on later ones.
        .set(conversationDocument(conversation.id), conversationFields, SetOptions.merge())
        .set(
            messageRef,
            mapOf("senderUid" to senderUid, "sentAt" to FieldValue.serverTimestamp()) +
                messageFields,
        )
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

  /**
   * Whether a file is already at [ref], like a photo whose upload ended after its send timed out.
   * The rules allow a chat photo to be created only once, so a retry with the same message ID must
   * reuse that file rather than upload it again.
   */
  private suspend fun exists(ref: StorageReference): Boolean =
      try {
        ref.metadata.await()
        true
      } catch (e: StorageException) {
        if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
        false
      }

  /**
   * Runs [block], and fails with NETWORK if it takes longer than [photoTimeout], as offline when
   * the Storage SDK keeps retrying.
   */
  private suspend fun <T> withNetworkTimeout(block: suspend () -> T): T =
      try {
        withTimeout(photoTimeout) { block() }
      } catch (e: TimeoutCancellationException) {
        // A CancellationException, which callers would take for their own cancellation and ignore.
        throw AppException(AppError.NETWORK, e)
      }

  /** Uploads the [jpeg] bytes to [ref], and cancels the upload if the caller is cancelled. */
  private suspend fun upload(ref: StorageReference, jpeg: ByteArray) {
    val task = ref.putBytes(jpeg, PhotoStorage.JPEG_METADATA)
    try {
      task.await()
    } catch (e: CancellationException) {
      // await() stops waiting but leaves the upload running.
      task.cancel()
      throw e
    }
  }

  /**
   * Where a message's photo is stored: the imagePath its message names. storage.rules only accept
   * an upload there from that message's sender.
   */
  private fun photoPath(conversationId: String, messageId: String) =
      "$CONVERSATIONS/$conversationId/$messageId.jpg"

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

    val imagePath = getString("imagePath")
    return ChatMessage(
        id = id,
        senderUid = getString("senderUid") ?: throw missing("senderUid"),
        // A photo-only message has no text.
        text = getString("text") ?: if (imagePath != null) "" else throw missing("text"),
        sentAt = instant("sentAt") ?: throw missing("sentAt"),
        imagePath = imagePath,
    )
  }

  private companion object {
    const val CONVERSATIONS = "conversations"
    const val MESSAGES = "messages"
    const val LISTINGS = "listings"
  }
}
