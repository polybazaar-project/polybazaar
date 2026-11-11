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

  /** I also rent Alice's tent. */
  private val tentWithAlice = drillWithAlice.copy(listingId = "tent", listingTitle = "Tent")

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

  /** Sets what the repository emits, without letting the ViewModel run yet. */
  private fun given(vararg conversations: Conversation) {
    chat.myConversations.value = Result.success(conversations.toList())
  }

  /** Makes the repository emit [conversations], then lets the ViewModel handle them. */
  private fun emit(vararg conversations: Conversation) {
    given(*conversations)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  /** Makes the repository's flow fail with [exception], then lets the ViewModel handle it. */
  private fun fail(exception: Exception) {
    chat.myConversations.value = Result.failure(exception)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  private fun firestoreError(code: FirebaseFirestoreException.Code) =
      FirebaseFirestoreException(code.name, code)

  /** The state once [rows] are shown, with [error] if loading then failed. */
  private fun loaded(vararg rows: InboxConversation, error: AppError? = null) =
      InboxUiState(conversations = rows.toList(), isLoading = false, error = error)

  /** The conversations shown, without the profiles. */
  private fun shownConversations() = viewModel.uiState.value.conversations.map { it.conversation }

  /** Loads the inbox of [me] and lets the first emission through. */
  private fun loadInbox() {
    viewModel.loadInbox(me.uid)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @Test
  fun loadInbox_isLoadingUntilTheFirstEmissionArrives() {
    given(drillWithAlice)

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
    given(older, newer)

    loadInbox()

    assertEquals(listOf(older, newer), shownConversations())
  }

  @Test
  fun loadInbox_withNoConversations_showsAnEmptyListWithoutError() {
    loadInbox()

    assertEquals(loaded(), viewModel.uiState.value)
  }

  @Test
  fun laterEmission_replacesTheList() {
    given(drillWithAlice, bikeWithBob)
    loadInbox()

    // Bob sends a new message: the bike conversation moves to the top.
    val bikeWithNewMessage = bikeWithBob.copy(lastMessageAt = Instant.ofEpochSecond(300))
    emit(bikeWithNewMessage, drillWithAlice)

    assertEquals(
        loaded(
            InboxConversation(bikeWithNewMessage, bob),
            InboxConversation(drillWithAlice, alice),
        ),
        viewModel.uiState.value,
    )
  }

  @Test
  fun signedOutFailure_setsUnauthenticatedAndKeepsTheRows() {
    given(drillWithAlice)
    loadInbox()

    fail(firestoreError(FirebaseFirestoreException.Code.UNAUTHENTICATED))

    assertEquals(
        loaded(InboxConversation(drillWithAlice, alice), error = AppError.UNAUTHENTICATED),
        viewModel.uiState.value,
    )
  }

  @Test
  fun networkFailureBeforeAnyEmission_setsNetworkAndStopsLoading() {
    chat.myConversations.value = Result.failure(UnknownHostException("offline"))

    loadInbox()

    assertEquals(loaded(error = AppError.NETWORK), viewModel.uiState.value)
  }

  @Test
  fun loadInboxAgain_afterAFailure_clearsTheErrorAndLoadsAgain() {
    given(drillWithAlice)
    loadInbox()
    fail(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)

    given(bikeWithBob, drillWithAlice)
    viewModel.loadInbox(me.uid)

    assertEquals(InboxUiState(isLoading = true), viewModel.uiState.value)
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(
        loaded(InboxConversation(bikeWithBob, bob), InboxConversation(drillWithAlice, alice)),
        viewModel.uiState.value,
    )
  }

  @Test
  fun loadInboxAgain_cancelsThePreviousCollection() {
    given(drillWithAlice)
    loadInbox()
    assertEquals(1, chat.myConversations.subscriptionCount.value)

    // Load again as Alice: an old collection still running as me would overwrite her rows. The
    // cancellation of the old collection is not reported as an error.
    viewModel.loadInbox(alice.uid)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, chat.myConversations.subscriptionCount.value)
    assertEquals(loaded(InboxConversation(drillWithAlice, me)), viewModel.uiState.value)
  }

  @Test
  fun otherPerson_isTheLenderWhenIRentAndTheRenterWhenILend() {
    given(drillWithAlice, bikeWithBob)

    loadInbox()

    assertEquals(
        loaded(InboxConversation(drillWithAlice, alice), InboxConversation(bikeWithBob, bob)),
        viewModel.uiState.value,
    )
    assertNull(profiles.lookups[me.uid])
  }

  @Test
  fun samePerson_isLookedUpOnceAcrossEmissions() {
    given(drillWithAlice, tentWithAlice)

    loadInbox()

    assertEquals(
        loaded(InboxConversation(drillWithAlice, alice), InboxConversation(tentWithAlice, alice)),
        viewModel.uiState.value,
    )
    assertEquals(mapOf(alice.uid to 1), profiles.lookups)

    // A new message in a conversation with Bob: only Bob is new.
    emit(bikeWithBob, tentWithAlice, drillWithAlice)

    assertEquals(mapOf(alice.uid to 1, bob.uid to 1), profiles.lookups)
    assertEquals(
        loaded(
            InboxConversation(bikeWithBob, bob),
            InboxConversation(tentWithAlice, alice),
            InboxConversation(drillWithAlice, alice),
        ),
        viewModel.uiState.value,
    )
  }

  @Test
  fun missingProfile_keepsTheRowWithoutOtherUserOrError() {
    val tentWithCarol = tentWithAlice.copy(lenderUid = "carol")
    given(tentWithCarol, drillWithAlice)

    loadInbox()

    assertEquals(
        loaded(InboxConversation(tentWithCarol, null), InboxConversation(drillWithAlice, alice)),
        viewModel.uiState.value,
    )

    // A missing profile is a real answer: it is not looked up again.
    emit(drillWithAlice, tentWithCarol)

    assertEquals(1, profiles.lookups["carol"])
  }

  @Test
  fun failedProfileLookup_isTriedAgainOnTheNextEmission() {
    profiles.failingUids += bob.uid
    given(bikeWithBob)
    loadInbox()
    assertEquals(loaded(InboxConversation(bikeWithBob, null)), viewModel.uiState.value)

    profiles.failingUids -= bob.uid
    emit(bikeWithBob.copy(lastMessageAt = Instant.ofEpochSecond(300)))

    assertEquals(2, profiles.lookups[bob.uid])
    assertEquals(bob, viewModel.uiState.value.conversations.single().otherUser)
  }

  @Test
  fun failingProfileLookup_keepsTheRowWithoutOtherUserOrError() {
    profiles.failingUids += bob.uid
    given(drillWithAlice, bikeWithBob)

    loadInbox()

    assertEquals(
        loaded(InboxConversation(drillWithAlice, alice), InboxConversation(bikeWithBob, null)),
        viewModel.uiState.value,
    )
    assertEquals(1, profiles.lookups[bob.uid])
  }

  @Test
  fun loadInboxAgain_whileAProfileLookupWaits_cancelsItWithoutError() {
    profiles.gate = CompletableDeferred()
    given(drillWithAlice)
    loadInbox()
    // The rows wait for their profiles, so the first load is still in progress.
    assertEquals(InboxUiState(isLoading = true), viewModel.uiState.value)

    profiles.gate = null
    loadInbox()

    assertEquals(loaded(InboxConversation(drillWithAlice, alice)), viewModel.uiState.value)
    assertEquals(1, chat.myConversations.subscriptionCount.value)
  }
}
