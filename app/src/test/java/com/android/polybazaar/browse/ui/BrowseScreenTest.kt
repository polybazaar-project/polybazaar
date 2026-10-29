// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Opus 5.5
package com.android.polybazaar.browse.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.browse.viewmodel.BrowseListing
import com.android.polybazaar.browse.viewmodel.BrowseUiState
import com.android.polybazaar.browse.viewmodel.BrowseViewModel
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowseScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val sampleListings =
      listOf(
          ItemListing("Tools", 8.0, "Bosch drill set", "", listingId = "drill"),
          ItemListing("Electronics", 12.0, "Mini projector", "", listingId = "projector"),
          ItemListing("Tools", 5.0, "Claw hammer", "", listingId = "hammer"),
      )
  private val sampleCategories = listOf("Tools", "Electronics")

  @Test
  fun search_filtersViewModelListings() {
    val viewModel = BrowseViewModel(FakeListingRepository(sampleListings))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithTag(BrowseScreenTags.SEARCH_FIELD).performTextInput("projector")

    composeTestRule.onNodeWithText("Mini projector").assertIsDisplayed()
    composeTestRule.onNodeWithText("Bosch drill set").assertDoesNotExist()
  }

  @Test
  fun categoryFilter_showsOnlyListingsInCategory() {
    val viewModel = BrowseViewModel(FakeListingRepository(sampleListings))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    clickCategory("Tools")

    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithText("Claw hammer").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mini projector").assertDoesNotExist()
  }

  @Test
  fun categoryToggle_clearsCategoryFilter() {
    val viewModel = BrowseViewModel(FakeListingRepository(sampleListings))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    clickCategory("Tools")
    clickCategory("Tools")

    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mini projector").assertIsDisplayed()
    composeTestRule.onNodeWithText("Claw hammer").assertIsDisplayed()
  }

  @Test
  fun searchAndCategoryTogether_showsIntersection() {
    val viewModel = BrowseViewModel(FakeListingRepository(sampleListings))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    clickCategory("Tools")
    composeTestRule.onNodeWithTag(BrowseScreenTags.SEARCH_FIELD).performTextInput("hammer")

    composeTestRule.onNodeWithText("Claw hammer").assertIsDisplayed()
    composeTestRule.onNodeWithText("Bosch drill set").assertDoesNotExist()
    composeTestRule.onNodeWithText("Mini projector").assertDoesNotExist()
  }

  @Test
  fun browseHeader_displaysCorrectly() {
    composeTestRule.setContent { BrowseHeader() }
    composeTestRule.onNodeWithTag(BrowseScreenTags.HEADER).assertIsDisplayed()
  }

  @Test
  fun searchField_displaysPlaceholder_whenEmpty() {
    composeTestRule.setContent { SearchField(query = "", onQueryChange = {}) }
    composeTestRule.onNodeWithTag(BrowseScreenTags.SEARCH_FIELD).assertIsDisplayed()
  }

  @Test
  fun searchField_showsQuery() {
    composeTestRule.setContent { SearchField(query = "test", onQueryChange = {}) }
    composeTestRule.onNodeWithText("test", substring = true).assertIsDisplayed()
  }

  @Test
  fun categorySelector_displaysAllCategories() {
    composeTestRule.setContent {
      CategorySelector(
          categories = sampleCategories,
          selectedCategory = null,
          onCategorySelected = {},
      )
    }
    composeTestRule.onNodeWithTag(BrowseScreenTags.CATEGORY_SELECTOR).assertIsDisplayed()
    composeTestRule.onNodeWithText("Tools").assertIsDisplayed()
    composeTestRule.onNodeWithText("Electronics").assertIsDisplayed()
  }

  @Test
  fun categorySelector_highlightsSelected() {
    var selectedCategory = "Tools"
    composeTestRule.setContent {
      CategorySelector(
          categories = sampleCategories,
          selectedCategory = selectedCategory,
          onCategorySelected = { selectedCategory = it },
      )
    }
    composeTestRule.onNodeWithText("Tools").assertIsDisplayed()
  }

  @Test
  fun listingCard_displaysAllInfo() {
    val listing = BrowseListing("id1", "Test Item", "Tools", "100 m away", "CHF 15")
    composeTestRule.setContent { ListingCard(listing) }

    composeTestRule.onNodeWithTag(BrowseScreenTags.LISTING_CARD).assertIsDisplayed()
    composeTestRule.onNodeWithText("Test Item").assertIsDisplayed()
    composeTestRule.onNodeWithText("Tools · 100 m away").assertIsDisplayed()
    composeTestRule.onNodeWithText("CHF 15").assertIsDisplayed()
    composeTestRule.onNodeWithText("/ day").assertIsDisplayed()
  }

  @Test
  fun emptyListings_displaysPricingFormat() {
    val listing = ItemListing("Category", 10.0, "Test Item", "", listingId = "id1")
    val viewModel = BrowseViewModel(FakeListingRepository(listOf(listing)))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText("Test Item").assertIsDisplayed()
    // Without a distance the card shows only the category, which is also a selector chip.
    composeTestRule
        .onNode(hasText("Category") and hasAnyAncestor(hasTestTag(BrowseScreenTags.LISTING_CARD)))
        .assertIsDisplayed()
    composeTestRule.onNodeWithText("CHF 10").assertIsDisplayed()
    composeTestRule.onNodeWithText("/ day").assertIsDisplayed()
  }

  @Test
  fun multipleListingsDisplay_withCorrectMetadata() {
    val viewModel = BrowseViewModel(FakeListingRepository(sampleListings))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mini projector").assertIsDisplayed()
    composeTestRule.onNodeWithText("Claw hammer").assertIsDisplayed()
  }

  @Test
  fun noResultsFound_stillShowsUI() {
    val viewModel = BrowseViewModel(FakeListingRepository(sampleListings))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(BrowseScreenTags.SEARCH_FIELD).performTextInput("nonexistent")

    composeTestRule.onNodeWithText("Bosch drill set").assertDoesNotExist()
  }

  @Test
  fun preview_showsSampleListings() {
    composeTestRule.setContent { BrowseScreenPreview() }

    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mini projector").assertIsDisplayed()
  }

  @Test
  fun firstLoad_showsTheLoadingIndicatorUntilListingsArrive() {
    val gate = CompletableDeferred<Unit>()
    val repository = FakeListingRepository(sampleListings).apply { this.gate = gate }
    val viewModel = BrowseViewModel(repository)
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(BrowseScreenTags.LOADING_INDICATOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(BrowseScreenTags.LISTING_CARD).assertDoesNotExist()

    gate.complete(Unit)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(BrowseScreenTags.LOADING_INDICATOR).assertDoesNotExist()
    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
  }

  @Test
  fun reload_keepsTheListingsInsteadOfTheLoadingIndicator() {
    composeTestRule.setContent {
      BrowseContent(
          uiState = BrowseUiState(listings = listOf(drillCard), isLoading = true),
          onQueryChange = {},
          onCategorySelected = {},
          onRetry = {},
      )
    }

    composeTestRule.onNodeWithTag(BrowseScreenTags.LOADING_INDICATOR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(BrowseScreenTags.LISTING_CARD).assertIsDisplayed()
  }

  @Test
  fun error_showsItsMessageNextToTheListingsAlreadyShown() {
    var retries = 0
    composeTestRule.setContent {
      BrowseContent(
          uiState = BrowseUiState(listings = listOf(drillCard), error = AppError.NETWORK),
          onQueryChange = {},
          onCategorySelected = {},
          onRetry = { retries++ },
      )
    }

    composeTestRule
        .onNode(
            hasAnyAncestor(hasTestTag(BrowseScreenTags.ERROR)) and
                hasText(string(R.string.error_network))
        )
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(BrowseScreenTags.LISTING_CARD).assertIsDisplayed()
    composeTestRule.onNodeWithTag(BrowseScreenTags.RETRY_BUTTON).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun retry_reloadsTheListingsAndHidesTheError() {
    val repository =
        FakeListingRepository(sampleListings).apply { failure = UnknownHostException("offline") }
    val viewModel = BrowseViewModel(repository)
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }
    composeTestRule.onNodeWithTag(BrowseScreenTags.ERROR).assertIsDisplayed()

    repository.failure = null
    composeTestRule.onNodeWithTag(BrowseScreenTags.RETRY_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(2, repository.getAllListingsCalls)
    composeTestRule.onNodeWithTag(BrowseScreenTags.ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
  }

  @Test
  fun listingCard_withoutDistance_showsOnlyTheCategory() {
    composeTestRule.setContent { ListingCard(drillCard) }

    composeTestRule
        .onNode(hasAnyAncestor(hasTestTag(BrowseScreenTags.LISTING_CARD)) and hasText("Tools"))
        .assertTextEquals("Tools")
    composeTestRule.onNodeWithText("·", substring = true).assertDoesNotExist()
  }

  private val drillCard =
      BrowseListing(id = "drill", title = "Bosch drill set", category = "Tools", price = "CHF 8")

  private fun string(id: Int): String =
      ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

  // Cards show the category too, so the click targets the chip in the category selector.
  private fun clickCategory(category: String) {
    composeTestRule
        .onNode(
            hasText(category) and hasAnyAncestor(hasTestTag(BrowseScreenTags.CATEGORY_SELECTOR))
        )
        .performClick()
  }
}
