// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.chat.model.ChatMessage
import com.android.polybazaar.chat.model.ChatRules
import com.android.polybazaar.chat.model.Conversation
import com.android.polybazaar.chat.model.FakeChatRepository
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.PhotoStorage
import com.android.polybazaar.listing.model.TakenPhoto
import com.android.polybazaar.profile.model.FakeProfileRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import java.net.UnknownHostException
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ConversationViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var chat: FakeChatRepository
  private lateinit var profiles: FakeProfileRepository
  private lateinit var listings: FakeListingRepository
  private lateinit var photoStorage: FakePhotoStorage

  private val lender = User(uid = "lender", email = "", username = "Lena")
  private val renter = User(uid = "renter", email = "", username = "Remi")

  private val listing =
      ItemListing(
          ListingCategory.ITEM_TOOLS,
          8.0,
          "Bosch drill",
          "With bits",
          listingId = "drill",
          ownerUid = lender.uid,
      )

  /** The conversation once Remi has sent a first message about the drill. */
  private val conversation =
      Conversation(
          listingId = "drill",
          listingTitle = "Bosch drill",
          renterUid = renter.uid,
          lenderUid = lender.uid,
          lastMessageAt = Instant.ofEpochSecond(100),
      )

  private val hello = ChatMessage("m1", renter.uid, "Hello", Instant.ofEpochSecond(100))
  private val hi = ChatMessage("m2", lender.uid, "Hi!", Instant.ofEpochSecond(200))

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    chat = FakeChatRepository()
    profiles = FakeProfileRepository(listOf(lender, renter).associateBy { it.uid })
    listings = FakeListingRepository(allListings = listOf(listing))
    photoStorage = FakePhotoStorage()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun advance() = testDispatcher.scheduler.advanceUntilIdle()

  /** Creates the ViewModel for the drill conversation, seen by [myUid], without running it. */
  private fun viewModel(myUid: String = renter.uid) =
      ConversationViewModel(
          listing.listingId!!,
          renter.uid,
          myUid,
          chat,
          profiles,
          listings,
          photoStorage,
      )

  /** Creates the ViewModel and lets its loads finish. */
  private fun loadedViewModel(myUid: String = renter.uid) = viewModel(myUid).also { advance() }

  /** Creates the ViewModel, shows its screen and lets its loads finish. */
  private fun visibleViewModel(myUid: String = renter.uid) =
      viewModel(myUid).also {
        it.setVisible(true)
        advance()
      }

  /** Makes the conversation exist with [messages], without letting the ViewModel run yet. */
  private fun givenConversation(
      conversation: Conversation = this.conversation,
      vararg messages: ChatMessage,
  ) {
    chat.myConversations.value = Result.success(listOf(conversation))
    chat.messages.value = mapOf(conversation.id to messages.toList())
  }

  private fun firestoreError(code: FirebaseFirestoreException.Code) =
      FirebaseFirestoreException(code.name, code)

  @Test
  fun init_isLoadingUntilEverythingArrives_thenShowsTheMessagesInOrder() {
    givenConversation(messages = arrayOf(hello, hi))

    val viewModel = viewModel()

    assertEquals(ConversationUiState(myUid = renter.uid), viewModel.uiState.value)
    advance()
    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals(ConversationListingStatus.ACTIVE, state.listingStatus)
    assertEquals(listOf(hello, hi), state.messages)
    assertEquals(conversation, state.conversation)
    assertEquals("Bosch drill", state.listingTitle)
    assertNull(state.loadError)
  }

  @Test
  fun init_staysLoadingUntilTheListingTheConversationAndTheMessagesHaveAllArrived() {
    // Each of the three is held back last once, so dropping any one of them from isLoading fails.
    for (last in 0..2) {
      chat = FakeChatRepository()
      listings = FakeListingRepository(allListings = listOf(listing))
      givenConversation(messages = arrayOf(hello))
      val gates = List(3) { CompletableDeferred<Unit>() }
      listings.gate = gates[0]
      chat.conversationGate = gates[1]
      chat.messagesGate = gates[2]
      val viewModel = loadedViewModel()

      gates
          .filterIndexed { index, _ -> index != last }
          .forEach { gate ->
            gate.complete(Unit)
            advance()
            assertTrue(viewModel.uiState.value.isLoading)
          }
      gates[last].complete(Unit)
      advance()
      assertFalse(viewModel.uiState.value.isLoading)
    }
  }

  @Test
  fun newMessage_isShownLive() {
    givenConversation(messages = arrayOf(hello))
    val viewModel = loadedViewModel()

    chat.messages.value = mapOf(conversation.id to listOf(hello, hi))
    advance()

    assertEquals(listOf(hello, hi), viewModel.uiState.value.messages)
  }

  @Test
  fun noConversationYet_isLoadedAndEmpty() {
    val state = loadedViewModel().uiState.value

    assertFalse(state.isLoading)
    assertNull(state.conversation)
    assertEquals(emptyList<ChatMessage>(), state.messages)
    assertEquals("Bosch drill", state.listingTitle)
  }

  @Test
  fun renter_seesTheLender() {
    val state = loadedViewModel().uiState.value

    assertEquals(lender, state.otherUser)
  }

  @Test
  fun lender_seesTheRenter() {
    givenConversation()

    val state = loadedViewModel(myUid = lender.uid).uiState.value

    assertEquals(renter, state.otherUser)
    assertNull(profiles.lookups[lender.uid])
  }

  @Test
  fun failingProfile_leavesOtherUserNull_withoutBlockingTheChat() {
    profiles.failingUids += lender.uid
    givenConversation(messages = arrayOf(hello))
    val viewModel = loadedViewModel()

    viewModel.onDraftChange("Still available?")
    viewModel.send()
    advance()

    val state = viewModel.uiState.value
    assertNull(state.otherUser)
    assertNull(state.loadError)
    assertEquals(listOf(hello), state.messages)
    assertEquals(listOf(conversation to "Still available?"), chat.sentMessages)
  }

  @Test
  fun failedProfileObservation_isNotRestartedByLaterConversationEmissions() {
    profiles.failingUids += lender.uid
    givenConversation()
    loadedViewModel()

    givenConversation(conversation.copy(lastMessageAt = Instant.ofEpochSecond(300)))
    advance()
    givenConversation(conversation.copy(lastMessageAt = Instant.ofEpochSecond(400)))
    advance()

    assertEquals(1, profiles.lookups[lender.uid])
  }

  @Test
  fun pausedListing_allowsRepliesButNoFirstMessage() {
    listings.allListings = listOf(listing.copy(isPaused = true))
    val viewModel = loadedViewModel()
    viewModel.onDraftChange("Hello")
    assertEquals(ConversationListingStatus.PAUSED, viewModel.uiState.value.listingStatus)
    assertFalse(viewModel.uiState.value.canSend)

    givenConversation()
    advance()
    assertTrue(viewModel.uiState.value.canSend)
    viewModel.send()
    advance()

    assertEquals(listOf(conversation to "Hello"), chat.sentMessages)
  }

  @Test
  fun pausedListingWithoutConversation_blocksTheFirstMessage_untilOneArrives() {
    listings.allListings = listOf(listing.copy(isPaused = true))
    val viewModel = loadedViewModel()
    assertTrue(viewModel.uiState.value.firstMessageBlockedByPause)

    givenConversation()
    advance()

    assertFalse(viewModel.uiState.value.firstMessageBlockedByPause)
  }

  @Test
  fun firstMessageBlockedByPause_isFalseWhileTheConversationLoads() {
    listings.allListings = listOf(listing.copy(isPaused = true))
    chat.conversationGate = CompletableDeferred()
    val viewModel = loadedViewModel()
    assertEquals(ConversationListingStatus.PAUSED, viewModel.uiState.value.listingStatus)
    assertFalse(viewModel.uiState.value.firstMessageBlockedByPause)

    chat.conversationGate!!.complete(Unit)
    advance()

    assertTrue(viewModel.uiState.value.firstMessageBlockedByPause)
  }

  @Test
  fun firstMessageBlockedByPause_isFalseAfterAFailedLoad_andTrueOnceRetryFindsNone() {
    listings.allListings = listOf(listing.copy(isPaused = true))
    chat.myConversations.value =
        Result.failure(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))
    val viewModel = loadedViewModel()
    assertEquals(AppError.NETWORK, viewModel.uiState.value.loadError)
    assertFalse(viewModel.uiState.value.firstMessageBlockedByPause)

    chat.myConversations.value = Result.success(emptyList())
    viewModel.retry()
    advance()

    assertTrue(viewModel.uiState.value.firstMessageBlockedByPause)
  }

  @Test
  fun firstMessageBlockedByPause_turnsFalseWhenTheConversationFailsAfterAnsweringNone() {
    listings.allListings = listOf(listing.copy(isPaused = true))
    val viewModel = loadedViewModel()
    assertTrue(viewModel.uiState.value.firstMessageBlockedByPause)

    chat.myConversations.value =
        Result.failure(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))
    advance()

    assertEquals(AppError.NETWORK, viewModel.uiState.value.loadError)
    assertFalse(viewModel.uiState.value.firstMessageBlockedByPause)
  }

  @Test
  fun firstMessageBlockedByPause_isFalseWhenActiveOrDeleted_orForTheLender() {
    assertFalse(loadedViewModel().uiState.value.firstMessageBlockedByPause)

    listings.allListings = emptyList()
    assertFalse(loadedViewModel().uiState.value.firstMessageBlockedByPause)

    listings.allListings = listOf(listing.copy(isPaused = true))
    val lenderState = loadedViewModel(myUid = lender.uid).uiState.value
    assertEquals(ConversationListingStatus.PAUSED, lenderState.listingStatus)
    assertFalse(lenderState.firstMessageBlockedByPause)
  }

  @Test
  fun firstMessageBlockedByPause_turnsFalseWhenTheListingIsResumed() {
    listings.allListings = listOf(listing.copy(isPaused = true))
    val viewModel = loadedViewModel()
    viewModel.onDraftChange("Hello")
    assertTrue(viewModel.uiState.value.firstMessageBlockedByPause)
    assertFalse(viewModel.uiState.value.canSend)

    listings.allListings = listOf(listing)
    advance()

    assertFalse(viewModel.uiState.value.firstMessageBlockedByPause)
    assertTrue(viewModel.uiState.value.canSend)
  }

  @Test
  fun deletedListing_showsTheConversationReadOnlyWithoutError() {
    listings.allListings = emptyList()
    givenConversation(messages = arrayOf(hello, hi))
    val viewModel = loadedViewModel()

    viewModel.onDraftChange("Hello")

    val state = viewModel.uiState.value
    assertEquals(ConversationListingStatus.DELETED, state.listingStatus)
    assertFalse(state.canSend)
    assertEquals(listOf(hello, hi), state.messages)
    assertEquals("Bosch drill", state.listingTitle)
    assertEquals(lender, state.otherUser)
    assertNull(state.loadError)
    assertFalse(state.isLoading)
  }

  @Test
  fun lender_cannotStartTheConversation() {
    val viewModel = loadedViewModel(myUid = lender.uid)

    viewModel.onDraftChange("Hello")
    viewModel.send()
    advance()

    assertEquals(ConversationListingStatus.ACTIVE, viewModel.uiState.value.listingStatus)
    assertFalse(viewModel.uiState.value.canSend)
    assertTrue(chat.sentMessages.isEmpty())
  }

  @Test
  fun canSend_followsTheMessageLimits() {
    val viewModel = loadedViewModel()

    fun canSend(draft: String): Boolean {
      viewModel.onDraftChange(draft)
      return viewModel.uiState.value.canSend
    }

    assertFalse(canSend(""))
    assertFalse(canSend("   "))
    assertTrue(canSend("a"))
    assertTrue(canSend("a".repeat(ChatRules.MAX_MESSAGE_LENGTH)))
    assertFalse(canSend("a".repeat(ChatRules.MAX_MESSAGE_LENGTH + 1)))
  }

  @Test
  fun canSend_isFalseWhileTheListingIsUnknown() {
    listings.gate = CompletableDeferred()
    val viewModel = loadedViewModel()

    viewModel.onDraftChange("Hello")

    assertNull(viewModel.uiState.value.listingStatus)
    assertFalse(viewModel.uiState.value.canSend)
  }

  @Test
  fun firstMessage_createsTheConversationWithTheCurrentTitle_andClearsTheDraft() {
    val viewModel = loadedViewModel()
    viewModel.onDraftChange("Is it free on Saturday?")

    viewModel.send()
    advance()

    val expected =
        Conversation(
            listingId = "drill",
            listingTitle = "Bosch drill",
            renterUid = renter.uid,
            lenderUid = lender.uid,
            lastMessageAt = null,
        )
    assertEquals(listOf(expected to "Is it free on Saturday?"), chat.sentMessages)
    assertEquals("", viewModel.uiState.value.draft)
    assertFalse(viewModel.uiState.value.canSend)
  }

  @Test
  fun reply_sendsTheObservedConversation() {
    val renamed = conversation.copy(listingTitle = "Old title")
    givenConversation(renamed)
    val viewModel = loadedViewModel(myUid = lender.uid)
    viewModel.onDraftChange("Yes")

    viewModel.send()
    advance()

    assertEquals(listOf(renamed to "Yes"), chat.sentMessages)
  }

  @Test
  fun send_whileASendIsInFlight_doesNothing_andKeepsTextTypedMeanwhile() {
    givenConversation()
    val viewModel = loadedViewModel()
    chat.gate = CompletableDeferred()
    viewModel.onDraftChange("Hello")

    viewModel.send()
    advance()
    viewModel.send()
    viewModel.onDraftChange("Hello again")
    viewModel.send()
    advance()
    assertEquals(listOf(conversation to "Hello"), chat.sentMessages)

    chat.gate!!.complete(Unit)
    advance()
    assertEquals("Hello again", viewModel.uiState.value.draft)

    viewModel.send()
    advance()
    assertEquals(
        listOf(conversation to "Hello", conversation to "Hello again"),
        chat.sentMessages,
    )
  }

  @Test
  fun synchronousFailure_setsSendErrorAndKeepsTheDraft_untilASendGoesThrough() {
    chat.sendFailure = firestoreError(FirebaseFirestoreException.Code.UNAUTHENTICATED)
    val viewModel = loadedViewModel()
    viewModel.onDraftChange("Hello")
    viewModel.send()
    advance()
    assertEquals(AppError.UNAUTHENTICATED, viewModel.uiState.value.sendError)
    assertEquals("Hello", viewModel.uiState.value.draft)

    viewModel.dismissSendError()
    assertNull(viewModel.uiState.value.sendError)

    chat.sendFailure = null
    viewModel.send()
    advance()
    assertEquals(2, chat.sentMessages.size)
    assertEquals("", viewModel.uiState.value.draft)
  }

  @Test
  fun laterWriteFailure_withBlankDraft_restoresTheText() {
    givenConversation()
    val viewModel = loadedViewModel()
    viewModel.onDraftChange("Hello")
    viewModel.send()
    advance()
    assertEquals("", viewModel.uiState.value.draft)

    chat.writeFailureCallbacks.single()(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))

    assertEquals(AppError.NETWORK, viewModel.uiState.value.sendError)
    assertEquals("Hello", viewModel.uiState.value.draft)
    assertTrue(viewModel.uiState.value.canSend)
  }

  @Test
  fun laterWriteFailure_withNewDraft_putsTheTextBeforeIt() {
    givenConversation()
    val viewModel = loadedViewModel()
    viewModel.onDraftChange("Hello")
    viewModel.send()
    advance()
    viewModel.onDraftChange("Are you there?")

    chat.writeFailureCallbacks.single()(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))

    assertEquals("Hello\nAre you there?", viewModel.uiState.value.draft)
  }

  @Test
  fun laterWriteFailure_beforeSendReturns_keepsTheTextOnce() {
    givenConversation()
    val viewModel = loadedViewModel()
    chat.gate = CompletableDeferred()
    viewModel.onDraftChange("Hello")
    viewModel.send()
    advance()

    chat.writeFailureCallbacks.single()(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))
    chat.gate!!.complete(Unit)
    advance()

    assertEquals("Hello", viewModel.uiState.value.draft)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.sendError)
  }

  @Test
  fun refusedFirstMessage_keepsTheDraft_andThePauseFromTheListingDisablesSend() {
    val viewModel = loadedViewModel()
    viewModel.onDraftChange("Hello")
    viewModel.send()
    advance()

    chat.writeFailureCallbacks.single()(
        firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    )
    assertEquals(AppError.PERMISSION_DENIED, viewModel.uiState.value.sendError)
    assertEquals("Hello", viewModel.uiState.value.draft)

    listings.allListings = listOf(listing.copy(isPaused = true))
    advance()

    val state = viewModel.uiState.value
    assertEquals(ConversationListingStatus.PAUSED, state.listingStatus)
    assertEquals("Hello", state.draft)
    assertFalse(state.canSend)
    assertEquals(1, listings.observeListingCalls)
  }

  @Test
  fun firstMessage_toADeletedListing_keepsTheDraft_andTheDeletionDisablesSendingAndKeepsTheTitle() {
    val viewModel = loadedViewModel()
    chat.sendFailure = firestoreError(FirebaseFirestoreException.Code.NOT_FOUND)
    viewModel.onDraftChange("Hello")

    viewModel.send()
    advance()
    listings.allListings = emptyList()
    advance()

    val state = viewModel.uiState.value
    assertEquals(AppError.NOT_FOUND, state.sendError)
    assertEquals("Hello", state.draft)
    assertEquals(ConversationListingStatus.DELETED, state.listingStatus)
    assertFalse(state.canSend)
    assertEquals("Bosch drill", state.listingTitle)
    assertNull(state.loadError)
  }

  @Test
  fun listingPausedResumedOrDeletedWhileOpen_updatesTheStatusAndSend() {
    val viewModel = loadedViewModel()
    viewModel.onDraftChange("Hello")
    assertTrue(viewModel.uiState.value.canSend)

    listings.allListings = listOf(listing.copy(isPaused = true))
    advance()
    assertEquals(ConversationListingStatus.PAUSED, viewModel.uiState.value.listingStatus)
    assertFalse(viewModel.uiState.value.canSend)

    listings.allListings = listOf(listing)
    advance()
    assertEquals(ConversationListingStatus.ACTIVE, viewModel.uiState.value.listingStatus)
    assertTrue(viewModel.uiState.value.canSend)

    listings.allListings = emptyList()
    advance()
    val state = viewModel.uiState.value
    assertEquals(ConversationListingStatus.DELETED, state.listingStatus)
    assertFalse(state.canSend)
    assertEquals("Bosch drill", state.listingTitle)
  }

  @Test
  fun renamedListingWhileOpen_updatesTheTitle() {
    val viewModel = loadedViewModel()

    listings.allListings = listOf(listing.copy(title = "Bosch drill and bits"))
    advance()

    assertEquals("Bosch drill and bits", viewModel.uiState.value.listingTitle)
  }

  @Test
  fun listingObservationFailingWhileOpen_setsLoadErrorAndKeepsTheStatus_andRetryRestartsIt() {
    givenConversation(messages = arrayOf(hello))
    val viewModel = loadedViewModel()

    listings.failure = firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE)
    advance()
    assertEquals(AppError.NETWORK, viewModel.uiState.value.loadError)
    assertEquals(ConversationListingStatus.ACTIVE, viewModel.uiState.value.listingStatus)

    listings.failure = null
    listings.allListings = listOf(listing.copy(isPaused = true))
    viewModel.retry()
    advance()

    val state = viewModel.uiState.value
    assertNull(state.loadError)
    assertEquals(ConversationListingStatus.PAUSED, state.listingStatus)
    assertEquals(2, listings.observeListingCalls)
    assertEquals(1, chat.observeConversationCalls)
    assertEquals(1, chat.observeMessagesCalls)
  }

  @Test
  fun listingLoadFailure_setsLoadError_andRetryRecoversWithoutResubscribing() {
    givenConversation(messages = arrayOf(hello))
    listings.failure = UnknownHostException("offline")
    val viewModel = loadedViewModel()

    assertEquals(AppError.NETWORK, viewModel.uiState.value.loadError)
    assertNull(viewModel.uiState.value.listingStatus)
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(listOf(hello), viewModel.uiState.value.messages)

    listings.failure = null
    viewModel.retry()
    advance()

    val state = viewModel.uiState.value
    assertNull(state.loadError)
    assertEquals(ConversationListingStatus.ACTIVE, state.listingStatus)
    assertEquals(2, listings.observeListingCalls)
    assertEquals(1, chat.observeConversationCalls)
    assertEquals(1, chat.observeMessagesCalls)
  }

  @Test
  fun retry_fromTheFullErrorState_isLoadingUntilTheRestartedLoadAnswers() {
    listings.failure = UnknownHostException("offline")
    val viewModel = loadedViewModel()
    assertEquals(AppError.NETWORK, viewModel.uiState.value.loadError)

    listings.failure = null
    listings.gate = CompletableDeferred()
    viewModel.retry()
    advance()
    assertTrue(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.loadError)

    listings.gate!!.complete(Unit)
    advance()
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun listingNotCachedWhileOffline_stopsLoadingWithANetworkError_andSendIsOff() {
    givenConversation(messages = arrayOf(hello))
    listings.notCached = true
    val viewModel = loadedViewModel()

    viewModel.onDraftChange("Hello")

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals(AppError.NETWORK, state.loadError)
    assertNull(state.listingStatus)
    assertFalse(state.canSend)
    assertEquals(listOf(hello), state.messages)
  }

  @Test
  fun retry_afterTheListingWasNotCached_replacesTheOpenObservation() {
    listings.notCached = true
    val viewModel = loadedViewModel()
    assertEquals(AppError.NETWORK, viewModel.uiState.value.loadError)

    val reload = CompletableDeferred<Unit>()
    listings.gate = reload
    viewModel.retry()
    advance()
    assertTrue(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.loadError)

    // Only the restarted observation, still held back, may answer now.
    listings.notCached = false
    advance()
    assertNull(viewModel.uiState.value.listingStatus)
    assertTrue(viewModel.uiState.value.isLoading)

    reload.complete(Unit)
    advance()
    val state = viewModel.uiState.value
    assertEquals(ConversationListingStatus.ACTIVE, state.listingStatus)
    assertNull(state.loadError)
    assertFalse(state.isLoading)
    assertEquals(2, listings.observeListingCalls)
    assertEquals(1, chat.observeConversationCalls)
    assertEquals(1, chat.observeMessagesCalls)
  }

  @Test
  fun retry_whileTheListingIsStillNotCached_showsTheNetworkErrorAgain() {
    givenConversation(messages = arrayOf(hello))
    listings.notCached = true
    val viewModel = loadedViewModel()

    viewModel.retry()
    advance()

    val state = viewModel.uiState.value
    assertEquals(AppError.NETWORK, state.loadError)
    assertFalse(state.isLoading)
    assertNull(state.listingStatus)
    assertEquals(2, listings.observeListingCalls)
  }

  @Test
  fun listingNotCached_thenTheServerAnswers_clearsTheErrorAndShowsTheStatus() {
    givenConversation(messages = arrayOf(hello))
    listings.allListings = listOf(listing.copy(isPaused = true))
    listings.notCached = true
    val viewModel = loadedViewModel()
    assertEquals(AppError.NETWORK, viewModel.uiState.value.loadError)

    listings.notCached = false
    advance()

    val state = viewModel.uiState.value
    assertNull(state.loadError)
    assertEquals(ConversationListingStatus.PAUSED, state.listingStatus)
    assertFalse(state.isLoading)
    assertEquals(1, listings.observeListingCalls)
  }

  @Test
  fun listingNotCached_thenTheServerSaysItIsDeleted_clearsTheError() {
    givenConversation(messages = arrayOf(hello))
    listings.notCached = true
    val viewModel = loadedViewModel()

    listings.allListings = emptyList()
    listings.notCached = false
    advance()

    val state = viewModel.uiState.value
    assertNull(state.loadError)
    assertEquals(ConversationListingStatus.DELETED, state.listingStatus)
    assertEquals("Bosch drill", state.listingTitle)
  }

  @Test
  fun listingAnsweringAfterNotCached_keepsTheErrorWhileTheMessagesStillFail() {
    givenConversation(messages = arrayOf(hello))
    listings.notCached = true
    chat.messagesFailure.value = firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE)
    val viewModel = loadedViewModel()

    listings.notCached = false
    advance()
    assertEquals(ConversationListingStatus.ACTIVE, viewModel.uiState.value.listingStatus)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.loadError)

    chat.messagesFailure.value = null
    viewModel.retry()
    advance()
    assertNull(viewModel.uiState.value.loadError)
    assertEquals(1, listings.observeListingCalls)
    assertEquals(2, chat.observeMessagesCalls)
  }

  @Test
  fun listingAnsweringAfterNotCached_keepsTheErrorWhileTheConversationStillFails() {
    chat.myConversations.value =
        Result.failure(firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED))
    listings.notCached = true
    val viewModel = loadedViewModel()

    listings.notCached = false
    advance()
    assertEquals(ConversationListingStatus.ACTIVE, viewModel.uiState.value.listingStatus)
    assertNotNull(viewModel.uiState.value.loadError)

    givenConversation()
    viewModel.retry()
    advance()
    assertNull(viewModel.uiState.value.loadError)
    assertEquals(2, chat.observeConversationCalls)
    assertEquals(1, listings.observeListingCalls)
  }

  @Test
  fun listingNotCached_onceTheListingIsKnown_keepsItWithoutAnError() {
    givenConversation(messages = arrayOf(hello))
    val viewModel = loadedViewModel()
    viewModel.onDraftChange("Hello")

    listings.notCached = true
    advance()

    val state = viewModel.uiState.value
    assertNull(state.loadError)
    assertEquals(ConversationListingStatus.ACTIVE, state.listingStatus)
    assertFalse(state.isLoading)
    assertTrue(state.canSend)
  }

  @Test
  fun messagesFailure_setsLoadErrorAndKeepsTheMessages_andRetryRestartsOnlyThatFlow() {
    givenConversation(messages = arrayOf(hello))
    val viewModel = loadedViewModel()

    chat.messagesFailure.value = firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE)
    advance()
    assertEquals(AppError.NETWORK, viewModel.uiState.value.loadError)
    assertEquals(listOf(hello), viewModel.uiState.value.messages)

    chat.messagesFailure.value = null
    chat.messages.value = mapOf(conversation.id to listOf(hello, hi))
    chat.messagesGate = CompletableDeferred()
    viewModel.retry()
    advance()
    assertTrue(viewModel.uiState.value.isLoading)
    assertEquals(listOf(hello), viewModel.uiState.value.messages)

    chat.messagesGate!!.complete(Unit)
    advance()
    val state = viewModel.uiState.value
    assertNull(state.loadError)
    assertFalse(state.isLoading)
    assertEquals(listOf(hello, hi), state.messages)
    assertEquals(2, chat.observeMessagesCalls)
    assertEquals(1, chat.observeConversationCalls)
    assertEquals(1, listings.observeListingCalls)
  }

  @Test
  fun conversationFailure_forTheLenderBeforeItExists_setsLoadError_andRetryRecovers() {
    chat.myConversations.value =
        Result.failure(firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED))
    val viewModel = loadedViewModel(myUid = lender.uid)
    assertEquals(AppError.PERMISSION_DENIED, viewModel.uiState.value.loadError)
    assertFalse(viewModel.uiState.value.isLoading)

    givenConversation()
    viewModel.retry()
    advance()

    val state = viewModel.uiState.value
    assertNull(state.loadError)
    assertEquals(conversation, state.conversation)
    assertEquals(2, chat.observeConversationCalls)
    assertEquals(1, chat.observeMessagesCalls)
  }

  /** The conversation after Lena replied, which Remi has not read yet. */
  private val unreadReply =
      conversation.copy(
          lastMessageAt = hi.sentAt,
          lastMessageText = hi.text,
          lastMessageSenderUid = lender.uid,
      )

  @Test
  fun unreadConversation_isMarkedReadOnceWhenOpened() {
    chat = FakeChatRepository(myUid = renter.uid)
    givenConversation(unreadReply)

    visibleViewModel()

    assertEquals(listOf(unreadReply.id), chat.markedRead)
  }

  @Test
  fun lender_opensAConversationWithAnUnreadMessage_marksItRead() {
    chat = FakeChatRepository(myUid = lender.uid)
    givenConversation(
        conversation.copy(lastMessageText = hello.text, lastMessageSenderUid = renter.uid)
    )

    visibleViewModel(myUid = lender.uid)

    assertEquals(listOf(conversation.id), chat.markedRead)
  }

  @Test
  fun alreadyReadConversation_isNotMarkedRead() {
    givenConversation(unreadReply.copy(lastReadAt = mapOf(renter.uid to hi.sentAt)))

    visibleViewModel()

    assertEquals(emptyList<String>(), chat.markedRead)
  }

  @Test
  fun myOwnLastMessage_isNotMarkedRead() {
    givenConversation(
        conversation.copy(lastMessageText = hello.text, lastMessageSenderUid = renter.uid)
    )

    visibleViewModel()

    assertEquals(emptyList<String>(), chat.markedRead)
  }

  @Test
  fun newMessageFromTheOtherPersonWhileOpen_isMarkedReadAgain() {
    chat = FakeChatRepository(myUid = renter.uid)
    givenConversation(unreadReply)
    visibleViewModel()

    val read = chat.myConversations.value.getOrThrow().single()
    val later = Instant.ofEpochSecond(300)
    chat.myConversations.value =
        Result.success(listOf(read.copy(lastMessageAt = later, lastMessageText = "Tomorrow?")))
    advance()

    assertEquals(listOf(unreadReply.id, unreadReply.id), chat.markedRead)
  }

  @Test
  fun sameUnreadMessageEmittedAgain_isNotMarkedReadTwice() {
    // Without myUid, the fake keeps the conversation unread, like a write still pending.
    givenConversation(unreadReply)
    visibleViewModel()

    chat.myConversations.value = Result.success(listOf(unreadReply.copy(listingTitle = "Drill")))
    advance()

    assertEquals(listOf(unreadReply.id), chat.markedRead)
  }

  @Test
  fun noConversationYet_isNotMarkedRead() {
    visibleViewModel()

    assertEquals(emptyList<String>(), chat.markedRead)
  }

  @Test
  fun unreadConversation_isNotMarkedReadWhileTheScreenIsNotVisible() {
    givenConversation(unreadReply)

    loadedViewModel()

    assertEquals(emptyList<String>(), chat.markedRead)
  }

  @Test
  fun messageArrivedInTheBackground_isMarkedReadWhenTheScreenIsVisibleAgain() {
    chat = FakeChatRepository(myUid = renter.uid)
    givenConversation(unreadReply)
    val viewModel = visibleViewModel()
    viewModel.setVisible(false)

    val read = chat.myConversations.value.getOrThrow().single()
    chat.myConversations.value =
        Result.success(listOf(read.copy(lastMessageAt = Instant.ofEpochSecond(300))))
    advance()
    assertEquals(listOf(unreadReply.id), chat.markedRead)

    viewModel.setVisible(true)

    assertEquals(listOf(unreadReply.id, unreadReply.id), chat.markedRead)
  }

  @Test
  fun visibleAgainWithTheSameUnreadMessage_isNotMarkedReadTwice() {
    // Without myUid, the fake keeps the conversation unread, like a write still pending.
    givenConversation(unreadReply)
    val viewModel = visibleViewModel()

    viewModel.setVisible(false)
    viewModel.setVisible(true)

    assertEquals(listOf(unreadReply.id), chat.markedRead)
  }

  // Photos

  private val galleryPhoto = Uri.parse("content://media/picker/1")
  private val otherGalleryPhoto = Uri.parse("content://media/picker/2")

  /** Creates the ViewModel on the existing drill conversation and lets it load. */
  private fun inConversation(myUid: String = renter.uid): ConversationViewModel {
    givenConversation()
    return loadedViewModel(myUid)
  }

  /** Takes a photo with the camera and attaches it; returns its file. */
  private fun ConversationViewModel.takePhoto(): Uri {
    assertNotNull(startTakingPhoto())
    finishTakingPhoto(saved = true)
    return photoStorage.taken.last().uri
  }

  /** Closes the screen: clears the ViewModel the way its ViewModelStore does. */
  private fun ConversationViewModel.close() {
    val store = ViewModelStore()
    val factory = viewModelFactory { initializer { this@close } }
    ViewModelProvider.create(store, factory)[ConversationViewModel::class]
    store.clear()
  }

  @Test
  fun attachPhoto_fromTheGallery_attachesIt_andItCanBeSentWithoutACaption() {
    val viewModel = inConversation()
    assertTrue(viewModel.uiState.value.canAttachPhoto)

    viewModel.attachPhoto(galleryPhoto)

    assertEquals(galleryPhoto, viewModel.uiState.value.attachedPhoto)
    assertTrue(viewModel.uiState.value.canSend)
  }

  @Test
  fun attachPhoto_beforeTheConversationExists_isIgnored() {
    val viewModel = loadedViewModel()

    viewModel.attachPhoto(galleryPhoto)

    assertFalse(viewModel.uiState.value.canAttachPhoto)
    assertNull(viewModel.uiState.value.attachedPhoto)
    assertFalse(viewModel.uiState.value.canSend)
  }

  @Test
  fun attachPhoto_toADeletedListing_isIgnored() {
    listings.allListings = emptyList()
    val viewModel = inConversation()

    viewModel.attachPhoto(galleryPhoto)

    assertFalse(viewModel.uiState.value.canAttachPhoto)
    assertNull(viewModel.uiState.value.attachedPhoto)
  }

  @Test
  fun attachPhoto_whileTheListingIsUnknown_isIgnored() {
    listings.gate = CompletableDeferred()
    val viewModel = inConversation()

    viewModel.attachPhoto(galleryPhoto)

    assertFalse(viewModel.uiState.value.canAttachPhoto)
    assertNull(viewModel.uiState.value.attachedPhoto)
  }

  @Test
  fun takePhoto_saved_attachesTheTakenFile() {
    val viewModel = inConversation()

    val cameraUri = viewModel.startTakingPhoto()
    viewModel.finishTakingPhoto(saved = true)

    val taken = photoStorage.taken.single()
    assertEquals(taken.cameraUri, cameraUri)
    assertEquals(taken.uri, viewModel.uiState.value.attachedPhoto)
    assertEquals(emptyList<Uri>(), photoStorage.deleted)
  }

  @Test
  fun takePhoto_cancelled_deletesTheFile_andAttachesNothing() {
    val viewModel = inConversation()

    viewModel.startTakingPhoto()
    viewModel.finishTakingPhoto(saved = false)

    assertNull(viewModel.uiState.value.attachedPhoto)
    assertEquals(listOf(photoStorage.taken.single().uri), photoStorage.deleted)
  }

  @Test
  fun startTakingPhoto_againBeforeTheCameraCloses_deletesTheFirstFile() {
    val viewModel = inConversation()

    viewModel.startTakingPhoto()
    viewModel.startTakingPhoto()
    viewModel.finishTakingPhoto(saved = true)

    val (first, second) = photoStorage.taken
    assertEquals(listOf(first.uri), photoStorage.deleted)
    assertEquals(second.uri, viewModel.uiState.value.attachedPhoto)
  }

  @Test
  fun startTakingPhoto_beforeTheConversationExists_returnsNullAndCreatesNoFile() {
    val viewModel = loadedViewModel()

    assertNull(viewModel.startTakingPhoto())
    assertEquals(emptyList<TakenPhoto>(), photoStorage.taken)
  }

  @Test
  fun startTakingPhoto_whenTheFileCannotBeCreated_returnsNull() {
    photoStorage.full = true
    val viewModel = inConversation()

    assertNull(viewModel.startTakingPhoto())
    viewModel.finishTakingPhoto(saved = true)

    assertNull(viewModel.uiState.value.attachedPhoto)
  }

  @Test
  fun finishTakingPhoto_withoutAPhotoBeingTaken_doesNothing() {
    val viewModel = inConversation()

    viewModel.finishTakingPhoto(saved = true)

    assertNull(viewModel.uiState.value.attachedPhoto)
    assertEquals(emptyList<Uri>(), photoStorage.deleted)
  }

  @Test
  fun finishTakingPhoto_onceAPhotoCanNoLongerBeAttached_deletesIt() {
    val viewModel = inConversation()
    viewModel.startTakingPhoto()

    chat.myConversations.value = Result.success(emptyList())
    advance()
    viewModel.finishTakingPhoto(saved = true)

    assertNull(viewModel.uiState.value.attachedPhoto)
    assertEquals(listOf(photoStorage.taken.single().uri), photoStorage.deleted)
  }

  @Test
  fun attachingAnotherPhoto_replacesATakenOne_andDeletesIt() {
    val viewModel = inConversation()
    val taken = viewModel.takePhoto()

    viewModel.attachPhoto(galleryPhoto)

    assertEquals(galleryPhoto, viewModel.uiState.value.attachedPhoto)
    assertEquals(listOf(taken), photoStorage.deleted)
  }

  @Test
  fun attachingAnotherPhoto_replacesAGalleryOne_withoutDeletingIt() {
    val viewModel = inConversation()
    viewModel.attachPhoto(galleryPhoto)

    viewModel.attachPhoto(otherGalleryPhoto)

    assertEquals(otherGalleryPhoto, viewModel.uiState.value.attachedPhoto)
    assertEquals(emptyList<Uri>(), photoStorage.deleted)
  }

  @Test
  fun attachingTheSamePhotoAgain_keepsIt() {
    val viewModel = inConversation()
    viewModel.attachPhoto(galleryPhoto)

    viewModel.attachPhoto(galleryPhoto)

    assertEquals(galleryPhoto, viewModel.uiState.value.attachedPhoto)
  }

  @Test
  fun removeAttachedPhoto_deletesATakenPhoto() {
    val viewModel = inConversation()
    val taken = viewModel.takePhoto()

    viewModel.removeAttachedPhoto()

    assertNull(viewModel.uiState.value.attachedPhoto)
    assertEquals(listOf(taken), photoStorage.deleted)
  }

  @Test
  fun removeAttachedPhoto_keepsAGalleryPhoto() {
    val viewModel = inConversation()
    viewModel.attachPhoto(galleryPhoto)

    viewModel.removeAttachedPhoto()

    assertNull(viewModel.uiState.value.attachedPhoto)
    assertFalse(viewModel.uiState.value.canSend)
    assertEquals(emptyList<Uri>(), photoStorage.deleted)
  }

  @Test
  fun canSend_withAPhoto_acceptsNoCaptionOrAValidOne_butNotATooLongOne() {
    val viewModel = inConversation()
    viewModel.attachPhoto(galleryPhoto)

    fun canSend(caption: String): Boolean {
      viewModel.onDraftChange(caption)
      return viewModel.uiState.value.canSend
    }

    assertTrue(canSend(""))
    assertTrue(canSend("   "))
    assertTrue(canSend("Here it is"))
    assertTrue(canSend("a".repeat(ChatRules.MAX_MESSAGE_LENGTH)))
    assertFalse(canSend("a".repeat(ChatRules.MAX_MESSAGE_LENGTH + 1)))
  }

  @Test
  fun closing_whileTheCameraIsOpen_deletesThePhotoBeingTaken() {
    val viewModel = inConversation()
    viewModel.startTakingPhoto()

    viewModel.close()

    assertEquals(listOf(photoStorage.taken.single().uri), photoStorage.deleted)
  }

  @Test
  fun closing_deletesTheAttachedTakenPhoto() {
    val viewModel = inConversation()
    val taken = viewModel.takePhoto()

    viewModel.close()

    assertEquals(listOf(taken), photoStorage.deleted)
  }

  @Test
  fun closing_keepsAnAttachedGalleryPhoto() {
    val viewModel = inConversation()
    viewModel.attachPhoto(galleryPhoto)

    viewModel.close()

    assertEquals(emptyList<Uri>(), photoStorage.deleted)
  }

  /**
   * Creates photos like the camera folder: "taken_0", "taken_1", … in [taken], or none while
   * [full]. Records the photos deleted.
   */
  private class FakePhotoStorage : PhotoStorage {
    var full = false
    val taken = mutableListOf<TakenPhoto>()
    val deleted = mutableListOf<Uri>()

    override fun newPhoto(): TakenPhoto? {
      if (full) return null
      val index = taken.size
      return TakenPhoto(
              uri = Uri.parse("file:///chat/taken_$index.jpg"),
              cameraUri = Uri.parse("content://camera/taken_$index.jpg"),
          )
          .also(taken::add)
    }

    override fun delete(photo: Uri) {
      deleted += photo
    }

    override fun deleteAllExcept(keep: List<Uri>) = Unit
  }
}
