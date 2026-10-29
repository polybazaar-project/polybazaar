// Co-authored-by: Copilot GPT-5.6 Terra
package com.android.polybazaar.browse.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.browse.viewmodel.BrowseListing
import com.android.polybazaar.browse.viewmodel.BrowseUiState
import com.android.polybazaar.browse.viewmodel.BrowseViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowseScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val sampleListings =
      listOf(
          BrowseListing("drill", "Bosch drill set", "Tools", "350 m away", "CHF 8"),
          BrowseListing("projector", "Mini projector", "Electronics", "600 m away", "CHF 12"),
          BrowseListing("hammer", "Claw hammer", "Tools", "200 m away", "CHF 5"),
      )
  private val sampleCategories = listOf("Tools", "Electronics")

  @Test
  fun search_filtersViewModelListings() {
    val viewModel =
        BrowseViewModel(BrowseUiState(listings = sampleListings, categories = sampleCategories))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithTag(BrowseScreenTags.SEARCH_FIELD).performTextInput("projector")

    composeTestRule.onNodeWithText("Mini projector").assertIsDisplayed()
    composeTestRule.onNodeWithText("Bosch drill set").assertDoesNotExist()
  }

  @Test
  fun categoryFilter_showsOnlyListingsInCategory() {
    val viewModel =
        BrowseViewModel(BrowseUiState(listings = sampleListings, categories = sampleCategories))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText("Tools").performClick()

    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithText("Claw hammer").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mini projector").assertDoesNotExist()
  }

  @Test
  fun categoryToggle_clearsCategoryFilter() {
    val viewModel =
        BrowseViewModel(BrowseUiState(listings = sampleListings, categories = sampleCategories))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText("Tools").performClick()
    composeTestRule.onNodeWithText("Tools").performClick()

    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mini projector").assertIsDisplayed()
    composeTestRule.onNodeWithText("Claw hammer").assertIsDisplayed()
  }

  @Test
  fun searchAndCategoryTogether_showsIntersection() {
    val viewModel =
        BrowseViewModel(BrowseUiState(listings = sampleListings, categories = sampleCategories))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText("Tools").performClick()
    composeTestRule.onNodeWithTag(BrowseScreenTags.SEARCH_FIELD).performTextInput("hammer")

    composeTestRule.onNodeWithText("Claw hammer").assertIsDisplayed()
    composeTestRule.onNodeWithText("Bosch drill set").assertDoesNotExist()
    composeTestRule.onNodeWithText("Mini projector").assertDoesNotExist()
  }

  @Test
  fun emptyListings_displaysPricingFormat() {
    val listing = BrowseListing("id1", "Test Item", "Category", "100 m", "CHF 10")
    val viewModel =
        BrowseViewModel(BrowseUiState(listings = listOf(listing), categories = emptyList()))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText("Test Item").assertIsDisplayed()
    composeTestRule.onNodeWithText("Category · 100 m").assertIsDisplayed()
    composeTestRule.onNodeWithText("CHF 10").assertIsDisplayed()
    composeTestRule.onNodeWithText("/ day").assertIsDisplayed()
  }

  @Test
  fun headerDisplaysCorrectly() {
    val viewModel =
        BrowseViewModel(BrowseUiState(listings = sampleListings, categories = sampleCategories))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun searchFieldIsAccessible() {
    val viewModel =
        BrowseViewModel(BrowseUiState(listings = sampleListings, categories = sampleCategories))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(BrowseScreenTags.SEARCH_FIELD).assertIsDisplayed()
  }

  @Test
  fun allCategoriesDisplay() {
    val viewModel =
        BrowseViewModel(BrowseUiState(listings = sampleListings, categories = sampleCategories))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText("Tools").assertIsDisplayed()
    composeTestRule.onNodeWithText("Electronics").assertIsDisplayed()
  }

  @Test
  fun multipleListingsDisplay_withCorrectMetadata() {
    val viewModel =
        BrowseViewModel(BrowseUiState(listings = sampleListings, categories = sampleCategories))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mini projector").assertIsDisplayed()
    composeTestRule.onNodeWithText("Claw hammer").assertIsDisplayed()

    composeTestRule.onNodeWithText("350 m away").assertIsDisplayed()
    composeTestRule.onNodeWithText("600 m away").assertIsDisplayed()
    composeTestRule.onNodeWithText("200 m away").assertIsDisplayed()

    composeTestRule.onNodeWithText("CHF 8").assertIsDisplayed()
    composeTestRule.onNodeWithText("CHF 12").assertIsDisplayed()
    composeTestRule.onNodeWithText("CHF 5").assertIsDisplayed()
  }

  @Test
  fun categorySelection_changesFocusedState() {
    val viewModel =
        BrowseViewModel(BrowseUiState(listings = sampleListings, categories = sampleCategories))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText("Electronics").performClick()
    composeTestRule.onNodeWithText("Mini projector").assertIsDisplayed()
  }

  @Test
  fun searchWithSpecialCharacters_filtersCorrectly() {
    val specialListing =
        listOf(
            BrowseListing("id1", "Item-with-dashes", "Tools", "100 m", "CHF 5"),
            BrowseListing("id2", "Normal Item", "Electronics", "200 m", "CHF 10"),
        )
    val viewModel =
        BrowseViewModel(BrowseUiState(listings = specialListing, categories = listOf("Tools")))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(BrowseScreenTags.SEARCH_FIELD).performTextInput("dashes")

    composeTestRule.onNodeWithText("Item-with-dashes").assertIsDisplayed()
    composeTestRule.onNodeWithText("Normal Item").assertDoesNotExist()
  }

  @Test
  fun noResultsFound_stillShowsUI() {
    val viewModel =
        BrowseViewModel(BrowseUiState(listings = sampleListings, categories = sampleCategories))
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(BrowseScreenTags.SEARCH_FIELD).performTextInput("nonexistent")

    composeTestRule.onNodeWithText("Bosch drill set").assertDoesNotExist()
  }
}
