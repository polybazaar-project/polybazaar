// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.model

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
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

      Result.success(
          User(
              uid = firebaseUser.uid,
              email = firebaseUser.email ?: throw Exception("User email cannot be null"),
              username = usernameForUid(firebaseUser.uid),
              isEmailVerified = firebaseUser.isEmailVerified,
          )
      )
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Result.failure(e)
    }
  }

  override suspend fun signUp(email: String, password: String, username: String): Result<User> {
    return try {
      val usernameDocRef = firestore.collection("usernames").document(username.trim().lowercase())

      val snapshot = usernameDocRef.get().await()
      if (snapshot.exists()) {
        return Result.failure(Exception("Username is already taken"))
      }

      val authResult = auth.createUserWithEmailAndPassword(email, password).await()
      val firebaseUser = authResult.user ?: throw Exception("Failed to create user account")

      val user =
          try {
            val usernameData =
                mapOf(
                    "uid" to firebaseUser.uid,
                    "username" to username,
                )
            usernameDocRef.set(usernameData).await()

            User(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: throw Exception("User email cannot be null"),
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

    return User(
        uid = firebaseUser.uid,
        email = firebaseUser.email ?: throw Exception("User email cannot be null"),
        username = usernameForUid(firebaseUser.uid),
        isEmailVerified = firebaseUser.isEmailVerified,
    )
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
      // An unknown address counts as success so the screen cannot be used to find out which
      // emails have an account (acceptance criterion of #13). With email enumeration protection
      // on, Firebase already returns success; this covers projects where it is off.
      if (e is FirebaseAuthInvalidUserException) Result.success(Unit)
      else Result.failure(PasswordResetException(passwordResetReason(e), e))
    }
  }

  private suspend fun usernameForUid(uid: String): String {
    val querySnapshot =
        firestore.collection("usernames").whereEqualTo("uid", uid).limit(1).get().await()

    if (querySnapshot.isEmpty) {
      throw Exception("Username not found for the current user")
    }

    val document = querySnapshot.documents.first()
    return document.getString("username")
        ?: throw Exception("Username field not found for the current user")
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
