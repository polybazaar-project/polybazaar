package com.android.polybazaar.auth.model

interface AuthRepository {

  suspend fun signIn(email: String, password: String): Result<User>

  suspend fun signUp(email: String, password: String, username: String): Result<User>

  suspend fun signOut()

  suspend fun getCurrentUser(): User

  /**
   * Asks Firebase to email a password reset link to [email].
   *
   * Succeeds for unknown addresses too, so the screen always shows the same neutral confirmation
   * and does not reveal which emails have an account. Fails with a [PasswordResetException].
   */
  suspend fun sendPasswordResetEmail(email: String): Result<Unit>
}
