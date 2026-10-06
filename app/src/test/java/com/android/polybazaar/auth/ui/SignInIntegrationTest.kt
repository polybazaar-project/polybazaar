// Portions of this code were generated with the help of Claude Code.
package com.android.polybazaar.auth.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.auth.model.FirebaseAuthRepository
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.viewmodel.AuthViewModel
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Integration tests wiring [SignInScreen], the real [AuthViewModel] and the real
 * [FirebaseAuthRepository] together. Only [FirebaseAuth] is mocked, so no request reaches Firebase.
 */
@RunWith(AndroidJUnit4::class)
class SignInIntegrationTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var mockAuth: FirebaseAuth
  private lateinit var mockAuthResult: AuthResult
  private lateinit var mockUser: FirebaseUser
  private lateinit var viewModel: AuthViewModel
  private var signedInCount = 0

  @Before
  fun setUp() {
    mockAuth = mock()
    mockAuthResult = mock()
    mockUser = mock()
    whenever(mockUser.uid).thenReturn("uid-ada")
    whenever(mockUser.email).thenReturn("ada@epfl.ch")
    whenever(mockUser.displayName).thenReturn("ada_lovelace")
    whenever(mockAuthResult.user).thenReturn(mockUser)
    viewModel = AuthViewModel(FirebaseAuthRepository(mockAuth))

    composeTestRule.setContent {
      SignInScreen(
          authViewModel = viewModel,
          onCreateAccount = {},
          onForgotPassword = {},
          onSignedIn = { signedInCount++ },
      )
    }
  }

  private fun fillAndSubmit(email: String, password: String) {
    composeTestRule.onNodeWithTag(SignInScreenTags.EMAIL_FIELD).performTextInput(email)
    composeTestRule.onNodeWithTag(SignInScreenTags.PASSWORD_FIELD).performTextInput(password)
    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun successfulSignIn_sendsTrimmedEmailToFirebaseAndCallsOnSignedIn() {
    whenever(mockAuth.signInWithEmailAndPassword("ada@epfl.ch", "secret123"))
        .thenReturn(Tasks.forResult(mockAuthResult))

    fillAndSubmit("  ada@epfl.ch ", "secret123")

    verify(mockAuth).signInWithEmailAndPassword("ada@epfl.ch", "secret123")
    assertEquals(
        User(uid = "uid-ada", email = "ada@epfl.ch", username = "ada_lovelace"),
        viewModel.uiState.value.user,
    )
    assertEquals(1, signedInCount)
    composeTestRule.onNodeWithTag(SignInScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun firebaseRejection_showsItsMessageAndDoesNotSignIn() {
    whenever(mockAuth.signInWithEmailAndPassword("ada@epfl.ch", "wrong"))
        .thenReturn(Tasks.forException(Exception("The supplied auth credential is incorrect.")))

    fillAndSubmit("ada@epfl.ch", "wrong")

    composeTestRule
        .onNodeWithTag(SignInScreenTags.ERROR_MESSAGE)
        .assertTextEquals("The supplied auth credential is incorrect.")
    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).assertIsEnabled()
    assertNull(viewModel.uiState.value.user)
    assertEquals(0, signedInCount)
  }

  @Test
  fun retryAfterFailure_clearsErrorAndSignsIn() {
    whenever(mockAuth.signInWithEmailAndPassword("ada@epfl.ch", "wrong"))
        .thenReturn(Tasks.forException(Exception("Wrong password")))
    whenever(mockAuth.signInWithEmailAndPassword("ada@epfl.ch", "wrong1"))
        .thenReturn(Tasks.forResult(mockAuthResult))
    fillAndSubmit("ada@epfl.ch", "wrong")
    composeTestRule.onNodeWithTag(SignInScreenTags.ERROR_MESSAGE).assertTextEquals("Wrong password")

    composeTestRule.onNodeWithTag(SignInScreenTags.PASSWORD_FIELD).performTextInput("1")
    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(SignInScreenTags.ERROR_MESSAGE).assertDoesNotExist()
    assertEquals(1, signedInCount)
  }

  @Test
  fun firebaseReturningNoUser_showsSignInFailedMessage() {
    whenever(mockAuthResult.user).thenReturn(null)
    whenever(mockAuth.signInWithEmailAndPassword("ada@epfl.ch", "secret123"))
        .thenReturn(Tasks.forResult(mockAuthResult))

    fillAndSubmit("ada@epfl.ch", "secret123")

    composeTestRule.onNodeWithTag(SignInScreenTags.ERROR_MESSAGE).assertTextEquals("Sign in failed")
    assertEquals(0, signedInCount)
  }

  @Test
  fun userWithoutDisplayName_cannotSignIn() {
    whenever(mockUser.displayName).thenReturn(null)
    whenever(mockAuth.signInWithEmailAndPassword("ada@epfl.ch", "secret123"))
        .thenReturn(Tasks.forResult(mockAuthResult))

    fillAndSubmit("ada@epfl.ch", "secret123")

    composeTestRule
        .onNodeWithTag(SignInScreenTags.ERROR_MESSAGE)
        .assertTextEquals("User username cannot be null")
    assertEquals(0, signedInCount)
  }

  @Test
  fun pendingFirebaseRequest_disablesSignInButtonUntilItCompletes() {
    val pending = TaskCompletionSource<AuthResult>()
    whenever(mockAuth.signInWithEmailAndPassword("ada@epfl.ch", "secret123"))
        .thenReturn(pending.task)

    fillAndSubmit("ada@epfl.ch", "secret123")

    composeTestRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).assertIsNotEnabled()
    assertEquals(0, signedInCount)

    pending.setResult(mockAuthResult)
    composeTestRule.waitForIdle()

    assertEquals(1, signedInCount)
    assertFalse(viewModel.uiState.value.isLoading)
  }
}
