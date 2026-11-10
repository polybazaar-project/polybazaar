// Co-authored-by: Claude Code (Claude Opus 5.5)
// Co-authored-by: Copilot GPT-5.6 Terra
package com.android.polybazaar.toolbox.viewmodel

import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingType
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
          ItemListing(
              ListingCategory.ITEM_TOOLS,
              8.0,
              "Bosch drill set",
              "With bits",
              120.0,
              listingId = "drill",
          ),
          ServiceListing(
              ListingCategory.SERVICE_TUTORING,
              25.0,
              "Math tutoring",
              "One hour",
              listingId = "math",
          ),
      )

  private val repository = FakeListingRepository(myListings = myListings)

  @Before
  fun setUp() {
    // init loads the listings on Dispatchers.Main; run it before each test's assertions.
    Dispatchers.setMain(UnconfinedTestDispatcher())
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
    val newListing = ItemListing(ListingCategory.ITEM_BOOKS, 3.0, "Novel", "", listingId = "novel")
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

  @Test
  fun init_selectsNoTypeAndShowsAllListings() {
    val viewModel = ToolboxViewModel(repository)

    assertNull(viewModel.uiState.value.selectedType)
    assertEquals(myListings, viewModel.uiState.value.filteredListings)
  }

  @Test
  fun selectType_item_showsOnlyItems() {
    val viewModel = ToolboxViewModel(repository)

    viewModel.selectType(ListingType.ITEM)

    assertEquals(ListingType.ITEM, viewModel.uiState.value.selectedType)
    assertEquals(listOf(myListings[0]), viewModel.uiState.value.filteredListings)
    // The filter does not drop the other listings from the state.
    assertEquals(myListings, viewModel.uiState.value.listings)
  }

  @Test
  fun selectType_service_showsOnlyServices() {
    val viewModel = ToolboxViewModel(repository)

    viewModel.selectType(ListingType.SERVICE)

    assertEquals(ListingType.SERVICE, viewModel.uiState.value.selectedType)
    assertEquals(listOf(myListings[1]), viewModel.uiState.value.filteredListings)
  }

  @Test
  fun selectType_null_showsAllListings() {
    val viewModel = ToolboxViewModel(repository)
    viewModel.selectType(ListingType.ITEM)

    viewModel.selectType(null)

    assertNull(viewModel.uiState.value.selectedType)
    assertEquals(myListings, viewModel.uiState.value.filteredListings)
  }

  @Test
  fun selectType_theSelectedTypeAgain_keepsIt() {
    val viewModel = ToolboxViewModel(repository)
    viewModel.selectType(ListingType.ITEM)

    viewModel.selectType(ListingType.ITEM)

    assertEquals(ListingType.ITEM, viewModel.uiState.value.selectedType)
    assertEquals(listOf(myListings[0]), viewModel.uiState.value.filteredListings)
  }

  @Test
  fun selectType_theOtherType_switchesDirectly() {
    val viewModel = ToolboxViewModel(repository)
    viewModel.selectType(ListingType.ITEM)

    viewModel.selectType(ListingType.SERVICE)

    assertEquals(ListingType.SERVICE, viewModel.uiState.value.selectedType)
    assertEquals(listOf(myListings[1]), viewModel.uiState.value.filteredListings)
  }

  @Test
  fun selectType_withNoListingOfThatType_filtersToAnEmptyList() {
    repository.myListings = listOf(myListings[0])
    val viewModel = ToolboxViewModel(repository)

    viewModel.selectType(ListingType.SERVICE)

    assertEquals(emptyList<Listing>(), viewModel.uiState.value.filteredListings)
    assertEquals(listOf(myListings[0]), viewModel.uiState.value.listings)
  }

  @Test
  fun loadListings_afterANewListing_keepsTheFilter() {
    val viewModel = ToolboxViewModel(repository)
    viewModel.selectType(ListingType.ITEM)
    val newItem = ItemListing(ListingCategory.ITEM_BOOKS, 3.0, "Novel", "", listingId = "novel")
    val newService =
        ServiceListing(ListingCategory.SERVICE_TUTORING, 20.0, "Physics", "", listingId = "phys")
    repository.myListings = myListings + newItem + newService

    viewModel.loadListings()

    assertEquals(ListingType.ITEM, viewModel.uiState.value.selectedType)
    assertEquals(listOf(myListings[0], newItem), viewModel.uiState.value.filteredListings)
  }

  @Test
  fun loadListings_retryAfterFailure_keepsTheFilter() {
    val viewModel = ToolboxViewModel(repository)
    viewModel.selectType(ListingType.SERVICE)
    repository.failure = UnknownHostException("offline")
    viewModel.loadListings()
    assertEquals(ListingType.SERVICE, viewModel.uiState.value.selectedType)

    repository.failure = null
    viewModel.loadListings()

    assertNull(viewModel.uiState.value.error)
    assertEquals(ListingType.SERVICE, viewModel.uiState.value.selectedType)
    assertEquals(listOf(myListings[1]), viewModel.uiState.value.filteredListings)
  }
}
