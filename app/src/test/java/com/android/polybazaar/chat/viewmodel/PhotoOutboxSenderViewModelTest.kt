// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.viewmodel

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.chat.model.Conversation
import com.android.polybazaar.chat.model.FakeChatRepository
import com.android.polybazaar.chat.model.FakePhotoOutbox
import com.android.polybazaar.chat.model.QueuedPhoto
import com.android.polybazaar.connectivity.model.FakeConnectivityRepository
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PhotoOutboxSenderViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private val renter = "renter"
  private val other = "other"

  private val drill =
      Conversation(
          listingId = "drill",
          listingTitle = "Bosch drill",
          renterUid = renter,
          lenderUid = "lender",
          lastMessageAt = Instant.ofEpochSecond(100),
      )
  private val bike = drill.copy(listingId = "bike", listingTitle = "City bike")

  private val chat = FakeChatRepository(conversations = listOf(drill, bike))
  private val outbox = FakePhotoOutbox(senderUid = renter)
  private val connectivity = FakeConnectivityRepository(online = true)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun advance() = testDispatcher.scheduler.advanceUntilIdle()

  /** The sender for [uid] signed in, without running it yet. */
  private fun sender(uid: String? = renter) =
      PhotoOutboxSenderViewModel(outbox, chat, connectivity).also { it.setSignedInUser(uid) }

  /** A photo of [uid] queued earlier in [conversation], as after a restart. */
  private fun queued(
      messageId: String,
      conversation: Conversation = drill,
      uid: String = renter,
      caption: String? = null,
      error: AppError? = null,
  ) =
      QueuedPhoto(
              messageId,
              conversation.id,
              uid,
              Uri.parse("file:///outbox/$messageId/photo.jpg"),
              caption,
              error = error,
          )
          .also(outbox::add)

  private fun sentIds() = chat.sentPhotos.map { it.messageId }

  private fun queuedIds() = outbox.queued.value.map { it.messageId }

  private fun goOfflineThenOnline() {
    connectivity.isOnline.value = false
    advance()
    connectivity.isOnline.value = true
    advance()
  }

  @Test
  fun startOnline_sendsEachPhotoWithItsIdAndCaption_oldestFirst_andRemovesIt() {
    val first = queued("m1", caption = "The bits")
    val second = queued("m2", conversation = bike)

    sender()
    advance()

    assertEquals(
        listOf(
            FakeChatRepository.SentPhoto(drill, "m1", first.photo, "The bits"),
            FakeChatRepository.SentPhoto(bike, "m2", second.photo, null),
        ),
        chat.sentPhotos,
    )
    assertTrue(outbox.queued.value.isEmpty())
  }

  @Test
  fun photos_areSentOneAtATime() {
    queued("m1")
    queued("m2")
    chat.gate = CompletableDeferred()

    sender()
    advance()

    assertEquals(listOf("m1"), sentIds())
    assertEquals(listOf(true, false), outbox.queued.value.map { it.sending })

    chat.gate!!.complete(Unit)
    advance()
    assertEquals(listOf("m1", "m2"), sentIds())
    assertTrue(outbox.queued.value.isEmpty())
  }

  @Test
  fun startOffline_sendsNothing_untilTheConnectionReturns() {
    connectivity.isOnline.value = false
    queued("m1")

    sender()
    advance()

    assertTrue(chat.sentPhotos.isEmpty())
    assertEquals(listOf("m1"), queuedIds())

    connectivity.isOnline.value = true
    advance()

    assertEquals(listOf("m1"), sentIds())
    assertTrue(outbox.queued.value.isEmpty())
  }

  @Test
  fun withNobodySignedIn_sendsNothing_untilSomeoneSignsIn() {
    queued("m1")

    val sender = sender(uid = null)
    advance()
    assertTrue(chat.sentPhotos.isEmpty())

    sender.setSignedInUser(renter)
    advance()
    assertEquals(listOf("m1"), sentIds())
  }

  @Test
  fun aPhotoQueuedWhileOnline_isSentWithoutAReconnect() {
    sender()
    advance()

    queued("m1")
    advance()

    assertEquals(listOf("m1"), sentIds())
    assertTrue(outbox.queued.value.isEmpty())
  }

  @Test
  fun anotherUsersPhotos_areLeftAlone_untilTheySignIn() {
    queued("mine")
    queued("theirs", uid = other)

    val sender = sender()
    advance()

    assertEquals(listOf("mine"), sentIds())
    assertEquals(listOf("theirs"), queuedIds())

    // The other user signs in on the same phone: only then are their photos sent.
    sender.setSignedInUser(other)
    advance()
    assertEquals(listOf("mine", "theirs"), sentIds())
    assertTrue(outbox.queued.value.isEmpty())
  }

  @Test
  fun networkFailure_keepsThePhoto_andDoesNotRetryItWhileStillOnline() {
    queued("m1")
    chat.sendFailure = AppException(AppError.NETWORK)

    sender()
    advance()
    // Another photo queued meanwhile changes the outbox, but must not send m1 again.
    chat.sendFailure = null
    queued("m2")
    advance()

    assertEquals(listOf("m1", "m2"), sentIds())
    val kept = outbox.queued.value.single()
    assertEquals("m1", kept.messageId)
    assertNull(kept.error)
    assertFalse(kept.sending)
  }

  @Test
  fun networkFailure_isSentAgainWithTheSameId_onTheNextReconnect() {
    queued("m1")
    chat.sendFailure = AppException(AppError.NETWORK)
    sender()
    advance()
    chat.sendFailure = null

    goOfflineThenOnline()

    assertEquals(listOf("m1", "m1"), sentIds())
    assertTrue(outbox.queued.value.isEmpty())
  }

  @Test
  fun signedOutDuringTheSend_keepsThePhotoForLater() {
    queued("m1")
    chat.sendFailure = AppException(AppError.UNAUTHENTICATED)

    sender()
    advance()

    assertEquals(listOf("m1"), sentIds())
    assertNull(outbox.queued.value.single().error)
  }

  @Test
  fun refusal_marksThePhotoFailed_andNeverRetriesIt() {
    queued("m1")
    chat.sendFailure = AppException(AppError.PERMISSION_DENIED)

    sender()
    advance()
    chat.sendFailure = null
    goOfflineThenOnline()

    assertEquals(listOf("m1"), sentIds())
    val failed = outbox.queued.value.single()
    assertEquals(AppError.PERMISSION_DENIED, failed.error)
    assertFalse(failed.sending)
  }

  @Test
  fun notFound_marksThePhotoFailed_andNeverRetriesIt() {
    queued("m1")
    chat.sendFailure = AppException(AppError.NOT_FOUND)

    sender()
    advance()
    chat.sendFailure = null
    goOfflineThenOnline()

    assertEquals(listOf("m1"), sentIds())
    assertEquals(AppError.NOT_FOUND, outbox.queued.value.single().error)
  }

  @Test
  fun missingConversation_marksThePhotoNotFound_withoutSendingIt() {
    queued("m1", conversation = drill.copy(listingId = "deleted"))

    sender()
    advance()

    assertTrue(chat.sentPhotos.isEmpty())
    val failed = outbox.queued.value.single()
    assertEquals(AppError.NOT_FOUND, failed.error)
    assertFalse(failed.sending)
  }

  @Test
  fun aPhotoThatFailedBefore_isSkipped() {
    queued("failed", error = AppError.PERMISSION_DENIED)
    queued("waiting")

    sender()
    advance()

    assertEquals(listOf("waiting"), sentIds())
    assertEquals(listOf("failed"), queuedIds())
  }

  @Test
  fun goingOfflineDuringASend_cancelsIt_releasesThePhoto_andSendsItWithTheSameIdOnReconnect() {
    queued("m1")
    chat.gate = CompletableDeferred()
    sender()
    advance()
    assertTrue(outbox.queued.value.single().sending)

    connectivity.isOnline.value = false
    advance()

    val kept = outbox.queued.value.single()
    assertFalse(kept.sending)
    assertNull(kept.error)

    chat.gate = null
    connectivity.isOnline.value = true
    advance()
    assertEquals(listOf("m1", "m1"), sentIds())
    assertTrue(outbox.queued.value.isEmpty())
  }

  @Test
  fun signingOutDuringASend_cancelsIt_andKeepsThePhoto() {
    queued("m1")
    chat.gate = CompletableDeferred()
    val sender = sender()
    advance()

    sender.setSignedInUser(null)
    advance()
    chat.gate!!.complete(Unit)
    advance()

    val kept = outbox.queued.value.single()
    assertEquals("m1", kept.messageId)
    assertFalse(kept.sending)
    assertNull(kept.error)
  }

  @Test
  fun aQuickOfflineOnlineDuringAPass_doesNotStartASecondOne() {
    queued("m1")
    queued("m2")
    chat.gate = CompletableDeferred()
    sender()
    advance()

    // Both changes before the sender runs, as two quick connectivity callbacks.
    connectivity.isOnline.value = false
    connectivity.isOnline.value = true
    advance()
    chat.gate!!.complete(Unit)
    advance()

    assertEquals(listOf("m1", "m2"), sentIds())
    assertTrue(outbox.queued.value.isEmpty())
  }

  @Test
  fun aReconnectAfterACancelledPass_sendsEachPhotoOnceMore_neverTwiceAtOnce() {
    queued("m1")
    queued("m2")
    chat.gate = CompletableDeferred()
    sender()
    advance()

    goOfflineThenOnline()
    // The new pass waits on the same send; no other photo has started meanwhile.
    assertEquals(listOf("m1", "m1"), sentIds())
    chat.gate!!.complete(Unit)
    advance()

    assertEquals(listOf("m1", "m1", "m2"), sentIds())
    assertTrue(outbox.queued.value.isEmpty())
  }

  @Test
  fun aPhotoQueuedDuringAPass_isSentOnce() {
    queued("m1")
    chat.gate = CompletableDeferred()
    sender()
    advance()

    queued("m2")
    advance()
    chat.gate!!.complete(Unit)
    advance()

    assertEquals(listOf("m1", "m2"), sentIds())
    assertTrue(outbox.queued.value.isEmpty())
  }
}
