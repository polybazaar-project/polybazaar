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
import com.google.firebase.auth.UserProfileChangeRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Integration tests wiring [CreateAccountScreen], the real [AuthViewModel] and the real
 * [FirebaseAuthRepository] together. Only [FirebaseAuth] is mocked, so no request reaches Firebase.
 */
@RunWith(AndroidJUnit4::class)
class CreateAccountIntegrationTest {

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
    whenever(mockAuthResult.user).thenReturn(mockUser)
    viewModel = AuthViewModel(FirebaseAuthRepository(mockAuth))

    composeTestRule.setContent {
      CreateAccountScreen(
          authViewModel = viewModel,
          onBack = {},
          onSignedIn = { signedInCount++ },
      )
    }
  }

  private fun fillAndSubmit(username: String, email: String, password: String) {
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.USERNAME_FIELD).performTextInput(username)
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.EMAIL_FIELD).performTextInput(email)
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.PASSWORD_FIELD).performTextInput(password)
    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.CONFIRM_PASSWORD_FIELD)
        .performTextInput(password)
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun successfulSignUp_createsFirebaseUserSetsDisplayNameAndCallsOnSignedIn() {
    whenever(mockAuth.createUserWithEmailAndPassword("ada@epfl.ch", "secret123"))
        .thenReturn(Tasks.forResult(mockAuthResult))
    whenever(mockUser.updateProfile(any())).thenReturn(Tasks.forResult(null))

    fillAndSubmit(" ada_lovelace ", " ada@epfl.ch ", "secret123")

    verify(mockAuth).createUserWithEmailAndPassword("ada@epfl.ch", "secret123")
    val profileCaptor = argumentCaptor<UserProfileChangeRequest>()
    verify(mockUser).updateProfile(profileCaptor.capture())
    assertEquals("ada_lovelace", profileCaptor.firstValue.displayName)
    assertEquals(
        User(uid = "uid-ada", email = "ada@epfl.ch", username = "ada_lovelace"),
        viewModel.uiState.value.user,
    )
    assertEquals(1, signedInCount)
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun firebaseRejection_showsItsMessageAndDoesNotSignIn() {
    whenever(mockAuth.createUserWithEmailAndPassword("ada@epfl.ch", "secret123"))
        .thenReturn(
            Tasks.forException(Exception("The email address is already in use by another account."))
        )

    fillAndSubmit("ada_lovelace", "ada@epfl.ch", "secret123")

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.ERROR_MESSAGE)
        .assertTextEquals("The email address is already in use by another account.")
    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsEnabled()
    verify(mockUser, never()).updateProfile(any())
    assertEquals(0, signedInCount)
  }

  @Test
  fun profileUpdateFailure_surfacesErrorAndDoesNotSignIn() {
    whenever(mockAuth.createUserWithEmailAndPassword("ada@epfl.ch", "secret123"))
        .thenReturn(Tasks.forResult(mockAuthResult))
    whenever(mockUser.updateProfile(any()))
        .thenReturn(Tasks.forException(Exception("Profile update failed")))

    fillAndSubmit("ada_lovelace", "ada@epfl.ch", "secret123")

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.ERROR_MESSAGE)
        .assertTextEquals("Profile update failed")
    assertEquals(0, signedInCount)
  }

  @Test
  fun firebaseReturningNoUser_showsSignUpFailedMessage() {
    whenever(mockAuthResult.user).thenReturn(null)
    whenever(mockAuth.createUserWithEmailAndPassword("ada@epfl.ch", "secret123"))
        .thenReturn(Tasks.forResult(mockAuthResult))

    fillAndSubmit("ada_lovelace", "ada@epfl.ch", "secret123")

    composeTestRule
        .onNodeWithTag(CreateAccountScreenTags.ERROR_MESSAGE)
        .assertTextEquals("Sign up failed")
    assertEquals(0, signedInCount)
  }

  @Test
  fun pendingFirebaseRequest_disablesCreateButtonUntilItCompletes() {
    val pending = TaskCompletionSource<AuthResult>()
    whenever(mockAuth.createUserWithEmailAndPassword("ada@epfl.ch", "secret123"))
        .thenReturn(pending.task)
    whenever(mockUser.updateProfile(any())).thenReturn(Tasks.forResult(null))

    fillAndSubmit("ada_lovelace", "ada@epfl.ch", "secret123")

    composeTestRule.onNodeWithTag(CreateAccountScreenTags.CREATE_BUTTON).assertIsNotEnabled()
    assertEquals(0, signedInCount)

    pending.setResult(mockAuthResult)
    composeTestRule.waitForIdle()

    assertEquals(1, signedInCount)
    assertFalse(viewModel.uiState.value.isLoading)
  }
}
