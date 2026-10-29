// Co-authored-by: Copilot GPT-5.6 Terra
package com.android.polybazaar.browse.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

  @Test
  fun search_filtersViewModelListings() {
    val viewModel =
        BrowseViewModel(
            BrowseUiState(
                listings =
                    listOf(
                        BrowseListing("drill", "Bosch drill set", "Tools", "350 m away", "CHF 8"),
                        BrowseListing(
                            "projector",
                            "Mini projector",
                            "Electronics",
                            "600 m away",
                            "CHF 12",
                        ),
                    ),
                categories = listOf("Tools", "Electronics"),
            )
        )
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithTag(BrowseScreenTags.SEARCH_FIELD).performTextInput("projector")

    composeTestRule.onNodeWithText("Mini projector").assertIsDisplayed()
    composeTestRule.onNodeWithText("Bosch drill set").assertDoesNotExist()
  }
}
