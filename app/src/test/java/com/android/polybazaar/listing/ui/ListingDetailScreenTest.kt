// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ServiceListing
import com.android.polybazaar.listing.viewmodel.ListingDetailUiState
import com.android.polybazaar.listing.viewmodel.ListingDetailViewModel
import com.android.polybazaar.profile.model.ProfileRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class ListingDetailScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private val drill =
      ItemListing(
          category = ListingCategory.ITEM_TOOLS,
          price = 8.0,
          title = "Bosch drill set",
          description = "Cordless drill with two batteries.",
          refundValue = 120.0,
          maxRentalDays = 7,
          listingId = "drill",
          ownerUid = "lender-1",
      )
  private val tutoring =
      ServiceListing(
          category = ListingCategory.SERVICE_TUTORING,
          price = 25.5,
          title = "Analysis tutoring",
          description = "One hour before the exam.",
          listingId = "tutoring",
          ownerUid = "lender-1",
      )
  private val lender =
      User(uid = "lender-1", email = "james@epfl.ch", username = "james", bio = "Weekend maker")

  private fun setContent(
      uiState: ListingDetailUiState,
      actions: ListingDetailActions = ListingDetailActions(),
      onRetry: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      ListingDetailContent(uiState = uiState, actions = actions, onRetry = onRetry)
    }
  }

  /** The returns line of an item whose return terms are not stored yet. */
  private fun returnsPending() =
      context.getString(
          R.string.listing_detail_returns,
          context.getString(R.string.listing_detail_returns_pending),
      )

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun text(text: String) = composeTestRule.onNodeWithText(text, useUnmergedTree = true)

  @Test
  fun beforeTheListingArrives_showsTheIndicatorAndBack() {
    var backs = 0
    // The state before the first load starts: no listing and no error counts as loading.
    setContent(ListingDetailUiState(), ListingDetailActions(onBack = { backs++ }))

    node(ListingDetailScreenTags.LOADING_INDICATOR).assertIsDisplayed()
    node(ListingDetailScreenTags.BACK_BUTTON).performClick()
    assertEquals(1, backs)
    node(ListingDetailScreenTags.TITLE).assertDoesNotExist()
  }

  @Test
  fun loadFailed_showsTheErrorAndRetry() {
    var retries = 0
    setContent(
        ListingDetailUiState(error = AppError.NETWORK),
        onRetry = { retries++ },
    )

    node(ListingDetailScreenTags.ERROR_MESSAGE)
        .assertTextEquals(context.getString(R.string.error_network))
    node(ListingDetailScreenTags.RETRY_BUTTON).performClick()
    assertEquals(1, retries)
    node(ListingDetailScreenTags.LOADING_INDICATOR).assertDoesNotExist()
  }

  @Test
  fun reloadFailed_keepsTheListingShown() {
    setContent(ListingDetailUiState(listing = drill, error = AppError.NETWORK))

    node(ListingDetailScreenTags.TITLE).assertTextEquals("Bosch drill set")
    node(ListingDetailScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun item_showsItsOverviewDescriptionAndPrice() {
    setContent(ListingDetailUiState(listing = drill))

    node(ListingDetailScreenTags.CATEGORY).assertTextEquals("TOOLS")
    node(ListingDetailScreenTags.TITLE).assertTextEquals("Bosch drill set")
    node(ListingDetailScreenTags.DESCRIPTION).assertTextEquals("Cordless drill with two batteries.")
    val perDay = context.getString(R.string.browse_price_per_day)
    node(ListingDetailScreenTags.PRICE).assertTextEquals("CHF 8 $perDay")
    node(ListingDetailScreenTags.DEPOSIT)
        .assertTextEquals(context.getString(R.string.listing_detail_deposit, "CHF 120"))
  }

  @Test
  fun item_showsItsTermsWithToBeConfirmedForThoseNotStored() {
    setContent(ListingDetailUiState(listing = drill))

    text(context.getString(R.string.listing_detail_item_terms_title)).assertExists()
    text(returnsPending()).assertExists()
    text(context.getString(R.string.listing_detail_damage, "CHF 120")).assertExists()
  }

  @Test
  fun service_hasNoDepositReturnsOrDamageTerms() {
    setContent(ListingDetailUiState(listing = tutoring))

    text(context.getString(R.string.listing_detail_service_terms_title)).assertExists()
    node(ListingDetailScreenTags.PRICE)
        .assertTextEquals("CHF 25.50 ${context.getString(R.string.browse_price_per_day)}")
    node(ListingDetailScreenTags.DEPOSIT).assertDoesNotExist()
    text(returnsPending()).assertDoesNotExist()
  }

  @Test
  fun lender_showsTheirNameAndBio_andOpensTheirProfile() {
    var opened: String? = null
    setContent(
        ListingDetailUiState(listing = drill, lender = lender),
        ListingDetailActions(onOpenLender = { opened = it }),
    )

    node(ListingDetailScreenTags.LENDER_NAME)
        .assertTextEquals(context.getString(R.string.listing_detail_lender_name, "james"))
    text("Weekend maker").assertExists()
    node(ListingDetailScreenTags.LENDER).performScrollTo().performClick()
    assertEquals("lender-1", opened)
  }

  @Test
  fun lenderWithoutABio_keepsA48dpTouchTarget() {
    setContent(ListingDetailUiState(listing = drill, lender = lender.copy(bio = "")))

    node(ListingDetailScreenTags.LENDER).assertHeightIsAtLeast(48.dp)
  }

  @Test
  fun lenderFailed_showsWhyInItsSectionOnly_andRetryReloads() {
    var retries = 0
    setContent(
        ListingDetailUiState(listing = drill, lenderError = AppError.NETWORK),
        onRetry = { retries++ },
    )

    node(ListingDetailScreenTags.LENDER_ERROR)
        .assertTextEquals(context.getString(R.string.error_network))
    node(ListingDetailScreenTags.LENDER).assertDoesNotExist()
    node(ListingDetailScreenTags.TITLE).assertIsDisplayed()
    node(ListingDetailScreenTags.LENDER_RETRY_BUTTON).performScrollTo().performClick()
    assertEquals(1, retries)
  }

  @Test
  fun lenderUnknown_showsOnlyMessageLender() {
    // Still loading, or the owner has no profile: both look the same.
    setContent(ListingDetailUiState(listing = drill, canContactLender = true))

    node(ListingDetailScreenTags.LENDER).assertDoesNotExist()
    node(ListingDetailScreenTags.LENDER_ERROR).assertDoesNotExist()
    node(ListingDetailScreenTags.MESSAGE_BUTTON).assertIsEnabled()
  }

  @Test
  fun lenderShown_staysWhenItsStreamFails() {
    setContent(
        ListingDetailUiState(listing = drill, lender = lender, lenderError = AppError.NETWORK)
    )

    node(ListingDetailScreenTags.LENDER_NAME)
        .assertTextEquals(context.getString(R.string.listing_detail_lender_name, "james"))
    node(ListingDetailScreenTags.LENDER_ERROR).assertDoesNotExist()
  }

  @Test
  fun canContactLender_enablesBothActions() {
    var messages = 0
    var dates = 0
    setContent(
        ListingDetailUiState(listing = drill, lender = lender, canContactLender = true),
        ListingDetailActions(onMessageLender = { messages++ }, onChooseDates = { dates++ }),
    )

    node(ListingDetailScreenTags.MESSAGE_BUTTON).performScrollTo().performClick()
    node(ListingDetailScreenTags.CHOOSE_DATES_BUTTON).performClick()
    assertEquals(1, messages)
    assertEquals(1, dates)
  }

  @Test
  fun cannotContactLender_disablesBothActions() {
    setContent(ListingDetailUiState(listing = drill, lender = lender, canContactLender = false))

    node(ListingDetailScreenTags.MESSAGE_BUTTON).assertIsNotEnabled()
    node(ListingDetailScreenTags.CHOOSE_DATES_BUTTON).assertIsNotEnabled()
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE) // Real text widths, so the price really is wide.
  fun longPriceOnANarrowScreen_keepsChooseDatesFullWidth() {
    composeTestRule.setContent {
      Box(modifier = Modifier.width(320.dp)) {
        ListingDetailContent(
            uiState = ListingDetailUiState(listing = drill.copy(price = 9999.5)),
            actions = ListingDetailActions(),
        )
      }
    }

    node(ListingDetailScreenTags.CHOOSE_DATES_BUTTON).assertWidthIsEqualTo(166.dp)
  }

  @Test
  fun preview_showsTheSampleListing() {
    composeTestRule.setContent { ListingDetailContentPreview() }

    node(ListingDetailScreenTags.TITLE).assertTextEquals("Bosch drill set")
    node(ListingDetailScreenTags.CHOOSE_DATES_BUTTON).assertIsEnabled()
  }

  @Test
  fun screen_showsWhatTheViewModelLoaded_andRetryReloads() {
    // The first load fails as if offline; Retry then reaches the repository again.
    val listings =
        FakeListingRepository(allListings = listOf(drill)).apply {
          failure =
              FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
        }
    val profiles = mock<ProfileRepository> { on { observeProfile(any()) } doReturn flowOf(lender) }
    val auth = mock<AuthRepository> { onBlocking { getCurrentUser() } doReturn lender }
    val viewModel = ListingDetailViewModel("drill", listings, profiles, auth)

    composeTestRule.setContent {
      ListingDetailScreen(viewModel = viewModel, actions = ListingDetailActions())
    }
    node(ListingDetailScreenTags.ERROR_MESSAGE)
        .assertTextEquals(context.getString(R.string.error_network))

    listings.failure = null
    node(ListingDetailScreenTags.RETRY_BUTTON).performClick()

    node(ListingDetailScreenTags.TITLE).assertTextEquals("Bosch drill set")
    node(ListingDetailScreenTags.LENDER_NAME)
        .assertTextEquals(context.getString(R.string.listing_detail_lender_name, "james"))
    // The viewer is the owner here, so both actions stay disabled.
    node(ListingDetailScreenTags.CHOOSE_DATES_BUTTON).assertIsNotEnabled()
    assertEquals(2, listings.getListingCalls)
  }
}
