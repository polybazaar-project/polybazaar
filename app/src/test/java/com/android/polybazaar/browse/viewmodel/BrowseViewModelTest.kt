// Co-authored-by: Copilot GPT-5.6 Terra
package com.android.polybazaar.browse.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseViewModelTest {

  private val listings =
      listOf(
          BrowseListing("drill", "Bosch drill set", "Tools", "350 m away", "CHF 8"),
          BrowseListing("projector", "Mini projector", "Electronics", "600 m away", "CHF 12"),
      )

  @Test
  fun updateQuery_filtersListings() {
    val viewModel = BrowseViewModel(BrowseUiState(listings = listings))

    viewModel.updateQuery("projector")

    assertEquals(listOf(listings[1]), viewModel.uiState.value.filteredListings)
  }

  @Test
  fun selectCategory_filtersListings() {
    val viewModel = BrowseViewModel(BrowseUiState(listings = listings))

    viewModel.selectCategory("Tools")

    assertEquals(listOf(listings[0]), viewModel.uiState.value.filteredListings)
  }

  @Test
  fun queryAndCategory_filterListingsTogether() {
    val viewModel = BrowseViewModel(BrowseUiState(listings = listings))

    viewModel.updateQuery("projector")
    viewModel.selectCategory("Tools")

    assertEquals(emptyList<BrowseListing>(), viewModel.uiState.value.filteredListings)
  }

  @Test
  fun clearingQuery_restoresListings() {
    val viewModel = BrowseViewModel(BrowseUiState(listings = listings))

    viewModel.updateQuery("projector")
    viewModel.updateQuery("")

    assertEquals(listings, viewModel.uiState.value.filteredListings)
  }
}
