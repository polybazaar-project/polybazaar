// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.toolbox.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.model.ServiceListing
import com.android.polybazaar.toolbox.viewmodel.ToolboxUiState
import com.android.polybazaar.toolbox.viewmodel.ToolboxViewModel
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class ToolboxCardTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val myListings =
      listOf(
          ItemListing(ListingCategory.ITEM_TOOLS, 8.0, "Bosch drill set", "", listingId = "drill"),
          ServiceListing(
              ListingCategory.SERVICE_TUTORING,
              25.5,
              "Math tutoring",
              "",
              listingId = "math",
          ),
      )

  @Test
  fun loading_showsTheSpinner() {
    setContent(ToolboxUiState(isLoading = true))

    composeTestRule.onNodeWithTag(ToolboxCardTags.LOADING_INDICATOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ToolboxCardTags.EMPTY).assertDoesNotExist()
  }

  @Test
  fun noListings_showsTheEmptyMessageAndNoFilterButtons() {
    setContent(ToolboxUiState())

    composeTestRule
        .onNodeWithTag(ToolboxCardTags.EMPTY)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.toolbox_empty))
    composeTestRule.onNodeWithTag(ToolboxCardTags.LOADING_INDICATOR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_ALL).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_ITEMS).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_SERVICES).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_EMPTY).assertDoesNotExist()
  }

  @Test
  fun listings_selectAllByDefaultAndShowAllRows() {
    setContent(ToolboxUiState(listings = myListings))

    composeTestRule
        .onNodeWithTag(ToolboxCardTags.FILTER_ALL)
        .assertIsDisplayed()
        .assertIsSelected()
        .assertTextEquals(string(R.string.toolbox_filter_all))
    composeTestRule
        .onNodeWithTag(ToolboxCardTags.FILTER_ITEMS)
        .assertIsDisplayed()
        .assertIsNotSelected()
        .assertTextEquals(string(R.string.toolbox_filter_items))
    composeTestRule
        .onNodeWithTag(ToolboxCardTags.FILTER_SERVICES)
        .assertIsDisplayed()
        .assertIsNotSelected()
        .assertTextEquals(string(R.string.toolbox_filter_services))
    composeTestRule.onAllNodesWithTag(ToolboxCardTags.LISTING_ROW).assertCountEquals(2)
    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_EMPTY).assertDoesNotExist()
  }

  @Test
  fun listings_showTheFilterButtonsBelowTheTitle() {
    setContent(ToolboxUiState(listings = myListings))

    val title =
        composeTestRule
            .onNodeWithText(string(R.string.toolbox_title))
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
    val all = composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_ALL).getUnclippedBoundsInRoot()
    val items =
        composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_ITEMS).getUnclippedBoundsInRoot()
    val services =
        composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_SERVICES).getUnclippedBoundsInRoot()
    // The buttons sit on their own line under the title: All, Items, Services.
    for (button in listOf(all, items, services)) assertTrue(title.bottom <= button.top)
    assertTrue(all.right <= items.left)
    assertTrue(items.right <= services.left)
    val firstRow =
        composeTestRule.onAllNodesWithTag(ToolboxCardTags.LISTING_ROW)[0].getUnclippedBoundsInRoot()
    assertTrue(items.bottom <= firstRow.top)
  }

  @Test
  @Config(qualifiers = "w360dp")
  @GraphicsMode(GraphicsMode.Mode.NATIVE) // Measures the text for real, so truncation shows.
  fun narrowScreen_showsTheWholeTitle() {
    // The profile screen pads the card by 18dp on each side.
    composeTestRule.setContent {
      ToolboxContent(
          uiState = ToolboxUiState(listings = myListings),
          onRetry = {},
          onTypeSelected = {},
          modifier = Modifier.padding(horizontal = 18.dp),
      )
    }

    val layouts = mutableListOf<TextLayoutResult>()
    composeTestRule.onNodeWithText(string(R.string.toolbox_title)).performSemanticsAction(
        SemanticsActions.GetTextLayoutResult
    ) {
      it(layouts)
    }
    assertFalse(layouts.single().isLineEllipsized(0))
  }

  @Test
  fun listings_showOneBoxEachWithTitleCategoryAndPricePerDay() {
    setContent(ToolboxUiState(listings = myListings))

    val rows = composeTestRule.onAllNodesWithTag(ToolboxCardTags.LISTING_ROW)
    rows.assertCountEquals(2)
    val perDay = string(R.string.browse_price_per_day)
    rows[0].assert(hasAnyDescendant(hasText("Bosch drill set")))
    rows[0].assert(hasAnyDescendant(hasText("Tools · ")))
    rows[0].assert(hasAnyDescendant(hasText("CHF 8")))
    rows[0].assert(hasAnyDescendant(hasText(perDay)))
    rows[1].assert(hasAnyDescendant(hasText("Math tutoring")))
    rows[1].assert(hasAnyDescendant(hasText("Tutoring · ")))
    rows[1].assert(hasAnyDescendant(hasText("CHF 25.50")))
    rows[1].assert(hasAnyDescendant(hasText(perDay)))
    // The listing type is no longer written out on the rows.
    composeTestRule.onNodeWithText("Item").assertDoesNotExist()
    composeTestRule.onNodeWithText("Service").assertDoesNotExist()
    composeTestRule.onNodeWithTag(ToolboxCardTags.EMPTY).assertDoesNotExist()
  }

  @Test
  fun itemsSelected_showsOnlyItemsAndSelectsTheItemsButton() {
    setContent(ToolboxUiState(listings = myListings, selectedType = ListingType.ITEM))

    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_ITEMS).assertIsSelected()
    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_ALL).assertIsNotSelected()
    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_SERVICES).assertIsNotSelected()
    composeTestRule.onAllNodesWithTag(ToolboxCardTags.LISTING_ROW).assertCountEquals(1)
    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithText("Math tutoring").assertDoesNotExist()
  }

  @Test
  fun servicesSelected_showsOnlyServicesAndSelectsTheServicesButton() {
    setContent(ToolboxUiState(listings = myListings, selectedType = ListingType.SERVICE))

    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_SERVICES).assertIsSelected()
    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_ALL).assertIsNotSelected()
    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_ITEMS).assertIsNotSelected()
    composeTestRule.onAllNodesWithTag(ToolboxCardTags.LISTING_ROW).assertCountEquals(1)
    composeTestRule.onNodeWithText("Math tutoring").assertIsDisplayed()
    composeTestRule.onNodeWithText("Bosch drill set").assertDoesNotExist()
  }

  @Test
  fun filterButtons_reportTheTappedType() {
    val tapped = mutableListOf<ListingType?>()
    setContent(ToolboxUiState(listings = myListings), onTypeSelected = { tapped += it })

    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_ITEMS).performClick()
    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_SERVICES).performClick()
    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_ALL).performClick()

    // All reports null: no type filter.
    assertEquals(listOf(ListingType.ITEM, ListingType.SERVICE, null), tapped)
  }

  @Test
  fun itemsSelectedWithNoItems_showsTheItemsMessageAndKeepsTheButtons() {
    setContent(ToolboxUiState(listings = listOf(myListings[1]), selectedType = ListingType.ITEM))

    composeTestRule
        .onNodeWithTag(ToolboxCardTags.FILTER_EMPTY)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.toolbox_empty_items))
    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_ITEMS).assertIsSelected()
    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_SERVICES).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ToolboxCardTags.LISTING_ROW).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ToolboxCardTags.EMPTY).assertDoesNotExist()
  }

  @Test
  fun servicesSelectedWithNoServices_showsTheServicesMessageAndKeepsTheButtons() {
    setContent(ToolboxUiState(listings = listOf(myListings[0]), selectedType = ListingType.SERVICE))

    composeTestRule
        .onNodeWithTag(ToolboxCardTags.FILTER_EMPTY)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.toolbox_empty_services))
    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_SERVICES).assertIsSelected()
    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_ITEMS).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ToolboxCardTags.LISTING_ROW).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ToolboxCardTags.EMPTY).assertDoesNotExist()
  }

  @Test
  fun previewSampleData_rendersBothListingRows() {
    composeTestRule.setContent { ToolboxContentPreview() }

    composeTestRule.onAllNodesWithTag(ToolboxCardTags.LISTING_ROW).assertCountEquals(2)
    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithText("Math tutoring").assertIsDisplayed()
  }

  @Test
  fun manyListings_scrollInsideTheCardAndKeepTheTitle() {
    val listings =
        List(30) {
          ItemListing(ListingCategory.ITEM_TOOLS, 8.0, "Listing $it", "", listingId = "id-$it")
        }
    composeTestRule.setContent {
      ToolboxContent(
          uiState = ToolboxUiState(listings = listings),
          onRetry = {},
          onTypeSelected = {},
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
  fun card_tappingTheFilterButtonsSelectsSwitchesAndShowsAllAgain() {
    val viewModel = ToolboxViewModel(FakeListingRepository(myListings = myListings))
    composeTestRule.setContent { ToolboxCard(viewModel = viewModel) }
    val all = composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_ALL)
    val items = composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_ITEMS)
    val services = composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_SERVICES)
    all.assertIsSelected()

    items.performClick()
    items.assertIsSelected()
    all.assertIsNotSelected()
    services.assertIsNotSelected()
    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithText("Math tutoring").assertDoesNotExist()

    // Tapping the other button switches directly.
    services.performClick()
    services.assertIsSelected()
    items.assertIsNotSelected()
    composeTestRule.onNodeWithText("Math tutoring").assertIsDisplayed()
    composeTestRule.onNodeWithText("Bosch drill set").assertDoesNotExist()

    // Tapping the selected button again keeps it selected.
    services.performClick()
    services.assertIsSelected()
    composeTestRule.onAllNodesWithTag(ToolboxCardTags.LISTING_ROW).assertCountEquals(1)

    // All shows everything again.
    all.performClick()
    all.assertIsSelected()
    services.assertIsNotSelected()
    items.assertIsNotSelected()
    composeTestRule.onAllNodesWithTag(ToolboxCardTags.LISTING_ROW).assertCountEquals(2)
  }

  @Test
  fun card_retryKeepsTheSelectedFilter() {
    val repository = FakeListingRepository(myListings = myListings)
    val viewModel = ToolboxViewModel(repository)
    composeTestRule.setContent { ToolboxCard(viewModel = viewModel) }
    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_SERVICES).performClick()
    repository.failure = UnknownHostException("offline")
    composeTestRule.runOnIdle { viewModel.loadListings() }
    composeTestRule.onNodeWithTag(ToolboxCardTags.ERROR).assertIsDisplayed()

    repository.failure = null
    composeTestRule.onNodeWithTag(ToolboxCardTags.RETRY_BUTTON).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(ToolboxCardTags.ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ToolboxCardTags.FILTER_SERVICES).assertIsSelected()
    composeTestRule.onNodeWithText("Math tutoring").assertIsDisplayed()
    composeTestRule.onNodeWithText("Bosch drill set").assertDoesNotExist()
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

  private fun setContent(
      uiState: ToolboxUiState,
      onRetry: () -> Unit = {},
      onTypeSelected: (ListingType?) -> Unit = {},
  ) {
    composeTestRule.setContent {
      ToolboxContent(uiState = uiState, onRetry = onRetry, onTypeSelected = onTypeSelected)
    }
  }

  private fun string(id: Int): String =
      ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)
}
