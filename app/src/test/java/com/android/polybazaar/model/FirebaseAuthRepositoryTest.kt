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

  @Before
  fun setUp() {
    mockAuth = mock()
    mockAuthResult = mock()
    authRepository = FirebaseAuthRepository(mockAuth)
  }

  private fun configureMockUser(uid: String, email: String, displayName: String): FirebaseUser {
    val mockUser: FirebaseUser = mock()
    whenever(mockUser.uid).thenReturn(uid)
    whenever(mockUser.email).thenReturn(email)
    whenever(mockUser.displayName).thenReturn(displayName)
    whenever(mockAuthResult.user).thenReturn(mockUser)
    return mockUser
  }

  @Test
  fun signIn_successful() = runTest {
    configureMockUser("uid123", "test@test.com", "TestUser")
    whenever(mockAuth.signInWithEmailAndPassword("test@test.com", "password123"))
        .thenReturn(Tasks.forResult(mockAuthResult))

    val result = authRepository.signIn("test@test.com", "password123")

    assertTrue(result.isSuccess)
    with(result.getOrNull()!!) {
      assertEquals("uid123", uid)
      assertEquals("test@test.com", email)
      assertEquals("TestUser", username)
    }
  }

  @Test
  fun signIn_failure() = runTest {
    val exception = Exception("Invalid credentials")
    whenever(mockAuth.signInWithEmailAndPassword("test@test.com", "wrong"))
        .thenReturn(Tasks.forException(exception))

    val result = authRepository.signIn("test@test.com", "wrong")

    assertTrue(result.isFailure)
    assertEquals(exception.message, result.exceptionOrNull()?.message)
  }

  @Test
  fun signUp_successful() = runTest {
    val mockUser = configureMockUser("uid123", "test@test.com", "TestUser")
    whenever(mockAuth.createUserWithEmailAndPassword("test@test.com", "password123"))
        .thenReturn(Tasks.forResult(mockAuthResult))
    whenever(mockUser.updateProfile(any())).thenReturn(Tasks.forResult(null))

    val result = authRepository.signUp("test@test.com", "password123", "TestUser")

    assertTrue(result.isSuccess)
    with(result.getOrNull()!!) {
      assertEquals("uid123", uid)
      assertEquals("test@test.com", email)
      assertEquals("TestUser", username)
    }
    verify(mockUser).updateProfile(any())
  }

  @Test
  fun signOut_callsFirebaseAuthSignOut() = runTest {
    authRepository.signOut()
    verify(mockAuth).signOut()
  }

  @Test
  fun getCurrentUser_returnsUser_whenSignedIn() {
    val mockUser = configureMockUser("uid123", "test@test.com", "TestUser")
    whenever(mockAuth.currentUser).thenReturn(mockUser)

    val returnedUser = authRepository.getCurrentUser()

    assertEquals("uid123", returnedUser.uid)
    assertEquals("test@test.com", returnedUser.email)
    assertEquals("TestUser", returnedUser.username)
  }

  @Test
  fun getCurrentUser_throwsException_whenNotSignedIn() {
    whenever(mockAuth.currentUser).thenReturn(null)

    assertThrows(IllegalStateException::class.java) { authRepository.getCurrentUser() }
  }
}
