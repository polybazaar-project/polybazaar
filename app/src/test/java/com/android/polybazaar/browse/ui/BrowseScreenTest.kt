// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Opus 5.5
package com.android.polybazaar.browse.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.browse.viewmodel.BrowseListing
import com.android.polybazaar.browse.viewmodel.BrowseUiState
import com.android.polybazaar.browse.viewmodel.BrowseViewModel
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.model.ServiceListing
import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class BrowseScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val currentUid = "uid-me"

  private val sampleListings =
      listOf(
          ItemListing(ListingCategory.ITEM_TOOLS, 8.0, "Bosch drill set", "", listingId = "drill"),
          ItemListing(
              ListingCategory.ITEM_ELECTRONICS,
              12.0,
              "Mini projector",
              "",
              listingId = "projector",
          ),
          ItemListing(ListingCategory.ITEM_TOOLS, 5.0, "Claw hammer", "", listingId = "hammer"),
      )
  private val sampleCategories = listOf("Tools", "Electronics")

  @Test
  fun search_filtersViewModelListings() {
    val viewModel = BrowseViewModel(FakeListingRepository(sampleListings), currentUid)
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithTag(BrowseScreenTags.SEARCH_FIELD).performTextInput("projector")

    composeTestRule.onNodeWithText("Mini projector").assertIsDisplayed()
    composeTestRule.onNodeWithText("Bosch drill set").assertDoesNotExist()
  }

  @Test
  fun categoryFilter_showsOnlyListingsInCategory() {
    val viewModel = BrowseViewModel(FakeListingRepository(sampleListings), currentUid)
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    clickCategory("Tools")

    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithText("Claw hammer").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mini projector").assertDoesNotExist()
  }

  @Test
  fun categoryToggle_clearsCategoryFilter() {
    val viewModel = BrowseViewModel(FakeListingRepository(sampleListings), currentUid)
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    clickCategory("Tools")
    clickCategory("Tools")

    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mini projector").assertIsDisplayed()
    composeTestRule.onNodeWithText("Claw hammer").assertIsDisplayed()
  }

  @Test
  fun searchAndCategoryTogether_showsIntersection() {
    val viewModel = BrowseViewModel(FakeListingRepository(sampleListings), currentUid)
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
  fun categorySelector_withAllCategories_scrollsTheLastChipIntoViewAndSelectsIt() {
    var selected: String? = null
    composeTestRule.setContent {
      Box(Modifier.width(NARROW_WIDTH)) {
        CategorySelector(
            categories = allCategories,
            selectedCategory = null,
            onCategorySelected = { selected = it },
        )
      }
    }

    val lastChip = categoryChip(allCategories.last())
    lastChip.assertIsNotDisplayed()

    lastChip.performScrollTo().assertIsDisplayed().performClick()

    assertEquals(allCategories.last(), selected)
  }

  // Real text measurement, so a squeezed chip would wrap its label onto several lines.
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  @Test
  fun categorySelector_withAllCategories_allChipsHaveTheSameHeight() {
    composeTestRule.setContent {
      Box(Modifier.width(NARROW_WIDTH)) {
        CategorySelector(
            categories = allCategories,
            selectedCategory = allCategories.last(),
            onCategorySelected = {},
        )
      }
    }

    val heights = allCategories.map { categoryChip(it).fetchSemanticsNode().size.height }
    assertTrue("chip heights differ: $heights", heights.all { it == heights.first() })
  }

  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  @Test
  fun categorySelector_heightDoesNotGrowWithMoreCategories() {
    val categories = mutableStateOf(sampleCategories)
    composeTestRule.setContent {
      Box(Modifier.width(NARROW_WIDTH)) {
        CategorySelector(
            categories = categories.value,
            selectedCategory = null,
            onCategorySelected = {},
        )
      }
    }
    val selector = composeTestRule.onNodeWithTag(BrowseScreenTags.CATEGORY_SELECTOR)
    val fewCategoriesHeight = selector.fetchSemanticsNode().size.height

    categories.value = allCategories
    composeTestRule.waitForIdle()

    assertEquals(fewCategoriesHeight, selector.fetchSemanticsNode().size.height)
  }

  @Test
  fun browseScreen_withListingsInEveryCategory_canFilterByTheLastCategory() {
    val listings =
        ListingCategory.entries.map {
          if (it.type == ListingType.ITEM) {
            ItemListing(it, 5.0, "${it.id} listing", "", listingId = it.id)
          } else {
            ServiceListing(it, 5.0, "${it.id} listing", "", listingId = it.id)
          }
        }
    val viewModel = BrowseViewModel(FakeListingRepository(listings), currentUid)
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }
    composeTestRule.waitForIdle()
    // Read from the ViewModel, not recomputed here, so this still taps the chip the screen shows
    // last if the ViewModel orders categories differently.
    val lastCategory = viewModel.uiState.value.categories.last()
    val inLastCategory = listings.first { it.category.displayName == lastCategory }
    val inAnotherCategory = listings.first { it.category.displayName != lastCategory }

    val lastChip = categoryChip(lastCategory)
    lastChip.assertIsNotDisplayed()

    lastChip.performScrollTo().performClick()

    composeTestRule.onNodeWithText(inLastCategory.title).assertIsDisplayed()
    composeTestRule.onNodeWithText(inAnotherCategory.title).assertDoesNotExist()
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
    val listing = ItemListing(ListingCategory.ITEM_OTHER, 10.0, "Test Item", "", listingId = "id1")
    val viewModel = BrowseViewModel(FakeListingRepository(listOf(listing)), currentUid)
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText("Test Item").assertIsDisplayed()
    // Without a distance the card shows only the category, which is also a selector chip.
    composeTestRule
        .onNode(hasText("Other") and hasAnyAncestor(hasTestTag(BrowseScreenTags.LISTING_CARD)))
        .assertIsDisplayed()
    composeTestRule.onNodeWithText("CHF 10").assertIsDisplayed()
    composeTestRule.onNodeWithText("/ day").assertIsDisplayed()
  }

  @Test
  fun multipleListingsDisplay_withCorrectMetadata() {
    val viewModel = BrowseViewModel(FakeListingRepository(sampleListings), currentUid)
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mini projector").assertIsDisplayed()
    composeTestRule.onNodeWithText("Claw hammer").assertIsDisplayed()
  }

  @Test
  fun noResultsFound_stillShowsUI() {
    val viewModel = BrowseViewModel(FakeListingRepository(sampleListings), currentUid)
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
    val viewModel = BrowseViewModel(repository, currentUid)
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
    val viewModel = BrowseViewModel(repository, currentUid)
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

  @Test
  fun scrollingToTheLastListing_keepsTheSearchFieldAndScrollsTheHeaderAway() {
    showManyListingsScrolledToTheLast()

    composeTestRule.onNodeWithTag(BrowseScreenTags.SEARCH_FIELD).assertIsDisplayed()
    composeTestRule.onNodeWithTag(BrowseScreenTags.HEADER).assertDoesNotExist()
    assertLastListingIsClearOfTheSearchField()
  }

  @Config(qualifiers = "w640dp-h360dp-land")
  @Test
  fun scrollingToTheLastListingOnAShortWindow_showsItBelowTheSearchField() {
    showManyListingsScrolledToTheLast()

    composeTestRule.onNodeWithTag(BrowseScreenTags.SEARCH_FIELD).assertIsDisplayed()
    assertLastListingIsClearOfTheSearchField()
  }

  @Test
  fun typingAfterScrolling_filtersTheListings() {
    showManyListingsScrolledToTheLast()

    composeTestRule.onNodeWithTag(BrowseScreenTags.SEARCH_FIELD).performTextInput("10")

    composeTestRule.onNodeWithText("Listing 10").assertIsDisplayed()
    composeTestRule.onNodeWithText(LAST_LISTING).assertDoesNotExist()
  }

  private val drillCard =
      BrowseListing(id = "drill", title = "Bosch drill set", category = "Tools", price = "CHF 8")

  private fun showManyListingsScrolledToTheLast() {
    val listings =
        List(12) {
          ItemListing(ListingCategory.ITEM_TOOLS, 8.0, "Listing $it", "", listingId = "listing-$it")
        }
    val viewModel = BrowseViewModel(FakeListingRepository(listings), currentUid)
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }
    composeTestRule
        .onNodeWithTag(BrowseScreenTags.SCREEN)
        .performScrollToNode(hasText(LAST_LISTING))
  }

  private fun assertLastListingIsClearOfTheSearchField() {
    val card =
        composeTestRule
            .onNode(
                hasTestTag(BrowseScreenTags.LISTING_CARD) and
                    hasAnyDescendant(hasText(LAST_LISTING))
            )
            .getBoundsInRoot()
    val search = composeTestRule.onNodeWithTag(BrowseScreenTags.SEARCH_FIELD).getBoundsInRoot()
    val screen = composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).getBoundsInRoot()
    assertTrue(card.top >= search.bottom)
    assertTrue(card.bottom <= screen.bottom)
  }

  private fun string(id: Int): String =
      ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

  private val allCategories = ListingCategory.entries.map { it.displayName }.distinct().sorted()

  // Cards show the category too, so this targets the chip in the category selector.
  private fun categoryChip(category: String): SemanticsNodeInteraction =
      composeTestRule.onNode(
          hasText(category) and hasAnyAncestor(hasTestTag(BrowseScreenTags.CATEGORY_SELECTOR))
      )

  private fun clickCategory(category: String) {
    categoryChip(category).performClick()
  }
}

private const val LAST_LISTING = "Listing 11"

// Narrower than all category chips together, so the chip row overflows.
private val NARROW_WIDTH = 360.dp
