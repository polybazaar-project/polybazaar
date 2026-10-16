// Co-authored-by: Claude Code (Claude Opus 5.5)
// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.ui.navigation

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.ui.CreateAccountScreenTags
import com.android.polybazaar.auth.ui.SignInScreenTags
import com.android.polybazaar.auth.viewmodel.AuthViewModel
import com.android.polybazaar.profile.model.ProfileRepository
import com.android.polybazaar.profile.ui.EditProfileScreenTags
import com.android.polybazaar.profile.ui.ProfileScreenTags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end tests of what a user can do in the app today: create an account, edit the profile,
 * sign out and sign back in. The real navigation graph, screens and [AuthViewModel] run together;
 * only the data layer is replaced by [InMemoryBackend], so state written by one screen is read back
 * by the others as it would be from Firebase.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndFlowTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val backend = InMemoryBackend()
  private lateinit var authViewModel: AuthViewModel

  private fun launchApp() {
    authViewModel = AuthViewModel(backend)
    composeTestRule.setContent {
      AppNavHost(
          authViewModel = authViewModel,
          authRepository = backend,
          profileRepository = backend,
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

  @Test
  fun newUser_createsAccountEditsProfileSignsOutAndSignsBackIn() {
    launchApp()
    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()

    createAccount("ada_lovelace", "ada@epfl.ch", "secret123")

    composeTestRule.onNodeWithTag(ProfileScreenTags.SCREEN).assertIsDisplayed()
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

    composeTestRule.onNodeWithTag(ProfileScreenTags.SCREEN).assertIsDisplayed()
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
        .assertTextEquals("Username is already taken")
  }

  @Test
  fun wrongPassword_keepsUserOnSignInWithError() {
    backend.register(email = "ada@epfl.ch", password = "secret123", username = "ada_lovelace")
    launchApp()

    signIn("ada@epfl.ch", "not-the-password")

    composeTestRule.onNodeWithTag(SignInScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTags.ERROR_MESSAGE).assertTextEquals("Wrong password")
  }

  @Test
  fun persistedSession_opensProfileWithSavedBio() {
    val user = backend.register(email = "ada@epfl.ch", password = "secret123", username = "ada")
    backend.setSignedIn(user.uid)
    backend.setBio(user.uid, "Back again")
    launchApp()

    composeTestRule.onNodeWithTag(ProfileScreenTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ProfileScreenTags.BIO).assertTextEquals("Back again")
  }

  private class InMemoryBackend : AuthRepository, ProfileRepository {
    private class Account(val password: String, val user: User)

    private val accounts = mutableMapOf<String, Account>()
    private var signedInUid: String? = null
    val profiles = MutableStateFlow<Map<String, User>>(emptyMap())

    fun register(email: String, password: String, username: String): User {
      val user = User(uid = "uid-$username", email = email, username = username)
      accounts[email] = Account(password, user)
      profiles.value = profiles.value + (user.uid to user)
      return user
    }

    fun setBio(uid: String, bio: String) {
      profiles.value = profiles.value + (uid to profiles.value.getValue(uid).copy(bio = bio))
    }

    fun setSignedIn(uid: String?) {
      signedInUid = uid
    }

    override suspend fun signIn(email: String, password: String): Result<User> {
      val account = accounts[email]
      if (account == null || account.password != password) {
        return Result.failure(IllegalArgumentException("Wrong password"))
      }
      signedInUid = account.user.uid
      return Result.success(account.user)
    }

    override suspend fun signUp(email: String, password: String, username: String): Result<User> {
      if (accounts.values.any { it.user.username == username }) {
        return Result.failure(IllegalStateException("Username is already taken"))
      }
      val user = register(email, password, username)
      signedInUid = user.uid
      return Result.success(user)
    }

    override suspend fun signOut() {
      signedInUid = null
    }

    override suspend fun getCurrentUser(): User {
      val uid = signedInUid ?: throw IllegalStateException("No user is currently logged in")
      return profiles.value.getValue(uid)
    }

    override suspend fun sendVerificationEmail(): Result<Unit> = Result.success(Unit)

    override suspend fun refreshUser(): Result<User> = Result.success(getCurrentUser())

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        Result.failure(IllegalStateException("Not used"))

    override fun observeProfile(uid: String): Flow<User?> = profiles.map { it[uid] }

    override suspend fun uploadProfilePhoto(uid: String, photoUri: Uri): String = "photo-url"

    override suspend fun removeProfilePhoto(uid: String) = Unit

    override suspend fun updateProfile(uid: String, photoUrl: String?, bio: String) {
      val current = profiles.value.getValue(uid)
      profiles.value = profiles.value + (uid to current.copy(bio = bio))
    }
  }
}
