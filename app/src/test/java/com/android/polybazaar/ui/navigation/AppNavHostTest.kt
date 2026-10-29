// Co-authored-by: Claude Code (Claude Opus 5.5)
// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.ui.navigation

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.ui.CreateAccountScreenTags
import com.android.polybazaar.auth.ui.ForgotPasswordScreenTags
import com.android.polybazaar.auth.ui.SignInScreenTags
import com.android.polybazaar.auth.ui.VerifyEmailScreenTags
import com.android.polybazaar.auth.viewmodel.AuthViewModel
import com.android.polybazaar.profile.model.ProfileRepository
import com.android.polybazaar.profile.ui.EditProfileScreenTags
import com.android.polybazaar.profile.ui.ProfileScreenTags
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

  private fun setContent(repository: FakeAuthRepository): AuthViewModel {
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent {
      AppNavHost(
          authViewModel = viewModel,
          authRepository = repository,
          profileRepository = profileRepository,
      )
    }
    return viewModel
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
  fun persistedUser_startsOnProfile() {
    setContent(FakeAuthRepository(currentUser = user))

    composeTestRule.onNodeWithTag(ProfileScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ProfileScreenTags.NAME).assertTextEquals("ada_lovelace")
  }

  @Test
  fun noPersistedUser_startsOnSignIn() {
    setContent(FakeAuthRepository(currentUser = null))

    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun signIn_navigatesToProfile() {
    val repository = FakeAuthRepository(currentUser = null, signInResult = Result.success(user))
    setContent(repository)

    composeTestRule.onNodeWithTag(SignInScreenTags.EMAIL_FIELD).performTextInput("ada@epfl.ch")
    composeTestRule.onNodeWithTag(SignInScreenTags.PASSWORD_FIELD).performTextInput("secret1")
    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).performClick()

    composeTestRule.onNodeWithTag(ProfileScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ProfileScreenTags.NAME).assertTextEquals("ada_lovelace")
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
    lateinit var navController: NavHostController
    val repository = FakeAuthRepository(currentUser = null, signInResult = Result.success(user))
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent {
      navController = rememberNavController()
      AppNavHost(
          authViewModel = viewModel,
          authRepository = repository,
          profileRepository = profileRepository,
          navController = navController,
      )
    }

    composeTestRule.onNodeWithTag(SignInScreenTags.CREATE_ACCOUNT_LINK).performClick()
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.SCREEN).assertIsDisplayed()
    viewModel.signIn("ada@epfl.ch", "secret1")
    composeTestRule.waitForIdle()

    assertEquals(Routes.PROFILE, navController.currentBackStackEntry?.destination?.route)
    assertNull(navController.previousBackStackEntry)
  }

  @Test
  fun editProfileButton_opensEditProfileAndCancelReturnsToProfile() {
    setContent(FakeAuthRepository(currentUser = user))

    composeTestRule.onNodeWithTag(ProfileScreenTags.EDIT_PROFILE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(EditProfileScreenTags.SCREEN).assertIsDisplayed()

    composeTestRule.onNodeWithTag(EditProfileScreenTags.CANCEL_BUTTON).performClick()
    composeTestRule.onNodeWithTag(ProfileScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun savingProfile_returnsToProfileWithUpdatedBio() {
    setContent(FakeAuthRepository(currentUser = user))

    composeTestRule.onNodeWithTag(ProfileScreenTags.EDIT_PROFILE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(EditProfileScreenTags.BIO_FIELD).performTextInput("Hello")
    composeTestRule.onNodeWithTag(EditProfileScreenTags.SAVE_BUTTON).performClick()

    composeTestRule.onNodeWithTag(ProfileScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ProfileScreenTags.BIO).assertTextEquals("Hello")
    assertEquals("Hello", profileRepository.savedBio)
  }

  @Test
  fun settingsButton_showsUnavailableMessageAndStaysOnProfile() {
    setContent(FakeAuthRepository(currentUser = user))

    composeTestRule.onNodeWithTag(ProfileScreenTags.SETTINGS_BUTTON).performClick()

    composeTestRule.onNodeWithText(SETTINGS_UNAVAILABLE_MESSAGE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ProfileScreenTags.SCREEN).assertIsDisplayed()
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
    lateinit var navController: NavHostController
    val repository = FakeAuthRepository(currentUser = user)
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent {
      navController = rememberNavController()
      AppNavHost(
          authViewModel = viewModel,
          authRepository = repository,
          profileRepository = profileRepository,
          navController = navController,
      )
    }
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
  fun emailVerifiedOutsideTheApp_opensProfileWithoutSigningInAgain() {
    // The cached session still says unverified, but the server already knows about the link.
    setContent(
        FakeAuthRepository(currentUser = unverifiedUser, refreshUserResult = Result.success(user))
    )

    composeTestRule.onNodeWithTag(ProfileScreenTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun checkButton_onceVerified_opensProfileAndClearsBackStack() {
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
          navController = navController,
      )
    }
    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.SCREEN).assertIsDisplayed()

    repository.refreshUserResult = Result.success(user)
    composeTestRule.onNodeWithTag(VerifyEmailScreenTags.CHECK_BUTTON).performClick()

    composeTestRule.onNodeWithTag(ProfileScreenTags.SCREEN).assertIsDisplayed()
    assertEquals(Routes.PROFILE, navController.currentBackStackEntry?.destination?.route)
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

  private class FakeProfileRepository(initial: User) : ProfileRepository {
    private val profile = MutableStateFlow<User?>(initial)
    var savedBio: String? = null
      private set

    override fun observeProfile(uid: String): Flow<User?> = profile

    override suspend fun uploadProfilePhoto(uid: String, photoUri: Uri): String = "photo-url"

    override suspend fun removeProfilePhoto(uid: String) = Unit

    override suspend fun updateProfile(uid: String, photoUrl: String?, bio: String) {
      savedBio = bio
      profile.value = profile.value?.copy(bio = bio)
    }
  }

  private class FakeAuthRepository(
      private val currentUser: User? = null,
      private val currentUserGate: CompletableDeferred<User?>? = null,
      private val signInResult: Result<User> = Result.failure(IllegalStateException("Not set")),
      var refreshUserResult: Result<User> = Result.failure(IllegalStateException("Not set")),
  ) : AuthRepository {
    override suspend fun signIn(email: String, password: String): Result<User> = signInResult

    override suspend fun signUp(email: String, password: String, username: String): Result<User> =
        signInResult

    override suspend fun signOut() = Unit

    override suspend fun getCurrentUser(): User {
      val result = if (currentUserGate != null) currentUserGate.await() else currentUser
      return result ?: throw IllegalStateException("No user is currently logged in")
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
