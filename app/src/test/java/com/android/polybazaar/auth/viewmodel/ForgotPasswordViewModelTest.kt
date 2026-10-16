// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.viewmodel

import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.PasswordResetException
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
class ForgotPasswordViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var repository: FakeAuthRepository
  private lateinit var viewModel: ForgotPasswordViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = FakeAuthRepository()
    viewModel = ForgotPasswordViewModel(repository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initialState_isEmptyAndIdle() {
    assertEquals(ForgotPasswordUiState(), viewModel.uiState.value)
    assertEquals("", viewModel.uiState.value.email)
    assertFalse(viewModel.uiState.value.isLoading)
    assertFalse(viewModel.uiState.value.emailSent)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun onEmailChange_storesEmailAsTyped() {
    viewModel.onEmailChange("  user@example.com ")

    assertEquals("  user@example.com ", viewModel.uiState.value.email)
    assertTrue(repository.resetEmails.isEmpty())
  }

  @Test
  fun sendResetLink_sendsTrimmedEmailAndMarksEmailSent() = runTest {
    viewModel.onEmailChange("  user@example.com\n")

    viewModel.sendResetLink()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("user@example.com"), repository.resetEmails)
    assertEquals(
        ForgotPasswordUiState(email = "  user@example.com\n", emailSent = true),
        viewModel.uiState.value,
    )
  }

  @Test
  fun sendResetLink_showsLoadingWhileRequestIsPending() = runTest {
    val gate = CompletableDeferred<Result<Unit>>()
    repository.resetGate = gate
    viewModel.onEmailChange("user@example.com")

    viewModel.sendResetLink()
    testDispatcher.scheduler.runCurrent()

    assertTrue(viewModel.uiState.value.isLoading)
    assertFalse(viewModel.uiState.value.emailSent)
    assertNull(viewModel.uiState.value.error)

    gate.complete(Result.success(Unit))
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertTrue(viewModel.uiState.value.emailSent)
  }

  @Test
  fun sendResetLink_isIgnoredWhileARequestIsInFlight() = runTest {
    val gate = CompletableDeferred<Result<Unit>>()
    repository.resetGate = gate
    viewModel.onEmailChange("user@example.com")

    viewModel.sendResetLink()
    testDispatcher.scheduler.runCurrent()
    viewModel.sendResetLink()
    viewModel.sendResetLink()
    testDispatcher.scheduler.runCurrent()
    gate.complete(Result.success(Unit))
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("user@example.com"), repository.resetEmails)
    assertTrue(viewModel.uiState.value.emailSent)
  }

  @Test
  fun sendResetLink_canBeSentAgainAfterTheFirstRequestCompletes() = runTest {
    viewModel.onEmailChange("user@example.com")
    viewModel.sendResetLink()
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.sendResetLink()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("user@example.com", "user@example.com"), repository.resetEmails)
    assertTrue(viewModel.uiState.value.emailSent)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun sendResetLink_exposesEachPasswordResetReason() = runTest {
    for (reason in PasswordResetException.Reason.entries) {
      repository.resetResult = Result.failure(PasswordResetException(reason))
      viewModel.onEmailChange("user@example.com")

      viewModel.sendResetLink()
      testDispatcher.scheduler.advanceUntilIdle()

      assertEquals("reason $reason", reason, viewModel.uiState.value.error)
      assertFalse("reason $reason", viewModel.uiState.value.isLoading)
      assertFalse("reason $reason", viewModel.uiState.value.emailSent)
    }
  }

  @Test
  fun sendResetLink_mapsUnexpectedExceptionToUnknown() = runTest {
    repository.resetResult = Result.failure(IllegalStateException("Firebase exploded"))
    viewModel.onEmailChange("user@example.com")

    viewModel.sendResetLink()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(PasswordResetException.Reason.UNKNOWN, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)
    assertFalse(viewModel.uiState.value.emailSent)
  }

  @Test
  fun sendResetLink_withBlankEmail_setsInvalidEmailWithoutCallingRepository() = runTest {
    for (input in listOf("", "   ", "\n\t")) {
      viewModel.onEmailChange(input)

      viewModel.sendResetLink()
      testDispatcher.scheduler.advanceUntilIdle()

      assertTrue("input \"$input\"", repository.resetEmails.isEmpty())
      assertEquals(
          "input \"$input\"",
          PasswordResetException.Reason.INVALID_EMAIL,
          viewModel.uiState.value.error,
      )
      assertFalse("input \"$input\"", viewModel.uiState.value.isLoading)
      assertFalse("input \"$input\"", viewModel.uiState.value.emailSent)
    }
  }

  @Test
  fun sendResetLink_afterBlankEmailError_sendsOnceEmailIsValid() = runTest {
    viewModel.onEmailChange("   ")
    viewModel.sendResetLink()
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(PasswordResetException.Reason.INVALID_EMAIL, viewModel.uiState.value.error)

    viewModel.onEmailChange("user@example.com")
    viewModel.sendResetLink()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("user@example.com"), repository.resetEmails)
    assertNull(viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.emailSent)
  }

  @Test
  fun sendResetLink_afterFailure_clearsPreviousErrorWhenItSucceeds() = runTest {
    repository.resetResult =
        Result.failure(PasswordResetException(PasswordResetException.Reason.NETWORK))
    viewModel.onEmailChange("user@example.com")
    viewModel.sendResetLink()
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(PasswordResetException.Reason.NETWORK, viewModel.uiState.value.error)

    repository.resetResult = Result.success(Unit)
    viewModel.sendResetLink()
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.emailSent)
  }

  @Test
  fun sendResetLink_clearsPreviousErrorWhileNewRequestIsPending() = runTest {
    repository.resetResult =
        Result.failure(PasswordResetException(PasswordResetException.Reason.TOO_MANY_REQUESTS))
    viewModel.onEmailChange("user@example.com")
    viewModel.sendResetLink()
    testDispatcher.scheduler.advanceUntilIdle()
    val gate = CompletableDeferred<Result<Unit>>()
    repository.resetGate = gate

    viewModel.sendResetLink()
    testDispatcher.scheduler.runCurrent()

    assertNull(viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.isLoading)
    gate.complete(Result.success(Unit))
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @Test
  fun onEmailChange_afterSuccess_clearsEmailSent() = runTest {
    viewModel.onEmailChange("user@example.com")
    viewModel.sendResetLink()
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.onEmailChange("other@example.com")

    assertEquals(ForgotPasswordUiState(email = "other@example.com"), viewModel.uiState.value)
  }

  @Test
  fun onEmailChange_afterFailure_clearsError() = runTest {
    repository.resetResult =
        Result.failure(PasswordResetException(PasswordResetException.Reason.INVALID_EMAIL))
    viewModel.onEmailChange("not-an-email")
    viewModel.sendResetLink()
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.onEmailChange("user@example.com")

    assertEquals(ForgotPasswordUiState(email = "user@example.com"), viewModel.uiState.value)
  }

  @Test
  fun sendResetLink_successForPreviousEmail_isIgnoredAfterEmailChanges() = runTest {
    val gate = CompletableDeferred<Result<Unit>>()
    repository.resetGate = gate
    viewModel.onEmailChange("a@example.com")

    viewModel.sendResetLink()
    testDispatcher.scheduler.runCurrent()
    viewModel.onEmailChange("b@example.com")
    gate.complete(Result.success(Unit))
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(ForgotPasswordUiState(email = "b@example.com"), viewModel.uiState.value)
  }

  @Test
  fun sendResetLink_failureForPreviousEmail_isIgnoredAfterEmailChanges() = runTest {
    val gate = CompletableDeferred<Result<Unit>>()
    repository.resetGate = gate
    viewModel.onEmailChange("a@example.com")

    viewModel.sendResetLink()
    testDispatcher.scheduler.runCurrent()
    viewModel.onEmailChange("b@example.com")
    gate.complete(
        Result.failure(PasswordResetException(PasswordResetException.Reason.INVALID_EMAIL))
    )
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.uiState.value.error)
  }

  private class FakeAuthRepository : AuthRepository {
    var resetResult: Result<Unit> = Result.success(Unit)
    var resetGate: CompletableDeferred<Result<Unit>>? = null
    val resetEmails = mutableListOf<String>()

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
      resetEmails += email
      return resetGate?.await() ?: resetResult
    }

    override suspend fun signIn(email: String, password: String): Result<User> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun signUp(email: String, password: String, username: String): Result<User> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun signOut() = Unit

    override suspend fun getCurrentUser(): User = throw IllegalStateException("Not used")

    override suspend fun sendVerificationEmail(): Result<Unit> =
        Result.failure(IllegalStateException("Not used"))

    override suspend fun refreshUser(): Result<User> =
        Result.failure(IllegalStateException("Not used"))
  }
}
