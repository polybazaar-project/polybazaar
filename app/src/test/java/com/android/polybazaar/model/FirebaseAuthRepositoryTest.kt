package com.android.polybazaar.model

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FirebaseAuthRepositoryTest {

  private lateinit var mockAuth: FirebaseAuth
  private lateinit var mockAuthResult: AuthResult
  private lateinit var authRepository: FirebaseAuthRepository

  private val TEST_EMAIL = "test@example.com"
  private val TEST_PASSWORD = "password123"
  private val TEST_USERNAME = "TestUser"
  private val TEST_UID = "uid123"

  @Before
  fun setUp() {
    mockAuth = mock()
    mockAuthResult = mock()
    authRepository = FirebaseAuthRepository(mockAuth)
  }

  private fun simulateFirebaseAuthSuccess(
      email: String? = TEST_EMAIL,
      username: String? = TEST_USERNAME,
      isSignUp: Boolean = false,
  ): FirebaseUser {
    val mockUser: FirebaseUser = mock()
    whenever(mockUser.uid).thenReturn(TEST_UID)
    whenever(mockUser.email).thenReturn(email)
    whenever(mockUser.displayName).thenReturn(username)
    whenever(mockAuthResult.user).thenReturn(mockUser)

    if (isSignUp) {
      whenever(
              mockAuth.createUserWithEmailAndPassword(
                  TEST_EMAIL,
                  TEST_PASSWORD,
              )
          )
          .thenReturn(Tasks.forResult(mockAuthResult))
      whenever(mockUser.updateProfile(any())).thenReturn(Tasks.forResult(null))
    } else {
      whenever(mockAuth.signInWithEmailAndPassword(TEST_EMAIL, TEST_PASSWORD))
          .thenReturn(Tasks.forResult(mockAuthResult))
    }
    return mockUser
  }

  private fun simulateFirebaseAuthError(exception: Exception, isSignUp: Boolean = false) {
    if (isSignUp) {
      whenever(mockAuth.createUserWithEmailAndPassword(any(), any()))
          .thenReturn(Tasks.forException(exception))
    } else {
      whenever(mockAuth.signInWithEmailAndPassword(any(), any()))
          .thenReturn(Tasks.forException(exception))
    }
  }

  @Test
  fun signIn_successful() = runTest {
    simulateFirebaseAuthSuccess()

    val result = authRepository.signIn(TEST_EMAIL, TEST_PASSWORD)

    val user = result.getOrThrow()
    assertEquals(TEST_UID, user.uid)
    assertEquals(TEST_EMAIL, user.email)
    assertEquals(TEST_USERNAME, user.username)
  }

  @Test
  fun signIn_failure_invalidCredentials() = runTest {
    val exception = Exception("Invalid credentials")
    simulateFirebaseAuthError(exception)

    val result = authRepository.signIn(TEST_EMAIL, "wrong")

    assertEquals(exception, result.exceptionOrNull())
  }

  @Test
  fun signIn_failure_nullEmailFromFirebase() = runTest {
    simulateFirebaseAuthSuccess(email = null)

    val result = authRepository.signIn(TEST_EMAIL, TEST_PASSWORD)

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is Exception)
  }

  @Test
  fun signIn_failure_nullUsernameFromFirebase() = runTest {
    simulateFirebaseAuthSuccess(username = null)

    val result = authRepository.signIn(TEST_EMAIL, TEST_PASSWORD)

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is Exception)
  }

  @Test
  fun signUp_successful() = runTest {
    val mockUser = simulateFirebaseAuthSuccess(isSignUp = true)

    val result = authRepository.signUp(TEST_EMAIL, TEST_PASSWORD, TEST_USERNAME)

    val user = result.getOrThrow()
    assertEquals(TEST_UID, user.uid)
    assertEquals(TEST_EMAIL, user.email)
    assertEquals(TEST_USERNAME, user.username)

    verify(mockUser).updateProfile(any())
  }

  @Test
  fun signUp_failure_emailAlreadyInUse() = runTest {
    val exception = Exception("Email already in use")
    simulateFirebaseAuthError(exception, isSignUp = true)

    val result = authRepository.signUp(TEST_EMAIL, TEST_PASSWORD, TEST_USERNAME)

    assertEquals(exception, result.exceptionOrNull())
  }

  @Test
  fun signUp_failure_nullEmailFromFirebase() = runTest {
    simulateFirebaseAuthSuccess(email = null, isSignUp = true)

    val result = authRepository.signUp(TEST_EMAIL, TEST_PASSWORD, TEST_USERNAME)

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is Exception)
  }

  @Test
  fun signIn_failure_nullUserFromFirebase() = runTest {
    whenever(mockAuthResult.user).thenReturn(null)
    whenever(mockAuth.signInWithEmailAndPassword(TEST_EMAIL, TEST_PASSWORD))
        .thenReturn(Tasks.forResult(mockAuthResult))

    val result = authRepository.signIn(TEST_EMAIL, TEST_PASSWORD)

    assertTrue(result.isFailure)
  }

  @Test
  fun signUp_failure_nullUserFromFirebase() = runTest {
    whenever(mockAuthResult.user).thenReturn(null)
    whenever(mockAuth.createUserWithEmailAndPassword(TEST_EMAIL, TEST_PASSWORD))
        .thenReturn(Tasks.forResult(mockAuthResult))

    val result = authRepository.signUp(TEST_EMAIL, TEST_PASSWORD, TEST_USERNAME)

    assertTrue(result.isFailure)
  }

  @Test
  fun getCurrentUser_throws_whenEmailNull() {
    val mockUser = simulateFirebaseAuthSuccess(email = null)
    whenever(mockAuth.currentUser).thenReturn(mockUser)

    assertThrows(IllegalStateException::class.java) { authRepository.getCurrentUser() }
  }

  @Test
  fun getCurrentUser_usesEmptyUsername_whenDisplayNameNull() {
    val mockUser = simulateFirebaseAuthSuccess(username = null)
    whenever(mockAuth.currentUser).thenReturn(mockUser)

    assertEquals("", authRepository.getCurrentUser().username)
  }

  @Test
  fun signOut_callsFirebaseAuthSignOut() = runTest {
    authRepository.signOut()
    verify(mockAuth).signOut()
  }

  @Test
  fun getCurrentUser_returnsUser_whenSignedIn() {
    val mockUser = simulateFirebaseAuthSuccess()
    whenever(mockAuth.currentUser).thenReturn(mockUser)

    val returnedUser = authRepository.getCurrentUser()

    assertEquals(TEST_UID, returnedUser.uid)
    assertEquals(TEST_EMAIL, returnedUser.email)
    assertEquals(TEST_USERNAME, returnedUser.username)
  }

  @Test
  fun getCurrentUser_throwsException_whenNotSignedIn() {
    whenever(mockAuth.currentUser).thenReturn(null)

    assertThrows(IllegalStateException::class.java) { authRepository.getCurrentUser() }
  }
}
