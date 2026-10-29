// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.browse.viewmodel

import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelTest {

  // The cards the repository's listings below map to.
  private val listings =
      listOf(
          BrowseListing(
              id = "drill",
              title = "Bosch drill set",
              category = "Tools",
              price = "CHF 8",
          ),
          BrowseListing(
              id = "projector",
              title = "Mini projector",
              category = "Electronics",
              price = "CHF 12",
          ),
      )

  private val repository =
      FakeListingRepository(
          listOf(
              ItemListing("Tools", 8.0, "Bosch drill set", "", listingId = "drill"),
              ItemListing("Electronics", 12.0, "Mini projector", "", listingId = "projector"),
          )
      )

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
  fun updateQuery_filtersListings() {
    val viewModel = BrowseViewModel(repository)

    viewModel.updateQuery("projector")

    assertEquals(listOf(listings[1]), viewModel.uiState.value.filteredListings)
  }

  @Test
  fun selectCategory_filtersListings() {
    val viewModel = BrowseViewModel(repository)

    viewModel.selectCategory("Tools")

    assertEquals(listOf(listings[0]), viewModel.uiState.value.filteredListings)
  }

  @Test
  fun clearingCategory_resetsFilter() {
    val viewModel = BrowseViewModel(repository)

    viewModel.selectCategory("Tools")
    viewModel.selectCategory("Tools")

    assertEquals(listings, viewModel.uiState.value.filteredListings)
  }

  @Test
  fun queryAndCategory_filterListingsTogether() {
    val viewModel = BrowseViewModel(repository)

    viewModel.updateQuery("projector")
    viewModel.selectCategory("Tools")

    assertEquals(emptyList<BrowseListing>(), viewModel.uiState.value.filteredListings)
  }

  @Test
  fun clearingQuery_restoresListings() {
    val viewModel = BrowseViewModel(repository)

    viewModel.updateQuery("projector")
    viewModel.updateQuery("")

    assertEquals(listings, viewModel.uiState.value.filteredListings)
  }

  private class FakeListingRepository(private val listings: List<Listing>) : ListingRepository {
    override suspend fun createListing(listing: Listing): Listing = listing

    override suspend fun getMyListings(): Result<List<Listing>> = Result.success(emptyList())

    override suspend fun getAllListings(): Result<List<Listing>> = Result.success(listings)
  }
}
