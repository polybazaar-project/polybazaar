// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.model

import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import com.android.polybazaar.error.model.toAppError
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthActionCodeException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
  fun confirmPasswordReset_blankCode_failsWithInvalidLinkWithoutCallingFirebase() = runTest {
    for (blank in listOf("", "   ", "\n\t")) {
      val error = repository.confirmPasswordReset(blank, newPassword).exceptionOrNull()

      assertEquals("code \"$blank\"", AppError.INVALID_LINK, (error as AppException).error)
      assertNull("code \"$blank\"", error.cause)
    }
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
  fun confirmPasswordReset_expiredOrUsedCode_failsWithTheOriginalExceptionShownAsInvalidLink() =
      runTest {
        for (errorCode in listOf("ERROR_EXPIRED_ACTION_CODE", "ERROR_INVALID_ACTION_CODE")) {
          val exception = FirebaseAuthActionCodeException(errorCode, "Expired")
          mockConfirmPasswordResetFailure(exception)

          val failure = repository.confirmPasswordReset(code, newPassword).exceptionOrNull()

          assertSame(errorCode, exception, failure)
          assertEquals(errorCode, AppError.INVALID_LINK, failure?.toAppError())
        }
      }

  @Test
  fun confirmPasswordReset_accountGoneOrDisabled_failsWithInvalidLinkAndKeepsCause() = runTest {
    for (errorCode in listOf("ERROR_USER_NOT_FOUND", "ERROR_USER_DISABLED")) {
      val exception = FirebaseAuthInvalidUserException(errorCode, "No user")
      mockConfirmPasswordResetFailure(exception)

      val failure = repository.confirmPasswordReset(code, newPassword).exceptionOrNull()

      assertEquals(errorCode, AppError.INVALID_LINK, (failure as AppException).error)
      assertSame(errorCode, exception, failure.cause)
      // Not INVALID_CREDENTIALS, which the cause alone would map to.
      assertEquals(errorCode, AppError.INVALID_LINK, failure.toAppError())
    }
  }

  @Test
  fun confirmPasswordReset_otherInvalidCredentials_failsAsUnknownAndKeepsCause() = runTest {
    for (errorCode in
        listOf("ERROR_INVALID_EMAIL", "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL")) {
      val exception = FirebaseAuthInvalidCredentialsException(errorCode, "Rejected")
      mockConfirmPasswordResetFailure(exception)

      val failure = repository.confirmPasswordReset(code, newPassword).exceptionOrNull()

      assertEquals(errorCode, AppError.UNKNOWN, (failure as AppException).error)
      assertSame(errorCode, exception, failure.cause)
      // Not INVALID_EMAIL or INVALID_CREDENTIALS, which the cause alone would map to.
      assertEquals(errorCode, AppError.UNKNOWN, failure.toAppError())
    }
  }

  @Test
  fun confirmPasswordReset_weakPassword_failsWithTheOriginalExceptionShownAsWeakPassword() =
      runTest {
        val exception =
            FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "Too weak", "Too short")
        mockConfirmPasswordResetFailure(exception)

        val failure = repository.confirmPasswordReset(code, newPassword).exceptionOrNull()

        assertSame(exception, failure)
        assertEquals(AppError.WEAK_PASSWORD, failure?.toAppError())
      }

  @Test
  fun confirmPasswordReset_noNetwork_failsWithTheOriginalExceptionShownAsNetwork() = runTest {
    val exception = FirebaseNetworkException("Network error")
    mockConfirmPasswordResetFailure(exception)

    val failure = repository.confirmPasswordReset(code, newPassword).exceptionOrNull()

    assertSame(exception, failure)
    assertEquals(AppError.NETWORK, failure?.toAppError())
  }

  @Test
  fun confirmPasswordReset_tooManyRequests_failsWithTheOriginalExceptionShownAsTooManyRequests() =
      runTest {
        val exception = FirebaseTooManyRequestsException("Blocked")
        mockConfirmPasswordResetFailure(exception)

        val failure = repository.confirmPasswordReset(code, newPassword).exceptionOrNull()

        assertSame(exception, failure)
        assertEquals(AppError.TOO_MANY_REQUESTS, failure?.toAppError())
      }

  @Test
  fun confirmPasswordReset_otherError_failsWithTheOriginalExceptionShownAsUnknown() = runTest {
    val exception = IllegalStateException("Unexpected")
    mockConfirmPasswordResetFailure(exception)

    val failure = repository.confirmPasswordReset(code, newPassword).exceptionOrNull()

    assertSame(exception, failure)
    assertEquals(AppError.UNKNOWN, failure?.toAppError {})
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
}
