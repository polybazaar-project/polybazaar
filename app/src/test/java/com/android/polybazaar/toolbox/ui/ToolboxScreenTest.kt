// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.toolbox.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ServiceListing
import com.android.polybazaar.toolbox.viewmodel.ToolboxUiState
import com.android.polybazaar.toolbox.viewmodel.ToolboxViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolboxScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val myListings =
      listOf(
          ItemListing("Tools", 8.0, "Bosch drill set", "", listingId = "drill"),
          ServiceListing("Tutoring", 25.5, "Math tutoring", "", listingId = "math"),
      )

  @Test
  fun loading_showsTheSpinner() {
    setContent(ToolboxUiState(isLoading = true))

    composeTestRule.onNodeWithTag(ToolboxScreenTags.LOADING_INDICATOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ToolboxScreenTags.EMPTY).assertDoesNotExist()
  }

  @Test
  fun noListings_showsTheEmptyMessage() {
    setContent(ToolboxUiState())

    composeTestRule.onNodeWithTag(ToolboxScreenTags.EMPTY).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ToolboxScreenTags.LOADING_INDICATOR).assertDoesNotExist()
  }

  @Test
  fun listings_showOneRowEachWithTitleTypeAndPrice() {
    setContent(ToolboxUiState(listings = myListings))

    composeTestRule.onAllNodesWithTag(ToolboxScreenTags.LISTING_ROW).assertCountEquals(2)
    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.toolbox_type_item)).assertIsDisplayed()
    composeTestRule.onNodeWithText("CHF 8").assertIsDisplayed()
    composeTestRule.onNodeWithText("Math tutoring").assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.toolbox_type_service)).assertIsDisplayed()
    composeTestRule.onNodeWithText("CHF 25.50").assertIsDisplayed()
    composeTestRule.onNodeWithTag(ToolboxScreenTags.EMPTY).assertDoesNotExist()
  }

  @Test
  fun error_showsItsMessageAndRetryCallsOnRetry() {
    var retries = 0
    setContent(ToolboxUiState(error = AppError.NETWORK), onRetry = { retries++ })

    composeTestRule
        .onNode(
            hasAnyAncestor(hasTestTag(ToolboxScreenTags.ERROR)) and
                hasText(string(R.string.error_network))
        )
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(ToolboxScreenTags.RETRY_BUTTON).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun errorWithListings_keepsTheRows() {
    setContent(ToolboxUiState(listings = myListings, error = AppError.NETWORK))

    composeTestRule.onNodeWithTag(ToolboxScreenTags.ERROR).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(ToolboxScreenTags.LISTING_ROW).assertCountEquals(2)
  }

  @Test
  fun screen_showsTheListingsLoadedByTheViewModel() {
    val viewModel = ToolboxViewModel(FakeListingRepository(myListings = myListings))
    composeTestRule.setContent { ToolboxScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText(string(R.string.toolbox_title)).assertIsDisplayed()
    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithText("Math tutoring").assertIsDisplayed()
  }

  private fun setContent(uiState: ToolboxUiState, onRetry: () -> Unit = {}) {
    composeTestRule.setContent { ToolboxContent(uiState = uiState, onRetry = onRetry) }
  }

  private fun string(id: Int): String =
      ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)
}
