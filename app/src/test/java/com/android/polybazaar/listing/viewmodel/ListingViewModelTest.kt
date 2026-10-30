// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.viewmodel

import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.listing.model.ServiceListing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListingViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var repository: FakeListingRepository
  private lateinit var viewModel: ListingViewModel

  private val listing =
      ServiceListing(
          category = "Tutoring",
          price = 25.0,
          title = "Math tutoring",
          description = "One-hour session",
      )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = FakeListingRepository()
    viewModel = ListingViewModel(repository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initialState_isIdleWithoutCreatedListing() {
    // A fresh ViewModel must not be creating, hold a listing or show an error.
    assertEquals(ListingUiState(), viewModel.uiState.value)
  }

  @Test
  fun createListing_delegatesListingAndExposesCreatedListing() = runTest {
    val createdListing = listing.copy(listingId = "listing-1")
    repository.result = createdListing

    viewModel.createListing(listing)
    testDispatcher.scheduler.advanceUntilIdle()

    // The listing is passed to the repository and the returned listing is exposed in the state.
    assertEquals(listing, repository.createdListing)
    assertEquals(createdListing, viewModel.uiState.value.createdListing)
    assertFalse(viewModel.uiState.value.isCreating)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun createListing_showsCreatingWhileRepositoryRequestIsPending() = runTest {
    val gate = CompletableDeferred<Listing>()
    repository.pendingResult = gate

    viewModel.createListing(listing)
    testDispatcher.scheduler.runCurrent()

    // While the repository has not answered, the state is "creating" with no result or error.
    assertTrue(viewModel.uiState.value.isCreating)
    assertNull(viewModel.uiState.value.createdListing)
    assertNull(viewModel.uiState.value.errorMessage)

    gate.complete(listing)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @Test
  fun createListing_exposesRepositoryFailure() = runTest {
    repository.failure = IllegalStateException("Storage unavailable")

    viewModel.createListing(listing)
    testDispatcher.scheduler.advanceUntilIdle()

    // A repository failure stops the creation and exposes the exception message.
    assertFalse(viewModel.uiState.value.isCreating)
    assertNull(viewModel.uiState.value.createdListing)
    assertEquals("Storage unavailable", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun createListing_usesFallbackMessageWhenRepositoryFailureHasNoMessage() = runTest {
    repository.failure = IllegalStateException()

    viewModel.createListing(listing)
    testDispatcher.scheduler.advanceUntilIdle()

    // An exception without a message still gives the UI a default error to display.
    assertEquals("Unable to create listing", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun createListing_retryAfterFailureClearsErrorAndExposesNewListing() = runTest {
    repository.failure = IllegalStateException("Storage unavailable")
    viewModel.createListing(listing)
    testDispatcher.scheduler.advanceUntilIdle()

    val createdListing = listing.copy(listingId = "listing-1")
    val gate = CompletableDeferred<Listing>()
    repository.failure = null
    repository.pendingResult = gate
    viewModel.createListing(listing)
    testDispatcher.scheduler.runCurrent()

    // A retry must immediately discard the prior failure while its request is pending.
    assertTrue(viewModel.uiState.value.isCreating)
    assertNull(viewModel.uiState.value.createdListing)
    assertNull(viewModel.uiState.value.errorMessage)

    gate.complete(createdListing)
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isCreating)
    assertEquals(createdListing, viewModel.uiState.value.createdListing)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun createListing_cancellationDoesNotExposeErrorOrLeaveCreationInProgress() = runTest {
    repository.failure = CancellationException()

    viewModel.createListing(listing)
    testDispatcher.scheduler.advanceUntilIdle()

    // Cancellation is control flow, so it must not be rendered as a creation failure.
    assertFalse(viewModel.uiState.value.isCreating)
    assertNull(viewModel.uiState.value.createdListing)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun createListing_ignoresSecondRequestWhileCreating() = runTest {
    val gate = CompletableDeferred<Listing>()
    repository.pendingResult = gate

    viewModel.createListing(listing)
    viewModel.createListing(listing.copy(title = "Physics tutoring"))
    testDispatcher.scheduler.runCurrent()

    // Only the first request reaches the repository while it is still in progress.
    assertEquals(listing, repository.createdListing)

    gate.complete(listing)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @Test
  fun clearCreatedListing_removesConsumedCreationResult() = runTest {
    val createdListing = listing.copy(listingId = "listing-1")
    repository.result = createdListing
    viewModel.createListing(listing)
    testDispatcher.scheduler.advanceUntilIdle()

    // Prevent navigating again when the screen's ViewModel outlives the first navigation.
    viewModel.clearCreatedListing()

    assertNull(viewModel.uiState.value.createdListing)
    assertFalse(viewModel.uiState.value.isCreating)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  private class FakeListingRepository : ListingRepository {
    var createdListing: Listing? = null
    var result: Listing? = null
    var failure: Exception? = null
    var pendingResult: CompletableDeferred<Listing>? = null

    override suspend fun createListing(listing: Listing): Listing {
      createdListing = listing
      failure?.let { throw it }
      return pendingResult?.await() ?: requireNotNull(result) { "Set a result before creating" }
    }

    override suspend fun getMyListings(): Result<List<Listing>> = Result.success(emptyList())

    override suspend fun getAllListings(): Result<List<Listing>> = Result.success(emptyList())

    override suspend fun loadListingImage(imagePath: String) = ByteArray(0)
  }
}
