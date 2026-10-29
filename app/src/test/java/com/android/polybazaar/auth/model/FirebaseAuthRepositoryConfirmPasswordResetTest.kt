// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.model

import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthActionCodeException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FirebaseAuthRepositoryConfirmPasswordResetTest {

  private lateinit var auth: FirebaseAuth
  private lateinit var repository: FirebaseAuthRepository

  private val code = "oobCode123"
  private val newPassword = "newPassword123"

  @Before
  fun setup() {
    auth = mock()
    repository = FirebaseAuthRepository(auth, mock<FirebaseFirestore>())
  }

  @Test
  fun confirmPasswordReset_emptyCode_failsWithInvalidLinkWithoutCallingFirebase() = runTest {
    val result = repository.confirmPasswordReset("", newPassword)

    assertConfirmPasswordResetFailure(result, ConfirmPasswordResetException.Reason.INVALID_LINK)
    assertNull(result.exceptionOrNull()?.cause)
    verify(auth, never()).confirmPasswordReset(any(), any())
  }

  @Test
  fun confirmPasswordReset_blankCode_failsWithInvalidLinkWithoutCallingFirebase() = runTest {
    val result = repository.confirmPasswordReset("   ", newPassword)

    assertConfirmPasswordResetFailure(result, ConfirmPasswordResetException.Reason.INVALID_LINK)
    assertNull(result.exceptionOrNull()?.cause)
    verify(auth, never()).confirmPasswordReset(any(), any())
  }

  @Test
  fun confirmPasswordReset_success_returnsSuccessAndSendsCodeAndPassword() = runTest {
    whenever(auth.confirmPasswordReset(code, newPassword)).thenReturn(Tasks.forResult(null))

    val result = repository.confirmPasswordReset(code, newPassword)

    assertEquals(Result.success(Unit), result)
    verify(auth).confirmPasswordReset(code, newPassword)
  }

  @Test
  fun confirmPasswordReset_expiredOrUsedCode_failsWithInvalidLinkAndKeepsCause() = runTest {
    val exception = FirebaseAuthActionCodeException("ERROR_EXPIRED_ACTION_CODE", "Expired")
    mockConfirmPasswordResetFailure(exception)

    val result = repository.confirmPasswordReset(code, newPassword)

    assertConfirmPasswordResetFailure(result, ConfirmPasswordResetException.Reason.INVALID_LINK)
    assertSame(exception, result.exceptionOrNull()?.cause)
  }

  @Test
  fun confirmPasswordReset_weakPassword_failsWithWeakPasswordAndKeepsCause() = runTest {
    val exception =
        FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "Too weak", "Too short")
    mockConfirmPasswordResetFailure(exception)

    val result = repository.confirmPasswordReset(code, newPassword)

    assertConfirmPasswordResetFailure(result, ConfirmPasswordResetException.Reason.WEAK_PASSWORD)
    assertSame(exception, result.exceptionOrNull()?.cause)
  }

  @Test
  fun confirmPasswordReset_noNetwork_failsWithNetworkAndKeepsCause() = runTest {
    val exception = FirebaseNetworkException("Network error")
    mockConfirmPasswordResetFailure(exception)

    val result = repository.confirmPasswordReset(code, newPassword)

    assertConfirmPasswordResetFailure(result, ConfirmPasswordResetException.Reason.NETWORK)
    assertSame(exception, result.exceptionOrNull()?.cause)
  }

  @Test
  fun confirmPasswordReset_tooManyRequests_failsWithTooManyRequestsAndKeepsCause() = runTest {
    val exception = FirebaseTooManyRequestsException("Blocked")
    mockConfirmPasswordResetFailure(exception)

    val result = repository.confirmPasswordReset(code, newPassword)

    assertConfirmPasswordResetFailure(
        result,
        ConfirmPasswordResetException.Reason.TOO_MANY_REQUESTS,
    )
    assertSame(exception, result.exceptionOrNull()?.cause)
  }

  @Test
  fun confirmPasswordReset_otherError_failsWithUnknownAndKeepsCause() = runTest {
    val exception = IllegalStateException("Unexpected")
    mockConfirmPasswordResetFailure(exception)

    val result = repository.confirmPasswordReset(code, newPassword)

    assertConfirmPasswordResetFailure(result, ConfirmPasswordResetException.Reason.UNKNOWN)
    assertSame(exception, result.exceptionOrNull()?.cause)
  }

  @Test
  fun confirmPasswordReset_cancellationException_isRethrown() = runTest {
    val exception = CancellationException("Cancelled")
    whenever(auth.confirmPasswordReset(code, newPassword)).thenThrow(exception)

    val result = runCatching { repository.confirmPasswordReset(code, newPassword) }

    assertSame(exception, result.exceptionOrNull())
  }

  private fun mockConfirmPasswordResetFailure(exception: Exception) {
    whenever(auth.confirmPasswordReset(code, newPassword)).thenReturn(Tasks.forException(exception))
  }

  private fun assertConfirmPasswordResetFailure(
      result: Result<Unit>,
      expected: ConfirmPasswordResetException.Reason,
  ) {
    val error = result.exceptionOrNull()
    assertTrue(
        "Expected ConfirmPasswordResetException but was $error",
        error is ConfirmPasswordResetException,
    )
    assertEquals(expected, (error as ConfirmPasswordResetException).reason)
  }
}
