// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.toolbox.ui

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ServiceListing
import com.android.polybazaar.toolbox.viewmodel.ToolboxUiState
import com.android.polybazaar.toolbox.viewmodel.ToolboxViewModel
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolboxCardTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val myListings =
      listOf(
          ItemListing("Tools", 8.0, "Bosch drill set", "", listingId = "drill"),
          ServiceListing("Tutoring", 25.5, "Math tutoring", "", listingId = "math"),
      )

  @Test
  fun loading_showsTheSpinner() {
    setContent(ToolboxUiState(isLoading = true))

    composeTestRule.onNodeWithTag(ToolboxCardTags.LOADING_INDICATOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ToolboxCardTags.EMPTY).assertDoesNotExist()
  }

  @Test
  fun noListings_showsTheEmptyMessage() {
    setContent(ToolboxUiState())

    composeTestRule.onNodeWithTag(ToolboxCardTags.EMPTY).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ToolboxCardTags.LOADING_INDICATOR).assertDoesNotExist()
  }

  @Test
  fun listings_showOneRowEachWithTitleTypeAndPrice() {
    setContent(ToolboxUiState(listings = myListings))

    composeTestRule.onAllNodesWithTag(ToolboxCardTags.LISTING_ROW).assertCountEquals(2)
    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.toolbox_type_item)).assertIsDisplayed()
    composeTestRule.onNodeWithText("CHF 8").assertIsDisplayed()
    composeTestRule.onNodeWithText("Math tutoring").assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.toolbox_type_service)).assertIsDisplayed()
    composeTestRule.onNodeWithText("CHF 25.50").assertIsDisplayed()
    composeTestRule.onNodeWithTag(ToolboxCardTags.EMPTY).assertDoesNotExist()
  }

  @Test
  fun manyListings_scrollInsideTheCardAndKeepTheTitle() {
    val listings = List(30) { ItemListing("Tools", 8.0, "Listing $it", "", listingId = "id-$it") }
    composeTestRule.setContent {
      ToolboxContent(
          uiState = ToolboxUiState(listings = listings),
          onRetry = {},
          modifier = Modifier.height(240.dp),
      )
    }

    composeTestRule
        .onNodeWithTag(ToolboxCardTags.LISTINGS)
        .performScrollToNode(hasText("Listing 29"))

    composeTestRule.onNodeWithText("Listing 29").assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.toolbox_title)).assertIsDisplayed()
  }

  @Test
  fun reloadWithListings_keepsTheRowsAndShowsNoSpinner() {
    setContent(ToolboxUiState(listings = myListings, isLoading = true))

    composeTestRule.onAllNodesWithTag(ToolboxCardTags.LISTING_ROW).assertCountEquals(2)
    composeTestRule.onNodeWithTag(ToolboxCardTags.LOADING_INDICATOR).assertDoesNotExist()
  }

  @Test
  fun error_showsItsMessageAndRetryCallsOnRetry() {
    var retries = 0
    setContent(ToolboxUiState(error = AppError.NETWORK), onRetry = { retries++ })

    composeTestRule
        .onNode(
            hasAnyAncestor(hasTestTag(ToolboxCardTags.ERROR)) and
                hasText(string(R.string.error_network))
        )
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(ToolboxCardTags.RETRY_BUTTON).performClick()
    assertEquals(1, retries)
    // A failed load must not claim the user has no listings.
    composeTestRule.onNodeWithTag(ToolboxCardTags.EMPTY).assertDoesNotExist()
  }

  @Test
  fun errorWithListings_keepsTheRows() {
    setContent(ToolboxUiState(listings = myListings, error = AppError.NETWORK))

    composeTestRule.onNodeWithTag(ToolboxCardTags.ERROR).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(ToolboxCardTags.LISTING_ROW).assertCountEquals(2)
  }

  @Test
  fun card_showsTheListingsLoadedByTheViewModel() {
    val viewModel = ToolboxViewModel(FakeListingRepository(myListings = myListings))
    composeTestRule.setContent { ToolboxCard(viewModel = viewModel) }

    composeTestRule.onNodeWithText(string(R.string.toolbox_title)).assertIsDisplayed()
    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithText("Math tutoring").assertIsDisplayed()
  }

  @Test
  fun retry_reloadsTheListingsAndHidesTheError() {
    val repository =
        FakeListingRepository(myListings = myListings).apply {
          failure = UnknownHostException("offline")
        }
    val viewModel = ToolboxViewModel(repository)
    composeTestRule.setContent { ToolboxCard(viewModel = viewModel) }
    composeTestRule.onNodeWithTag(ToolboxCardTags.ERROR).assertIsDisplayed()

    repository.failure = null
    composeTestRule.onNodeWithTag(ToolboxCardTags.RETRY_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(2, repository.getMyListingsCalls)
    composeTestRule.onNodeWithTag(ToolboxCardTags.ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
  }

  private fun setContent(uiState: ToolboxUiState, onRetry: () -> Unit = {}) {
    composeTestRule.setContent { ToolboxContent(uiState = uiState, onRetry = onRetry) }
  }

  private fun string(id: Int): String =
      ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)
}
