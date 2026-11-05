// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.viewmodel

import android.net.Uri
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingPendingDetails
import com.android.polybazaar.profile.model.ProfileRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
class ListingDetailViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private val lender = User(uid = "lender", email = "lender@epfl.ch", username = "Lena")

  private val listing =
      ItemListing(
          "Tools",
          8.0,
          "Bosch drill set",
          "With bits",
          120.0,
          listingId = "drill",
          ownerUid = lender.uid,
      )

  private val verifiedViewer =
      User(uid = "viewer", email = "viewer@epfl.ch", username = "Vic", isEmailVerified = true)

  private val listings = FakeListingRepository(allListings = listOf(listing))
  private val profiles = FakeProfileRepository(mapOf(lender.uid to lender))
  private val auth = FakeAuthRepository(verifiedViewer)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** Creates the ViewModel and runs its first load to the end. */
  private fun loadedViewModel(listingId: String = listing.listingId!!): ListingDetailViewModel {
    val viewModel = ListingDetailViewModel(listingId, listings, profiles, auth)
    testDispatcher.scheduler.advanceUntilIdle()
    return viewModel
  }

  @Test
  fun init_loadsTheListingAndItsLender() {
    val state = loadedViewModel().uiState.value

    assertEquals(listing, state.listing)
    assertEquals(lender, state.lender)
    assertFalse(state.isLoading)
    assertNull(state.error)
    assertNull(state.lenderError)
    assertEquals(listOf(lender.uid), profiles.observedUids)
  }

  @Test
  fun init_exposesEmptyPendingDetails() {
    assertEquals(ListingPendingDetails(), loadedViewModel().uiState.value.pendingDetails)
  }

  @Test
  fun loadListing_isLoadingOnlyWhileTheRepositoryAnswers() {
    val gate = CompletableDeferred<Unit>()
    listings.gate = gate

    val viewModel = ListingDetailViewModel(listing.listingId!!, listings, profiles, auth)
    testDispatcher.scheduler.runCurrent()
    assertTrue(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.listing)

    gate.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(listing, viewModel.uiState.value.listing)
  }

  @Test
  fun verifiedViewer_onSomeoneElsesListing_canContactTheLender() {
    assertTrue(loadedViewModel().uiState.value.canContactLender)
  }

  @Test
  fun unverifiedViewer_cannotContactTheLender() {
    auth.currentUser = verifiedViewer.copy(isEmailVerified = false)

    assertFalse(loadedViewModel().uiState.value.canContactLender)
  }

  @Test
  fun owner_cannotContactThemselves() {
    auth.currentUser = lender.copy(isEmailVerified = true)

    assertFalse(loadedViewModel().uiState.value.canContactLender)
  }

  @Test
  fun unknownViewer_cannotContactTheLender_butTheListingIsShownWithoutError() {
    auth.currentUser = null

    val state = loadedViewModel().uiState.value

    assertFalse(state.canContactLender)
    assertEquals(listing, state.listing)
    assertNull(state.error)
  }

  @Test
  fun listingWithoutOwner_observesNoLender_andCannotBeContacted() {
    listings.allListings = listOf(listing.copy(ownerUid = null))

    val state = loadedViewModel().uiState.value

    assertFalse(state.canContactLender)
    assertNull(state.lender)
    assertTrue(profiles.observedUids.isEmpty())
  }

  @Test
  fun missingListing_showsNotFound() {
    val state = loadedViewModel(listingId = "deleted").uiState.value

    assertNull(state.listing)
    assertEquals(AppError.NOT_FOUND, state.error)
    assertFalse(state.isLoading)
    assertTrue(profiles.observedUids.isEmpty())
  }

  @Test
  fun offlineFailure_showsANetworkError() {
    listings.failure = UnknownHostException("firestore.googleapis.com")

    val state = loadedViewModel().uiState.value

    assertEquals(AppError.NETWORK, state.error)
    assertFalse(state.isLoading)
  }

  @Test
  fun loadListing_afterAFailure_retriesAndClearsTheError() {
    listings.failure = UnknownHostException("firestore.googleapis.com")
    val viewModel = loadedViewModel()

    listings.failure = null
    viewModel.loadListing()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listing, viewModel.uiState.value.listing)
    assertNull(viewModel.uiState.value.error)
    assertEquals(2, listings.getListingCalls)
  }

  @Test
  fun failedReload_keepsTheListingShown() {
    val viewModel = loadedViewModel()

    listings.failure = UnknownHostException("firestore.googleapis.com")
    viewModel.loadListing()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listing, viewModel.uiState.value.listing)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
  }

  @Test
  fun reload_doesNotReadAKnownViewerAgain() {
    val viewModel = loadedViewModel()

    viewModel.loadListing()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, auth.getCurrentUserCalls)
  }

  @Test
  fun reload_whenTheViewerCanNoLongerBeRead_keepsTheActionsEnabled() {
    val viewModel = loadedViewModel()

    // Offline: the listing comes from the cache but the viewer read would fail.
    auth.currentUser = null
    viewModel.loadListing()
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value.canContactLender)
  }

  @Test
  fun reload_afterAFailedViewerRead_readsTheViewerAgain() {
    auth.currentUser = null
    val viewModel = loadedViewModel()
    assertFalse(viewModel.uiState.value.canContactLender)

    auth.currentUser = verifiedViewer
    viewModel.loadListing()
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value.canContactLender)
    assertEquals(2, auth.getCurrentUserCalls)
  }

  @Test
  fun loadListing_whileALoadIsRunning_isIgnored() {
    val gate = CompletableDeferred<Unit>()
    listings.gate = gate
    val viewModel = ListingDetailViewModel(listing.listingId!!, listings, profiles, auth)
    testDispatcher.scheduler.runCurrent()

    viewModel.loadListing()
    testDispatcher.scheduler.runCurrent()
    gate.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, listings.getListingCalls)
  }

  @Test
  fun cancelledLoad_isNotShownAsAnError_andAllowsTheNextLoad() {
    listings.failure = CancellationException("Screen closed")
    val viewModel = loadedViewModel()

    assertNull(viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)

    listings.failure = null
    viewModel.loadListing()
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(listing, viewModel.uiState.value.listing)
  }

  @Test
  fun lenderProfileChanges_areShown() {
    val viewModel = loadedViewModel()

    val newBio = lender.copy(bio = "Engineering student, drills and tools")
    profiles.profile(lender.uid).value = newBio
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(newBio, viewModel.uiState.value.lender)
  }

  @Test
  fun lenderFailure_showsALenderError_andKeepsTheListing() {
    profiles.failure =
        FirebaseFirestoreException("Denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)

    val state = loadedViewModel().uiState.value

    assertEquals(listing, state.listing)
    assertNull(state.lender)
    assertNull(state.error)
    assertEquals(AppError.PERMISSION_DENIED, state.lenderError)
  }

  @Test
  fun reload_keepsTheRunningLenderObservation() {
    val viewModel = loadedViewModel()

    viewModel.loadListing()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf(lender.uid), profiles.observedUids)
  }

  @Test
  fun reload_afterALenderFailure_observesTheLenderAgain() {
    profiles.failure = UnknownHostException("firestore.googleapis.com")
    val viewModel = loadedViewModel()

    profiles.failure = null
    viewModel.loadListing()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(lender, viewModel.uiState.value.lender)
    assertNull(viewModel.uiState.value.lenderError)
    assertEquals(2, profiles.observedUids.size)
  }

  /**
   * A [ProfileRepository] whose [observeProfile] emits from one [MutableStateFlow] per uid, or
   * fails with [failure] when it is set.
   */
  private class FakeProfileRepository(initial: Map<String, User>) : ProfileRepository {
    private val flows = initial.mapValues { MutableStateFlow<User?>(it.value) }.toMutableMap()
    var failure: Exception? = null
    val observedUids = mutableListOf<String>()

    fun profile(uid: String): MutableStateFlow<User?> =
        flows.getOrPut(uid) { MutableStateFlow(null) }

    override fun observeProfile(uid: String): Flow<User?> {
      observedUids += uid
      val error = failure ?: return profile(uid)
      return flow { throw error }
    }

    override suspend fun uploadProfilePhoto(uid: String, photoUri: Uri): String = error("Not used")

    override suspend fun loadProfilePhoto(photoPath: String): ByteArray = error("Not used")

    override suspend fun removeProfilePhoto(uid: String) = error("Not used")

    override suspend fun updateProfile(uid: String, photoPath: String?, bio: String) =
        error("Not used")
  }

  /**
   * An [AuthRepository] whose [getCurrentUser] answers [currentUser], or throws when it is null,
   * and counts its calls in [getCurrentUserCalls].
   */
  private class FakeAuthRepository(var currentUser: User?) : AuthRepository {
    var getCurrentUserCalls = 0
      private set

    override suspend fun getCurrentUser(): User {
      getCurrentUserCalls++
      return currentUser ?: throw IllegalStateException("No user is currently logged in")
    }

    override suspend fun signIn(email: String, password: String): Result<User> = error("Not used")

    override suspend fun signUp(email: String, password: String, username: String): Result<User> =
        error("Not used")

    override suspend fun signOut() = error("Not used")

    override suspend fun sendVerificationEmail(): Result<Unit> = error("Not used")

    override suspend fun refreshUser(): Result<User> = error("Not used")

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = error("Not used")

    override suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit> =
        error("Not used")
  }
}
