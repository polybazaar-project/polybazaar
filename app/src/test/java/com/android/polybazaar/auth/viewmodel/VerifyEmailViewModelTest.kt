// Co-authored-by: Claude Opus 5.5
package com.android.polybazaar.auth.viewmodel

import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.User
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
class VerifyEmailViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var repository: FakeAuthRepository
  private lateinit var viewModel: VerifyEmailViewModel

  private val unverifiedUser = User(uid = "user-1", email = "user@example.com", username = "User")

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = FakeAuthRepository()
    viewModel = VerifyEmailViewModel(repository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initialState_isUnverifiedAndIdle() {
    assertEquals(VerifyEmailUiState(), viewModel.uiState.value)
  }

  @Test
  fun resend_success_sendsEmailAndStartsCooldown() = runTest {
    viewModel.resend()
    testDispatcher.scheduler.runCurrent()

    assertEquals(1, repository.sendVerificationEmailCalls)
    assertEquals(RESEND_COOLDOWN_SECONDS, viewModel.uiState.value.resendCooldownSeconds)
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun resend_duringCooldown_doesNotSendAgain() = runTest {
    viewModel.resend()
    testDispatcher.scheduler.runCurrent()
    testDispatcher.scheduler.advanceTimeBy((RESEND_COOLDOWN_SECONDS - 1) * 1_000L)
    testDispatcher.scheduler.runCurrent()

    viewModel.resend()
    testDispatcher.scheduler.runCurrent()

    assertEquals(1, repository.sendVerificationEmailCalls)
    assertEquals(1, viewModel.uiState.value.resendCooldownSeconds)
  }

  @Test
  fun resend_afterCooldownElapsed_sendsAgain() = runTest {
    viewModel.resend()
    testDispatcher.scheduler.runCurrent()
    testDispatcher.scheduler.advanceTimeBy(RESEND_COOLDOWN_SECONDS * 1_000L)
    testDispatcher.scheduler.runCurrent()
    assertEquals(0, viewModel.uiState.value.resendCooldownSeconds)

    viewModel.resend()
    testDispatcher.scheduler.runCurrent()

    assertEquals(2, repository.sendVerificationEmailCalls)
  }

  @Test
  fun resend_afterEmailIsVerified_doesNotSend() = runTest {
    repository.refreshUserResult = Result.success(unverifiedUser.copy(isEmailVerified = true))
    viewModel.refresh()
    testDispatcher.scheduler.runCurrent()

    viewModel.resend()
    testDispatcher.scheduler.runCurrent()

    assertEquals(0, repository.sendVerificationEmailCalls)
  }

  @Test
  fun resend_failure_exposesErrorWithoutStartingCooldown() = runTest {
    repository.sendVerificationEmailResult = Result.failure(Exception("Too many requests"))

    viewModel.resend()
    testDispatcher.scheduler.runCurrent()

    assertEquals("Too many requests", viewModel.uiState.value.errorMessage)
    assertEquals(0, viewModel.uiState.value.resendCooldownSeconds)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun refresh_verifiedEmail_updatesStateToVerified() = runTest {
    repository.refreshUserResult = Result.success(unverifiedUser.copy(isEmailVerified = true))

    viewModel.refresh()
    testDispatcher.scheduler.runCurrent()

    assertTrue(viewModel.uiState.value.isVerified)
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun refresh_unverifiedEmail_staysUnverifiedAndTellsTheUser() = runTest {
    repository.refreshUserResult = Result.success(unverifiedUser)

    viewModel.refresh()
    testDispatcher.scheduler.runCurrent()

    assertFalse(viewModel.uiState.value.isVerified)
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals("Email not verified yet", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun refresh_failure_exposesErrorAndStopsLoading() = runTest {
    repository.refreshUserResult = Result.failure(Exception("Network error"))

    viewModel.refresh()
    testDispatcher.scheduler.runCurrent()

    assertFalse(viewModel.uiState.value.isVerified)
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals("Network error", viewModel.uiState.value.errorMessage)
  }

  private class FakeAuthRepository : AuthRepository {
    var sendVerificationEmailResult: Result<Unit> = Result.success(Unit)
    var refreshUserResult: Result<User> = Result.failure(IllegalStateException("Not configured"))
    var sendVerificationEmailCalls = 0

    override suspend fun signIn(email: String, password: String): Result<User> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun signUp(email: String, password: String, username: String): Result<User> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun signOut() {}

    override suspend fun getCurrentUser(): User = throw IllegalStateException("Not used")

    override suspend fun sendVerificationEmail(): Result<Unit> {
      sendVerificationEmailCalls++
      return sendVerificationEmailResult
    }

    override suspend fun refreshUser(): Result<User> = refreshUserResult

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        Result.failure(IllegalStateException("Not used"))
  }
}
