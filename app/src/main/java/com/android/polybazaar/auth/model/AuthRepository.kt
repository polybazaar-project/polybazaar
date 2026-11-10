// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.model

/**
 * Thrown by [AuthRepository.getCurrentUser] when no session is saved on the device. That is the
 * normal state of a signed-out user, not a failure, so callers can tell it apart from real errors.
 */
class NoSessionException : Exception("No user is currently logged in")

interface AuthRepository {

  /**
   * Signs in with [email] and [password].
   *
   * Fails with an exception that [com.android.polybazaar.error.model.toAppError] maps to the
   * message to show: [com.android.polybazaar.error.model.AppError.INVALID_CREDENTIALS] if the
   * password is wrong or the account does not exist or is disabled,
   * [com.android.polybazaar.error.model.AppError.INVALID_EMAIL] if [email] is badly formatted,
   * [com.android.polybazaar.error.model.AppError.NETWORK] offline.
   */
  suspend fun signIn(email: String, password: String): Result<User>

  /**
   * Creates an account, reserves [username] for it and sends the verification email.
   *
   * Fails with an exception that [com.android.polybazaar.error.model.toAppError] maps to the
   * message to show: [com.android.polybazaar.error.model.AppError.USERNAME_TAKEN] if another
   * account uses [username], [com.android.polybazaar.error.model.AppError.EMAIL_ALREADY_IN_USE] if
   * one uses [email], [com.android.polybazaar.error.model.AppError.WEAK_PASSWORD] if Firebase
   * rejects [password], [com.android.polybazaar.error.model.AppError.NETWORK] offline. Inputs the
   * sign-up screen already rejects, such as a non-EPFL email, map to
   * [com.android.polybazaar.error.model.AppError.UNKNOWN].
   */
  suspend fun signUp(email: String, password: String, username: String): Result<User>

  suspend fun signOut()

  /**
   * Returns the user of the session saved on the device.
   *
   * Throws [NoSessionException] when there is none, and other exceptions when the session cannot be
   * checked, for example offline.
   */
  suspend fun getCurrentUser(): User

  /**
   * Sends the verification email again to the signed-in user.
   *
   * Fails with an exception that [com.android.polybazaar.error.model.toAppError] maps to the
   * message to show: [com.android.polybazaar.error.model.AppError.UNAUTHENTICATED] if no user is
   * signed in or the account was deleted or disabled,
   * [com.android.polybazaar.error.model.AppError.TOO_MANY_REQUESTS] if emails were sent too often,
   * [com.android.polybazaar.error.model.AppError.NETWORK] offline.
   */
  suspend fun sendVerificationEmail(): Result<Unit>

  /**
   * Reloads the signed-in user from the server, to learn whether their email is now verified.
   *
   * Fails with an exception that [com.android.polybazaar.error.model.toAppError] maps to the
   * message to show: [com.android.polybazaar.error.model.AppError.UNAUTHENTICATED] if no user is
   * signed in, the session expired or the account was deleted or disabled,
   * [com.android.polybazaar.error.model.AppError.NETWORK] offline.
   */
  suspend fun refreshUser(): Result<User>

  /**
   * Asks Firebase to email a password reset link to [email].
   *
   * Succeeds for unknown addresses too, so the screen always shows the same neutral confirmation
   * and does not reveal which emails have an account. Fails with an exception that
   * [com.android.polybazaar.error.model.toAppError] maps to the message to show:
   * [com.android.polybazaar.error.model.AppError.INVALID_EMAIL] if [email] is blank or badly
   * formatted.
   */
  suspend fun sendPasswordResetEmail(email: String): Result<Unit>

  /**
   * Sets [newPassword] for the account that requested the reset link whose one-time code is [code].
   *
   * Fails with an exception that [com.android.polybazaar.error.model.toAppError] maps to the
   * message to show: [com.android.polybazaar.error.model.AppError.INVALID_LINK] if [code] is blank,
   * malformed, expired or already used, or if its account no longer exists or is disabled,
   * [com.android.polybazaar.error.model.AppError.WEAK_PASSWORD] if Firebase rejects [newPassword].
   */
  suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit>
}
