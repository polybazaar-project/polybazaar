// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.profile.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.browse.viewmodel.BrowseListing
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.profile.model.ProfileRepository
import com.android.polybazaar.profile.viewmodel.LenderProfileUiState
import com.android.polybazaar.profile.viewmodel.LenderProfileViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LenderProfileScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private val lender =
      User(
          uid = "lender-1",
          email = "james.richard@epfl.ch",
          username = "james",
          bio = "Weekend maker",
      )
  private val drill =
      BrowseListing(id = "drill", title = "Bosch drill set", category = "Tools", price = "CHF 8")

  /** A state where both the profile and the listings have answered. */
  private fun loaded(
      profile: User? = lender,
      listings: List<BrowseListing> = listOf(drill),
      error: AppError? = null,
  ) =
      LenderProfileUiState(
          profile = profile,
          listings = listings,
          profileLoaded = true,
          listingsLoaded = true,
          error = error,
      )

  private fun setContent(
      uiState: LenderProfileUiState,
      onBack: () -> Unit = {},
      onOpenListing: (String) -> Unit = {},
  ) {
    composeTestRule.setContent {
      LenderProfileContent(uiState = uiState, onBack = onBack, onOpenListing = onOpenListing)
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  @Test
  fun loading_showsOnlyTheIndicator() {
    setContent(LenderProfileUiState(profileLoaded = true))

    node(LenderProfileScreenTags.LOADING_INDICATOR).assertIsDisplayed()
    node(LenderProfileScreenTags.NAME).assertDoesNotExist()
    node(LenderProfileScreenTags.NOT_FOUND).assertDoesNotExist()
  }

  @Test
  fun profileShown_withNameBioAndVerified_butNotTheEmail() {
    setContent(loaded())

    node(LenderProfileScreenTags.NAME).assertTextEquals("james")
    node(LenderProfileScreenTags.BIO).assertTextEquals("Weekend maker")
    node(LenderProfileScreenTags.VERIFIED)
        .assertTextEquals(context.getString(R.string.lender_profile_verified))
    node(ProfileComponentTags.AVATAR).assertIsDisplayed()
    composeTestRule.onNodeWithText(lender.email, substring = true).assertDoesNotExist()
    node(LenderProfileScreenTags.LOADING_INDICATOR).assertDoesNotExist()
  }

  @Test
  fun blankBio_isNotShown() {
    setContent(loaded(profile = lender.copy(bio = " ")))

    node(LenderProfileScreenTags.BIO).assertDoesNotExist()
  }

  @Test
  fun listings_areShownAndOpenTheTappedOne() {
    val opened = mutableListOf<String>()
    val projector = drill.copy(id = "projector", title = "Mini projector")
    setContent(loaded(listings = listOf(drill, projector)), onOpenListing = { opened += it })

    composeTestRule
        .onNodeWithTag(LenderProfileScreenTags.SCREEN)
        .performScrollToNode(hasTestTag(LenderProfileScreenTags.listing("projector")))
    node(LenderProfileScreenTags.listing("projector")).performClick()

    composeTestRule.onNodeWithText("Bosch drill set").assertExists()
    assertEquals(listOf("projector"), opened)
    node(LenderProfileScreenTags.NO_LISTINGS).assertDoesNotExist()
  }

  @Test
  fun noListings_showsTheEmptyText() {
    setContent(loaded(listings = emptyList()))

    node(LenderProfileScreenTags.NO_LISTINGS)
        .assertTextEquals(context.getString(R.string.lender_profile_no_listings))
  }

  @Test
  fun listingsFailed_showsTheErrorInsteadOfTheEmptyText() {
    setContent(loaded(listings = emptyList(), error = AppError.NETWORK))

    node(LenderProfileScreenTags.NAME).assertTextEquals("james")
    node(LenderProfileScreenTags.ERROR_MESSAGE)
        .assertTextEquals(context.getString(R.string.error_network))
    node(LenderProfileScreenTags.NO_LISTINGS).assertDoesNotExist()
  }

  @Test
  fun profileFailed_showsTheErrorAndNotNotFound() {
    setContent(loaded(profile = null, listings = emptyList(), error = AppError.NETWORK))

    node(LenderProfileScreenTags.ERROR_MESSAGE)
        .assertTextEquals(context.getString(R.string.error_network))
    node(LenderProfileScreenTags.NOT_FOUND).assertDoesNotExist()
    node(LenderProfileScreenTags.NAME).assertDoesNotExist()
  }

  @Test
  fun missingProfile_showsNotFound() {
    setContent(loaded(profile = null, listings = emptyList()))

    node(LenderProfileScreenTags.NOT_FOUND)
        .assertTextEquals(context.getString(R.string.lender_profile_not_found))
    node(LenderProfileScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun backButton_callsOnBack() {
    var backs = 0
    setContent(loaded(), onBack = { backs++ })

    node(LenderProfileScreenTags.BACK_BUTTON).performClick()

    assertEquals(1, backs)
  }

  @Test
  fun screen_loadsTheLenderOfTheGivenUid_andReloadsWhenItChanges() {
    val other = User(uid = "lender-2", email = "", username = "anna")
    val listings =
        FakeListingRepository(
            allListings =
                listOf(
                    ItemListing(
                        ListingCategory.ITEM_TOOLS,
                        8.0,
                        "Bosch drill set",
                        "",
                        listingId = "drill",
                        ownerUid = lender.uid,
                    ),
                    ItemListing(
                        ListingCategory.ITEM_ELECTRONICS,
                        12.0,
                        "Mini projector",
                        "",
                        listingId = "projector",
                        ownerUid = other.uid,
                    ),
                )
        )
    val viewModel = LenderProfileViewModel(FakeProfileRepository(listOf(lender, other)), listings)
    var uid by mutableStateOf(lender.uid)
    composeTestRule.setContent {
      LenderProfileScreen(viewModel = viewModel, lenderUid = uid, onBack = {}, onOpenListing = {})
    }

    node(LenderProfileScreenTags.NAME).assertTextEquals("james")
    node(LenderProfileScreenTags.listing("drill")).assertExists()

    uid = other.uid
    composeTestRule.waitForIdle()

    node(LenderProfileScreenTags.NAME).assertTextEquals("anna")
    node(LenderProfileScreenTags.listing("projector")).assertExists()
    node(LenderProfileScreenTags.listing("drill")).assertDoesNotExist()
  }

  /** Answers [observeProfile] with the matching user; the other calls are not used here. */
  private class FakeProfileRepository(users: List<User>) : ProfileRepository {
    private val usersByUid = users.associateBy { it.uid }

    override fun observeProfile(uid: String): Flow<User?> = flowOf(usersByUid[uid])

    override suspend fun uploadProfilePhoto(uid: String, photoUri: Uri) = ""

    override suspend fun loadProfilePhoto(photoPath: String) = ByteArray(0)

    override suspend fun removeProfilePhoto(uid: String) = Unit

    override suspend fun updateProfile(uid: String, photoPath: String?, bio: String) = Unit
  }
}
