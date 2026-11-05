// Co-authored-by: Claude Code (Claude Opus 5.5)
// Co-authored-by: Claude Code (Claude Sonnet 5.5)
// Co-authored-by: Copilot GPT-5.6 Terra
package com.android.polybazaar.ui.navigation

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.NoSessionException
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.ui.CreateAccountScreenTags
import com.android.polybazaar.auth.ui.ForgotPasswordScreenTags
import com.android.polybazaar.auth.ui.SignInScreenTags
import com.android.polybazaar.auth.ui.VerifyEmailScreenTags
import com.android.polybazaar.auth.viewmodel.AuthViewModel
import com.android.polybazaar.browse.ui.BrowseScreenTags
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.FilePhotoStorage
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.ui.CreateListingScreenTags
import com.android.polybazaar.profile.model.ProfileRepository
import com.android.polybazaar.profile.ui.EditProfileScreenTags
import com.android.polybazaar.profile.ui.ProfileScreenTags
import com.android.polybazaar.profile.ui.photoPicker
import com.android.polybazaar.profile.ui.redPhoto
import com.android.polybazaar.profile.ui.waitForAvatar
import com.android.polybazaar.settings.ui.SettingsScreenTags
import com.android.polybazaar.toolbox.ui.ToolboxCardTags
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class AppNavHostTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val user =
      User(
          uid = "uid-ada",
          email = "ada@epfl.ch",
          username = "ada_lovelace",
          isEmailVerified = true,
      )
  private val unverifiedUser = user.copy(isEmailVerified = false)

  private val profileRepository = FakeProfileRepository(user)

  private val listingRepository = FakeListingRepository()
  private val photoStorage = FilePhotoStorage(ApplicationProvider.getApplicationContext())
  private lateinit var navController: NavHostController

  /** Shows the app, under [lifecycleOwner] when given so a test can pause and resume it. */
  private fun setContent(
      repository: FakeAuthRepository,
      lifecycleOwner: LifecycleOwner? = null,
  ): AuthViewModel {
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent {
      navController = rememberNavController()
      val owner = lifecycleOwner ?: LocalLifecycleOwner.current
      CompositionLocalProvider(
          LocalLifecycleOwner provides owner,
          LocalActivityResultRegistryOwner provides
              photoPicker(Uri.parse("content://picked/photo")),
      ) {
        AppNavHost(
            authViewModel = viewModel,
            authRepository = repository,
            profileRepository = profileRepository,
            listingRepository = listingRepository,
            photoStorage = photoStorage,
            navController = navController,
        )
      }
    }
    return viewModel
  }

  /** A lifecycle the test moves by hand, starting resumed. */
  private class ManualLifecycleOwner : LifecycleOwner {
    val registry =
        LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle
      get() = registry
  }

  /**
   * Rebuilds the UI from its saved state with a new [AuthViewModel] and new ViewModel stores, as
   * after process death.
   */
  private fun restoreSavedState(
      before: FakeAuthRepository,
      after: FakeAuthRepository = before,
      actions: () -> Unit,
  ) {
    val tester = StateRestorationTester(composeTestRule)
    var repository = before
    var storeOwner = newViewModelStoreOwner()
    tester.setContent {
      CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val viewModel = remember { AuthViewModel(repository) }
        navController = rememberNavController()
        AppNavHost(
            authViewModel = viewModel,
            authRepository = repository,
            profileRepository = profileRepository,
            listingRepository = listingRepository,
            photoStorage = photoStorage,
            navController = navController,
        )
      }
    }
    actions()
    repository = after
    storeOwner = newViewModelStoreOwner()
    tester.emulateSavedInstanceStateRestore()
  }

  private fun newViewModelStoreOwner() =
      object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
      }

  private fun openProfile() {
    composeTestRule.onNodeWithTag(BottomBarTags.PROFILE_TAB).performClick()
    composeTestRule.onNodeWithTag(ProfileScreenTags.SCREEN).assertIsDisplayed()
  }

  private fun openCreateListing() {
    composeTestRule.onNodeWithTag(BottomBarTags.CREATE_LISTING_TAB).performClick()
    composeTestRule.onNodeWithTag(CreateListingScreenTags.SCREEN).assertIsDisplayed()
  }

  private fun publishListing(title: String) {
    composeTestRule.onNodeWithTag(CreateListingScreenTags.TITLE_FIELD).performTextInput(title)
    composeTestRule
        .onNodeWithTag(CreateListingScreenTags.DESCRIPTION_FIELD)
        .performTextInput("Works well")
    composeTestRule.onNodeWithTag(CreateListingScreenTags.PRICE_FIELD).performTextInput("5")
    composeTestRule.onNodeWithTag(CreateListingScreenTags.CATEGORY_FIELD).performClick()
    composeTestRule
        .onNodeWithTag(CreateListingScreenTags.categoryOption(ListingCategory.ITEM_TOOLS))
        .performClick()
    composeTestRule
        .onNodeWithTag(CreateListingScreenTags.CREATE_BUTTON)
        .performScrollTo()
        .performClick()
  }

  private fun openBrowse() {
    composeTestRule.onNodeWithTag(BottomBarTags.BROWSE_TAB).performClick()
    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
  }

  private fun profileScrollPosition(): Float =
      composeTestRule
          .onNode(hasScrollAction())
          .fetchSemanticsNode()
          .config[SemanticsProperties.VerticalScrollAxisRange]
          .value()

  private fun assertBottomBarHidden() {
    composeTestRule.onNodeWithTag(BottomBarTags.BAR).assertDoesNotExist()
  }

  private fun assertEndsAboveBottomBar(screenTag: String) {
    val content = composeTestRule.onNodeWithTag(screenTag).getBoundsInRoot()
    val bar = composeTestRule.onNodeWithTag(BottomBarTags.BAR).getBoundsInRoot()
    assertTrue(content.bottom <= bar.top)
  }

  private fun openSettings() {
    openProfile()
    composeTestRule.onNodeWithTag(ProfileScreenTags.SETTINGS_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun showsLoadingUntilSessionCheckCompletes() {
    val gate = CompletableDeferred<User?>()
    setContent(FakeAuthRepository(currentUserGate = gate))

    composeTestRule.onNodeWithTag(AppNavHostTags.LOADING).assertIsDisplayed()

    gate.complete(null)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun persistedUser_startsOnBrowse() {
    setContent(FakeAuthRepository(currentUser = user))

    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun browse_showsTheListingsFromTheListingRepository() {
    listingRepository.allListings =
        listOf(
            ItemListing(ListingCategory.ITEM_TOOLS, 8.0, "Bosch drill set", "", listingId = "drill")
        )
    setContent(FakeAuthRepository(currentUser = user))

    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    assertEquals(1, listingRepository.getAllListingsCalls)
  }

  @Test
  fun sessionCheckOffline_offersRetryInsteadOfSignIn() {
    setContent(
        FakeAuthRepository(currentUser = user, currentUserError = UnknownHostException("offline"))
    )

    composeTestRule.onNodeWithTag(AppNavHostTags.SESSION_ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertDoesNotExist()
  }

  @Test
  fun sessionCheckOffline_retryOnceOnline_startsOnBrowse() {
    val repository =
        FakeAuthRepository(currentUser = user, currentUserError = UnknownHostException("offline"))
    setContent(repository)
    composeTestRule.onNodeWithTag(AppNavHostTags.SESSION_ERROR).assertIsDisplayed()

    repository.currentUserError = null
    composeTestRule.onNodeWithTag(AppNavHostTags.SESSION_RETRY).performClick()

    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
  }

  // Coming back to the app after turning the connection on must not need a tap on Retry.
  @Test
  fun sessionCheckOffline_retriesWhenTheAppResumes() {
    val repository =
        FakeAuthRepository(currentUser = user, currentUserError = UnknownHostException("offline"))
    val lifecycleOwner = ManualLifecycleOwner()
    setContent(repository, lifecycleOwner)
    composeTestRule.onNodeWithTag(AppNavHostTags.SESSION_ERROR).assertIsDisplayed()

    repository.currentUserError = null
    composeTestRule.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.STARTED }
    composeTestRule.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED }

    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun sessionCheckOffline_resumingWhileStillOfflineKeepsTheRetryOffer() {
    val repository =
        FakeAuthRepository(currentUser = user, currentUserError = UnknownHostException("offline"))
    val lifecycleOwner = ManualLifecycleOwner()
    setContent(repository, lifecycleOwner)

    composeTestRule.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.STARTED }
    composeTestRule.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED }

    composeTestRule.onNodeWithTag(AppNavHostTags.SESSION_ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertDoesNotExist()
  }

  @Test
  fun profile_showsTheUsersListingsInTheToolbox() {
    listingRepository.myListings =
        listOf(
            ItemListing(ListingCategory.ITEM_TOOLS, 8.0, "Bosch drill set", "", listingId = "drill")
        )
    setContent(FakeAuthRepository(currentUser = user))
    openProfile()

    composeTestRule.onNodeWithTag(ToolboxCardTags.LISTING_ROW).assertIsDisplayed()
    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
  }

  private fun manyListings() =
      List(30) {
        ItemListing(ListingCategory.ITEM_TOOLS, 8.0, "Listing $it", "", listingId = "listing-$it")
      }

  @Config(qualifiers = "h900dp")
  @Test
  fun profile_staysFixedWhileTheToolboxScrolls() {
    listingRepository.myListings = manyListings()
    setContent(FakeAuthRepository(currentUser = user))
    openProfile()
    val editButton = composeTestRule.onNodeWithTag(ProfileScreenTags.EDIT_PROFILE_BUTTON)
    val editBounds = editButton.getBoundsInRoot()
    composeTestRule.onNodeWithText("Listing 0").assertIsDisplayed()

    composeTestRule.onNodeWithTag(ToolboxCardTags.LISTINGS).performTouchInput { swipeUp() }

    composeTestRule.onNodeWithText("Listing 0").assertDoesNotExist()
    editButton.assertIsDisplayed()
    assertEquals(editBounds, editButton.getBoundsInRoot())
    composeTestRule.onNodeWithTag(ProfileScreenTags.SETTINGS_BUTTON).assertIsDisplayed()
  }

  @Config(qualifiers = "h900dp")
  @Test
  fun switchingTabs_keepsTheToolboxScrollPosition() {
    listingRepository.myListings = manyListings()
    setContent(FakeAuthRepository(currentUser = user))
    openProfile()
    composeTestRule
        .onNodeWithTag(ToolboxCardTags.LISTINGS)
        .performScrollToNode(hasText("Listing 29"))

    openBrowse()
    openProfile()

    composeTestRule.onNodeWithText("Listing 29").assertIsDisplayed()
  }

  @Test
  fun toolbox_showsAListingPublishedSinceProfileWasLastOpen() {
    setContent(FakeAuthRepository(currentUser = user))
    openProfile()
    composeTestRule.onNodeWithTag(ToolboxCardTags.LISTING_ROW).assertDoesNotExist()

    openCreateListing()
    listingRepository.myListings =
        listOf(
            ItemListing(
                ListingCategory.ITEM_TOOLS,
                5.0,
                "Bosch drill set",
                "Works well",
                listingId = "drill",
            )
        )
    publishListing("Bosch drill set")
    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()

    openProfile()
    composeTestRule.onNodeWithTag(ToolboxCardTags.LISTING_ROW).assertIsDisplayed()
  }

  @Test
  fun noPersistedUser_startsOnSignIn() {
    setContent(FakeAuthRepository(currentUser = null))

    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun signIn_navigatesToBrowse() {
    val repository = FakeAuthRepository(currentUser = null, signInResult = Result.success(user))
    setContent(repository)

    composeTestRule.onNodeWithTag(SignInScreenTags.EMAIL_FIELD).performTextInput("ada@epfl.ch")
    composeTestRule.onNodeWithTag(SignInScreenTags.PASSWORD_FIELD).performTextInput("secret1")
    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).performClick()

    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun wrongCredentials_stayOnSignInWithError() {
    val repository =
        FakeAuthRepository(
            currentUser = null,
            signInResult = Result.failure(IllegalArgumentException("Wrong password")),
        )
    setContent(repository)

    composeTestRule.onNodeWithTag(SignInScreenTags.EMAIL_FIELD).performTextInput("ada@epfl.ch")
    composeTestRule.onNodeWithTag(SignInScreenTags.PASSWORD_FIELD).performTextInput("wrong")
    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).performClick()

    composeTestRule.onNodeWithTag(SignInScreenTags.ERROR_MESSAGE).assertTextEquals("Wrong password")
    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun createAccountLink_opensCreateAccountAndBackReturnsToSignIn() {
    setContent(FakeAuthRepository(currentUser = null))

    composeTestRule.onNodeWithTag(SignInScreenTags.CREATE_ACCOUNT_LINK).performClick()
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.SCREEN).assertIsDisplayed()

    composeTestRule.onNodeWithTag(CreateAccountScreenTags.BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun signIn_removesAuthScreensFromBackStack() {
    val repository = FakeAuthRepository(currentUser = null, signInResult = Result.success(user))
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent {
      navController = rememberNavController()
      AppNavHost(
          authViewModel = viewModel,
          authRepository = repository,
          profileRepository = profileRepository,
          listingRepository = listingRepository,
          photoStorage = photoStorage,
          navController = navController,
      )
    }

    composeTestRule.onNodeWithTag(SignInScreenTags.CREATE_ACCOUNT_LINK).performClick()
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.SCREEN).assertIsDisplayed()
    viewModel.signIn("ada@epfl.ch", "secret1")
    composeTestRule.waitForIdle()

    assertEquals(Routes.BROWSE, navController.currentBackStackEntry?.destination?.route)
    assertNull(navController.previousBackStackEntry)
  }

  @Test
  fun editProfileButton_opensEditProfileAndCancelReturnsToProfile() {
    setContent(FakeAuthRepository(currentUser = user))
    openProfile()

    composeTestRule.onNodeWithTag(ProfileScreenTags.EDIT_PROFILE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(EditProfileScreenTags.SCREEN).assertIsDisplayed()

    composeTestRule.onNodeWithTag(EditProfileScreenTags.CANCEL_BUTTON).performClick()
    composeTestRule.onNodeWithTag(ProfileScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE) // Decoding and drawing the photo need real bitmaps.
  fun savedPhoto_showsOnEditProfile_andSavingOnlyTheBioDoesNotDownloadItAgain() {
    profileRepository.photo = redPhoto()
    profileRepository.profile.value = user.copy(photoPath = "photo-path")
    setContent(FakeAuthRepository(currentUser = user))
    openProfile()
    composeTestRule.waitForAvatar(isRed = true)

    composeTestRule.onNodeWithTag(ProfileScreenTags.EDIT_PROFILE_BUTTON).performClick()
    composeTestRule.waitForAvatar(isRed = true)
    composeTestRule.onNodeWithTag(EditProfileScreenTags.BIO_FIELD).performTextInput("Hello")
    composeTestRule.onNodeWithTag(EditProfileScreenTags.SAVE_BUTTON).performClick()

    composeTestRule.onNodeWithTag(ProfileScreenTags.BIO).assertTextEquals("Hello")
    assertEquals(1, profileRepository.photoLoads)
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun savingANewPhoto_downloadsAndShowsIt() {
    profileRepository.profile.value = user.copy(photoPath = "photo-path")
    setContent(FakeAuthRepository(currentUser = user))
    openProfile()
    composeTestRule.onNodeWithTag(ProfileScreenTags.EDIT_PROFILE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(EditProfileScreenTags.CHANGE_PHOTO_BUTTON).performClick()
    profileRepository.photo = redPhoto()

    composeTestRule.onNodeWithTag(EditProfileScreenTags.SAVE_BUTTON).performClick()

    composeTestRule.waitForAvatar(isRed = true)
  }

  @Test
  fun savingProfile_returnsToProfileWithUpdatedBio() {
    setContent(FakeAuthRepository(currentUser = user))
    openProfile()

    composeTestRule.onNodeWithTag(ProfileScreenTags.EDIT_PROFILE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(EditProfileScreenTags.BIO_FIELD).performTextInput("Hello")
    composeTestRule.onNodeWithTag(EditProfileScreenTags.SAVE_BUTTON).performClick()

    composeTestRule.onNodeWithTag(ProfileScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ProfileScreenTags.BIO).assertTextEquals("Hello")
    assertEquals("Hello", profileRepository.savedBio)
  }

  @Test
  fun settingsButton_opensSettingsAndBackReturnsToProfile() {
    setContent(FakeAuthRepository(currentUser = user))
    openProfile()

    composeTestRule.onNodeWithTag(ProfileScreenTags.SETTINGS_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTags.SCREEN).assertIsDisplayed()

    composeTestRule.onNodeWithTag(SettingsScreenTags.BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(ProfileScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun signOutFromSettings_returnsToSignInAndClearsBackStack() {
    val viewModel = setContent(FakeAuthRepository(currentUser = user))
    openSettings()

    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_CONFIRM).performClick()

    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()
    assertNull(viewModel.uiState.value.user)
    assertEquals(Routes.SIGN_IN, navController.currentBackStackEntry?.destination?.route)
    assertNull(navController.previousBackStackEntry)
  }

  @Test
  fun cancellingSignOut_staysOnSettingsSignedIn() {
    val viewModel = setContent(FakeAuthRepository(currentUser = user))
    openSettings()

    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_CANCEL).performClick()

    composeTestRule.onNodeWithTag(SettingsScreenTags.SCREEN).assertIsDisplayed()
    assertNotNull(viewModel.uiState.value.user)
  }

  @Test
  fun failedSignOut_showsErrorAndStaysOnSettings() {
    setContent(
        FakeAuthRepository(currentUser = user, signOutError = IllegalStateException("No network"))
    )
    openSettings()

    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_CONFIRM).performClick()

    composeTestRule.onNodeWithText("No network").assertIsDisplayed()
    composeTestRule.onNodeWithTag(SettingsScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun failedSignOut_clearsErrorOnceShown() {
    val viewModel =
        setContent(
            FakeAuthRepository(
                currentUser = user,
                signOutError = IllegalStateException("No network"),
            )
        )
    openSettings()
    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_CONFIRM).performClick()
    composeTestRule.onNodeWithText("No network").assertIsDisplayed()
    composeTestRule.waitForIdle()

    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun failedSignOut_errorDoesNotReturnOnNextVisit() {
    setContent(
        FakeAuthRepository(currentUser = user, signOutError = IllegalStateException("No network"))
    )
    openSettings()
    confirmSignOut()
    composeTestRule.onNodeWithText("No network").assertIsDisplayed()
    waitForSnackbarToDisappear("No network")

    composeTestRule.onNodeWithTag(SettingsScreenTags.BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(ProfileScreenTags.SETTINGS_BUTTON).performClick()

    composeTestRule.onNodeWithTag(SettingsScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithText("No network").assertDoesNotExist()
  }

  @Test
  fun failedSignOut_repeatedFailureShowsErrorAgain() {
    setContent(
        FakeAuthRepository(currentUser = user, signOutError = IllegalStateException("No network"))
    )
    openSettings()
    confirmSignOut()
    composeTestRule.onNodeWithText("No network").assertIsDisplayed()
    waitForSnackbarToDisappear("No network")

    confirmSignOut()

    composeTestRule.onNodeWithText("No network").assertIsDisplayed()
  }

  private fun confirmSignOut() {
    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTags.SIGN_OUT_CONFIRM).performClick()
  }

  private fun waitForSnackbarToDisappear(text: String) {
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }
  }

  @Test
  fun forgotPasswordLink_opensForgotPasswordAndBackReturnsToSignIn() {
    setContent(FakeAuthRepository(currentUser = null))

    composeTestRule.onNodeWithTag(SignInScreenTags.FORGOT_PASSWORD_LINK).performClick()
    composeTestRule.onNodeWithTag(ForgotPasswordScreenTags.SCREEN).assertIsDisplayed()

    composeTestRule.onNodeWithTag(ForgotPasswordScreenTags.BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun forgotPassword_sendsTheLinkThroughTheGivenRepository() {
    val repository = FakeAuthRepository(currentUser = null)
    setContent(repository)

    composeTestRule.onNodeWithTag(SignInScreenTags.FORGOT_PASSWORD_LINK).performClick()
    composeTestRule
        .onNodeWithTag(ForgotPasswordScreenTags.EMAIL_FIELD)
        .performTextInput("ada@epfl.ch")
    composeTestRule.onNodeWithTag(ForgotPasswordScreenTags.SEND_BUTTON).performClick()

    composeTestRule.onNodeWithTag(ForgotPasswordScreenTags.SENT_MESSAGE).assertIsDisplayed()
    assertEquals(listOf("ada@epfl.ch"), repository.resetEmails)

    // "Back to sign in" leaves the screen, and coming back starts with an empty form.
    composeTestRule.onNodeWithTag(ForgotPasswordScreenTags.BACK_TO_SIGN_IN_LINK).performClick()
    composeTestRule.onNodeWithTag(SignInScreenTags.FORGOT_PASSWORD_LINK).performClick()
    composeTestRule.onNodeWithTag(ForgotPasswordScreenTags.SENT_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun signOut_returnsToSignInAndClearsBackStack() {
    val repository = FakeAuthRepository(currentUser = user)
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent {
      navController = rememberNavController()
      AppNavHost(
          authViewModel = viewModel,
          authRepository = repository,
          profileRepository = profileRepository,
          listingRepository = listingRepository,
          photoStorage = photoStorage,
          navController = navController,
      )
    }
    openProfile()
    composeTestRule.onNodeWithTag(ProfileScreenTags.EDIT_PROFILE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(EditProfileScreenTags.SCREEN).assertIsDisplayed()

    viewModel.signOut()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()
    assertEquals(Routes.SIGN_IN, navController.currentBackStackEntry?.destination?.route)
    assertNull(navController.previousBackStackEntry)
  }

  @Test
  fun persistedUnverifiedUser_startsOnVerifyEmailWithoutMessage() {
    setContent(
        FakeAuthRepository(
            currentUser = unverifiedUser,
            refreshUserResult = Result.success(unverifiedUser),
        )
    )

    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.EMAIL_TEXT).assertTextEquals("ada@epfl.ch")
    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun signIn_unverifiedUser_opensVerifyEmail() {
    setContent(
        FakeAuthRepository(
            currentUser = null,
            signInResult = Result.success(unverifiedUser),
            refreshUserResult = Result.success(unverifiedUser),
        )
    )

    composeTestRule.onNodeWithTag(SignInScreenTags.EMAIL_FIELD).performTextInput("ada@epfl.ch")
    composeTestRule.onNodeWithTag(SignInScreenTags.PASSWORD_FIELD).performTextInput("secret1")
    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).performClick()

    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun emailVerifiedOutsideTheApp_opensBrowseWithoutSigningInAgain() {
    // The cached session still says unverified, but the server already knows about the link.
    setContent(
        FakeAuthRepository(currentUser = unverifiedUser, refreshUserResult = Result.success(user))
    )

    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun checkButton_onceVerified_opensBrowseAndClearsBackStack() {
    lateinit var navController: NavHostController
    val repository =
        FakeAuthRepository(
            currentUser = unverifiedUser,
            refreshUserResult = Result.success(unverifiedUser),
        )
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent {
      navController = rememberNavController()
      AppNavHost(
          authViewModel = viewModel,
          authRepository = repository,
          profileRepository = profileRepository,
          listingRepository = listingRepository,
          photoStorage = photoStorage,
          navController = navController,
      )
    }
    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.SCREEN).assertIsDisplayed()

    repository.refreshUserResult = Result.success(user)
    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.CHECK_BUTTON).performClick()

    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
    assertEquals(Routes.BROWSE, navController.currentBackStackEntry?.destination?.route)
    assertNull(navController.previousBackStackEntry)
    assertEquals(true, viewModel.uiState.value.user?.isEmailVerified)
  }

  @Test
  fun signOut_fromVerifyEmail_returnsToSignIn() {
    val viewModel =
        setContent(
            FakeAuthRepository(
                currentUser = unverifiedUser,
                refreshUserResult = Result.success(unverifiedUser),
            )
        )
    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.SCREEN).assertIsDisplayed()

    viewModel.signOut()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun useAnotherAccountLink_fromVerifyEmail_signsOutAndReturnsToSignIn() {
    val viewModel =
        setContent(
            FakeAuthRepository(
                currentUser = unverifiedUser,
                refreshUserResult = Result.success(unverifiedUser),
            )
        )

    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.USE_ANOTHER_ACCOUNT_LINK).performClick()

    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()
    assertNull(viewModel.uiState.value.user)
  }

  @Test
  fun backArrow_fromVerifyEmail_signsOutAndReturnsToSignIn() {
    val viewModel =
        setContent(
            FakeAuthRepository(
                currentUser = unverifiedUser,
                refreshUserResult = Result.success(unverifiedUser),
            )
        )

    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.BACK_BUTTON).performClick()

    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()
    assertNull(viewModel.uiState.value.user)
  }

  @Test
  fun bottomBar_isShownOnEveryTab() {
    setContent(FakeAuthRepository(currentUser = user))

    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(BottomBarTags.BAR).assertIsDisplayed()

    openCreateListing()
    composeTestRule.onNodeWithTag(BottomBarTags.BAR).assertIsDisplayed()

    openProfile()
    composeTestRule.onNodeWithTag(BottomBarTags.BAR).assertIsDisplayed()
  }

  @Test
  fun bottomBar_isHiddenOnLoading() {
    setContent(FakeAuthRepository(currentUserGate = CompletableDeferred()))

    composeTestRule.onNodeWithTag(AppNavHostTags.LOADING).assertIsDisplayed()
    assertBottomBarHidden()
  }

  @Test
  fun bottomBar_isHiddenOnAuthScreens() {
    setContent(FakeAuthRepository(currentUser = null))

    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()
    assertBottomBarHidden()

    composeTestRule.onNodeWithTag(SignInScreenTags.CREATE_ACCOUNT_LINK).performClick()
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.SCREEN).assertIsDisplayed()
    assertBottomBarHidden()

    composeTestRule.onNodeWithTag(CreateAccountScreenTags.BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SignInScreenTags.FORGOT_PASSWORD_LINK).performClick()
    composeTestRule.onNodeWithTag(ForgotPasswordScreenTags.SCREEN).assertIsDisplayed()
    assertBottomBarHidden()
  }

  @Test
  fun bottomBar_isHiddenOnVerifyEmail() {
    setContent(
        FakeAuthRepository(
            currentUser = unverifiedUser,
            refreshUserResult = Result.success(unverifiedUser),
        )
    )

    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.SCREEN).assertIsDisplayed()
    assertBottomBarHidden()
  }

  @Test
  fun bottomBar_isHiddenOnEditProfileAndSettings() {
    setContent(FakeAuthRepository(currentUser = user))
    openProfile()

    composeTestRule.onNodeWithTag(ProfileScreenTags.EDIT_PROFILE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(EditProfileScreenTags.SCREEN).assertIsDisplayed()
    assertBottomBarHidden()

    composeTestRule.onNodeWithTag(EditProfileScreenTags.CANCEL_BUTTON).performClick()
    composeTestRule.onNodeWithTag(ProfileScreenTags.SETTINGS_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTags.SCREEN).assertIsDisplayed()
    assertBottomBarHidden()
  }

  @Test
  fun tabContent_endsAboveTheBottomBar() {
    setContent(FakeAuthRepository(currentUser = user))

    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
    assertEndsAboveBottomBar(BrowseScreenTags.SCREEN)

    openCreateListing()
    assertEndsAboveBottomBar(CreateListingScreenTags.SCREEN)

    openProfile()
    assertEndsAboveBottomBar(ProfileScreenTags.SCREEN)
  }

  @Test
  fun selectedTab_reportsSelectedSemantics() {
    val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    setContent(FakeAuthRepository(currentUser = user))

    composeTestRule.onNodeWithTag(BottomBarTags.BROWSE_TAB).assertIsSelected().assert(isTab)
    composeTestRule
        .onNodeWithTag(BottomBarTags.CREATE_LISTING_TAB)
        .assertIsNotSelected()
        .assert(isTab)
    composeTestRule.onNodeWithTag(BottomBarTags.PROFILE_TAB).assertIsNotSelected().assert(isTab)

    openCreateListing()

    composeTestRule.onNodeWithTag(BottomBarTags.BROWSE_TAB).assertIsNotSelected()
    composeTestRule.onNodeWithTag(BottomBarTags.CREATE_LISTING_TAB).assertIsSelected()

    openProfile()

    composeTestRule.onNodeWithTag(BottomBarTags.CREATE_LISTING_TAB).assertIsNotSelected()
    composeTestRule.onNodeWithTag(BottomBarTags.PROFILE_TAB).assertIsSelected()
  }

  @Test
  fun repeatedTabSwitches_doNotGrowTheBackStack() {
    setContent(FakeAuthRepository(currentUser = user))
    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()

    repeat(3) {
      openCreateListing()
      openProfile()
      openBrowse()
    }

    assertEquals(Routes.BROWSE, navController.currentBackStackEntry?.destination?.route)
    assertNull(navController.previousBackStackEntry)
  }

  @Test
  fun tappingTheCurrentTab_doesNothing() {
    setContent(FakeAuthRepository(currentUser = user))
    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()

    val browseEntry = navController.currentBackStackEntry
    composeTestRule.onNodeWithTag(BottomBarTags.BROWSE_TAB).performClick()
    composeTestRule.waitForIdle()
    assertSame(browseEntry, navController.currentBackStackEntry)
    assertNull(navController.previousBackStackEntry)

    openProfile()
    val profileEntry = navController.currentBackStackEntry
    composeTestRule.onNodeWithTag(BottomBarTags.PROFILE_TAB).performClick()
    composeTestRule.waitForIdle()
    assertSame(profileEntry, navController.currentBackStackEntry)
    assertSame(browseEntry, navController.previousBackStackEntry)
  }

  // Profile only scrolls as a page on a window too short to fit it, like this one.
  @Config(qualifiers = "h400dp")
  @Test
  fun switchingAwayFromProfileAndBack_restoresItsEntryAndScrollPosition() {
    setContent(FakeAuthRepository(currentUser = user))
    openProfile()
    val entryId = navController.currentBackStackEntry?.id
    val scrollable = composeTestRule.onNode(hasScrollAction())
    scrollable.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 40f) }
    composeTestRule.waitForIdle()
    val scrolled = profileScrollPosition()
    assertTrue(scrolled > 0f)

    openBrowse()
    openProfile()

    assertEquals(entryId, navController.currentBackStackEntry?.id)
    assertEquals(scrolled, profileScrollPosition(), 0f)
  }

  @Test
  fun createListingForm_survivesSwitchingTabs() {
    setContent(FakeAuthRepository(currentUser = user))
    openCreateListing()
    composeTestRule.onNodeWithTag(CreateListingScreenTags.TITLE_FIELD).performTextInput("Drill")

    openProfile()
    openCreateListing()

    composeTestRule.onNodeWithTag(CreateListingScreenTags.TITLE_FIELD).assertTextEquals("Drill")
  }

  @Test
  fun backFromCreateListing_returnsToBrowseAndKeepsTheForm() {
    setContent(FakeAuthRepository(currentUser = user))
    openCreateListing()
    composeTestRule.onNodeWithTag(CreateListingScreenTags.TITLE_FIELD).performTextInput("Drill")

    Espresso.pressBack()

    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
    assertNull(navController.previousBackStackEntry)
    openCreateListing()
    composeTestRule.onNodeWithTag(CreateListingScreenTags.TITLE_FIELD).assertTextEquals("Drill")
  }

  @Test
  fun publishing_opensBrowseShowingTheListingAndLeavesAnEmptyForm() {
    setContent(FakeAuthRepository(currentUser = user))
    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
    openCreateListing()

    publishListing("Bosch drill set")

    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithTag(BottomBarTags.BROWSE_TAB).assertIsSelected()
    assertNull(navController.previousBackStackEntry)
    assertEquals(2, listingRepository.getAllListingsCalls)

    openCreateListing()
    composeTestRule.onNodeWithTag(CreateListingScreenTags.CREATE_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithText("Bosch drill set").assertDoesNotExist()
  }

  @Test
  fun publishingFailure_staysOnTheFormWithTheError() {
    setContent(FakeAuthRepository(currentUser = user))
    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
    openCreateListing()
    listingRepository.failure = IOException("Network down")

    publishListing("Bosch drill set")

    composeTestRule
        .onNodeWithTag(CreateListingScreenTags.ERROR_MESSAGE)
        .assertTextEquals("Network down")
    assertEquals(Routes.CREATE_LISTING, navController.currentBackStackEntry?.destination?.route)
    composeTestRule
        .onNodeWithTag(CreateListingScreenTags.TITLE_FIELD)
        .assertTextEquals("Bosch drill set")
  }

  @Test
  fun publishingFinishedWhileOnAnotherTab_opensBrowseOnComingBack() {
    setContent(FakeAuthRepository(currentUser = user))
    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
    openCreateListing()
    val upload = CompletableDeferred<Unit>()
    listingRepository.gate = upload

    publishListing("Bosch drill set")
    openProfile()
    composeTestRule.runOnIdle { upload.complete(Unit) }
    composeTestRule.onNodeWithTag(BottomBarTags.CREATE_LISTING_TAB).performClick()

    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
    composeTestRule.onNodeWithTag(BottomBarTags.BROWSE_TAB).assertIsSelected()
    assertNull(navController.previousBackStackEntry)
  }

  @Test
  fun signOutOnCreateListing_returnsToSignIn() {
    val viewModel = setContent(FakeAuthRepository(currentUser = user))
    openCreateListing()

    viewModel.signOut()

    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()
    assertNull(navController.previousBackStackEntry)
  }

  @Test
  fun backFromProfile_returnsToBrowse() {
    setContent(FakeAuthRepository(currentUser = user))
    openProfile()

    Espresso.pressBack()

    composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(BottomBarTags.BROWSE_TAB).assertIsSelected()
    assertNull(navController.previousBackStackEntry)
  }

  @Test
  fun browseSearchText_survivesSwitchingToProfileAndBack() {
    setContent(FakeAuthRepository(currentUser = user))

    composeTestRule.onNodeWithTag(BrowseScreenTags.SEARCH_FIELD).performTextInput("drill")
    openProfile()
    openBrowse()

    composeTestRule.onNodeWithTag(BrowseScreenTags.SEARCH_FIELD).assertTextEquals("drill")
    assertEquals(1, listingRepository.getAllListingsCalls)
  }

  @Config(qualifiers = "w640dp-h360dp-land")
  @Test
  fun browseListing_isReachableOnAShortWindow() {
    listingRepository.allListings =
        listOf(
            ItemListing(ListingCategory.ITEM_TOOLS, 8.0, "Bosch drill set", "", listingId = "drill")
        )
    setContent(FakeAuthRepository(currentUser = user))

    composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Bosch drill set"))

    composeTestRule.onNodeWithText("Bosch drill set").assertIsDisplayed()
  }

  @Test
  fun restoredStateWithSession_keepsProfileOverBrowseAndTabsStillSwitch() {
    restoreSavedState(before = FakeAuthRepository(currentUser = user)) { openProfile() }
    composeTestRule.onNodeWithTag(ProfileScreenTags.SCREEN).assertIsDisplayed()

    openBrowse()
    openProfile()

    assertEquals(Routes.BROWSE, navController.previousBackStackEntry?.destination?.route)
    composeTestRule.onNodeWithTag(BottomBarTags.PROFILE_TAB).assertIsSelected()
  }

  @Test
  fun restoredStateOnCreateListing_keepsTheDraft() {
    restoreSavedState(before = FakeAuthRepository(currentUser = user)) {
      composeTestRule.onNodeWithTag(BrowseScreenTags.SCREEN).assertIsDisplayed()
      openCreateListing()
      composeTestRule.onNodeWithTag(CreateListingScreenTags.TITLE_FIELD).performTextInput("Drill")
    }

    composeTestRule.onNodeWithTag(CreateListingScreenTags.TITLE_FIELD).assertTextEquals("Drill")
    composeTestRule.onNodeWithTag(BottomBarTags.CREATE_LISTING_TAB).assertIsSelected()
  }

  @Test
  fun restoredStateWithoutSession_returnsToSignInAndClearsBackStack() {
    restoreSavedState(
        before = FakeAuthRepository(currentUser = user),
        after = FakeAuthRepository(currentUser = null),
    ) {
      openProfile()
      openBrowse()
    }

    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()
    assertBottomBarHidden()
    assertNull(navController.previousBackStackEntry)
  }

  private class FakeProfileRepository(initial: User) : ProfileRepository {
    val profile = MutableStateFlow<User?>(initial)
    var photo = ByteArray(0)
    var photoLoads = 0
      private set

    var savedBio: String? = null
      private set

    override fun observeProfile(uid: String): Flow<User?> = profile

    override suspend fun uploadProfilePhoto(uid: String, photoUri: Uri): String = "photo-path"

    override suspend fun loadProfilePhoto(photoPath: String): ByteArray {
      photoLoads++
      return photo
    }

    override suspend fun removeProfilePhoto(uid: String) = Unit

    override suspend fun updateProfile(uid: String, photoPath: String?, bio: String) {
      savedBio = bio
      profile.value = profile.value?.copy(bio = bio)
    }
  }

  private class FakeAuthRepository(
      private val currentUser: User? = null,
      private val currentUserGate: CompletableDeferred<User?>? = null,
      private val signInResult: Result<User> = Result.failure(IllegalStateException("Not set")),
      var refreshUserResult: Result<User> = Result.failure(IllegalStateException("Not set")),
      private val signOutError: Exception? = null,
      var currentUserError: Exception? = null,
  ) : AuthRepository {
    override suspend fun signIn(email: String, password: String): Result<User> = signInResult

    override suspend fun signUp(email: String, password: String, username: String): Result<User> =
        signInResult

    override suspend fun signOut() {
      signOutError?.let { throw it }
    }

    override suspend fun getCurrentUser(): User {
      currentUserError?.let { throw it }
      val result = if (currentUserGate != null) currentUserGate.await() else currentUser
      return result ?: throw NoSessionException()
    }

    override suspend fun sendVerificationEmail(): Result<Unit> = Result.success(Unit)

    override suspend fun refreshUser(): Result<User> = refreshUserResult

    val resetEmails = mutableListOf<String>()

    override suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
      resetEmails += email
      return Result.success(Unit)
    }
  }
}
