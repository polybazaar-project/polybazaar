// Co-authored-by: Claude Code (Claude Opus 5.5)
// Co-authored-by: Claude Code (Claude Sonnet 5.5)
// Co-authored-by: Copilot GPT-5.6 Terra
package com.android.polybazaar.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.auth.ui.CreateAccountScreenTags
import com.android.polybazaar.auth.ui.SignInScreenTags
import com.android.polybazaar.auth.ui.VerifyEmailScreenTags
import com.android.polybazaar.auth.viewmodel.AuthViewModel
import com.android.polybazaar.browse.ui.BrowseScreenTags
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.FilePhotoStorage
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.ui.CreateListingScreenTags
import com.android.polybazaar.profile.ui.EditProfileScreenTags
import com.android.polybazaar.profile.ui.ProfileScreenTags
import com.android.polybazaar.settings.ui.SettingsScreenTags
import com.android.polybazaar.testing.InMemoryBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end tests of what a user can do in the app today: create an account, verify the email,
 * edit the profile, publish a listing, sign out and sign back in. The real navigation graph,
 * screens and [AuthViewModel] run together; only the data layer is replaced, by [InMemoryBackend]
 * and [FakeListingRepository], so state written by one screen is read back by the others as it
 * would be from Firebase.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndFlowTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val backend = InMemoryBackend()

  // Signed-in users land on Browse, which starts empty. Published listings belong to the "ada"
  // account ([InMemoryBackend.register] gives it the uid "uid-ada"), so its own Browse hides them.
  private val listingRepository = FakeListingRepository(ownerUid = "uid-ada")
  private val photoStorage =
      FilePhotoStorage(
          ApplicationProvider.getApplicationContext(),
          FilePhotoStorage.CREATE_FORM,
      )
  private lateinit var authViewModel: AuthViewModel
  private lateinit var navController: NavHostController

  private fun launchApp() {
    authViewModel = AuthViewModel(backend)
    composeTestRule.setContent {
      navController = rememberNavController()
      AppNavHost(
          authViewModel = authViewModel,
          authRepository = backend,
          profileRepository = backend,
          listingRepository = listingRepository,
          photoStorage = photoStorage,
          navController = navController,
      )
    }
  }

  private fun createAccount(username: String, email: String, password: String) {
    composeTestRule.onNodeWithTag(SignInScreenTags.CREATE_ACCOUNT_LINK).performClick()
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.USERNAME_FIELD).performTextInput(username)
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.EMAIL_FIELD).performTextInput(email)
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.PASSWORD_FIELD).performTextInput(password)
    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput(password)
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).performClick()
  }

  private fun signIn(email: String, password: String) {
    composeTestRule.onNodeWithTag(SignInScreenTags.EMAIL_FIELD).performTextInput(email)
    composeTestRule.onNodeWithTag(SignInScreenTags.PASSWORD_FIELD).performTextInput(password)
    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).performClick()
  }

  private fun openProfileFromBrowse() {
    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(BottomBarTags.PROFILE_TAB).performClick()
    composeTestRule.onNodeWithTag(ProfileScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun newUser_createsAccountVerifiesEmailEditsProfileSignsOutAndSignsBackIn() {
    launchApp()
    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()

    createAccount("ada_lovelace", "ada@epfl.ch", "secret123")

    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.SCREEN).assertIsDisplayed()
    backend.openVerificationLink("ada@epfl.ch")
    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.CHECK_BUTTON).performClick()

    openProfileFromBrowse()
    composeTestRule.onNodeWithTag(ProfileScreenTags.NAME).assertTextEquals("ada_lovelace")

    composeTestRule.onNodeWithTag(ProfileScreenTags.EDIT_PROFILE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(EditProfileScreenTags.BIO_FIELD).performTextInput("Mathematician")
    composeTestRule.onNodeWithTag(EditProfileScreenTags.SAVE_BUTTON).performClick()

    composeTestRule.onNodeWithTag(ProfileScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ProfileScreenTags.BIO).assertTextEquals("Mathematician")

    composeTestRule.runOnIdle { authViewModel.signOut() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()

    signIn("ada@epfl.ch", "secret123")

    openProfileFromBrowse()
    composeTestRule.onNodeWithTag(ProfileScreenTags.NAME).assertTextEquals("ada_lovelace")
    composeTestRule.onNodeWithTag(ProfileScreenTags.BIO).assertTextEquals("Mathematician")
  }

  @Test
  fun takenUsername_keepsUserOnCreateAccountWithError() {
    backend.register(email = "grace@epfl.ch", password = "secret123", username = "ada_lovelace")
    launchApp()

    createAccount("ada_lovelace", "ada@epfl.ch", "secret123")

    composeTestRule.onNodeWithTag(CreateAccountScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.ERROR_MESSAGE)
        .assertTextEquals("This username is already taken.")
  }

  @Test
  fun wrongPassword_keepsUserOnSignInWithError() {
    backend.register(email = "ada@epfl.ch", password = "secret123", username = "ada_lovelace")
    launchApp()

    signIn("ada@epfl.ch", "not-the-password")

    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(SignInScreenTags.ERROR_MESSAGE)
        .assertTextEquals("Incorrect email or password.")
  }

  @Test
  fun persistedSession_opensBrowseAndProfileTabShowsSavedBio() {
    val user = backend.register(email = "ada@epfl.ch", password = "secret123", username = "ada")
    backend.setSignedIn(user.uid)
    backend.setBio(user.uid, "Back again")
    launchApp()

    openProfileFromBrowse()
    composeTestRule.onNodeWithTag(ProfileScreenTags.BIO).assertTextEquals("Back again")
  }

  @Test
  fun verifiedUser_publishesAListingAndDoesNotSeeItOnBrowse() {
    val user = backend.register(email = "ada@epfl.ch", password = "secret123", username = "ada")
    backend.setSignedIn(user.uid)
    launchApp()
    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()

    composeTestRule.onNodeWithTag(BottomBarTags.CREATE_LISTING_TAB).performClick()
    composeTestRule
        .onNodeWithTag(CreateListingScreenTags.TITLE_FIELD)
        .performTextInput("Bosch drill set")
    composeTestRule
        .onNodeWithTag(CreateListingScreenTags.DESCRIPTION_FIELD)
        .performTextInput("Comes with bits")
    composeTestRule.onNodeWithTag(CreateListingScreenTags.PRICE_FIELD).performTextInput("8")
    composeTestRule.onNodeWithTag(CreateListingScreenTags.CATEGORY_FIELD).performClick()
    composeTestRule
        .onNodeWithTag(CreateListingScreenTags.categoryOption(ListingCategory.ITEM_TOOLS))
        .performClick()
    composeTestRule
        .onNodeWithTag(CreateListingScreenTags.CREATE_BUTTON)
        .performScrollTo()
        .performClick()

    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
    // Saved as Ada's, so it is hidden on purpose and not because publishing failed.
    assertEquals(user.uid, listingRepository.allListings.single().ownerUid)
    composeTestRule.onNodeWithText("Bosch drill set").assertDoesNotExist()
  }

  @Test
  fun signOutFromSettingsWithADraftListing_nextUserGetsAnEmptyForm() {
    val ada = backend.register(email = "ada@epfl.ch", password = "secret123", username = "ada")
    backend.register(email = "grace@epfl.ch", password = "secret456", username = "grace")
    backend.setSignedIn(ada.uid)
    launchApp()
    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(BottomBarTags.CREATE_LISTING_TAB).performClick()
    composeTestRule
        .onNodeWithTag(CreateListingScreenTags.TITLE_FIELD)
        .performTextInput("Ada's draft")

    composeTestRule.onNodeWithTag(BottomBarTags.PROFILE_TAB).performClick()
    composeTestRule.onNodeWithTag(ProfileScreenTags.SETTINGS_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_CONFIRM).performClick()
    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()

    signIn("grace@epfl.ch", "secret456")
    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(BottomBarTags.CREATE_LISTING_TAB).performClick()

    composeTestRule.onNodeWithTag(CreateListingScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithText("Ada's draft").assertDoesNotExist()
  }

  @Test
  fun signOutWithSavedProfileTab_thenSignInAsAnotherUser_profileTabShowsTheNewUser() {
    val ada = backend.register(email = "ada@epfl.ch", password = "secret123", username = "ada")
    backend.register(email = "grace@epfl.ch", password = "secret456", username = "grace")
    backend.setBio(ada.uid, "Ada's bio")
    backend.setSignedIn(ada.uid)
    launchApp()
    openProfileFromBrowse()
    val adaProfileEntryId = navController.currentBackStackEntry?.id

    // Ada's Profile entry is saved, not on the back stack, when the session ends.
    composeTestRule.onNodeWithTag(BottomBarTags.BROWSE_TAB).performClick()
    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.runOnIdle { authViewModel.signOut() }
    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.waitForIdle()

    // Ada's ProfileViewModel has been cleared, so nothing keeps observing her profile.
    assertEquals(0, backend.profiles.subscriptionCount.value)

    signIn("grace@epfl.ch", "secret456")
    openProfileFromBrowse()

    assertNotEquals(adaProfileEntryId, navController.currentBackStackEntry?.id)
    composeTestRule.onNodeWithTag(ProfileScreenTags.NAME).assertTextEquals("grace")
    composeTestRule.onNodeWithText("Ada's bio").assertDoesNotExist()
  }
}
