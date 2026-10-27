// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.error.model

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class AppErrorTest {

  private val logged = mutableListOf<Throwable>()

  private fun Throwable.map(): AppError = toAppError { logged += it }

  private fun firestore(code: FirebaseFirestoreException.Code) =
      FirebaseFirestoreException("Firestore error", code)

  private fun storage(errorCode: Int): StorageException = mock {
    on { this.errorCode } doReturn errorCode
  }

  @Test
  fun firebaseNetworkException_isNetwork() {
    assertEquals(AppError.NETWORK, FirebaseNetworkException("Offline").map())
  }

  @Test
  fun networkIoExceptions_areNetwork() {
    listOf(
            UnknownHostException("firestore.googleapis.com"),
            SocketTimeoutException("Timed out"),
            ConnectException("Connection refused"),
            NoRouteToHostException("No route to host"),
            SocketException("Network is unreachable"),
        )
        .forEach { assertEquals("$it", AppError.NETWORK, it.map()) }
  }

  @Test
  fun fileNotFoundException_isUnknown() {
    assertEquals(AppError.UNKNOWN, FileNotFoundException("content://photo/1").map())
  }

  @Test
  fun unreadableUploadFile_wrappedInStorageException_isUnknown() {
    val exception: StorageException = mock {
      on { errorCode } doReturn StorageException.ERROR_UNKNOWN
      on { cause } doReturn FileNotFoundException("content://photo/1")
    }

    assertEquals(AppError.UNKNOWN, exception.map())
  }

  @Test
  fun tooManyRequestsException_isTooManyRequests() {
    assertEquals(AppError.TOO_MANY_REQUESTS, FirebaseTooManyRequestsException("Slow down").map())
  }

  @Test
  fun weakPasswordException_isWeakPassword_notInvalidCredentials() {
    val exception = FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "Weak", "Too short")

    assertEquals(AppError.WEAK_PASSWORD, exception.map())
  }

  @Test
  fun invalidCredentialsException_isInvalidCredentials() {
    val exception = FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "Wrong")

    assertEquals(AppError.INVALID_CREDENTIALS, exception.map())
  }

  @Test
  fun invalidEmailCode_isInvalidEmail() {
    val exception = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", "Bad email")

    assertEquals(AppError.INVALID_EMAIL, exception.map())
  }

  @Test
  fun networkCause_wrappedInStorageException_isNetwork() {
    val exception: StorageException = mock {
      on { errorCode } doReturn StorageException.ERROR_UNKNOWN
      on { cause } doReturn UnknownHostException("firebasestorage.googleapis.com")
    }

    assertEquals(AppError.NETWORK, exception.map())
  }

  @Test
  fun userNotFound_isInvalidCredentials() {
    val exception = FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "No user")

    assertEquals(AppError.INVALID_CREDENTIALS, exception.map())
  }

  @Test
  fun userDisabled_isInvalidCredentials() {
    val exception = FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "Disabled")

    assertEquals(AppError.INVALID_CREDENTIALS, exception.map())
  }

  @Test
  fun userTokenExpired_isUnauthenticated() {
    val exception = FirebaseAuthInvalidUserException("ERROR_USER_TOKEN_EXPIRED", "Expired")

    assertEquals(AppError.UNAUTHENTICATED, exception.map())
  }

  @Test
  fun invalidUserToken_isUnauthenticated() {
    val exception = FirebaseAuthInvalidUserException("ERROR_INVALID_USER_TOKEN", "Invalid token")

    assertEquals(AppError.UNAUTHENTICATED, exception.map())
  }

  @Test
  fun otherInvalidUserCode_isUnknown() {
    val exception = FirebaseAuthInvalidUserException("ERROR_SOMETHING_NEW", "New")

    assertEquals(AppError.UNKNOWN, exception.map())
  }

  @Test
  fun userCollisionException_isEmailAlreadyInUse() {
    val exception = FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "Taken")

    assertEquals(AppError.EMAIL_ALREADY_IN_USE, exception.map())
  }

  @Test
  fun firestoreCodes_mapToTheirError() {
    val expected =
        mapOf(
            FirebaseFirestoreException.Code.UNAVAILABLE to AppError.NETWORK,
            FirebaseFirestoreException.Code.PERMISSION_DENIED to AppError.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.NOT_FOUND to AppError.NOT_FOUND,
            FirebaseFirestoreException.Code.UNAUTHENTICATED to AppError.UNAUTHENTICATED,
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED to AppError.TOO_MANY_REQUESTS,
        )

    expected.forEach { (code, error) -> assertEquals("Code $code", error, firestore(code).map()) }
    assertTrue(logged.isEmpty())
  }

  @Test
  fun unhandledFirestoreCode_isUnknown() {
    assertEquals(AppError.UNKNOWN, firestore(FirebaseFirestoreException.Code.INTERNAL).map())
  }

  @Test
  fun storageCodes_mapToTheirError() {
    val expected =
        mapOf(
            StorageException.ERROR_RETRY_LIMIT_EXCEEDED to AppError.NETWORK,
            StorageException.ERROR_NOT_AUTHORIZED to AppError.PERMISSION_DENIED,
            StorageException.ERROR_OBJECT_NOT_FOUND to AppError.NOT_FOUND,
            StorageException.ERROR_BUCKET_NOT_FOUND to AppError.NOT_FOUND,
            StorageException.ERROR_PROJECT_NOT_FOUND to AppError.NOT_FOUND,
            StorageException.ERROR_NOT_AUTHENTICATED to AppError.UNAUTHENTICATED,
            StorageException.ERROR_QUOTA_EXCEEDED to AppError.TOO_MANY_REQUESTS,
        )

    expected.forEach { (code, error) -> assertEquals("Code $code", error, storage(code).map()) }
    assertTrue(logged.isEmpty())
  }

  @Test
  fun unhandledStorageCode_isUnknown() {
    assertEquals(AppError.UNKNOWN, storage(StorageException.ERROR_UNKNOWN).map())
  }

  @Test
  fun wrappedKnownException_isFoundThroughTheCause() {
    val exception =
        Exception(
            "Failed to register username. Account creation rolled back.",
            firestore(FirebaseFirestoreException.Code.PERMISSION_DENIED),
        )

    assertEquals(AppError.PERMISSION_DENIED, exception.map())
    assertTrue(logged.isEmpty())
  }

  @Test
  fun outermostKnownException_wins() {
    val exception =
        FirebaseFirestoreException(
            "Denied",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseNetworkException("Offline"),
        )

    assertEquals(AppError.PERMISSION_DENIED, exception.map())
  }

  @Test
  fun unknownException_isUnknown_andLogsTheOriginal() {
    val exception = IllegalStateException("Username not found for the current user")

    assertEquals(AppError.UNKNOWN, exception.map())
    assertEquals(1, logged.size)
    assertSame(exception, logged.single())
  }
}
