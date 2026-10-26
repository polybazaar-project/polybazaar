// Co-authored-by: Claude Code (Claude Opus 5.5)
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
class ResetPasswordViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var repository: FakeAuthRepository
  private lateinit var viewModel: ResetPasswordViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = FakeAuthRepository()
    viewModel = ResetPasswordViewModel(repository, OOB_CODE)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initialState_isEmptyAndIdle() {
    assertEquals(ResetPasswordUiState(), viewModel.uiState.value)
    assertEquals("", viewModel.uiState.value.newPassword)
    assertEquals("", viewModel.uiState.value.confirmPassword)
    assertFalse(viewModel.uiState.value.isLoading)
    assertFalse(viewModel.uiState.value.passwordReset)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun onNewPasswordChange_storesPasswordAsTyped() {
    viewModel.onNewPasswordChange("  abcd1234\n")

    assertEquals(ResetPasswordUiState(newPassword = "  abcd1234\n"), viewModel.uiState.value)
    assertTrue(repository.resetCalls.isEmpty())
  }

  @Test
  fun onConfirmPasswordChange_storesConfirmationAsTyped() {
    viewModel.onConfirmPasswordChange("\tabcd1234 ")

    assertEquals(ResetPasswordUiState(confirmPassword = "\tabcd1234 "), viewModel.uiState.value)
    assertTrue(repository.resetCalls.isEmpty())
  }

  @Test
  fun onNewPasswordChange_afterFailure_clearsError() = runTest {
    viewModel.resetPassword()
    assertEquals(ResetPasswordError.EMPTY_PASSWORD, viewModel.uiState.value.error)

    viewModel.onNewPasswordChange("abcd1234")

    assertEquals(ResetPasswordUiState(newPassword = "abcd1234"), viewModel.uiState.value)
  }

  @Test
  fun onConfirmPasswordChange_afterFailure_clearsError() = runTest {
    viewModel.onNewPasswordChange("abcd1234")
    viewModel.onConfirmPasswordChange("abcd123")
    viewModel.resetPassword()
    assertEquals(ResetPasswordError.PASSWORDS_DO_NOT_MATCH, viewModel.uiState.value.error)

    viewModel.onConfirmPasswordChange("abcd1234")

    assertEquals(
        ResetPasswordUiState(newPassword = "abcd1234", confirmPassword = "abcd1234"),
        viewModel.uiState.value,
    )
  }

  @Test
  fun onNewPasswordChange_afterSuccess_clearsPasswordReset() = runTest {
    enterPasswords("abcd1234")
    viewModel.resetPassword()
    testDispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value.passwordReset)

    viewModel.onNewPasswordChange("efgh5678")

    assertEquals(
        ResetPasswordUiState(newPassword = "efgh5678", confirmPassword = "abcd1234"),
        viewModel.uiState.value,
    )
  }

  @Test
  fun onConfirmPasswordChange_afterSuccess_clearsPasswordReset() = runTest {
    enterPasswords("abcd1234")
    viewModel.resetPassword()
    testDispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value.passwordReset)

    viewModel.onConfirmPasswordChange("efgh5678")

    assertEquals(
        ResetPasswordUiState(newPassword = "abcd1234", confirmPassword = "efgh5678"),
        viewModel.uiState.value,
    )
  }

  @Test
  fun resetPassword_blankPassword_failsWithoutCallingRepository() = runTest {
    for (input in listOf("", "        ", "\t\n")) {
      enterPasswords(input)

      viewModel.resetPassword()
      testDispatcher.scheduler.advanceUntilIdle()

      assertLocalError("input \"$input\"", ResetPasswordError.EMPTY_PASSWORD)
    }
  }

  @Test
  fun resetPassword_shortPassword_failsAsWeakWithoutCallingRepository() = runTest {
    enterPasswords("abcd123")

    viewModel.resetPassword()
    testDispatcher.scheduler.advanceUntilIdle()

    assertLocalError("7 characters", ResetPasswordError.WEAK_PASSWORD)
  }

  @Test
  fun resetPassword_passwordWithoutDigit_failsAsWeakWithoutCallingRepository() = runTest {
    enterPasswords("abcdefgh")

    viewModel.resetPassword()
    testDispatcher.scheduler.advanceUntilIdle()

    assertLocalError("no digit", ResetPasswordError.WEAK_PASSWORD)
  }

  @Test
  fun resetPassword_passwordWithoutLetter_failsAsWeakWithoutCallingRepository() = runTest {
    enterPasswords("12345678")

    viewModel.resetPassword()
    testDispatcher.scheduler.advanceUntilIdle()

    assertLocalError("no letter", ResetPasswordError.WEAK_PASSWORD)
  }

  @Test
  fun resetPassword_passwordOfMinimumLength_isSent() = runTest {
    enterPasswords("abcdefg1")

    viewModel.resetPassword()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf(OOB_CODE to "abcdefg1"), repository.resetCalls)
    assertTrue(viewModel.uiState.value.passwordReset)
  }

  @Test
  fun resetPassword_unicodeLettersAndDigits_meetThePasswordRules() = runTest {
    // Cyrillic letters, then an Arabic-Indic digit.
    for (password in listOf("пароль12", "abcdefg٣")) {
      enterPasswords(password)

      viewModel.resetPassword()
      testDispatcher.scheduler.advanceUntilIdle()

      assertEquals("password $password", OOB_CODE to password, repository.resetCalls.last())
      assertNull("password $password", viewModel.uiState.value.error)
      assertTrue("password $password", viewModel.uiState.value.passwordReset)
    }
  }

  @Test
  fun resetPassword_confirmationMismatch_failsWithoutCallingRepository() = runTest {
    for (confirmation in listOf("abcd1235", "", "   ", "abcd1234 ", "ABCD1234")) {
      viewModel.onNewPasswordChange("abcd1234")
      viewModel.onConfirmPasswordChange(confirmation)

      viewModel.resetPassword()
      testDispatcher.scheduler.advanceUntilIdle()

      assertLocalError("confirmation \"$confirmation\"", ResetPasswordError.PASSWORDS_DO_NOT_MATCH)
    }
  }

  @Test
  fun resetPassword_blankPasswordAndMismatch_reportsEmptyPasswordFirst() = runTest {
    viewModel.onNewPasswordChange("   ")
    viewModel.onConfirmPasswordChange("abcd1234")

    viewModel.resetPassword()
    testDispatcher.scheduler.advanceUntilIdle()

    assertLocalError("blank and mismatch", ResetPasswordError.EMPTY_PASSWORD)
  }

  @Test
  fun resetPassword_weakPasswordAndMismatch_reportsWeakPasswordFirst() = runTest {
    viewModel.onNewPasswordChange("abc1")
    viewModel.onConfirmPasswordChange("xyz9")

    viewModel.resetPassword()
    testDispatcher.scheduler.advanceUntilIdle()

    assertLocalError("weak and mismatch", ResetPasswordError.WEAK_PASSWORD)
  }

  private fun enterPasswords(password: String) {
    viewModel.onNewPasswordChange(password)
    viewModel.onConfirmPasswordChange(password)
  }

  private fun assertLocalError(case: String, expected: ResetPasswordError) {
    assertTrue(case, repository.resetCalls.isEmpty())
    assertEquals(case, expected, viewModel.uiState.value.error)
    assertFalse(case, viewModel.uiState.value.isLoading)
    assertFalse(case, viewModel.uiState.value.passwordReset)
  }

  private class FakeAuthRepository : AuthRepository {
    var resetResult: Result<Unit> = Result.success(Unit)
    var resetGate: CompletableDeferred<Result<Unit>>? = null
    val resetCalls = mutableListOf<Pair<String, String>>()

    override suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit> {
      resetCalls += code to newPassword
      return resetGate?.await() ?: resetResult
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        Result.failure(IllegalStateException("Not used"))

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

  private companion object {
    const val OOB_CODE = "oob-code-123"
  }
}
