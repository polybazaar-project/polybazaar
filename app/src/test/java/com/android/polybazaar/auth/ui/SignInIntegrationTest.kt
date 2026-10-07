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
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
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
 * [FirebaseAuthRepository] together. Only [FirebaseAuth] and [FirebaseFirestore] are mocked, so no
 * request reaches Firebase.
 */
@RunWith(AndroidJUnit4::class)
class SignInIntegrationTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var mockAuth: FirebaseAuth
  private lateinit var mockAuthResult: AuthResult
  private lateinit var mockUser: FirebaseUser
  private lateinit var mockUsernameLookup: QuerySnapshot
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

    val mockFirestore = mock<FirebaseFirestore>()
    val mockUsernames = mock<CollectionReference>()
    val mockQuery = mock<Query>()
    val usernameDoc = mock<DocumentSnapshot>()
    mockUsernameLookup = mock()
    whenever(mockFirestore.collection("usernames")).thenReturn(mockUsernames)
    whenever(mockUsernames.whereEqualTo("uid", "uid-ada")).thenReturn(mockQuery)
    whenever(mockQuery.limit(1)).thenReturn(mockQuery)
    whenever(mockQuery.get()).thenReturn(Tasks.forResult(mockUsernameLookup))
    whenever(mockUsernameLookup.isEmpty).thenReturn(false)
    whenever(mockUsernameLookup.documents).thenReturn(listOf(usernameDoc))
    whenever(usernameDoc.getString("username")).thenReturn("ada_lovelace")

    viewModel = AuthViewModel(FirebaseAuthRepository(mockAuth, mockFirestore))

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
  fun userWithoutUsernameDocument_cannotSignIn() {
    whenever(mockUsernameLookup.isEmpty).thenReturn(true)
    whenever(mockAuth.signInWithEmailAndPassword("ada@epfl.ch", "secret123"))
        .thenReturn(Tasks.forResult(mockAuthResult))

    fillAndSubmit("ada@epfl.ch", "secret123")

    composeTestRule
        .onNodeWithTag(SignInScreenTags.ERROR_MESSAGE)
        .assertTextEquals("Username not found for the current user")
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
