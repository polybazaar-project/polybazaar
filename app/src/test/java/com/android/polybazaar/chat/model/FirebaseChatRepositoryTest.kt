// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

import android.net.Uri
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import com.android.polybazaar.error.model.toAppError
import com.android.polybazaar.photo.PhotoCompressor
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.SnapshotMetadata
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import java.io.FileNotFoundException
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FirebaseChatRepositoryTest {
  private val firestore = mock<FirebaseFirestore>()
  private val auth = mock<FirebaseAuth>()
  private val conversations = mock<CollectionReference>()
  private val conversationRef = mock<DocumentReference>()
  private val messages = mock<CollectionReference>()
  private val messageRef = mock<DocumentReference>()
  private val orderedMessages = mock<Query>()
  private val myConversations = mock<Query>()
  private val recentConversations = mock<Query>()
  private val batch = mock<WriteBatch>()
  private val registration = mock<ListenerRegistration>()
  private val listings = mock<CollectionReference>()
  private val listingRef = mock<DocumentReference>()
  private val listing = mock<DocumentSnapshot>()
  private val storage = mock<FirebaseStorage>()
  private val storageRoot = mock<StorageReference>()
  private val photoRef = mock<StorageReference>()
  // A separate mock from messageRef, so a test fails if a photo is written to a new document
  // instead of the one its ID names.
  private val photoMessageRef = mock<DocumentReference>()
  private val uploadTask = mock<UploadTask>()
  private val photo = mock<Uri>()
  private val compressedPhoto = byteArrayOf(4, 2)
  private val photoCompressor = PhotoCompressor {
    assertSame(photo, it)
    compressedPhoto
  }
  private val repository = FirebaseChatRepository(photoCompressor, firestore, auth, storage)
  private val conversation =
      Conversation(
          listingId = "listing",
          listingTitle = "Drill",
          renterUid = "renter",
          lenderUid = "lender",
      )
  // A photo can't be the first message, so photo tests send to a conversation that has one.
  private val replied = conversation.copy(lastMessageAt = Instant.ofEpochSecond(1_700_000_000))

  @Before
  fun setUp() {
    signInAs("renter")
    whenever(firestore.collection("conversations")).thenReturn(conversations)
    whenever(conversations.document("listing_renter")).thenReturn(conversationRef)
    whenever(conversationRef.collection("messages")).thenReturn(messages)
    whenever(messages.document()).thenReturn(messageRef)
    whenever(messageRef.id).thenReturn("message-id")
    whenever(messages.orderBy("sentAt")).thenReturn(orderedMessages)
    whenever(conversations.whereArrayContains("participants", "renter")).thenReturn(myConversations)
    whenever(myConversations.orderBy("lastMessageAt", Query.Direction.DESCENDING))
        .thenReturn(recentConversations)
    whenever(firestore.batch()).thenReturn(batch)
    whenever(batch.set(any(), any())).thenReturn(batch)
    whenever(batch.set(any(), any(), any<SetOptions>())).thenReturn(batch)
    whenever(batch.commit()).thenReturn(Tasks.forResult(null))
    whenever(firestore.collection("listings")).thenReturn(listings)
    whenever(listings.document("listing")).thenReturn(listingRef)
    whenever(listingRef.get()).thenReturn(Tasks.forResult(listing))
    whenever(listing.exists()).thenReturn(true)
    whenever(listing.getString("title")).thenReturn("Drill")
    whenever(messages.document(PHOTO_ID)).thenReturn(photoMessageRef)
    whenever(photoMessageRef.id).thenReturn(PHOTO_ID)
    // By default the photo's message is not on the server yet, as for a first try.
    val notWritten = messageSnapshot(exists = false)
    whenever(photoMessageRef.get(Source.SERVER)).thenReturn(Tasks.forResult(notWritten))
    whenever(photoMessageRef.update("photoPending", false)).thenReturn(Tasks.forResult(null))
    whenever(storage.reference).thenReturn(storageRoot)
    whenever(storageRoot.child(PHOTO_PATH)).thenReturn(photoRef)
    val notUploaded = storageException(StorageException.ERROR_OBJECT_NOT_FOUND)
    whenever(photoRef.metadata).thenReturn(Tasks.forException(notUploaded))
    whenever(photoRef.putBytes(any(), any())).thenReturn(uploadTask)
    whenever(uploadTask.isComplete).thenReturn(true)
    val uploaded = mock<UploadTask.TaskSnapshot>()
    whenever(uploadTask.result).thenReturn(uploaded)
  }

  @Test
  fun conversationIdJoinsListingAndRenter() {
    assertEquals("l1_bob", conversationId("l1", "bob"))
    assertEquals("listing_renter", conversation.id)
    assertEquals(listOf("renter", "lender"), conversation.participants)
  }

  @Test
  fun sendMessageFirstMessageMergesFullConversationAndTrimmedMessageInOneBatch() = runTest {
    repository.sendMessage(conversation, "  Is it available on Saturday?  ")

    val order = inOrder(batch)
    order
        .verify(batch)
        .set(
            eq(conversationRef),
            eq(
                mapOf(
                    "listingId" to "listing",
                    "listingTitle" to "Drill",
                    "renterUid" to "renter",
                    "lenderUid" to "lender",
                    "participants" to listOf("renter", "lender"),
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastMessageId" to "message-id",
                    "lastMessageText" to "Is it available on Saturday?",
                    "lastMessageSenderUid" to "renter",
                    "lastReadAt" to mapOf("renter" to FieldValue.serverTimestamp()),
                )
            ),
            eq(SetOptions.merge()),
        )
    order
        .verify(batch)
        .set(
            eq(messageRef),
            eq(
                mapOf(
                    "senderUid" to "renter",
                    "text" to "Is it available on Saturday?",
                    "sentAt" to FieldValue.serverTimestamp(),
                )
            ),
        )
    order.verify(batch).commit()
    verify(listingRef).get()
  }

  @Test
  fun sendMessageFirstMessageSendsListingCurrentTitle() = runTest {
    whenever(listing.getString("title")).thenReturn("Hammer drill")

    repository.sendMessage(conversation, "Hi")

    verify(batch)
        .set(
            eq(conversationRef),
            eq(
                mapOf(
                    "listingId" to "listing",
                    "listingTitle" to "Hammer drill",
                    "renterUid" to "renter",
                    "lenderUid" to "lender",
                    "participants" to listOf("renter", "lender"),
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastMessageId" to "message-id",
                    "lastMessageText" to "Hi",
                    "lastMessageSenderUid" to "renter",
                    "lastReadAt" to mapOf("renter" to FieldValue.serverTimestamp()),
                )
            ),
            eq(SetOptions.merge()),
        )
  }

  @Test
  fun sendMessageFirstMessageRejectsDeletedListing() = runTest {
    whenever(listing.exists()).thenReturn(false)

    val error = runCatching { repository.sendMessage(conversation, "Hi") }.exceptionOrNull()

    assertTrue(error is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.NOT_FOUND,
        (error as FirebaseFirestoreException).code,
    )
    verify(firestore, never()).batch()
  }

  @Test
  fun sendMessageReplyLeavesStoredListingTitleUnchangedWithoutReadingListing() = runTest {
    val stored = conversation.copy(lastMessageAt = Instant.ofEpochSecond(1_700_000_000))

    repository.sendMessage(stored, "Still available?")

    verify(batch)
        .set(
            eq(conversationRef),
            eq(
                mapOf(
                    "listingId" to "listing",
                    "renterUid" to "renter",
                    "lenderUid" to "lender",
                    "participants" to listOf("renter", "lender"),
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastMessageId" to "message-id",
                    "lastMessageText" to "Still available?",
                    "lastMessageSenderUid" to "renter",
                    "lastReadAt" to mapOf("renter" to FieldValue.serverTimestamp()),
                )
            ),
            eq(SetOptions.merge()),
        )
    verify(batch)
        .set(
            eq(messageRef),
            eq(
                mapOf(
                    "senderUid" to "renter",
                    "text" to "Still available?",
                    "sentAt" to FieldValue.serverTimestamp(),
                )
            ),
        )
    verify(batch).commit()
    verify(listingRef, never()).get()
  }

  @Test
  fun sendMessageFromLenderUsesLenderUidAsSender() = runTest {
    signInAs("lender")

    repository.sendMessage(conversation, "Yes, it is")

    verify(batch)
        .set(
            eq(messageRef),
            eq(
                mapOf(
                    "senderUid" to "lender",
                    "text" to "Yes, it is",
                    "sentAt" to FieldValue.serverTimestamp(),
                )
            ),
        )
  }

  @Test
  fun sendMessageFromLenderStoresLenderAsLastSenderAndMarksItReadForLenderOnly() = runTest {
    signInAs("lender")

    repository.sendMessage(conversation, "  Yes, it is  ")

    val fields = argumentCaptor<Map<String, Any>>()
    verify(batch).set(eq(conversationRef), fields.capture(), eq(SetOptions.merge()))
    assertEquals("Yes, it is", fields.firstValue["lastMessageText"])
    assertEquals("lender", fields.firstValue["lastMessageSenderUid"])
    assertEquals(mapOf("lender" to FieldValue.serverTimestamp()), fields.firstValue["lastReadAt"])
  }

  @Test
  fun sendMessageReturnsWhileServerConfirmationIsPending() = runTest {
    val pendingWrite = TaskCompletionSource<Void>()
    whenever(batch.commit()).thenReturn(pendingWrite.task)

    withTimeout(1_000) { repository.sendMessage(conversation, "Hi") }

    assertTrue(!pendingWrite.task.isComplete)
    verify(batch).commit()
  }

  @Test
  fun sendMessageReportsLaterWriteFailures() = runTest {
    val pendingWrite = TaskCompletionSource<Void>()
    whenever(batch.commit()).thenReturn(pendingWrite.task)
    var reportedFailure: Exception? = null
    val repository =
        FirebaseChatRepository(photoCompressor, firestore, auth, storage) { reportedFailure = it }
    var reportedToCaller: Exception? = null

    repository.sendMessage(conversation, "Hi") { reportedToCaller = it }
    val failure = IllegalStateException("Write rejected")
    pendingWrite.setException(failure)

    assertSame(failure, reportedFailure)
    assertSame(failure, reportedToCaller)
  }

  @Test
  fun sendMessageRejectsBlankAndTooLongText() = runTest {
    assertTrue(
        runCatching { repository.sendMessage(conversation, "   ") }.exceptionOrNull()
            is IllegalArgumentException
    )
    assertTrue(
        runCatching { repository.sendMessage(conversation, "a".repeat(1001)) }.exceptionOrNull()
            is IllegalArgumentException
    )
    verify(firestore, never()).batch()
  }

  @Test
  fun sendMessageDoesNotReportSuccessfulWrites() = runTest {
    var reportedFailure: Exception? = null
    val repository =
        FirebaseChatRepository(photoCompressor, firestore, auth, storage) { reportedFailure = it }
    var reportedToCaller: Exception? = null

    repository.sendMessage(conversation, "Hi") { reportedToCaller = it }

    verify(batch).commit()
    assertNull(reportedFailure)
    assertNull(reportedToCaller)
  }

  @Test
  fun sendMessageAcceptsTextAtLengthLimit() = runTest {
    repository.sendMessage(conversation, "a".repeat(1000))

    verify(batch).commit()
  }

  @Test
  fun sendMessageAcceptsPaddedTextAtLengthLimitAndSendsItTrimmed() = runTest {
    repository.sendMessage(conversation, "  " + "a".repeat(1000) + "  ")

    verify(batch)
        .set(
            eq(messageRef),
            eq(
                mapOf(
                    "senderUid" to "renter",
                    "text" to "a".repeat(1000),
                    "sentAt" to FieldValue.serverTimestamp(),
                )
            ),
        )
    verify(batch).commit()
  }

  @Test
  fun sendMessageRejectsSignedOutUser() = runTest {
    whenever(auth.currentUser).thenReturn(null)

    val error = runCatching { repository.sendMessage(conversation, "Hi") }.exceptionOrNull()

    assertTrue(error is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.UNAUTHENTICATED,
        (error as FirebaseFirestoreException).code,
    )
    verify(firestore, never()).batch()
  }

  @Test
  fun sendMessageRejectsNonParticipant() = runTest {
    signInAs("stranger")

    assertTrue(
        runCatching { repository.sendMessage(conversation, "Hi") }.exceptionOrNull()
            is IllegalArgumentException
    )
    verify(firestore, never()).batch()
  }

  @Test
  fun newMessageIdIsTheIdOfANewMessageDocument() {
    assertEquals("message-id", repository.newMessageId("listing_renter"))

    verify(messages).document()
  }

  @Test
  fun sendPhotoUploadsCompressedPhotoAsJpegToTheMessagePath() = runTest {
    repository.sendPhoto(replied, PHOTO_ID, photo)

    val metadata = argumentCaptor<StorageMetadata>()
    verify(storageRoot).child(PHOTO_PATH)
    verify(photoRef).putBytes(eq(compressedPhoto), metadata.capture())
    assertEquals("image/jpeg", metadata.firstValue.contentType)
  }

  @Test
  fun sendPhotoChecksTheServerThenCommitsTheMessageBeforeUploading() = runTest {
    repository.sendPhoto(replied, PHOTO_ID, photo)

    // storage.rules only accept the upload once the server has the message.
    val order = inOrder(photoMessageRef, batch, photoRef)
    order.verify(photoMessageRef).get(Source.SERVER)
    order.verify(batch).commit()
    order.verify(photoRef).putBytes(any(), any())
  }

  @Test
  fun sendPhotoWaitsForTheServerToConfirmTheMessageBeforeUploading() = runTest {
    val pendingWrite = TaskCompletionSource<Void>()
    whenever(batch.commit()).thenReturn(pendingWrite.task)
    val sending = async { repository.sendPhoto(replied, PHOTO_ID, photo) }
    runCurrent()

    verify(photoRef, never()).putBytes(any(), any())
    pendingWrite.setResult(null)
    sending.await()

    verify(photoRef).putBytes(any(), any())
  }

  @Test
  fun sendPhotoWithoutCaptionWritesImagePathWithoutTextAndEmptyPreview() = runTest {
    repository.sendPhoto(replied, PHOTO_ID, photo)

    verifyPhotoBatch(
        preview = "",
        message =
            mapOf(
                "senderUid" to "renter",
                "sentAt" to FieldValue.serverTimestamp(),
                "imagePath" to PHOTO_PATH,
                "photoPending" to true,
            ),
    )
  }

  @Test
  fun sendPhotoWithCaptionWritesTrimmedCaptionAsTextAndPreview() = runTest {
    repository.sendPhoto(replied, PHOTO_ID, photo, caption = "  The drill bits  ")

    verifyPhotoBatch(
        preview = "The drill bits",
        message =
            mapOf(
                "senderUid" to "renter",
                "sentAt" to FieldValue.serverTimestamp(),
                "imagePath" to PHOTO_PATH,
                "photoPending" to true,
                "text" to "The drill bits",
            ),
    )
  }

  @Test
  fun sendPhotoTreatsBlankCaptionAsNoCaption() = runTest {
    repository.sendPhoto(replied, PHOTO_ID, photo, caption = "   ")

    verifyPhotoBatch(
        preview = "",
        message =
            mapOf(
                "senderUid" to "renter",
                "sentAt" to FieldValue.serverTimestamp(),
                "imagePath" to PHOTO_PATH,
                "photoPending" to true,
            ),
    )
  }

  @Test
  fun sendPhotoFromLenderIsSentAndReadByLenderOnly() = runTest {
    signInAs("lender")

    repository.sendPhoto(replied, PHOTO_ID, photo)

    val fields = argumentCaptor<Map<String, Any>>()
    verify(batch).set(eq(conversationRef), fields.capture(), eq(SetOptions.merge()))
    assertEquals("lender", fields.firstValue["lastMessageSenderUid"])
    assertEquals(mapOf("lender" to FieldValue.serverTimestamp()), fields.firstValue["lastReadAt"])
    val message = argumentCaptor<Map<String, Any>>()
    verify(batch).set(eq(photoMessageRef), message.capture())
    assertEquals("lender", message.firstValue["senderUid"])
  }

  @Test
  fun sendPhotoRejectedMessageIsLoggedAndThrownAndNothingIsUploaded() = runTest {
    val failure =
        FirebaseFirestoreException(
            "Missing or insufficient permissions.",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )
    whenever(batch.commit()).thenReturn(Tasks.forException(failure))
    var logged: Exception? = null
    val repository =
        FirebaseChatRepository(photoCompressor, firestore, auth, storage) { logged = it }

    val error = runCatching { repository.sendPhoto(replied, PHOTO_ID, photo) }.exceptionOrNull()

    assertSame(failure, logged)
    assertEquals(AppError.PERMISSION_DENIED, error?.toAppError())
    verify(photoRef, never()).putBytes(any(), any())
  }

  @Test
  fun sendPhotoOfflineFailsWithNetworkBeforeWritingOrUploadingAnything() = runTest {
    val offline =
        FirebaseFirestoreException(
            "Failed to get document from server.",
            FirebaseFirestoreException.Code.UNAVAILABLE,
        )
    whenever(photoMessageRef.get(Source.SERVER)).thenReturn(Tasks.forException(offline))

    val error = runCatching { repository.sendPhoto(replied, PHOTO_ID, photo) }.exceptionOrNull()

    assertEquals(AppError.NETWORK, error?.toAppError())
    // Nothing queued: an offline write would reach the server later without its photo.
    verify(firestore, never()).batch()
    verify(photoRef, never()).putBytes(any(), any())
  }

  @Test
  fun sendPhotoMessageWithNoServerAnswerFailsWithNetworkAfterTimeoutWithoutUploading() = runTest {
    whenever(batch.commit()).thenReturn(TaskCompletionSource<Void>().task)

    val error = runCatching {
      shortTimeoutRepository().sendPhoto(replied, PHOTO_ID, photo)
    }
        .exceptionOrNull()

    assertEquals(AppError.NETWORK, (error as AppException).error)
    assertEquals(5_000L, testScheduler.currentTime)
    verify(photoRef, never()).putBytes(any(), any())
  }

  @Test
  fun sendPhotoRetryAfterFailedUploadOnlyUploadsWithoutRewritingTheMessage() = runTest {
    messageAlreadyOnServer()

    repository.sendPhoto(replied, PHOTO_ID, photo)

    // The rules refuse rewriting a message, and the conversation already names it.
    verify(firestore, never()).batch()
    verify(photoRef).putBytes(eq(compressedPhoto), any())
  }

  @Test
  fun sendPhotoRejectsSignedOutUser() = runTest {
    whenever(auth.currentUser).thenReturn(null)

    val error = runCatching { repository.sendPhoto(replied, PHOTO_ID, photo) }.exceptionOrNull()

    assertTrue(error is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.UNAUTHENTICATED,
        (error as FirebaseFirestoreException).code,
    )
    verifyNothingSent()
  }

  @Test
  fun sendPhotoRejectsNonParticipant() = runTest {
    signInAs("stranger")

    assertTrue(
        runCatching { repository.sendPhoto(replied, PHOTO_ID, photo) }.exceptionOrNull()
            is IllegalArgumentException
    )
    verifyNothingSent()
  }

  @Test
  fun sendPhotoRejectsConversationWithoutMessages() = runTest {
    assertTrue(
        runCatching { repository.sendPhoto(conversation, PHOTO_ID, photo) }.exceptionOrNull()
            is IllegalArgumentException
    )
    verifyNothingSent()
  }

  @Test
  fun sendPhotoRejectsTooLongCaption() = runTest {
    assertTrue(
        runCatching { repository.sendPhoto(replied, PHOTO_ID, photo, "a".repeat(1001)) }
            .exceptionOrNull() is IllegalArgumentException
    )
    verifyNothingSent()
  }

  @Test
  fun sendPhotoUploadFailureOfflineIsNetworkAfterTheMessageIsWritten() = runTest {
    val failure = storageException(StorageException.ERROR_RETRY_LIMIT_EXCEEDED)
    failUpload(failure)

    val error = runCatching { repository.sendPhoto(replied, PHOTO_ID, photo) }.exceptionOrNull()

    assertSame(failure, error)
    assertEquals(AppError.NETWORK, error?.toAppError())
    // Kept: a retry with the same ID only uploads.
    verify(batch).commit()
  }

  @Test
  fun sendPhotoRefusedUploadIsPermissionDenied() = runTest {
    val failure = storageException(StorageException.ERROR_NOT_AUTHORIZED)
    failUpload(failure)

    val error = runCatching { repository.sendPhoto(replied, PHOTO_ID, photo) }.exceptionOrNull()

    assertSame(failure, error)
    assertEquals(AppError.PERMISSION_DENIED, error?.toAppError())
  }

  @Test
  fun sendPhotoCompressionFailureWritesAndUploadsNothing() = runTest {
    val repository =
        FirebaseChatRepository(
            { throw IllegalStateException("Unreadable photo") },
            firestore,
            auth,
            storage,
        )

    val error = runCatching { repository.sendPhoto(replied, PHOTO_ID, photo) }.exceptionOrNull()

    // Not assertSame: coroutine stack-trace recovery may rethrow a copy of it.
    assertTrue(error is IllegalStateException)
    assertEquals("Unreadable photo", error?.message)
    verify(photoMessageRef, never()).get(any<Source>())
    verifyNothingSent()
  }

  @Test
  fun sendPhotoUnreadablePhotoIsNotFoundAndSendsNothing() = runTest {
    val failure = FileNotFoundException("Deleted photo")

    val error = runCatching {
      compressorFailing(failure).sendPhoto(replied, PHOTO_ID, photo)
    }
        .exceptionOrNull()

    assertEquals(AppError.NOT_FOUND, (error as AppException).error)
    assertSame(failure, error.cause)
    verifyNothingSent()
  }

  @Test
  fun sendPhotoWithoutPermissionToReadThePhotoIsNotFoundAndSendsNothing() = runTest {
    val failure = SecurityException("Permission revoked")

    val error = runCatching {
      compressorFailing(failure).sendPhoto(replied, PHOTO_ID, photo)
    }
        .exceptionOrNull()

    assertEquals(AppError.NOT_FOUND, (error as AppException).error)
    assertSame(failure, error.cause)
    verifyNothingSent()
  }

  @Test
  fun sendPhotoSlowCompressionDoesNotCountAgainstTheTimeout() = runTest {
    val repository =
        FirebaseChatRepository(
            {
              delay(10.seconds)
              compressedPhoto
            },
            firestore,
            auth,
            storage,
            photoTimeout = 5.seconds,
        )

    repository.sendPhoto(replied, PHOTO_ID, photo)

    verify(photoRef).putBytes(eq(compressedPhoto), any())
  }

  @Test
  fun sendPhotoRetryWithMessageAndPhotoStoredWritesAndUploadsNothing() = runTest {
    messageAlreadyOnServer()
    val stored = mock<StorageMetadata>()
    whenever(photoRef.metadata).thenReturn(Tasks.forResult(stored))

    repository.sendPhoto(replied, PHOTO_ID, photo, caption = "The drill bits")

    verify(photoRef, never()).putBytes(any(), any())
    verify(firestore, never()).batch()
  }

  @Test
  fun sendPhotoMarksThePhotoUploadedOnlyAfterUploadingIt() = runTest {
    repository.sendPhoto(replied, PHOTO_ID, photo)

    val order = inOrder(batch, photoRef, photoMessageRef)
    order.verify(batch).commit()
    order.verify(photoRef).putBytes(any(), any())
    order.verify(photoMessageRef).update("photoPending", false)
  }

  @Test
  fun sendPhotoWaitsForTheServerToConfirmTheMarkBeforeReturning() = runTest {
    val pendingMark = TaskCompletionSource<Void>()
    whenever(photoMessageRef.update("photoPending", false)).thenReturn(pendingMark.task)
    val sending = async { repository.sendPhoto(replied, PHOTO_ID, photo) }
    runCurrent()

    assertFalse(sending.isCompleted)
    pendingMark.setResult(null)
    sending.await()
  }

  @Test
  fun sendPhotoRetryWithPhotoStoredButNotMarkedOnlyMarksIt() = runTest {
    messageAlreadyOnServer()
    whenever(photoRef.metadata).thenReturn(Tasks.forResult(mock<StorageMetadata>()))

    repository.sendPhoto(replied, PHOTO_ID, photo)

    verify(firestore, never()).batch()
    verify(photoRef, never()).putBytes(any(), any())
    verify(photoMessageRef).update("photoPending", false)
  }

  @Test
  fun sendPhotoRetryWithPhotoAlreadyMarkedUploadedWritesNothing() = runTest {
    messageAlreadyOnServer(pending = false)
    whenever(photoRef.metadata).thenReturn(Tasks.forResult(mock<StorageMetadata>()))

    repository.sendPhoto(replied, PHOTO_ID, photo)

    // The rules allow the mark only once.
    verify(photoMessageRef, never()).update(any<String>(), any())
    verify(firestore, never()).batch()
  }

  @Test
  fun sendPhotoRetryForMessageWithoutPendingFieldDoesNotMarkIt() = runTest {
    messageAlreadyOnServer(pending = null)
    whenever(photoRef.metadata).thenReturn(Tasks.forResult(mock<StorageMetadata>()))

    repository.sendPhoto(replied, PHOTO_ID, photo)

    verify(photoMessageRef, never()).update(any<String>(), any())
  }

  @Test
  fun sendPhotoFailedUploadLeavesThePhotoPending() = runTest {
    failUpload(storageException(StorageException.ERROR_RETRY_LIMIT_EXCEEDED))

    runCatching { repository.sendPhoto(replied, PHOTO_ID, photo) }

    verify(photoMessageRef, never()).update(any<String>(), any())
  }

  @Test
  fun sendPhotoRejectedMarkIsLoggedAndThrown() = runTest {
    val failure =
        FirebaseFirestoreException(
            "Missing or insufficient permissions.",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )
    whenever(photoMessageRef.update("photoPending", false)).thenReturn(Tasks.forException(failure))
    var logged: Exception? = null
    val repository =
        FirebaseChatRepository(photoCompressor, firestore, auth, storage) { logged = it }

    val error = runCatching { repository.sendPhoto(replied, PHOTO_ID, photo) }.exceptionOrNull()

    assertSame(failure, logged)
    assertSame(failure, error)
  }

  @Test
  fun sendPhotoMarkWithNoServerAnswerFailsWithNetworkAfterTimeout() = runTest {
    whenever(photoMessageRef.update("photoPending", false))
        .thenReturn(TaskCompletionSource<Void>().task)

    val error = runCatching {
      shortTimeoutRepository().sendPhoto(replied, PHOTO_ID, photo)
    }
        .exceptionOrNull()

    assertEquals(AppError.NETWORK, (error as AppException).error)
    assertEquals(5_000L, testScheduler.currentTime)
  }

  @Test
  fun sendPhotoFirstTryDoesNotWaitForOtherWrites() = runTest {
    repository.sendPhoto(replied, PHOTO_ID, photo)

    verify(firestore, never()).waitForPendingWrites()
  }

  @Test
  fun sendPhotoRetryWithMessageOnServerDoesNotWaitForOtherWrites() = runTest {
    messageAlreadyOnServer()

    repository.sendPhoto(replied, PHOTO_ID, photo)

    verify(firestore, never()).waitForPendingWrites()
  }

  @Test
  fun sendPhotoRetryAfterQueuedMessageWaitsForItInsteadOfRewritingIt() = runTest {
    val written = TaskCompletionSource<Void>()
    // A server read includes this device's queued write: the message looks written already.
    messageQueuedOnDevice(written.task, then = messageSnapshot(exists = true, pending = true))
    val sending = async { repository.sendPhoto(replied, PHOTO_ID, photo) }
    runCurrent()

    // storage.rules would refuse the upload until the server has the message.
    verify(photoRef, never()).putBytes(any(), any())
    written.setResult(null)
    sending.await()

    // Writing it again would be an update once the queued write lands, which the rules refuse.
    verify(firestore, never()).batch()
    verify(photoRef).putBytes(eq(compressedPhoto), any())
    verify(photoMessageRef).update("photoPending", false)
  }

  @Test
  fun sendPhotoRetryAfterQueuedMessageWasRefusedWritesItAgain() = runTest {
    // The queued write ended, but the server refused it: the message is still not there.
    messageQueuedOnDevice(Tasks.forResult(null), then = messageSnapshot(exists = false))

    repository.sendPhoto(replied, PHOTO_ID, photo)

    verify(batch).commit()
    verify(photoRef).putBytes(eq(compressedPhoto), any())
  }

  @Test
  fun sendPhotoRetryAfterQueuedMarkWaitsForItInsteadOfMarkingAgain() = runTest {
    // The photo was uploaded and its mark queued, then the send timed out.
    messageQueuedOnDevice(
        Tasks.forResult(null),
        queued = messageSnapshot(exists = true, pending = false, queued = true),
        then = messageSnapshot(exists = true, pending = false),
    )
    whenever(photoRef.metadata).thenReturn(Tasks.forResult(mock<StorageMetadata>()))

    repository.sendPhoto(replied, PHOTO_ID, photo)

    verify(firestore).waitForPendingWrites()
    verify(photoMessageRef, never()).update(any<String>(), any())
    verify(photoRef, never()).putBytes(any(), any())
  }

  @Test
  fun sendPhotoQueuedMessageNeverSentFailsWithNetworkAfterTimeout() = runTest {
    messageQueuedOnDevice(TaskCompletionSource<Void>().task)

    val error = runCatching {
      shortTimeoutRepository().sendPhoto(replied, PHOTO_ID, photo)
    }
        .exceptionOrNull()

    assertEquals(AppError.NETWORK, (error as AppException).error)
    assertEquals(5_000L, testScheduler.currentTime)
    verify(firestore, never()).batch()
    verify(photoRef, never()).putBytes(any(), any())
  }

  @Test
  fun sendPhotoUploadsOnlyAfterCheckingNoPhotoIsStored() = runTest {
    repository.sendPhoto(replied, PHOTO_ID, photo)

    val order = inOrder(photoRef)
    order.verify(photoRef).metadata
    order.verify(photoRef).putBytes(any(), any())
  }

  @Test
  fun sendPhotoRethrowsOtherStoredPhotoCheckFailuresWithoutUploading() = runTest {
    val failure = storageException(StorageException.ERROR_NOT_AUTHORIZED)
    whenever(photoRef.metadata).thenReturn(Tasks.forException(failure))

    val error = runCatching { repository.sendPhoto(replied, PHOTO_ID, photo) }.exceptionOrNull()

    assertSame(failure, error)
    verify(photoRef, never()).putBytes(any(), any())
  }

  @Test
  fun sendPhotoUploadWithNoAnswerFailsWithNetworkAfterTimeoutAndCancelsIt() = runTest {
    whenever(uploadTask.isComplete).thenReturn(false)

    val error = runCatching {
      shortTimeoutRepository().sendPhoto(replied, PHOTO_ID, photo)
    }
        .exceptionOrNull()

    assertEquals(AppError.NETWORK, (error as AppException).error)
    assertTrue(error.cause is TimeoutCancellationException)
    assertEquals(5_000L, testScheduler.currentTime)
    verify(uploadTask).cancel()
  }

  @Test
  fun sendPhotoStoredPhotoCheckWithNoAnswerFailsWithNetworkWithoutUploading() = runTest {
    whenever(photoRef.metadata).thenReturn(TaskCompletionSource<StorageMetadata>().task)

    val error = runCatching {
      shortTimeoutRepository().sendPhoto(replied, PHOTO_ID, photo)
    }
        .exceptionOrNull()

    assertEquals(AppError.NETWORK, (error as AppException).error)
    verify(photoRef, never()).putBytes(any(), any())
  }

  @Test
  fun sendPhotoCancelledByCallerCancelsUploadAndPropagatesCancellation() = runTest {
    whenever(uploadTask.isComplete).thenReturn(false)
    var thrown: Throwable? = null
    val sending = launch {
      try {
        repository.sendPhoto(replied, PHOTO_ID, photo)
      } catch (e: Throwable) {
        thrown = e
        throw e
      }
    }
    runCurrent()
    verify(photoRef).putBytes(any(), any())

    sending.cancel()
    runCurrent()

    // What sendPhoto itself threw: a cancelled job always ends with a CancellationException.
    assertTrue(thrown is CancellationException)
    assertFalse(thrown is TimeoutCancellationException)
    verify(uploadTask).cancel()
  }

  @Test
  fun loadMessagePhotoDownloadsUpToTenMegabytesFromItsPath() = runTest {
    val bytes = byteArrayOf(7, 7)
    whenever(photoRef.getBytes(10L * 1024 * 1024)).thenReturn(Tasks.forResult(bytes))

    assertSame(bytes, repository.loadMessagePhoto(PHOTO_PATH))
    verify(storageRoot).child(PHOTO_PATH)
  }

  @Test
  fun loadMessagePhotoPropagatesMissingPhotoAsNotFound() = runTest {
    val failure = storageException(StorageException.ERROR_OBJECT_NOT_FOUND)
    whenever(photoRef.getBytes(any())).thenReturn(Tasks.forException(failure))

    val error = runCatching { repository.loadMessagePhoto(PHOTO_PATH) }.exceptionOrNull()

    assertSame(failure, error)
    assertEquals(AppError.NOT_FOUND, error?.toAppError())
  }

  @Test
  fun loadMessagePhotoPropagatesRefusalAsPermissionDenied() = runTest {
    val failure = storageException(StorageException.ERROR_NOT_AUTHORIZED)
    whenever(photoRef.getBytes(any())).thenReturn(Tasks.forException(failure))

    val error = runCatching { repository.loadMessagePhoto(PHOTO_PATH) }.exceptionOrNull()

    assertSame(failure, error)
    assertEquals(AppError.PERMISSION_DENIED, error?.toAppError())
  }

  @Test
  fun loadMessagePhotoWithNoAnswerFailsWithNetworkAfterTimeout() = runTest {
    whenever(photoRef.getBytes(any())).thenReturn(TaskCompletionSource<ByteArray>().task)

    val error = runCatching {
      shortTimeoutRepository().loadMessagePhoto(PHOTO_PATH)
    }
        .exceptionOrNull()

    assertEquals(AppError.NETWORK, (error as AppException).error)
    assertTrue(error.cause is TimeoutCancellationException)
    assertEquals(5_000L, testScheduler.currentTime)
  }

  @Test
  fun markReadSetsOnlyMyReadTimeToServerTime() {
    whenever(conversationRef.update(any<FieldPath>(), any(), anyVararg()))
        .thenReturn(Tasks.forResult(null))

    repository.markRead("listing_renter")

    verify(conversationRef)
        .update(FieldPath.of("lastReadAt", "renter"), FieldValue.serverTimestamp())
  }

  @Test
  fun markReadByLenderSetsLenderReadTime() {
    signInAs("lender")
    whenever(conversationRef.update(any<FieldPath>(), any(), anyVararg()))
        .thenReturn(Tasks.forResult(null))

    repository.markRead("listing_renter")

    verify(conversationRef)
        .update(FieldPath.of("lastReadAt", "lender"), FieldValue.serverTimestamp())
  }

  @Test
  fun markReadReturnsWhileServerConfirmationIsPending() {
    val pendingWrite = TaskCompletionSource<Void>()
    whenever(conversationRef.update(any<FieldPath>(), any(), anyVararg()))
        .thenReturn(pendingWrite.task)
    var reportedFailure: Exception? = null
    val repository =
        FirebaseChatRepository(photoCompressor, firestore, auth, storage) { reportedFailure = it }

    repository.markRead("listing_renter")

    assertFalse(pendingWrite.task.isComplete)
    assertNull(reportedFailure)
  }

  @Test
  fun markReadLogsLaterWriteFailures() {
    val pendingWrite = TaskCompletionSource<Void>()
    whenever(conversationRef.update(any<FieldPath>(), any(), anyVararg()))
        .thenReturn(pendingWrite.task)
    var reportedFailure: Exception? = null
    val repository =
        FirebaseChatRepository(photoCompressor, firestore, auth, storage) { reportedFailure = it }

    repository.markRead("listing_renter")
    val failure = IllegalStateException("Write rejected")
    pendingWrite.setException(failure)

    assertSame(failure, reportedFailure)
  }

  @Test
  fun markReadWhenSignedOutWritesNothing() {
    whenever(auth.currentUser).thenReturn(null)

    repository.markRead("listing_renter")

    verify(conversations, never()).document(any())
  }

  @Test
  fun observeConversationMapsStoredFieldsAndRemovesListener() = runTest {
    val listener = captureConversationListener()
    val stored = mock<DocumentSnapshot>()
    whenever(stored.exists()).thenReturn(true)
    stubConversation(stored)
    whenever(timestampOf(stored, "lastMessageAt")).thenReturn(Timestamp(1_700_000_000, 0))

    val observed = async { repository.observeConversation("listing_renter").first() }
    runCurrent()
    listener().onEvent(stored, null)

    assertEquals(
        conversation.copy(lastMessageAt = Instant.ofEpochSecond(1_700_000_000)),
        observed.await(),
    )
    verify(registration).remove()
  }

  @Test
  fun observeConversationMapsPreviewAndParticipantsReadTimes() = runTest {
    val listener = captureConversationListener()
    val stored = mock<DocumentSnapshot>()
    whenever(stored.exists()).thenReturn(true)
    stubConversation(stored)
    whenever(timestampOf(stored, "lastMessageAt")).thenReturn(Timestamp(1_700_000_060, 0))
    whenever(stored.getString("lastMessageText")).thenReturn("Still available?")
    whenever(stored.getString("lastMessageSenderUid")).thenReturn("lender")
    whenever(timestampOf(stored, "lastReadAt.renter")).thenReturn(Timestamp(1_700_000_000, 0))
    whenever(timestampOf(stored, "lastReadAt.lender")).thenReturn(Timestamp(1_700_000_060, 0))

    val observed = async { repository.observeConversation("listing_renter").first() }
    runCurrent()
    listener().onEvent(stored, null)

    assertEquals(
        conversation.copy(
            lastMessageAt = Instant.ofEpochSecond(1_700_000_060),
            lastMessageText = "Still available?",
            lastMessageSenderUid = "lender",
            lastReadAt =
                mapOf(
                    "renter" to Instant.ofEpochSecond(1_700_000_000),
                    "lender" to Instant.ofEpochSecond(1_700_000_060),
                ),
        ),
        observed.await(),
    )
  }

  @Test
  fun observeConversationMapsOnlyTheReadTimesThatExist() = runTest {
    val listener = captureConversationListener()
    val stored = mock<DocumentSnapshot>()
    whenever(stored.exists()).thenReturn(true)
    stubConversation(stored)
    whenever(timestampOf(stored, "lastReadAt.lender")).thenReturn(Timestamp(1_700_000_000, 0))

    val observed = async { repository.observeConversation("listing_renter").first() }
    runCurrent()
    listener().onEvent(stored, null)

    assertEquals(
        mapOf("lender" to Instant.ofEpochSecond(1_700_000_000)),
        observed.await()?.lastReadAt,
    )
  }

  @Test
  fun observeConversationMapsConversationStoredBeforePreviewsToDefaults() = runTest {
    val listener = captureConversationListener()
    val stored = mock<DocumentSnapshot>()
    whenever(stored.exists()).thenReturn(true)
    stubConversation(stored)
    whenever(timestampOf(stored, "lastMessageAt")).thenReturn(Timestamp(1_700_000_000, 0))

    val observed = async { repository.observeConversation("listing_renter").first() }
    runCurrent()
    listener().onEvent(stored, null)

    val mapped = observed.await()
    assertNull(mapped?.lastMessageText)
    assertNull(mapped?.lastMessageSenderUid)
    assertEquals(emptyMap<String, Instant>(), mapped?.lastReadAt)
  }

  @Test
  fun observeConversationEmitsNullForMissingConversation() = runTest {
    val listener = captureConversationListener()
    val missing = mock<DocumentSnapshot>()
    whenever(missing.exists()).thenReturn(false)

    val observed = async { repository.observeConversation("listing_renter").first() }
    runCurrent()
    listener().onEvent(missing, null)

    assertNull(observed.await())
  }

  @Test
  fun observeConversationPropagatesListenerErrors() = runTest {
    val listener = captureConversationListener()
    val failure = mock<FirebaseFirestoreException>()

    val observed = observeConversationFailure()
    runCurrent()
    listener().onEvent(null, failure)

    assertSame(failure, observed.await())
  }

  @Test
  fun observeConversationPropagatesMalformedConversation() = runTest {
    val listener = captureConversationListener()
    val malformed = mock<DocumentSnapshot>()
    whenever(malformed.exists()).thenReturn(true)
    whenever(malformed.id).thenReturn("listing_renter")

    val observed = observeConversationFailure()
    runCurrent()
    listener().onEvent(malformed, null)

    val error = observed.await()
    assertTrue(error is IllegalStateException)
    assertEquals("Conversation listing_renter is missing listingId", error?.message)
  }

  @Test
  fun observeMessagesMapsDocumentsOrderedBySentAtAndRemovesListener() = runTest {
    val listener = captureMessagesListener()
    val first = message("m1", "renter", "Hi", 1_700_000_000)
    val second = message("m2", "lender", "Hello", 1_700_000_060)
    val snapshot = mock<QuerySnapshot>()
    whenever(snapshot.documents).thenReturn(listOf(first, second))

    val observed = async { repository.observeMessages("listing_renter").first() }
    runCurrent()
    listener().onEvent(snapshot, null)

    assertEquals(
        listOf(
            ChatMessage("m1", "renter", "Hi", Instant.ofEpochSecond(1_700_000_000)),
            ChatMessage("m2", "lender", "Hello", Instant.ofEpochSecond(1_700_000_060)),
        ),
        observed.await(),
    )
    verify(messages).orderBy("sentAt")
    verify(registration).remove()
  }

  @Test
  fun observeMessagesPropagatesListenerErrors() = runTest {
    val listener = captureMessagesListener()
    val failure = mock<FirebaseFirestoreException>()

    val observed = observeMessagesFailure()
    runCurrent()
    listener().onEvent(null, failure)

    assertSame(failure, observed.await())
  }

  @Test
  fun observeMessagesPropagatesMalformedMessage() = runTest {
    val listener = captureMessagesListener()
    val malformed = mock<DocumentSnapshot>()
    whenever(malformed.id).thenReturn("m1")
    whenever(malformed.getString("senderUid")).thenReturn("renter")
    whenever(malformed.getString("text")).thenReturn("Hi")
    val snapshot = mock<QuerySnapshot>()
    whenever(snapshot.documents).thenReturn(listOf(malformed))

    val observed = observeMessagesFailure()
    runCurrent()
    listener().onEvent(snapshot, null)

    val error = observed.await()
    assertTrue(error is IllegalStateException)
    assertEquals("Message m1 is missing sentAt", error?.message)
  }

  @Test
  fun observeMessagesMapsPhotoWithoutTextToEmptyTextAndItsPath() = runTest {
    val path = "conversations/listing_renter/m1.jpg"

    val observed = observeOnlyMessage(message("m1", "lender", null, 1_700_000_000, path))

    assertEquals(
        ChatMessage("m1", "lender", "", Instant.ofEpochSecond(1_700_000_000), path),
        observed,
    )
  }

  @Test
  fun observeMessagesMapsPhotoWithCaptionToBoth() = runTest {
    val path = "conversations/listing_renter/m1.jpg"

    val observed =
        observeOnlyMessage(message("m1", "renter", "The drill bits", 1_700_000_000, path))

    assertEquals("The drill bits", observed.text)
    assertEquals(path, observed.imagePath)
  }

  @Test
  fun observeMessagesMapsPhotoStillUploadingAsPending() = runTest {
    val path = "conversations/listing_renter/m1.jpg"

    val observed =
        observeOnlyMessage(message("m1", "lender", null, 1_700_000_000, path, photoPending = true))

    assertTrue(observed.photoPending)
  }

  @Test
  fun observeMessagesMapsUploadedPhotoAsNotPending() = runTest {
    val path = "conversations/listing_renter/m1.jpg"

    val observed =
        observeOnlyMessage(message("m1", "lender", null, 1_700_000_000, path, photoPending = false))

    assertFalse(observed.photoPending)
  }

  @Test
  fun observeMessagesMapsPhotoWithoutPendingFieldAsNotPending() = runTest {
    val path = "conversations/listing_renter/m1.jpg"

    val observed = observeOnlyMessage(message("m1", "lender", null, 1_700_000_000, path))

    assertFalse(observed.photoPending)
  }

  @Test
  fun observeMessagesMapsTextMessageWithoutPhoto() = runTest {
    val observed = observeOnlyMessage(message("m1", "renter", "Hi", 1_700_000_000))

    assertEquals("Hi", observed.text)
    assertNull(observed.imagePath)
  }

  @Test
  fun observeMessagesRejectsMessageWithNeitherTextNorPhoto() = runTest {
    val listener = captureMessagesListener()
    val empty = message("m1", "renter", null, 1_700_000_000)
    val snapshot = mock<QuerySnapshot>()
    whenever(snapshot.documents).thenReturn(listOf(empty))

    val observed = observeMessagesFailure()
    runCurrent()
    listener().onEvent(snapshot, null)

    val error = observed.await()
    assertTrue(error is IllegalStateException)
    assertEquals("Message m1 is missing text", error?.message)
  }

  @Test
  fun observeMyConversationsQueriesUserConversationsByRecencyAndRemovesListener() = runTest {
    val listener = captureMyConversationsListener()
    val other = conversation.copy(listingId = "other", listingTitle = "Tent")
    val recent = mock<DocumentSnapshot>()
    stubConversation(recent, other)
    whenever(timestampOf(recent, "lastMessageAt")).thenReturn(Timestamp(1_700_000_060, 0))
    val older = mock<DocumentSnapshot>()
    stubConversation(older)
    whenever(timestampOf(older, "lastMessageAt")).thenReturn(Timestamp(1_700_000_000, 0))
    val snapshot = mock<QuerySnapshot>()
    whenever(snapshot.documents).thenReturn(listOf(recent, older))

    val observed = async { repository.observeMyConversations().first() }
    runCurrent()
    listener().onEvent(snapshot, null)

    assertEquals(
        listOf(
            other.copy(lastMessageAt = Instant.ofEpochSecond(1_700_000_060)),
            conversation.copy(lastMessageAt = Instant.ofEpochSecond(1_700_000_000)),
        ),
        observed.await(),
    )
    verify(conversations).whereArrayContains("participants", "renter")
    verify(myConversations).orderBy("lastMessageAt", Query.Direction.DESCENDING)
    verify(registration).remove()
  }

  @Test
  fun observeMyConversationsRejectsSignedOutUser() = runTest {
    whenever(auth.currentUser).thenReturn(null)

    val error = observeMyConversationsFailure().await()

    assertTrue(error is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.UNAUTHENTICATED,
        (error as FirebaseFirestoreException).code,
    )
    verify(conversations, never()).whereArrayContains(any<String>(), any())
  }

  @Test
  fun observeMyConversationsPropagatesListenerErrors() = runTest {
    val listener = captureMyConversationsListener()
    val failure = mock<FirebaseFirestoreException>()

    val observed = observeMyConversationsFailure()
    runCurrent()
    listener().onEvent(null, failure)

    assertSame(failure, observed.await())
  }

  @Test
  fun observeMyConversationsPropagatesMalformedConversation() = runTest {
    val listener = captureMyConversationsListener()
    val malformed = mock<DocumentSnapshot>()
    whenever(malformed.id).thenReturn("listing_renter")
    val snapshot = mock<QuerySnapshot>()
    whenever(snapshot.documents).thenReturn(listOf(malformed))

    val observed = observeMyConversationsFailure()
    runCurrent()
    listener().onEvent(snapshot, null)

    val error = observed.await()
    assertTrue(error is IllegalStateException)
    assertEquals("Conversation listing_renter is missing listingId", error?.message)
  }

  /** Checks the one batch of a photo reply: the conversation merge and the photo's message. */
  private fun verifyPhotoBatch(preview: String, message: Map<String, Any>) {
    verify(batch)
        .set(
            eq(conversationRef),
            eq(
                mapOf(
                    "listingId" to "listing",
                    "renterUid" to "renter",
                    "lenderUid" to "lender",
                    "participants" to listOf("renter", "lender"),
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastMessageId" to PHOTO_ID,
                    "lastMessageText" to preview,
                    "lastMessageSenderUid" to "renter",
                    "lastReadAt" to mapOf("renter" to FieldValue.serverTimestamp()),
                )
            ),
            eq(SetOptions.merge()),
        )
    verify(batch).set(eq(photoMessageRef), eq(message))
    verify(batch).commit()
    verify(listingRef, never()).get()
  }

  private fun verifyNothingSent() {
    verifyNoInteractions(storage)
    verify(firestore, never()).batch()
  }

  private fun compressorFailing(failure: Exception) =
      FirebaseChatRepository({ throw failure }, firestore, auth, storage)

  private fun shortTimeoutRepository() =
      FirebaseChatRepository(
          photoCompressor,
          firestore,
          auth,
          storage,
          photoTimeout = 5.seconds,
      )

  /**
   * The photo's message as a server read returns it: missing unless it [exists], with [pending] as
   * its photoPending, and [queued] when this device has a write of it the server does not have yet.
   */
  private fun messageSnapshot(
      exists: Boolean,
      pending: Boolean? = null,
      queued: Boolean = false,
  ): DocumentSnapshot {
    val metadata = mock<SnapshotMetadata>()
    whenever(metadata.hasPendingWrites()).thenReturn(queued)
    return mock<DocumentSnapshot>().also {
      whenever(it.exists()).thenReturn(exists)
      whenever(it.getBoolean("photoPending")).thenReturn(pending)
      whenever(it.metadata).thenReturn(metadata)
    }
  }

  /**
   * Puts the photo's message on the server, as after a send whose upload failed. With [pending]
   * false its photo was already marked uploaded; null is a message from before the flag existed.
   */
  private fun messageAlreadyOnServer(pending: Boolean? = true) {
    val written = messageSnapshot(exists = true, pending = pending)
    whenever(photoMessageRef.get(Source.SERVER)).thenReturn(Tasks.forResult(written))
  }

  /**
   * Leaves a write of the photo's message queued on this device, as after a send that timed out
   * while writing it: the first server read returns [queued], which includes it. [written] ends
   * once this device's queued writes have reached the server; reads after that return [then].
   */
  private fun messageQueuedOnDevice(
      written: Task<Void>,
      queued: DocumentSnapshot = messageSnapshot(exists = true, pending = true, queued = true),
      then: DocumentSnapshot = messageSnapshot(exists = true, pending = true),
  ) {
    whenever(photoMessageRef.get(Source.SERVER))
        .thenReturn(Tasks.forResult(queued), Tasks.forResult(then))
    whenever(firestore.waitForPendingWrites()).thenReturn(written)
  }

  /** Makes the photo upload end with [failure]. */
  private fun failUpload(failure: Exception) {
    whenever(uploadTask.isSuccessful).thenReturn(false)
    whenever(uploadTask.exception).thenReturn(failure)
  }

  private fun storageException(errorCode: Int): StorageException = mock {
    on { this.errorCode } doReturn errorCode
  }

  private fun signInAs(uid: String) {
    val user = mock<FirebaseUser>()
    whenever(user.uid).thenReturn(uid)
    whenever(auth.currentUser).thenReturn(user)
  }

  private fun timestampOf(snapshot: DocumentSnapshot, field: String) =
      snapshot.getTimestamp(eq(field), eq(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))

  private fun stubConversation(snapshot: DocumentSnapshot, stored: Conversation = conversation) {
    whenever(snapshot.getString("listingId")).thenReturn(stored.listingId)
    whenever(snapshot.getString("listingTitle")).thenReturn(stored.listingTitle)
    whenever(snapshot.getString("renterUid")).thenReturn(stored.renterUid)
    whenever(snapshot.getString("lenderUid")).thenReturn(stored.lenderUid)
  }

  private fun message(
      id: String,
      senderUid: String,
      text: String?,
      sentAt: Long,
      imagePath: String? = null,
      photoPending: Boolean? = null,
  ) =
      mock<DocumentSnapshot>().also {
        whenever(it.id).thenReturn(id)
        whenever(it.getString("senderUid")).thenReturn(senderUid)
        whenever(it.getString("text")).thenReturn(text)
        whenever(it.getString("imagePath")).thenReturn(imagePath)
        whenever(it.getBoolean("photoPending")).thenReturn(photoPending)
        whenever(timestampOf(it, "sentAt")).thenReturn(Timestamp(sentAt, 0))
      }

  /** Emits [document] as the only message of the conversation and returns what is observed. */
  private suspend fun TestScope.observeOnlyMessage(document: DocumentSnapshot): ChatMessage {
    val listener = captureMessagesListener()
    val snapshot = mock<QuerySnapshot>()
    whenever(snapshot.documents).thenReturn(listOf(document))
    val observed = async { repository.observeMessages("listing_renter").first() }
    runCurrent()
    listener().onEvent(snapshot, null)
    return observed.await().single()
  }

  private fun captureConversationListener(): () -> EventListener<DocumentSnapshot> {
    val captor = argumentCaptor<EventListener<DocumentSnapshot>>()
    whenever(conversationRef.addSnapshotListener(captor.capture())).thenReturn(registration)
    return { captor.firstValue }
  }

  private fun captureMessagesListener(): () -> EventListener<QuerySnapshot> {
    val captor = argumentCaptor<EventListener<QuerySnapshot>>()
    whenever(orderedMessages.addSnapshotListener(captor.capture())).thenReturn(registration)
    return { captor.firstValue }
  }

  private fun captureMyConversationsListener(): () -> EventListener<QuerySnapshot> {
    val captor = argumentCaptor<EventListener<QuerySnapshot>>()
    whenever(recentConversations.addSnapshotListener(captor.capture())).thenReturn(registration)
    return { captor.firstValue }
  }

  private fun TestScope.observeConversationFailure() = async {
    runCatching { repository.observeConversation("listing_renter").first() }.exceptionOrNull()
  }

  private fun TestScope.observeMessagesFailure() = async {
    runCatching { repository.observeMessages("listing_renter").first() }.exceptionOrNull()
  }

  private fun TestScope.observeMyConversationsFailure() = async {
    runCatching { repository.observeMyConversations().first() }.exceptionOrNull()
  }

  private companion object {
    const val PHOTO_ID = "photo-id"
    const val PHOTO_PATH = "conversations/listing_renter/photo-id.jpg"
  }
}
