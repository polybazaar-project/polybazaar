// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.toolbox.viewmodel

import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ServiceListing
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class ToolboxViewModelTest {

  private val myListings =
      listOf(
          ItemListing("Tools", 8.0, "Bosch drill set", "With bits", 120.0, listingId = "drill"),
          ServiceListing("Tutoring", 25.0, "Math tutoring", "One hour", listingId = "math"),
      )

  private val repository = FakeListingRepository()

  @Before
  fun setUp() {
    // init loads the listings on Dispatchers.Main; run it before each test's assertions.
    Dispatchers.setMain(UnconfinedTestDispatcher())
    repository.myListings = myListings
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun init_loadsMyListings() {
    val viewModel = ToolboxViewModel(repository)

    val state = viewModel.uiState.value
    assertEquals(myListings, state.listings)
    assertFalse(state.isLoading)
    assertNull(state.error)
    assertEquals(1, repository.getMyListingsCalls)
    // Browse's query is not used for the Toolbox.
    assertEquals(0, repository.getAllListingsCalls)
  }

  @Test
  fun init_withNoListings_showsAnEmptyToolboxWithoutError() {
    repository.myListings = emptyList()

    val viewModel = ToolboxViewModel(repository)

    assertEquals(emptyList<Listing>(), viewModel.uiState.value.listings)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun loadListings_isLoadingOnlyWhileTheRepositoryAnswers() {
    val gate = CompletableDeferred<Unit>()
    repository.gate = gate

    val viewModel = ToolboxViewModel(repository)
    assertTrue(viewModel.uiState.value.isLoading)
    assertEquals(emptyList<Listing>(), viewModel.uiState.value.listings)

    gate.complete(Unit)
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(myListings, viewModel.uiState.value.listings)
  }

  @Test
  fun loadListings_whileALoadIsRunning_isIgnored() {
    val gate = CompletableDeferred<Unit>()
    repository.gate = gate
    val viewModel = ToolboxViewModel(repository)

    viewModel.loadListings()
    viewModel.loadListings()

    assertEquals(1, repository.getMyListingsCalls)
    gate.complete(Unit)
    assertEquals(myListings, viewModel.uiState.value.listings)
  }

  @Test
  fun loadListings_onFailure_setsTheErrorAndStopsLoading() {
    repository.failure = UnknownHostException("offline")

    val viewModel = ToolboxViewModel(repository)

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals(AppError.NETWORK, state.error)
    assertEquals(emptyList<Listing>(), state.listings)
  }

  @Test
  fun loadListings_onFailure_keepsTheListingsAlreadyShown() {
    val viewModel = ToolboxViewModel(repository)
    repository.failure = UnknownHostException("offline")

    viewModel.loadListings()

    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    assertEquals(myListings, viewModel.uiState.value.listings)
  }

  @Test
  fun loadListings_retryAfterFailure_clearsTheErrorAndShowsTheListings() {
    repository.failure = UnknownHostException("offline")
    val viewModel = ToolboxViewModel(repository)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)

    val gate = CompletableDeferred<Unit>()
    repository.gate = gate
    repository.failure = null
    viewModel.loadListings()
    // The old error goes away as soon as the retry starts.
    assertNull(viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.isLoading)

    gate.complete(Unit)
    assertNull(viewModel.uiState.value.error)
    assertEquals(myListings, viewModel.uiState.value.listings)
    assertEquals(2, repository.getMyListingsCalls)
  }

  @Test
  fun loadListings_afterANewListing_showsIt() {
    val viewModel = ToolboxViewModel(repository)
    val newListing = ItemListing("Books", 3.0, "Novel", "", listingId = "novel")
    repository.myListings = myListings + newListing

    viewModel.loadListings()

    assertEquals(myListings + newListing, viewModel.uiState.value.listings)
  }

  @Test
  fun loadListings_whenCancelled_reportsNoErrorAndAllowsTheNextLoad() {
    repository.failure = CancellationException("left the screen")
    val viewModel = ToolboxViewModel(repository)
    assertNull(viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)

    repository.failure = null
    viewModel.loadListings()

    assertEquals(myListings, viewModel.uiState.value.listings)
    assertEquals(2, repository.getMyListingsCalls)
  }
}
