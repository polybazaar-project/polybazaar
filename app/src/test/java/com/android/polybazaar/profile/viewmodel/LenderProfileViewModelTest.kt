// Co-authored-by: GitHub Copilot CLI (Claude Haiku 5.5)
package com.android.polybazaar.profile.viewmodel

import android.net.Uri
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.browse.viewmodel.BrowseListing
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.profile.model.ProfileRepository
import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LenderProfileViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var profiles: FakeProfileRepository
  private lateinit var listings: FakeListingRepository
  private lateinit var viewModel: LenderProfileViewModel

  private val lender = User(uid = "lender-1", email = "", username = "Lender", bio = "Tools")
  private val otherLender = User(uid = "lender-2", email = "", username = "Other")

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    profiles = FakeProfileRepository(mapOf(lender.uid to lender, otherLender.uid to otherLender))
    listings =
        FakeListingRepository(
            allListings =
                listOf(
                    ItemListing(
                        ListingCategory.ITEM_TOOLS,
                        8.0,
                        "Bosch drill set",
                        "",
                        listingId = "drill",
                        ownerUid = lender.uid,
                    ),
                    ItemListing(
                        ListingCategory.ITEM_ELECTRONICS,
                        12.0,
                        "Mini projector",
                        "",
                        listingId = "projector",
                        ownerUid = otherLender.uid,
                    ),
                )
        )
    viewModel = LenderProfileViewModel(profiles, listings)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun loadLender_showsProfileAndOnlyTheirListings() {
    viewModel.loadLender(lender.uid)
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(lender, state.profile)
    assertEquals(
        listOf(
            BrowseListing(
                id = "drill",
                title = "Bosch drill set",
                category = "Tools",
                price = "CHF 8",
            )
        ),
        state.listings,
    )
    assertEquals(false, state.isLoading)
    assertNull(state.error)
  }

  @Test
  fun loadLender_forAnotherLender_dropsThePreviousOne() {
    viewModel.loadLender(lender.uid)
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.loadLender(otherLender.uid)
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(otherLender, state.profile)
    assertEquals(listOf("projector"), state.listings.map { it.id })
  }

  @Test
  fun loadLender_cancelledDelayedRequest_doesNotClearLoadingForTheNewLender() {
    val firstListings = CompletableDeferred<Unit>()
    val secondListings = CompletableDeferred<Unit>()
    listings.gate = firstListings
    viewModel.loadLender(lender.uid)
    testDispatcher.scheduler.advanceUntilIdle()

    listings.gate = secondListings
    viewModel.loadLender(otherLender.uid)
    testDispatcher.scheduler.advanceUntilIdle()

    // The first request was cancelled while it waited; it must not end the new request's loading.
    assertEquals(otherLender, viewModel.uiState.value.profile)
    assertTrue(viewModel.uiState.value.isLoading)

    secondListings.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(false, state.isLoading)
    assertEquals(listOf("projector"), state.listings.map { it.id })
  }

  @Test
  fun loadLender_whenListingsFail_setsErrorAndKeepsProfile() {
    listings.failure = UnknownHostException("offline")

    viewModel.loadLender(lender.uid)
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(lender, state.profile)
    assertTrue(state.listings.isEmpty())
    assertTrue(state.error is AppError)
    assertEquals(false, state.isLoading)
  }

  @Test
  fun loadLender_whenProfileFails_setsError() {
    profiles.failingUids += lender.uid

    viewModel.loadLender(lender.uid)
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.uiState.value.profile)
    assertTrue(viewModel.uiState.value.error is AppError)
  }

  private class FakeProfileRepository(private val users: Map<String, User>) : ProfileRepository {
    val failingUids = mutableSetOf<String>()

    override fun observeProfile(uid: String): Flow<User?> =
        if (uid in failingUids) {
          flow { throw UnknownHostException("offline") }
        } else {
          flowOf(users[uid])
        }

    override suspend fun uploadProfilePhoto(uid: String, photoUri: Uri) = ""

    override suspend fun loadProfilePhoto(photoPath: String) = ByteArray(0)

    override suspend fun removeProfilePhoto(uid: String) = Unit

    override suspend fun updateProfile(uid: String, photoPath: String?, bio: String) = Unit
  }
}
