// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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
  private val repository = FirebaseChatRepository(firestore, auth)
  private val conversation =
      Conversation(
          listingId = "listing",
          listingTitle = "Drill",
          renterUid = "renter",
          lenderUid = "lender",
      )

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
    val repository = FirebaseChatRepository(firestore, auth) { reportedFailure = it }
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
    val repository = FirebaseChatRepository(firestore, auth) { reportedFailure = it }
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

  private fun message(id: String, senderUid: String, text: String, sentAt: Long) =
      mock<DocumentSnapshot>().also {
        whenever(it.id).thenReturn(id)
        whenever(it.getString("senderUid")).thenReturn(senderUid)
        whenever(it.getString("text")).thenReturn(text)
        whenever(timestampOf(it, "sentAt")).thenReturn(Timestamp(sentAt, 0))
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
}
