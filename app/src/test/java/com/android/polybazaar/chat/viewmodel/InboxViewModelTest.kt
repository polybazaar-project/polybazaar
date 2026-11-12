// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.viewmodel

import com.android.polybazaar.auth.model.User
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
import kotlinx.coroutines.CancellationException
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
  private lateinit var listings: FakeListingRepository
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
    listings =
        FakeListingRepository(
            listOf(drillWithAlice, bikeWithBob, tentWithAlice).map { listingOf(it) }
        )
    viewModel = InboxViewModel(chat, profiles, listings)
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

  /** The active listing [conversation] is about, under the title it stores. */
  private fun listingOf(conversation: Conversation) =
      ItemListing(
          category = ListingCategory.ITEM_TOOLS,
          price = 1.0,
          title = conversation.listingTitle,
          description = "",
          listingId = conversation.listingId,
      )

  /**
   * The row for [conversation] with [otherUser], its listing active under its stored title and
   * without photos.
   */
  private fun row(conversation: Conversation, otherUser: User?) =
      InboxConversation(
          conversation,
          otherUser,
          conversation.listingTitle,
          ListingStatus.ACTIVE,
          listingPhotoPath = null,
      )

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
            row(bikeWithNewMessage, bob),
            row(drillWithAlice, alice),
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
        loaded(row(drillWithAlice, alice), error = AppError.UNAUTHENTICATED),
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
        loaded(row(bikeWithBob, bob), row(drillWithAlice, alice)),
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
    assertEquals(loaded(row(drillWithAlice, me)), viewModel.uiState.value)
  }

  @Test
  fun otherPerson_isTheLenderWhenIRentAndTheRenterWhenILend() {
    given(drillWithAlice, bikeWithBob)

    loadInbox()

    assertEquals(
        loaded(row(drillWithAlice, alice), row(bikeWithBob, bob)),
        viewModel.uiState.value,
    )
    assertNull(profiles.lookups[me.uid])
  }

  @Test
  fun samePerson_isLookedUpOnceAcrossEmissions() {
    given(drillWithAlice, tentWithAlice)

    loadInbox()

    assertEquals(
        loaded(row(drillWithAlice, alice), row(tentWithAlice, alice)),
        viewModel.uiState.value,
    )
    assertEquals(mapOf(alice.uid to 1), profiles.lookups)

    // A new message in a conversation with Bob: only Bob is new.
    emit(bikeWithBob, tentWithAlice, drillWithAlice)

    assertEquals(mapOf(alice.uid to 1, bob.uid to 1), profiles.lookups)
    assertEquals(
        loaded(
            row(bikeWithBob, bob),
            row(tentWithAlice, alice),
            row(drillWithAlice, alice),
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
        loaded(row(tentWithCarol, null), row(drillWithAlice, alice)),
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
    assertEquals(loaded(row(bikeWithBob, null)), viewModel.uiState.value)

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
        loaded(row(drillWithAlice, alice), row(bikeWithBob, null)),
        viewModel.uiState.value,
    )
    assertEquals(1, profiles.lookups[bob.uid])
  }

  @Test
  fun cancelledProfileLookup_keepsTheRowWithoutOtherUserOrError() {
    // A lookup cancelled on its own, while the inbox is not, must not leave it loading forever.
    profiles.failingUids += bob.uid
    profiles.failure = CancellationException("cancelled")
    given(drillWithAlice, bikeWithBob)

    loadInbox()

    assertEquals(
        loaded(row(drillWithAlice, alice), row(bikeWithBob, null)),
        viewModel.uiState.value,
    )
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

    assertEquals(loaded(row(drillWithAlice, alice)), viewModel.uiState.value)
    assertEquals(1, chat.myConversations.subscriptionCount.value)
  }

  @Test
  fun renamedListing_showsItsCurrentTitle() {
    listings.allListings = listOf(listingOf(drillWithAlice).copy(title = "Bosch drill 18V"))
    given(drillWithAlice)

    loadInbox()

    assertEquals(
        loaded(row(drillWithAlice, alice).copy(listingTitle = "Bosch drill 18V")),
        viewModel.uiState.value,
    )
  }

  @Test
  fun pausedListing_isPaused() {
    listings.allListings = listOf(listingOf(drillWithAlice).copy(isPaused = true))
    given(drillWithAlice)

    loadInbox()

    assertEquals(
        loaded(row(drillWithAlice, alice).copy(listingStatus = ListingStatus.PAUSED)),
        viewModel.uiState.value,
    )
  }

  @Test
  fun deletedListing_keepsTheRowWithTheStoredTitleAndNoError() {
    // The fake fails with NOT_FOUND for an id it does not have.
    val kayakWithBob = bikeWithBob.copy(listingId = "kayak", listingTitle = "Old kayak")
    given(drillWithAlice, kayakWithBob)

    loadInbox()

    assertEquals(
        loaded(
            row(drillWithAlice, alice),
            row(kayakWithBob, bob).copy(listingStatus = ListingStatus.DELETED),
        ),
        viewModel.uiState.value,
    )
  }

  @Test
  fun otherListingFailure_isUnknownWithTheStoredTitleAndNoError() {
    // Renamed, so a row that used the listing would show the new title.
    listings.allListings = listOf(listingOf(drillWithAlice).copy(title = "Bosch drill 18V"))
    listings.failure = firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE)
    given(drillWithAlice)

    loadInbox()

    assertEquals(
        loaded(row(drillWithAlice, alice).copy(listingStatus = ListingStatus.UNKNOWN)),
        viewModel.uiState.value,
    )
  }

  @Test
  fun cancelledListingRead_isUnknownWithTheStoredTitleAndNoError() {
    // A read cancelled on its own, while the inbox is not, must not leave it loading forever.
    listings.allListings = listOf(listingOf(drillWithAlice).copy(title = "Bosch drill 18V"))
    listings.failure = CancellationException("cancelled")
    given(drillWithAlice)

    loadInbox()

    assertEquals(
        loaded(row(drillWithAlice, alice).copy(listingStatus = ListingStatus.UNKNOWN)),
        viewModel.uiState.value,
    )
  }

  @Test
  fun sameListing_isReadOnceAcrossEmissions() {
    val bikeWithCarol = bikeWithBob.copy(renterUid = "carol")
    given(bikeWithBob, bikeWithCarol)

    loadInbox()

    assertEquals(
        loaded(row(bikeWithBob, bob), row(bikeWithCarol, null)),
        viewModel.uiState.value,
    )
    assertEquals(1, listings.getListingCalls)

    // A new message in a conversation about the drill: only the drill is new.
    emit(drillWithAlice, bikeWithCarol, bikeWithBob)

    assertEquals(2, listings.getListingCalls)
    assertEquals(
        loaded(row(drillWithAlice, alice), row(bikeWithCarol, null), row(bikeWithBob, bob)),
        viewModel.uiState.value,
    )
  }

  @Test
  fun failedListingRead_isTriedAgainOnTheNextEmission() {
    listings.failure = firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE)
    given(drillWithAlice)
    loadInbox()
    assertEquals(
        ListingStatus.UNKNOWN,
        viewModel.uiState.value.conversations.single().listingStatus,
    )

    listings.failure = null
    emit(drillWithAlice.copy(lastMessageAt = Instant.ofEpochSecond(300)))

    assertEquals(2, listings.getListingCalls)
    assertEquals(ListingStatus.ACTIVE, viewModel.uiState.value.conversations.single().listingStatus)
  }

  private val alicePhoto = "profiles/alice.jpg"
  private val drillPhoto = "listings/drill/0.jpg"
  private val aliceWithPhoto = alice.copy(photoPath = alicePhoto)

  /** Gives Alice a profile photo and the drill two photos, and starts a new ViewModel on them. */
  private fun givePhotos() {
    profiles = FakeProfileRepository(listOf(me, aliceWithPhoto, bob).associateBy { it.uid })
    listings.allListings =
        listOf(
            listingOf(drillWithAlice).copy(imagePaths = listOf(drillPhoto, "listings/drill/1.jpg")),
            listingOf(tentWithAlice),
        )
    viewModel = InboxViewModel(chat, profiles, listings)
  }

  /** The Storage paths of the photos downloaded so far. */
  private fun photoPaths() = viewModel.uiState.value.photos.keys

  @Test
  fun photos_areKeyedByPathAndArriveAfterTheRows() {
    givePhotos()
    profiles.photoGate = CompletableDeferred()
    given(drillWithAlice)

    loadInbox()

    // The listing's first photo is the row's; Alice's photo is still downloading.
    assertEquals(
        listOf(row(drillWithAlice, aliceWithPhoto).copy(listingPhotoPath = drillPhoto)),
        viewModel.uiState.value.conversations,
    )
    assertEquals(setOf(drillPhoto), photoPaths())

    profiles.photoGate!!.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(setOf(drillPhoto, alicePhoto), photoPaths())
  }

  @Test
  fun downloadedPhoto_isNotDownloadedAgain() {
    givePhotos()
    // Alice is in both conversations, so her photo is in two rows.
    given(drillWithAlice, tentWithAlice)
    loadInbox()

    emit(tentWithAlice, drillWithAlice)

    assertEquals(mapOf(alicePhoto to 1), profiles.photoLoads)
    assertEquals(setOf(drillPhoto, alicePhoto), photoPaths())
  }

  @Test
  fun photoStillDownloading_isNotStartedAgainByALaterEmission() {
    givePhotos()
    profiles.photoGate = CompletableDeferred()
    given(drillWithAlice)
    loadInbox()

    emit(drillWithAlice.copy(lastMessageAt = Instant.ofEpochSecond(300)))
    profiles.photoGate!!.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(mapOf(alicePhoto to 1), profiles.photoLoads)
    assertEquals(setOf(drillPhoto, alicePhoto), photoPaths())
  }

  @Test
  fun photoNoRowUses_isDropped() {
    givePhotos()
    given(drillWithAlice, tentWithAlice)
    loadInbox()
    assertEquals(setOf(drillPhoto, alicePhoto), photoPaths())

    // The drill conversation is gone; Alice is still in the tent one.
    emit(tentWithAlice)

    assertEquals(setOf(alicePhoto), photoPaths())
  }

  @Test
  fun failedPhoto_isNotAnErrorAndIsNotRetriedOnTheNextEmission() {
    givePhotos()
    profiles.failingPhotoPaths += alicePhoto
    given(drillWithAlice)
    loadInbox()

    assertNull(viewModel.uiState.value.error)
    assertEquals(setOf(drillPhoto), photoPaths())

    profiles.failingPhotoPaths.clear()
    emit(drillWithAlice.copy(lastMessageAt = Instant.ofEpochSecond(300)))

    assertEquals(mapOf(alicePhoto to 1), profiles.photoLoads)
    assertEquals(setOf(drillPhoto), photoPaths())
  }

  @Test
  fun failedPhoto_isRetriedOnTheNextLoad() {
    givePhotos()
    profiles.failingPhotoPaths += alicePhoto
    given(drillWithAlice)
    loadInbox()

    profiles.failingPhotoPaths.clear()
    loadInbox()

    assertEquals(mapOf(alicePhoto to 2), profiles.photoLoads)
    assertEquals(setOf(drillPhoto, alicePhoto), photoPaths())
  }

  @Test
  fun cancelledPhoto_staysMissingWithoutError() {
    givePhotos()
    profiles.failingPhotoPaths += alicePhoto
    profiles.failure = CancellationException("cancelled")
    given(drillWithAlice)

    loadInbox()

    assertEquals(
        loaded(row(drillWithAlice, aliceWithPhoto).copy(listingPhotoPath = drillPhoto)),
        viewModel.uiState.value.copy(photos = emptyMap()),
    )
    assertEquals(setOf(drillPhoto), photoPaths())
  }

  @Test
  fun photoDownload_finishesAfterTheFlowFails() {
    givePhotos()
    profiles.photoGate = CompletableDeferred()
    given(drillWithAlice)
    loadInbox()

    fail(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))
    profiles.photoGate!!.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    assertEquals(listOf(drillWithAlice), shownConversations())
    assertEquals(setOf(drillPhoto, alicePhoto), photoPaths())
  }

  @Test
  fun loadInboxAgain_clearsThePhotosAndDownloadsThemAgain() {
    // A changed photo keeps its Storage path, so a photo from the last load may be stale.
    givePhotos()
    given(drillWithAlice)
    loadInbox()

    viewModel.loadInbox(me.uid)

    assertEquals(InboxUiState(isLoading = true), viewModel.uiState.value)
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(mapOf(alicePhoto to 2), profiles.photoLoads)
    assertEquals(setOf(drillPhoto, alicePhoto), photoPaths())
  }

  @Test
  fun openInbox_firstTime_loadsTheInbox() {
    given(drillWithAlice)

    viewModel.openInbox(me.uid)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(loaded(row(drillWithAlice, alice)), viewModel.uiState.value)
  }

  @Test
  fun openInboxAgain_whileItLoads_keepsTheRowsAndPhotos() {
    // Coming back to the Conversations tab: the screen opens the inbox again.
    givePhotos()
    given(drillWithAlice)
    loadInbox()

    viewModel.openInbox(me.uid)

    assertEquals(listOf(drillWithAlice), shownConversations())
    assertFalse(viewModel.uiState.value.isLoading)
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(1, chat.myConversations.subscriptionCount.value)
    assertEquals(mapOf(alicePhoto to 1), profiles.photoLoads)
  }

  @Test
  fun openInboxAgain_afterAFailure_loadsAgainEvenWhileAPhotoDownloads() {
    // The waiting download keeps the failed load running, so only the error tells it apart.
    givePhotos()
    profiles.photoGate = CompletableDeferred()
    given(drillWithAlice)
    loadInbox()
    fail(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)

    profiles.photoGate = null
    given(drillWithAlice)
    viewModel.openInbox(me.uid)
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.uiState.value.error)
    assertEquals(listOf(drillWithAlice), shownConversations())
    assertEquals(setOf(drillPhoto, alicePhoto), photoPaths())
  }

  @Test
  fun openInbox_asAnotherUser_loadsTheirInbox() {
    given(drillWithAlice)
    loadInbox()

    viewModel.openInbox(alice.uid)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, chat.myConversations.subscriptionCount.value)
    assertEquals(loaded(row(drillWithAlice, me)), viewModel.uiState.value)
  }
}
