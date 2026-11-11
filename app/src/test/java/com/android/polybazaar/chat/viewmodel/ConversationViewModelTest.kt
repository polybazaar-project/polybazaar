// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.viewmodel

import com.android.polybazaar.auth.model.User
import com.android.polybazaar.chat.model.ChatMessage
import com.android.polybazaar.chat.model.ChatRules
import com.android.polybazaar.chat.model.Conversation
import com.android.polybazaar.chat.model.FakeChatRepository
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var chat: FakeChatRepository
  private lateinit var profiles: FakeProfileRepository
  private lateinit var listings: FakeListingRepository

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
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun advance() = testDispatcher.scheduler.advanceUntilIdle()

  /** Creates the ViewModel for the drill conversation, seen by [myUid], without running it. */
  private fun viewModel(myUid: String = renter.uid) =
      ConversationViewModel(listing.listingId!!, renter.uid, myUid, chat, profiles, listings)

  /** Creates the ViewModel and lets its loads finish. */
  private fun loadedViewModel(myUid: String = renter.uid) = viewModel(myUid).also { advance() }

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
  fun laterWriteFailure_withBlankDraft_restoresTheText_withoutReloadingTheListing() {
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
    advance()
    assertEquals(1, listings.getListingCalls)
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
  fun permissionDeniedWriteFailure_afterAPause_reloadsTheListingAndDisablesTheFirstMessage() {
    val viewModel = loadedViewModel()
    viewModel.onDraftChange("Hello")
    viewModel.send()
    advance()

    listings.allListings = listOf(listing.copy(isPaused = true))
    chat.writeFailureCallbacks.single()(
        firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    )
    advance()

    val state = viewModel.uiState.value
    assertEquals(2, listings.getListingCalls)
    assertEquals(AppError.PERMISSION_DENIED, state.sendError)
    assertEquals(ConversationListingStatus.PAUSED, state.listingStatus)
    assertEquals("Hello", state.draft)
    assertFalse(state.canSend)
  }

  @Test
  fun secondRefusedSend_whileTheListingReloads_reloadsItAgain() {
    val viewModel = loadedViewModel()
    viewModel.onDraftChange("Hello")
    viewModel.send()
    advance()
    viewModel.onDraftChange("Are you there?")
    viewModel.send()
    advance()
    val (first, second) = chat.writeFailureCallbacks

    listings.gate = CompletableDeferred()
    first(firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED))
    advance()
    listings.allListings = listOf(listing.copy(isPaused = true))
    second(firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED))
    advance()
    assertEquals(3, listings.getListingCalls)

    listings.gate!!.complete(Unit)
    advance()
    assertEquals(ConversationListingStatus.PAUSED, viewModel.uiState.value.listingStatus)
    assertFalse(viewModel.uiState.value.canSend)
  }

  @Test
  fun retryLoad_replacedByAReloadAfterARefusedSend_staysLoadingUntilTheReloadAnswers() {
    val viewModel = loadedViewModel()
    viewModel.onDraftChange("Hello")
    viewModel.send()
    advance()
    listings.failure = UnknownHostException("offline")
    chat.writeFailureCallbacks.single()(
        firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    )
    advance()
    assertEquals(AppError.NETWORK, viewModel.uiState.value.loadError)

    listings.failure = null
    listings.gate = CompletableDeferred()
    viewModel.retry()
    advance()
    assertTrue(viewModel.uiState.value.isLoading)
    viewModel.send()
    advance()
    chat.writeFailureCallbacks.last()(
        firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    )
    advance()
    assertEquals(4, listings.getListingCalls)
    assertTrue(viewModel.uiState.value.isLoading)

    listings.gate!!.complete(Unit)
    advance()
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.loadError)
  }

  @Test
  fun firstMessage_toADeletedListing_reloadsItAndDisablesSendingAndKeepsTheTitle() {
    val viewModel = loadedViewModel()
    listings.allListings = emptyList()
    chat.sendFailure = firestoreError(FirebaseFirestoreException.Code.NOT_FOUND)
    viewModel.onDraftChange("Hello")

    viewModel.send()
    advance()

    val state = viewModel.uiState.value
    assertEquals(AppError.NOT_FOUND, state.sendError)
    assertEquals("Hello", state.draft)
    assertEquals(2, listings.getListingCalls)
    assertEquals(ConversationListingStatus.DELETED, state.listingStatus)
    assertFalse(state.canSend)
    assertEquals("Bosch drill", state.listingTitle)
    assertNull(state.loadError)
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
    assertEquals(2, listings.getListingCalls)
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
    assertEquals(1, listings.getListingCalls)
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
}
