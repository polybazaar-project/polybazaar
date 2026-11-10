// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.viewmodel

import com.android.polybazaar.auth.model.User
import com.android.polybazaar.chat.model.Conversation
import com.android.polybazaar.chat.model.FakeChatRepository
import com.android.polybazaar.error.model.AppError
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var chat: FakeChatRepository
  private lateinit var profiles: FakeProfileRepository
  private lateinit var viewModel: InboxViewModel

  private val me = User(uid = "me", email = "", username = "Me")
  private val alice = User(uid = "alice", email = "", username = "Alice")
  private val bob = User(uid = "bob", email = "", username = "Bob")

  /** I rent Alice's drill. */
  private val drillWithAlice =
      Conversation(
          listingId = "drill",
          listingTitle = "Bosch drill",
          renterUid = me.uid,
          lenderUid = alice.uid,
          lastMessageAt = Instant.ofEpochSecond(200),
      )

  /** Bob rents my bike. */
  private val bikeWithBob =
      Conversation(
          listingId = "bike",
          listingTitle = "City bike",
          renterUid = bob.uid,
          lenderUid = me.uid,
          lastMessageAt = Instant.ofEpochSecond(100),
      )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    chat = FakeChatRepository()
    profiles = FakeProfileRepository(listOf(me, alice, bob).associateBy { it.uid })
    viewModel = InboxViewModel(chat, profiles)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** Makes the repository emit [conversations], then lets the ViewModel handle them. */
  private fun emit(vararg conversations: Conversation) {
    chat.myConversations.value = Result.success(conversations.toList())
    testDispatcher.scheduler.advanceUntilIdle()
  }

  /** Makes the repository's flow fail with [exception], then lets the ViewModel handle it. */
  private fun fail(exception: Exception) {
    chat.myConversations.value = Result.failure(exception)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  private fun firestoreError(code: FirebaseFirestoreException.Code) =
      FirebaseFirestoreException(code.name, code)

  /** The conversations shown, without the profiles. */
  private fun shownConversations() = viewModel.uiState.value.conversations.map { it.conversation }

  /** Loads the inbox of [me] and lets the first emission through. */
  private fun loadInbox() {
    viewModel.loadInbox(me.uid)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @Test
  fun loadInbox_showsConversationWithTheOtherPersonsProfile() {
    chat.myConversations.value = Result.success(listOf(drillWithAlice))

    loadInbox()

    assertEquals(
        InboxUiState(
            conversations = listOf(InboxConversation(drillWithAlice, alice)),
            isLoading = false,
            error = null,
        ),
        viewModel.uiState.value,
    )
  }

  @Test
  fun loadInbox_isLoadingUntilTheFirstEmissionArrives() {
    chat.myConversations.value = Result.success(listOf(drillWithAlice))

    viewModel.loadInbox(me.uid)

    assertEquals(InboxUiState(isLoading = true), viewModel.uiState.value)
    testDispatcher.scheduler.advanceUntilIdle()
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(listOf(drillWithAlice), shownConversations())
  }

  @Test
  fun loadInbox_keepsTheRepositoryOrder() {
    // Oldest message first, so a ViewModel that sorts by lastMessageAt would swap them.
    val older = bikeWithBob.copy(lastMessageAt = Instant.ofEpochSecond(1))
    val newer = drillWithAlice.copy(lastMessageAt = Instant.ofEpochSecond(2))
    chat.myConversations.value = Result.success(listOf(older, newer))

    loadInbox()

    assertEquals(listOf(older, newer), shownConversations())
  }

  @Test
  fun loadInbox_withNoConversations_showsAnEmptyListWithoutError() {
    loadInbox()

    assertEquals(InboxUiState(isLoading = false), viewModel.uiState.value)
  }

  @Test
  fun laterEmission_replacesTheList() {
    chat.myConversations.value = Result.success(listOf(drillWithAlice, bikeWithBob))
    loadInbox()

    // Bob sends a new message: the bike conversation moves to the top.
    val bikeWithNewMessage = bikeWithBob.copy(lastMessageAt = Instant.ofEpochSecond(300))
    emit(bikeWithNewMessage, drillWithAlice)

    assertEquals(listOf(bikeWithNewMessage, drillWithAlice), shownConversations())
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun signedOutFailure_setsUnauthenticatedAndKeepsTheRows() {
    chat.myConversations.value = Result.success(listOf(drillWithAlice))
    loadInbox()

    fail(firestoreError(FirebaseFirestoreException.Code.UNAUTHENTICATED))

    assertEquals(
        InboxUiState(
            conversations = listOf(InboxConversation(drillWithAlice, alice)),
            isLoading = false,
            error = AppError.UNAUTHENTICATED,
        ),
        viewModel.uiState.value,
    )
  }

  @Test
  fun networkFailureBeforeAnyEmission_setsNetworkAndStopsLoading() {
    chat.myConversations.value = Result.failure(UnknownHostException("offline"))

    loadInbox()

    assertEquals(
        InboxUiState(conversations = emptyList(), isLoading = false, error = AppError.NETWORK),
        viewModel.uiState.value,
    )
  }

  @Test
  fun loadInboxAgain_afterAFailure_clearsTheErrorAndLoadsAgain() {
    chat.myConversations.value = Result.success(listOf(drillWithAlice))
    loadInbox()
    fail(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)

    chat.myConversations.value = Result.success(listOf(bikeWithBob, drillWithAlice))
    viewModel.loadInbox(me.uid)

    assertEquals(InboxUiState(isLoading = true), viewModel.uiState.value)
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(
        InboxUiState(
            conversations =
                listOf(
                    InboxConversation(bikeWithBob, bob),
                    InboxConversation(drillWithAlice, alice),
                ),
            isLoading = false,
            error = null,
        ),
        viewModel.uiState.value,
    )
  }

  @Test
  fun loadInboxAgain_cancelsThePreviousCollection() {
    chat.myConversations.value = Result.success(listOf(drillWithAlice))
    loadInbox()
    assertEquals(1, chat.myConversations.subscriptionCount.value)

    // Load again as Alice: an old collection still running as me would overwrite her rows.
    viewModel.loadInbox(alice.uid)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, chat.myConversations.subscriptionCount.value)
    assertEquals(
        listOf(InboxConversation(drillWithAlice, me)),
        viewModel.uiState.value.conversations,
    )
    // The cancellation of the old collection is not reported as an error.
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun otherPerson_isTheLenderWhenIRentAndTheRenterWhenILend() {
    chat.myConversations.value = Result.success(listOf(drillWithAlice, bikeWithBob))

    loadInbox()

    assertEquals(
        listOf(InboxConversation(drillWithAlice, alice), InboxConversation(bikeWithBob, bob)),
        viewModel.uiState.value.conversations,
    )
    assertNull(profiles.lookups[me.uid])
  }

  @Test
  fun samePersonInTwoConversations_isLookedUpOncePerEmission() {
    // I also rent Alice's tent.
    val tentWithAlice = drillWithAlice.copy(listingId = "tent", listingTitle = "Tent")
    chat.myConversations.value = Result.success(listOf(drillWithAlice, tentWithAlice))

    loadInbox()

    assertEquals(
        listOf(InboxConversation(drillWithAlice, alice), InboxConversation(tentWithAlice, alice)),
        viewModel.uiState.value.conversations,
    )
    assertEquals(mapOf(alice.uid to 1), profiles.lookups)

    emit(tentWithAlice, drillWithAlice)

    assertEquals(mapOf(alice.uid to 2), profiles.lookups)
  }

  @Test
  fun missingProfile_keepsTheRowWithoutOtherUserOrError() {
    val tentWithCarol =
        drillWithAlice.copy(listingId = "tent", listingTitle = "Tent", lenderUid = "carol")
    chat.myConversations.value = Result.success(listOf(tentWithCarol, drillWithAlice))

    loadInbox()

    assertEquals(
        InboxUiState(
            conversations =
                listOf(
                    InboxConversation(tentWithCarol, null),
                    InboxConversation(drillWithAlice, alice),
                ),
            isLoading = false,
            error = null,
        ),
        viewModel.uiState.value,
    )
  }

  @Test
  fun failingProfileLookup_keepsTheRowWithoutOtherUserOrError() {
    profiles.failingUids += bob.uid
    chat.myConversations.value = Result.success(listOf(drillWithAlice, bikeWithBob))

    loadInbox()

    assertEquals(
        InboxUiState(
            conversations =
                listOf(
                    InboxConversation(drillWithAlice, alice),
                    InboxConversation(bikeWithBob, null),
                ),
            isLoading = false,
            error = null,
        ),
        viewModel.uiState.value,
    )
    assertEquals(1, profiles.lookups[bob.uid])
  }

  @Test
  fun loadInboxAgain_whileAProfileLookupWaits_cancelsItWithoutError() {
    profiles.gate = CompletableDeferred()
    chat.myConversations.value = Result.success(listOf(drillWithAlice))
    loadInbox()
    // The rows wait for their profiles, so the first load is still in progress.
    assertEquals(InboxUiState(isLoading = true), viewModel.uiState.value)

    profiles.gate = null
    loadInbox()

    assertEquals(
        InboxUiState(
            conversations = listOf(InboxConversation(drillWithAlice, alice)),
            isLoading = false,
            error = null,
        ),
        viewModel.uiState.value,
    )
    assertEquals(1, chat.myConversations.subscriptionCount.value)
  }
}
