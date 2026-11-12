// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.viewmodel

import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.NoSessionException
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import java.net.UnknownHostException
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
  private val loggedErrors = mutableListOf<Throwable>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = FakeAuthRepository()
    viewModel = AuthViewModel(repository, logUnexpectedError = loggedErrors::add)
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
    assertNull(viewModel.uiState.value.error)
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
    assertNull(viewModel.uiState.value.error)
    signInGate.complete(repository.signInResult)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @Test
  fun signIn_unrecognisedFailure_exposesUnknownError() = runTest {
    repository.signInResult = Result.failure(IllegalStateException())

    viewModel.signIn("user@example.com", "password")
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(AppError.UNKNOWN, viewModel.uiState.value.error)
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
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun signUp_exposesRepositoryFailure() = runTest {
    repository.signUpResult = Result.failure(AppException(AppError.USERNAME_TAKEN))

    viewModel.signUp("user@example.com", "password", "User")
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.uiState.value.user)
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(AppError.USERNAME_TAKEN, viewModel.uiState.value.error)
  }

  @Test
  fun signIn_offline_exposesNetworkError() = runTest {
    repository.signInResult = Result.failure(UnknownHostException("raw offline text"))

    viewModel.signIn("user@example.com", "password")
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.uiState.value.user)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
  }

  @Test
  fun signIn_rejectedCredentials_exposesInvalidCredentialsError() = runTest {
    repository.signInResult = Result.failure(AppException(AppError.INVALID_CREDENTIALS))

    viewModel.signIn("user@example.com", "wrong")
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(AppError.INVALID_CREDENTIALS, viewModel.uiState.value.error)
  }

  @Test
  fun signUp_clearsPreviousErrorWhileRequestIsPending() = runTest {
    repository.signUpResult = Result.failure(AppException(AppError.USERNAME_TAKEN))
    viewModel.signUp("user@example.com", "password", "User")
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(AppError.USERNAME_TAKEN, viewModel.uiState.value.error)
    val signUpGate = CompletableDeferred<Result<User>>()
    repository.signUpGate = signUpGate

    viewModel.signUp("user@example.com", "password", "Other")
    testDispatcher.scheduler.runCurrent()

    assertTrue(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
    signUpGate.complete(Result.success(User("user-1", "user@example.com", "Other")))
    testDispatcher.scheduler.advanceUntilIdle()
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun signOut_clearsPreviousErrorWhileRequestIsPending() = runTest {
    repository.signInResult = Result.success(User("user-1", "user@example.com", "User"))
    viewModel.signIn("user@example.com", "password")
    testDispatcher.scheduler.advanceUntilIdle()
    repository.signOutException = UnknownHostException("offline")
    viewModel.signOut()
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    repository.signOutException = null
    val signOutGate = CompletableDeferred<Unit>()
    repository.signOutGate = signOutGate

    viewModel.signOut()
    testDispatcher.scheduler.runCurrent()

    assertTrue(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
    signOutGate.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()
    assertNull(viewModel.uiState.value.user)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun signOut_offline_exposesNetworkError() = runTest {
    repository.signOutException = UnknownHostException("raw offline text")

    viewModel.signOut()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
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
  fun signOut_preservesUserAndExposesErrorWhenItFails() = runTest {
    val user = User(uid = "user-1", email = "user@example.com", username = "User")
    repository.signInResult = Result.success(user)
    viewModel.signIn("user@example.com", "password")
    testDispatcher.scheduler.advanceUntilIdle()
    repository.signOutException = IllegalStateException()

    viewModel.signOut()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(user, viewModel.uiState.value.user)
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(AppError.UNKNOWN, viewModel.uiState.value.error)
  }

  @Test
  fun clearError_removesErrorAndKeepsUser() = runTest {
    val user = User(uid = "user-1", email = "user@example.com", username = "User")
    repository.signInResult = Result.success(user)
    viewModel.signIn("user@example.com", "password")
    testDispatcher.scheduler.advanceUntilIdle()
    repository.signOutException = IllegalStateException()
    viewModel.signOut()
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.clearError()

    assertNull(viewModel.uiState.value.error)
    assertEquals(user, viewModel.uiState.value.user)
  }

  @Test
  fun restoreSession_exposesPersistedUserAndMarksSessionChecked() = runTest {
    val user = User(uid = "user-1", email = "user@example.com", username = "User")
    repository.currentUser = user

    viewModel.restoreSession()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(user, viewModel.uiState.value.user)
    assertFalse(viewModel.uiState.value.isLoading)
    assertTrue(viewModel.uiState.value.sessionChecked)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun restoreSession_withoutPersistedUser_staysSignedOutWithoutError() = runTest {
    viewModel.restoreSession()
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.uiState.value.user)
    assertFalse(viewModel.uiState.value.isLoading)
    assertTrue(viewModel.uiState.value.sessionChecked)
    assertNull(viewModel.uiState.value.error)
    // Being signed out is the normal case, not worth a log.
    assertTrue(loggedErrors.isEmpty())
  }

  @Test
  fun restoreSession_networkFailure_keepsSessionUncheckedWithNetworkError() = runTest {
    repository.currentUser = User("user-1", "user@example.com", "User")
    repository.getCurrentUserError = UnknownHostException("offline")

    viewModel.restoreSession()
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.uiState.value.user)
    assertFalse(viewModel.uiState.value.sessionChecked)
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.sessionError)
  }

  @Test
  fun restoreSession_retryAfterNetworkFailure_restoresUserAndClearsError() = runTest {
    val user = User("user-1", "user@example.com", "User")
    repository.currentUser = user
    repository.getCurrentUserError = UnknownHostException("offline")
    viewModel.restoreSession()
    testDispatcher.scheduler.advanceUntilIdle()

    repository.getCurrentUserError = null
    viewModel.restoreSession()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(user, viewModel.uiState.value.user)
    assertTrue(viewModel.uiState.value.sessionChecked)
    assertNull(viewModel.uiState.value.sessionError)
  }

  @Test
  fun restoreSession_otherFailure_signsOutWithoutSessionErrorAndLogsIt() = runTest {
    repository.currentUser = User("user-1", "user@example.com", "User")
    val failure = IllegalStateException("Username not found")
    repository.getCurrentUserError = failure

    viewModel.restoreSession()
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.uiState.value.user)
    assertTrue(viewModel.uiState.value.sessionChecked)
    assertNull(viewModel.uiState.value.sessionError)
    // Unexpected, so it must leave a trace before signing the user out.
    assertEquals(listOf<Throwable>(failure), loggedErrors)
  }

  @Test
  fun restoreSession_networkFailure_isNotLogged() = runTest {
    repository.getCurrentUserError = UnknownHostException("offline")

    viewModel.restoreSession()
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(loggedErrors.isEmpty())
  }

  @Test
  fun restoreSession_runsOnlyOnce() = runTest {
    viewModel.restoreSession()
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.restoreSession()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repository.getCurrentUserCalls)
  }

  @Test
  fun restoreSession_resultArrivingAfterSignIn_doesNotOverwriteSignedInUser() = runTest {
    val user = User(uid = "user-1", email = "user@example.com", username = "User")
    val restoreGate = CompletableDeferred<User?>()
    repository.getCurrentUserGate = restoreGate
    repository.signInResult = Result.success(user)
    viewModel.restoreSession()
    testDispatcher.scheduler.runCurrent()

    viewModel.signIn("user@example.com", "password")
    testDispatcher.scheduler.runCurrent()
    restoreGate.complete(null)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(user, viewModel.uiState.value.user)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun restoreSession_networkFailureArrivingAfterSignIn_doesNotShowSessionError() = runTest {
    val user = User(uid = "user-1", email = "user@example.com", username = "User")
    val restoreGate = CompletableDeferred<User?>()
    repository.getCurrentUserGate = restoreGate
    repository.getCurrentUserError = UnknownHostException("offline")
    repository.signInResult = Result.success(user)
    viewModel.restoreSession()
    testDispatcher.scheduler.runCurrent()

    viewModel.signIn("user@example.com", "password")
    testDispatcher.scheduler.runCurrent()
    restoreGate.complete(null)
    testDispatcher.scheduler.advanceUntilIdle()

    // The user is signed in, so a retry offer for the old check would be wrong.
    assertEquals(user, viewModel.uiState.value.user)
    assertNull(viewModel.uiState.value.sessionError)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun signOut_afterRestoreSession_keepsSessionChecked() = runTest {
    repository.currentUser = User("user-1", "user@example.com", "User")
    viewModel.restoreSession()
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.signOut()
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.uiState.value.user)
    assertTrue(viewModel.uiState.value.sessionChecked)
  }

  @Test
  fun onEmailVerified_marksSignedInUserVerified() = runTest {
    repository.signInResult = Result.success(User("user-1", "user@example.com", "User"))
    viewModel.signIn("user@example.com", "password")
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.onEmailVerified()

    assertEquals(true, viewModel.uiState.value.user?.isEmailVerified)
  }

  private class FakeAuthRepository : AuthRepository {
    var signInResult: Result<User> = Result.failure(IllegalStateException("Not configured"))
    var signUpResult: Result<User> = Result.failure(IllegalStateException("Not configured"))
    var signInCredentials: Pair<String, String>? = null
    var signInGate: CompletableDeferred<Result<User>>? = null
    var signUpDetails: Triple<String, String, String>? = null
    var signUpGate: CompletableDeferred<Result<User>>? = null
    var signOutException: Exception? = null
    var signOutGate: CompletableDeferred<Unit>? = null
    var didSignOut = false
    var currentUser: User? = null
    var getCurrentUserCalls = 0
    var getCurrentUserGate: CompletableDeferred<User?>? = null
    var getCurrentUserError: Exception? = null

    override suspend fun signIn(email: String, password: String): Result<User> {
      signInCredentials = email to password
      return signInGate?.await() ?: signInResult
    }

    override suspend fun signUp(email: String, password: String, username: String): Result<User> {
      signUpDetails = Triple(email, password, username)
      return signUpGate?.await() ?: signUpResult
    }

    override suspend fun signOut() {
      signOutGate?.await()
      didSignOut = true
      signOutException?.let { throw it }
    }

    override suspend fun getCurrentUser(): User {
      getCurrentUserCalls++
      // The gate comes first, so a test can make the check fail after something else happened.
      val gate = getCurrentUserGate
      val user = if (gate != null) gate.await() else currentUser
      getCurrentUserError?.let { throw it }
      return user ?: throw NoSessionException()
    }

    override suspend fun sendVerificationEmail(): Result<Unit> =
        Result.failure(IllegalStateException("Not configured"))

    override suspend fun refreshUser(): Result<User> =
        Result.failure(IllegalStateException("Not configured"))

    override suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        Result.failure(IllegalStateException("Not used"))
  }
}
