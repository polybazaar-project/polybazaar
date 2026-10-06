// Portions of this code were generated with the help of Claude Code.
package com.android.polybazaar.auth.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.viewmodel.AuthViewModel
import com.android.polybazaar.screen.CreateAccountPage
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import io.github.kakaocup.compose.node.element.ComposeScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Functional tests: a user goes through the whole sign-up journey on a device. The backend is
 * replaced by an in-memory [AuthRepository] so the test never reaches Firebase.
 */
@RunWith(AndroidJUnit4::class)
class CreateAccountFunctionalTest : TestCase() {

  @get:Rule val composeTestRule = createComposeRule()

  private val repository = InMemoryAuthRepository(takenEmails = setOf("taken@epfl.ch"))
  private var signedInCount = 0
  private var backCount = 0

  private fun launchScreen() {
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent {
      CreateAccountScreen(
          authViewModel = viewModel,
          onBack = { backCount++ },
          onSignedIn = { signedInCount++ },
      )
    }
  }

  @Test
  fun userFixesMistakesAndCreatesAccount() = run {
    launchScreen()

    step("Fill the form with a mismatched confirmation") {
      ComposeScreen.onComposeScreen<CreateAccountPage>(composeTestRule) {
        createButton { assertIsNotEnabled() }
        usernameField { performTextInput("ada_lovelace") }
        emailField { performTextInput("taken@epfl.ch") }
        passwordField { performTextInput("secret123") }
        confirmPasswordField { performTextInput("secret12") }
        mismatchMessage {
          assertIsDisplayed()
          assertTextEquals("Passwords do not match")
        }
        createButton { assertIsNotEnabled() }
      }
    }

    step("Fix the confirmation and submit with an email already in use") {
      ComposeScreen.onComposeScreen<CreateAccountPage>(composeTestRule) {
        confirmPasswordField { performTextInput("3") }
        mismatchMessage { assertDoesNotExist() }
        createButton {
          assertIsEnabled()
          performClick()
        }
        errorMessage {
          assertIsDisplayed()
          assertTextEquals("The email address is already in use by another account.")
        }
      }
      assertEquals(0, signedInCount)
    }

    step("Use another email and create the account") {
      ComposeScreen.onComposeScreen<CreateAccountPage>(composeTestRule) {
        emailField {
          performTextClearance()
          performTextInput("ada@epfl.ch")
        }
        createButton { performClick() }
      }
      composeTestRule.waitForIdle()
      assertEquals(
          listOf("taken@epfl.ch", "ada@epfl.ch"),
          repository.signUpAttempts.map { it.email },
      )
      assertEquals(
          User(uid = "uid-ada@epfl.ch", email = "ada@epfl.ch", username = "ada_lovelace"),
          repository.createdUsers.single(),
      )
      assertEquals(1, signedInCount)
    }
  }

  @Test
  fun userCancelsWithoutCreatingAnAccount() = run {
    launchScreen()

    step("Start filling the form, then leave with Cancel and the back arrow") {
      ComposeScreen.onComposeScreen<CreateAccountPage>(composeTestRule) {
        usernameField { performTextInput("ada_lovelace") }
        cancelButton { performClick() }
        backButton { performClick() }
      }
    }

    step("No account was created") {
      assertEquals(2, backCount)
      assertEquals(emptyList<SignUpAttempt>(), repository.signUpAttempts)
      assertEquals(0, signedInCount)
    }
  }

  private data class SignUpAttempt(val email: String, val password: String, val username: String)

  private class InMemoryAuthRepository(private val takenEmails: Set<String>) : AuthRepository {
    val signUpAttempts = mutableListOf<SignUpAttempt>()
    val createdUsers = mutableListOf<User>()

    override suspend fun signIn(email: String, password: String): Result<User> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun signUp(email: String, password: String, username: String): Result<User> {
      signUpAttempts += SignUpAttempt(email, password, username)
      if (email in takenEmails) {
        return Result.failure(
            IllegalArgumentException("The email address is already in use by another account.")
        )
      }
      val user = User(uid = "uid-$email", email = email, username = username)
      createdUsers += user
      return Result.success(user)
    }

    override suspend fun signOut() {}

    override fun getCurrentUser(): User = throw IllegalStateException("Not used")
  }
}
