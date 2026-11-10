// Co-authored-by: Claude Opus 5.5
package com.android.polybazaar.auth.viewmodel

import com.android.polybazaar.auth.model.AuthRepository
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
class VerifyEmailViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var repository: FakeAuthRepository
  private lateinit var viewModel: VerifyEmailViewModel

  private val unverifiedUser = User(uid = "user-1", email = "user@example.com", username = "User")

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = FakeAuthRepository()
    viewModel = VerifyEmailViewModel(repository, initialCooldownSeconds = 0)
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
  fun byDefault_startsWithCooldownAndIgnoresResend() = runTest {
    val defaultViewModel = VerifyEmailViewModel(repository)

    assertEquals(RESEND_COOLDOWN_SECONDS, defaultViewModel.uiState.value.resendCooldownSeconds)
    defaultViewModel.resend()
    testDispatcher.scheduler.runCurrent()
    assertEquals(0, repository.sendVerificationEmailCalls)

    testDispatcher.scheduler.advanceTimeBy(RESEND_COOLDOWN_SECONDS * 1_000L + 1)
    assertEquals(0, defaultViewModel.uiState.value.resendCooldownSeconds)
    defaultViewModel.resend()
    testDispatcher.scheduler.runCurrent()
    assertEquals(1, repository.sendVerificationEmailCalls)
  }

  @Test
  fun resend_success_sendsEmailAndStartsCooldown() = runTest {
    viewModel.resend()
    testDispatcher.scheduler.runCurrent()

    assertEquals(1, repository.sendVerificationEmailCalls)
    assertEquals(RESEND_COOLDOWN_SECONDS, viewModel.uiState.value.resendCooldownSeconds)
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
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
    repository.sendVerificationEmailResult = Result.failure(AppException(AppError.UNAUTHENTICATED))

    viewModel.resend()
    testDispatcher.scheduler.runCurrent()

    assertEquals(AppError.UNAUTHENTICATED, viewModel.uiState.value.error)
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
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun refresh_unverifiedEmail_staysUnverifiedAndTellsTheUser() = runTest {
    repository.refreshUserResult = Result.success(unverifiedUser)

    viewModel.refresh()
    testDispatcher.scheduler.runCurrent()

    assertFalse(viewModel.uiState.value.isVerified)
    assertFalse(viewModel.uiState.value.isLoading)
    assertTrue(viewModel.uiState.value.isNotVerifiedYet)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun refresh_automaticCheckWhileUnverified_showsNoMessage() = runTest {
    repository.refreshUserResult = Result.success(unverifiedUser)

    viewModel.refresh(automatic = true)
    testDispatcher.scheduler.runCurrent()

    assertFalse(viewModel.uiState.value.isVerified)
    assertFalse(viewModel.uiState.value.isNotVerifiedYet)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun refresh_failure_exposesErrorAndStopsLoading() = runTest {
    repository.refreshUserResult = Result.failure(UnknownHostException("offline"))

    viewModel.refresh()
    testDispatcher.scheduler.runCurrent()

    assertFalse(viewModel.uiState.value.isVerified)
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
  }

  @Test
  fun refresh_automaticCheckFailure_showsNoMessage() = runTest {
    repository.refreshUserResult = Result.failure(UnknownHostException("offline"))

    viewModel.refresh(automatic = true)
    testDispatcher.scheduler.runCurrent()

    assertFalse(viewModel.uiState.value.isVerified)
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun resend_clearsNotVerifiedYetAndErrorWhilePending() = runTest {
    repository.refreshUserResult = Result.success(unverifiedUser)
    viewModel.refresh()
    testDispatcher.scheduler.runCurrent()
    assertTrue(viewModel.uiState.value.isNotVerifiedYet)
    val sendGate = CompletableDeferred<Result<Unit>>()
    repository.sendVerificationEmailGate = sendGate

    viewModel.resend()
    testDispatcher.scheduler.runCurrent()

    assertTrue(viewModel.uiState.value.isLoading)
    assertFalse(viewModel.uiState.value.isNotVerifiedYet)
    assertNull(viewModel.uiState.value.error)
    sendGate.complete(Result.success(Unit))
    testDispatcher.scheduler.runCurrent()
    assertFalse(viewModel.uiState.value.isNotVerifiedYet)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun resend_afterFailedResend_clearsPreviousErrorWhilePending() = runTest {
    repository.sendVerificationEmailResult = Result.failure(UnknownHostException("offline"))
    viewModel.resend()
    testDispatcher.scheduler.runCurrent()
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    val sendGate = CompletableDeferred<Result<Unit>>()
    repository.sendVerificationEmailGate = sendGate

    viewModel.resend()
    testDispatcher.scheduler.runCurrent()

    assertTrue(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
    sendGate.complete(Result.success(Unit))
    testDispatcher.scheduler.runCurrent()
  }

  @Test
  fun refresh_clearsPreviousErrorAndNotVerifiedYetWhilePending() = runTest {
    repository.refreshUserResult = Result.failure(UnknownHostException("offline"))
    viewModel.refresh()
    testDispatcher.scheduler.runCurrent()
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    val refreshGate = CompletableDeferred<Result<User>>()
    repository.refreshUserGate = refreshGate

    viewModel.refresh()
    testDispatcher.scheduler.runCurrent()

    assertTrue(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isNotVerifiedYet)
    refreshGate.complete(Result.success(unverifiedUser))
    testDispatcher.scheduler.runCurrent()
    // The manual check now finds the email unverified: a flag, not an error.
    assertNull(viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.isNotVerifiedYet)
  }

  @Test
  fun refresh_automaticCheckAfterManualOne_clearsNotVerifiedYet() = runTest {
    repository.refreshUserResult = Result.success(unverifiedUser)
    viewModel.refresh()
    testDispatcher.scheduler.runCurrent()
    assertTrue(viewModel.uiState.value.isNotVerifiedYet)

    viewModel.refresh(automatic = true)
    testDispatcher.scheduler.runCurrent()

    assertFalse(viewModel.uiState.value.isNotVerifiedYet)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun refresh_automaticCheckFailureAfterManualFailure_clearsErrorAndAddsNone() = runTest {
    repository.refreshUserResult = Result.failure(AppException(AppError.UNAUTHENTICATED))
    viewModel.refresh()
    testDispatcher.scheduler.runCurrent()
    assertEquals(AppError.UNAUTHENTICATED, viewModel.uiState.value.error)

    repository.refreshUserResult = Result.failure(UnknownHostException("offline"))
    viewModel.refresh(automatic = true)
    testDispatcher.scheduler.runCurrent()

    assertNull(viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isNotVerifiedYet)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun refresh_noSignedInUser_exposesUnauthenticated() = runTest {
    repository.refreshUserResult = Result.failure(AppException(AppError.UNAUTHENTICATED))

    viewModel.refresh()
    testDispatcher.scheduler.runCurrent()

    assertEquals(AppError.UNAUTHENTICATED, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isNotVerifiedYet)
  }

  @Test
  fun refresh_whileLoading_isIgnored() = runTest {
    val refreshGate = CompletableDeferred<Result<User>>()
    repository.refreshUserGate = refreshGate
    viewModel.refresh()
    testDispatcher.scheduler.runCurrent()

    viewModel.refresh()
    testDispatcher.scheduler.runCurrent()

    assertEquals(1, repository.refreshUserCalls)
    refreshGate.complete(Result.success(unverifiedUser))
    testDispatcher.scheduler.runCurrent()
  }

  private class FakeAuthRepository : AuthRepository {
    var sendVerificationEmailResult: Result<Unit> = Result.success(Unit)
    var refreshUserResult: Result<User> = Result.failure(IllegalStateException("Not configured"))
    var sendVerificationEmailGate: CompletableDeferred<Result<Unit>>? = null
    var refreshUserGate: CompletableDeferred<Result<User>>? = null
    var sendVerificationEmailCalls = 0
    var refreshUserCalls = 0

    override suspend fun signIn(email: String, password: String): Result<User> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun signUp(email: String, password: String, username: String): Result<User> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun signOut() {}

    override suspend fun getCurrentUser(): User = throw IllegalStateException("Not used")

    override suspend fun sendVerificationEmail(): Result<Unit> {
      sendVerificationEmailCalls++
      return sendVerificationEmailGate?.await() ?: sendVerificationEmailResult
    }

    override suspend fun refreshUser(): Result<User> {
      refreshUserCalls++
      return refreshUserGate?.await() ?: refreshUserResult
    }

    override suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        Result.failure(IllegalStateException("Not used"))
  }
}
