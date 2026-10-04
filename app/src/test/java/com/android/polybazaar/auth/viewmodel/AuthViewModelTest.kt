package com.android.polybazaar.auth.viewmodel

import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.User
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var repository: FakeAuthRepository
  private lateinit var viewModel: AuthViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = FakeAuthRepository()
    viewModel = AuthViewModel(repository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initialState_isUnauthenticatedAndIdle() {
    assertEquals(AuthUiState(), viewModel.uiState.value)
  }

  @Test
  fun signIn_delegatesCredentialsAndUpdatesStateWithAuthenticatedUser() = runTest {
    val user = User(uid = "user-1", email = "user@example.com", username = "User")
    repository.signInResult = Result.success(user)

    viewModel.signIn("user@example.com", "password")
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals("user@example.com" to "password", repository.signInCredentials)
    assertEquals(user, viewModel.uiState.value.user)
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun signIn_showsLoadingAndClearsPreviousErrorWhileRequestIsPending() = runTest {
    repository.signInResult = Result.failure(IllegalArgumentException("Invalid credentials"))
    viewModel.signIn("user@example.com", "incorrect-password")
    testDispatcher.scheduler.advanceUntilIdle()
    repository.signInResult = Result.success(User("user-1", "user@example.com", "User"))
    val signInGate = CompletableDeferred<Result<User>>()
    repository.signInGate = signInGate

    viewModel.signIn("user@example.com", "password")
    testDispatcher.scheduler.runCurrent()

    assertTrue(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.errorMessage)
    signInGate.complete(repository.signInResult)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @Test
  fun signIn_usesFallbackMessageWhenRepositoryFailureHasNoMessage() = runTest {
    repository.signInResult = Result.failure(IllegalStateException())

    viewModel.signIn("user@example.com", "password")
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals("Authentication failed", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun signUp_delegatesDetailsAndUpdatesStateWithAuthenticatedUser() = runTest {
    val user = User(uid = "user-1", email = "user@example.com", username = "User")
    repository.signUpResult = Result.success(user)

    viewModel.signUp("user@example.com", "password", "User")
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(Triple("user@example.com", "password", "User"), repository.signUpDetails)
    assertEquals(user, viewModel.uiState.value.user)
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun signUp_exposesRepositoryFailure() = runTest {
    repository.signUpResult = Result.failure(IllegalArgumentException("Email already exists"))

    viewModel.signUp("user@example.com", "password", "User")
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.uiState.value.user)
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals("Email already exists", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun signOut_clearsAuthenticatedUser() = runTest {
    val user = User(uid = "user-1", email = "user@example.com", username = "User")
    repository.signInResult = Result.success(user)
    viewModel.signIn("user@example.com", "password")
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.signOut()
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.uiState.value.user)
    assertFalse(viewModel.uiState.value.isLoading)
    assertTrue(repository.didSignOut)
  }

  @Test
  fun signOut_preservesUserAndExposesFallbackMessageWhenItFails() = runTest {
    val user = User(uid = "user-1", email = "user@example.com", username = "User")
    repository.signInResult = Result.success(user)
    viewModel.signIn("user@example.com", "password")
    testDispatcher.scheduler.advanceUntilIdle()
    repository.signOutException = IllegalStateException()

    viewModel.signOut()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(user, viewModel.uiState.value.user)
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals("Unable to sign out", viewModel.uiState.value.errorMessage)
  }

  private class FakeAuthRepository : AuthRepository {
    var signInResult: Result<User> = Result.failure(IllegalStateException("Not configured"))
    var signUpResult: Result<User> = Result.failure(IllegalStateException("Not configured"))
    var signInCredentials: Pair<String, String>? = null
    var signInGate: CompletableDeferred<Result<User>>? = null
    var signUpDetails: Triple<String, String, String>? = null
    var signOutException: Exception? = null
    var didSignOut = false

    override suspend fun signIn(email: String, password: String): Result<User> {
      signInCredentials = email to password
      return signInGate?.await() ?: signInResult
    }

    override suspend fun signUp(email: String, password: String, username: String): Result<User> {
      signUpDetails = Triple(email, password, username)
      return signUpResult
    }

    override suspend fun signOut() {
      didSignOut = true
      signOutException?.let { throw it }
    }

    override fun getCurrentUser(): User {
      throw IllegalStateException("Not configured")
    }
  }
}
