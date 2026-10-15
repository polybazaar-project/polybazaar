// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.model

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : AuthRepository {

  override suspend fun signIn(email: String, password: String): Result<User> {
    return try {
      val authResult = auth.signInWithEmailAndPassword(email, password).await()
      val firebaseUser = authResult.user ?: throw Exception("Sign in failed")

      Result.success(signedInUser(firebaseUser))
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Result.failure(e)
    }
  }

  override suspend fun signUp(email: String, password: String, username: String): Result<User> {
    if (!isEpflEmail(email)) {
      return Result.failure(Exception("Use your EPFL email (ending in @$EPFL_EMAIL_DOMAIN)"))
    }
    return try {
      require(username == username.trim().lowercase()) { "Username must be trimmed and lowercase" }
      val usernameDocRef = firestore.collection("usernames").document(username)

      val snapshot = usernameDocRef.get().await()
      if (snapshot.exists()) {
        return Result.failure(Exception("Username is already taken"))
      }

      val authResult = auth.createUserWithEmailAndPassword(email, password).await()
      val firebaseUser = authResult.user ?: throw Exception("Failed to create user account")

      val user =
          try {
            val email = firebaseUser.email ?: throw Exception("User email cannot be null")
            // One batch, so the account never ends up with a username but no profile document.
            firestore
                .batch()
                .set(usernameDocRef, mapOf("uid" to firebaseUser.uid))
                .set(profileDocument(firebaseUser.uid), profileData(username), SetOptions.merge())
                .commit()
                .await()

            User(
                uid = firebaseUser.uid,
                email = email,
                username = username,
                isEmailVerified = firebaseUser.isEmailVerified,
            )
          } catch (e: Exception) {
            withContext(NonCancellable) {
              try {
                firebaseUser.delete().await()
              } catch (rollbackEx: Exception) {
                e.addSuppressed(rollbackEx)
              }
            }
            if (e is CancellationException) throw e
            throw Exception("Failed to register username. Account creation rolled back.", e)
          }

      // Outside the rollback above: a failed or cancelled send must not undo the sign-up, the
      // user can ask for the email again.
      sendVerificationEmailTo(firebaseUser)
      Result.success(user)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Result.failure(e)
    }
  }

  override suspend fun signOut() {
    auth.signOut()
  }

  override suspend fun getCurrentUser(): User {
    val firebaseUser = auth.currentUser ?: throw Exception("No user is currently logged in")
    return signedInUser(firebaseUser)
  }

  override suspend fun sendVerificationEmail(): Result<Unit> {
    val firebaseUser =
        auth.currentUser ?: return Result.failure(Exception("No user is currently logged in"))
    return sendVerificationEmailTo(firebaseUser)
  }

  override suspend fun refreshUser(): Result<User> {
    return try {
      val firebaseUser = auth.currentUser ?: throw Exception("No user is currently logged in")
      firebaseUser.reload().await()
      Result.success(getCurrentUser())
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Result.failure(e)
    }
  }

  private suspend fun sendVerificationEmailTo(firebaseUser: FirebaseUser): Result<Unit> {
    return try {
      firebaseUser.sendEmailVerification().await()
      Result.success(Unit)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Result.failure(e)
    }
  }

  override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
    // Firebase throws IllegalArgumentException on an empty string before any request is sent,
    // so catch the blank case here and report it like any other invalid address.
    if (email.isBlank()) {
      return Result.failure(PasswordResetException(PasswordResetException.Reason.INVALID_EMAIL))
    }
    return try {
      auth.sendPasswordResetEmail(email).await()
      Result.success(Unit)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      // An unknown or disabled address counts as success so the screen cannot be used to find out
      // which emails have an account (acceptance criterion of #13).
      // FirebaseAuthInvalidUserException
      // covers both (ERROR_USER_NOT_FOUND and ERROR_USER_DISABLED); this is intended. With email
      // enumeration protection on, Firebase already returns success; this covers projects where it
      // is off.
      if (e is FirebaseAuthInvalidUserException) Result.success(Unit)
      else Result.failure(PasswordResetException(passwordResetReason(e), e))
    }
  }

  private suspend fun signedInUser(firebaseUser: FirebaseUser): User {
    val user =
        User(
            uid = firebaseUser.uid,
            email = firebaseUser.email ?: throw Exception("User email cannot be null"),
            username = usernameForUid(firebaseUser.uid),
            isEmailVerified = firebaseUser.isEmailVerified,
        )
    ensureProfileDocument(user)
    return user
  }

  /**
   * Creates the profile document of accounts made before sign-up wrote it. The write is not
   * awaited: offline it would only complete once the connection returns, and Firestore already
   * applies it to the local cache, which is what the profile screen reads.
   */
  private fun ensureProfileDocument(user: User) {
    profileDocument(user.uid).set(profileData(user.username), SetOptions.merge())
  }

  private fun profileDocument(uid: String) = firestore.collection("users").document(uid)

  private fun profileData(username: String) = mapOf("username" to username)

  private suspend fun usernameForUid(uid: String): String {
    val querySnapshot =
        firestore.collection("usernames").whereEqualTo("uid", uid).limit(1).get().await()

    if (querySnapshot.isEmpty) {
      throw Exception("Username not found for the current user")
    }

    return querySnapshot.documents.first().id
  }
}

/**
 * Maps a Firebase error from a password reset request to the [PasswordResetException.Reason] shown
 * to the user. Anything not listed becomes [PasswordResetException.Reason.UNKNOWN].
 */
private fun passwordResetReason(e: Exception): PasswordResetException.Reason =
    when (e) {
      // Firebase reports a badly formatted address as an invalid credential (ERROR_INVALID_EMAIL).
      is FirebaseAuthInvalidCredentialsException -> PasswordResetException.Reason.INVALID_EMAIL
      is FirebaseNetworkException -> PasswordResetException.Reason.NETWORK
      is FirebaseTooManyRequestsException -> PasswordResetException.Reason.TOO_MANY_REQUESTS
      else -> PasswordResetException.Reason.UNKNOWN
    }
