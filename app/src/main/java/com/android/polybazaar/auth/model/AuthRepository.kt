// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.model

/**
 * Thrown by [AuthRepository.getCurrentUser] when no session is saved on the device. That is the
 * normal state of a signed-out user, not a failure, so callers can tell it apart from real errors.
 */
class NoSessionException : Exception("No user is currently logged in")

interface AuthRepository {

  suspend fun signIn(email: String, password: String): Result<User>

  suspend fun signUp(email: String, password: String, username: String): Result<User>

  suspend fun signOut()

  /**
   * Returns the user of the session saved on the device.
   *
   * Throws [NoSessionException] when there is none, and other exceptions when the session cannot be
   * checked, for example offline.
   */
  suspend fun getCurrentUser(): User

  suspend fun sendVerificationEmail(): Result<Unit>

  suspend fun refreshUser(): Result<User>

  /**
   * Asks Firebase to email a password reset link to [email].
   *
   * Succeeds for unknown addresses too, so the screen always shows the same neutral confirmation
   * and does not reveal which emails have an account. Fails with a [PasswordResetException].
   */
  suspend fun sendPasswordResetEmail(email: String): Result<Unit>

  /**
   * Sets [newPassword] for the account that requested the reset link whose one-time code is [code].
   *
   * Fails with a [ConfirmPasswordResetException]:
   * [ConfirmPasswordResetException.Reason.INVALID_LINK] if [code] is blank, malformed, expired or
   * already used, [ConfirmPasswordResetException.Reason.WEAK_PASSWORD] if Firebase rejects
   * [newPassword].
   */
  suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit>
}
