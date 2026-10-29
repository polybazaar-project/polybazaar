// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.error.model

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * A failure the user can be told about, independent of the library that raised it.
 *
 * ViewModels keep an [AppError] in their state instead of an exception message, and the screen
 * picks the localized text for it, so raw Firebase messages never reach the user.
 */
enum class AppError {
  /** The device could not reach the server. */
  NETWORK,

  /** The security rules refused the request. */
  PERMISSION_DENIED,

  /** The document or file asked for does not exist. */
  NOT_FOUND,

  /** The request needs a signed-in user and there is none, or the session expired. */
  UNAUTHENTICATED,

  /** The email or password is wrong, or the account does not exist. */
  INVALID_CREDENTIALS,

  /** The email address is badly formatted. */
  INVALID_EMAIL,

  /** The password does not meet the Firebase strength rules. */
  WEAK_PASSWORD,

  /** An account already uses this email address. */
  EMAIL_ALREADY_IN_USE,

  /** Too many requests were sent from this device, or a quota was exceeded. */
  TOO_MANY_REQUESTS,

  /** Any other error. */
  UNKNOWN,
}

private const val TAG = "AppError"

/**
 * Maps this exception to the [AppError] shown to the user.
 *
 * The cause chain is searched, so a repository can wrap a Firebase error in its own exception and
 * still get the right message. When nothing in the chain is recognised, the original exception is
 * passed to [log] and [AppError.UNKNOWN] is returned.
 *
 * Callers must rethrow a [kotlinx.coroutines.CancellationException] before calling this: it is not
 * an error to show.
 *
 * @param log receives exceptions that map to [AppError.UNKNOWN]; logs to Logcat by default.
 */
fun Throwable.toAppError(
    log: (Throwable) -> Unit = { Log.e(TAG, "Unmapped exception shown as UNKNOWN", it) }
): AppError {
  val error = generateSequence(this) { it.cause }.firstNotNullOfOrNull { knownError(it) }
  if (error != null) return error
  log(this)
  return AppError.UNKNOWN
}

/**
 * The [AppError] for this exception alone, ignoring its cause, or null if it is not a known one.
 */
private fun knownError(e: Throwable): AppError? =
    when (e) {
      is FirebaseNetworkException -> AppError.NETWORK
      is FirebaseTooManyRequestsException -> AppError.TOO_MANY_REQUESTS
      // Must come before FirebaseAuthInvalidCredentialsException, which it extends.
      is FirebaseAuthWeakPasswordException -> AppError.WEAK_PASSWORD
      is FirebaseAuthInvalidCredentialsException -> invalidCredentialsError(e.errorCode)
      is FirebaseAuthInvalidUserException -> invalidUserError(e.errorCode)
      is FirebaseAuthUserCollisionException -> AppError.EMAIL_ALREADY_IN_USE
      is FirebaseFirestoreException -> firestoreError(e.code)
      is StorageException -> storageError(e.errorCode)
      // Only network failures: other IOExceptions, such as a FileNotFoundException from an upload
      // whose local file cannot be read, are not a connection problem.
      // SocketException covers ConnectException, NoRouteToHostException and "Network is
      // unreachable".
      is UnknownHostException,
      is SocketTimeoutException,
      is SocketException -> AppError.NETWORK
      else -> null
    }

private fun invalidCredentialsError(errorCode: String): AppError =
    when (errorCode) {
      "ERROR_INVALID_EMAIL" -> AppError.INVALID_EMAIL
      else -> AppError.INVALID_CREDENTIALS
    }

private fun invalidUserError(errorCode: String): AppError? =
    when (errorCode) {
      // An unknown or disabled account on sign-in. Telling it apart from a wrong password would
      // reveal which emails have an account.
      "ERROR_USER_NOT_FOUND",
      "ERROR_USER_DISABLED" -> AppError.INVALID_CREDENTIALS
      // Raised by operations on the signed-in user, such as reload(): the session is no longer
      // valid.
      "ERROR_USER_TOKEN_EXPIRED",
      "ERROR_INVALID_USER_TOKEN" -> AppError.UNAUTHENTICATED
      else -> null
    }

private fun firestoreError(code: FirebaseFirestoreException.Code): AppError? =
    when (code) {
      // Firestore returns UNAVAILABLE when offline and the data is not in the local cache.
      FirebaseFirestoreException.Code.UNAVAILABLE -> AppError.NETWORK
      FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.PERMISSION_DENIED
      FirebaseFirestoreException.Code.NOT_FOUND -> AppError.NOT_FOUND
      FirebaseFirestoreException.Code.UNAUTHENTICATED -> AppError.UNAUTHENTICATED
      FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> AppError.TOO_MANY_REQUESTS
      else -> null
    }

private fun storageError(errorCode: Int): AppError? =
    when (errorCode) {
      StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> AppError.NETWORK
      StorageException.ERROR_NOT_AUTHORIZED -> AppError.PERMISSION_DENIED
      StorageException.ERROR_OBJECT_NOT_FOUND,
      StorageException.ERROR_BUCKET_NOT_FOUND,
      StorageException.ERROR_PROJECT_NOT_FOUND -> AppError.NOT_FOUND
      StorageException.ERROR_NOT_AUTHENTICATED -> AppError.UNAUTHENTICATED
      StorageException.ERROR_QUOTA_EXCEEDED -> AppError.TOO_MANY_REQUESTS
      else -> null
    }
