// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.ui.CreateAccountScreenTags
import com.android.polybazaar.auth.ui.SignInScreenTags
import com.android.polybazaar.auth.viewmodel.AuthViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavHostTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val user = User(uid = "uid-ada", email = "ada@epfl.ch", username = "ada_lovelace")

  private fun setContent(repository: FakeAuthRepository): AuthViewModel {
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent { AppNavHost(authViewModel = viewModel) }
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

    composeTestRule.onNodeWithTag(ProfilePlaceholderTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ProfilePlaceholderTags.USERNAME).assertTextEquals("ada_lovelace")
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

    composeTestRule.onNodeWithTag(ProfilePlaceholderTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ProfilePlaceholderTags.USERNAME).assertTextEquals("ada_lovelace")
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
    val viewModel =
        AuthViewModel(FakeAuthRepository(currentUser = null, signInResult = Result.success(user)))
    composeTestRule.setContent {
      navController = rememberNavController()
      AppNavHost(authViewModel = viewModel, navController = navController)
    }

    composeTestRule.onNodeWithTag(SignInScreenTags.CREATE_ACCOUNT_LINK).performClick()
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.SCREEN).assertIsDisplayed()
    viewModel.signIn("ada@epfl.ch", "secret1")
    composeTestRule.waitForIdle()

    assertEquals(Routes.PROFILE, navController.currentBackStackEntry?.destination?.route)
    assertNull(navController.previousBackStackEntry)
  }

  private class FakeAuthRepository(
      private val currentUser: User? = null,
      private val currentUserGate: CompletableDeferred<User?>? = null,
      private val signInResult: Result<User> = Result.failure(IllegalStateException("Not set")),
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

    override suspend fun refreshUser(): Result<User> = signInResult
  }
}
