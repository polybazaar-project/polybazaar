// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.browse.viewmodel

import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.listing.model.ServiceListing
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

  @Test
  fun toBrowseListing_mapsItemsAndServicesTheSameWay() {
    val item = ItemListing("Tools", 8.0, "Bosch drill set", "With bits", 120.0, listingId = "drill")
    val service = ServiceListing("Tutoring", 25.0, "Math tutoring", "One hour", listingId = "math")

    assertEquals(
        BrowseListing(id = "drill", title = "Bosch drill set", category = "Tools", price = "CHF 8"),
        item.toBrowseListing(),
    )
    assertEquals(
        BrowseListing(
            id = "math",
            title = "Math tutoring",
            category = "Tutoring",
            price = "CHF 25",
        ),
        service.toBrowseListing(),
    )
  }

  @Test
  fun toBrowseListing_withoutListingId_returnsNull() {
    assertNull(ServiceListing("Tutoring", 25.0, "Math tutoring", "One hour").toBrowseListing())
  }

  @Test
  fun toBrowseListing_keepsWholePricesShortAndShowsCentsOtherwise() {
    val prices =
        mapOf(
            0.0 to "CHF 0",
            8.0 to "CHF 8",
            100.0 to "CHF 100",
            8.5 to "CHF 8.50",
            12.25 to "CHF 12.25",
            10.1 to "CHF 10.10",
            // Rounded to the cent first, so it becomes a whole price.
            8.999 to "CHF 9",
        )

    prices.forEach { (price, expected) ->
      assertEquals(expected, priced(price).toBrowseListing()?.price)
    }
  }

  @Test
  fun toBrowseListing_usesADecimalPointWhateverTheDeviceLanguage() {
    val default = Locale.getDefault()
    // German formats 8.5 as "8,50".
    Locale.setDefault(Locale.GERMANY)
    try {
      assertEquals("CHF 8.50", priced(8.5).toBrowseListing()?.price)
    } finally {
      Locale.setDefault(default)
    }
  }

  @Test
  fun toBrowseListing_leavesDistanceOut() {
    assertNull(priced(8.0).toBrowseListing()?.distance)
  }

  private fun priced(price: Double) = ItemListing("Tools", price, "Drill", "", listingId = "drill")

  private class FakeListingRepository(private val listings: List<Listing>) : ListingRepository {
    override suspend fun createListing(listing: Listing): Listing = listing

    override suspend fun getMyListings(): Result<List<Listing>> = Result.success(emptyList())

    override suspend fun getAllListings(): Result<List<Listing>> = Result.success(listings)
  }
}
