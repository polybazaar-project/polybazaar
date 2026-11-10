// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.testing

import android.net.Uri
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.NoSessionException
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import com.android.polybazaar.profile.model.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * An in-memory [AuthRepository] and [ProfileRepository] shared by the unit and instrumented tests,
 * so what one screen writes is read back by the others as it would be from Firebase.
 */
class InMemoryBackend : AuthRepository, ProfileRepository {
  private class Account(val password: String, val user: User)

  private val accounts = mutableMapOf<String, Account>()
  private val verifiedUids = mutableSetOf<String>()
  private var signedInUid: String? = null
  val profiles = MutableStateFlow<Map<String, User>>(emptyMap())

  /** Accounts registered by a test are verified unless [verified] says otherwise. */
  fun register(
      email: String,
      password: String,
      username: String,
      verified: Boolean = true,
  ): User {
    val user = User(uid = "uid-$username", email = email, username = username)
    accounts[email] = Account(password, user)
    if (verified) verifiedUids += user.uid
    profiles.value = profiles.value + (user.uid to user)
    return user
  }

  /** Simulates the user clicking the link in the verification email. */
  fun openVerificationLink(email: String) {
    verifiedUids += accounts.getValue(email).user.uid
  }

  private fun withVerification(user: User) = user.copy(isEmailVerified = user.uid in verifiedUids)

  fun setBio(uid: String, bio: String) {
    profiles.value = profiles.value + (uid to profiles.value.getValue(uid).copy(bio = bio))
  }

  fun setSignedIn(uid: String?) {
    signedInUid = uid
  }

  override suspend fun signIn(email: String, password: String): Result<User> {
    val account = accounts[email]
    if (account == null || account.password != password) {
      return Result.failure(AppException(AppError.INVALID_CREDENTIALS))
    }
    signedInUid = account.user.uid
    return Result.success(withVerification(account.user))
  }

  override suspend fun signUp(email: String, password: String, username: String): Result<User> {
    if (accounts.values.any { it.user.username == username }) {
      return Result.failure(AppException(AppError.USERNAME_TAKEN))
    }
    val user = register(email, password, username, verified = false)
    signedInUid = user.uid
    return Result.success(user)
  }

  override suspend fun signOut() {
    signedInUid = null
  }

  override suspend fun getCurrentUser(): User {
    val uid = signedInUid ?: throw NoSessionException()
    return withVerification(profiles.value.getValue(uid))
  }

  override suspend fun sendVerificationEmail(): Result<Unit> = Result.success(Unit)

  override suspend fun refreshUser(): Result<User> = Result.success(getCurrentUser())

  override suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit> =
      Result.failure(IllegalStateException("Not used"))

  override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
      Result.failure(IllegalStateException("Not used"))

  override fun observeProfile(uid: String): Flow<User?> = profiles.map { it[uid] }

  override suspend fun uploadProfilePhoto(uid: String, photoUri: Uri): String = "photo-path"

  override suspend fun loadProfilePhoto(photoPath: String) = ByteArray(0)

  override suspend fun removeProfilePhoto(uid: String) = Unit

  override suspend fun updateProfile(uid: String, photoPath: String?, bio: String) {
    val current = profiles.value.getValue(uid)
    profiles.value = profiles.value + (uid to current.copy(bio = bio))
  }
}
