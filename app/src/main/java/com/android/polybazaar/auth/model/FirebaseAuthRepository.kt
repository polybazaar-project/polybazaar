// Co-authored-by: Claude Code (Claude Opus 5.5, Claude Sonnet 5.5)
package com.android.polybazaar.auth.model

import com.google.firebase.auth.FirebaseAuth
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
    if (username.isBlank()) return Result.failure(Exception("Username is required"))
    AuthRules.usernameError(AuthRules.normalizeUsername(username))?.let {
      return Result.failure(Exception(it))
    }
    if (email.length > AuthRules.MAX_EMAIL_LENGTH) {
      return Result.failure(
          Exception("Email must be at most ${AuthRules.MAX_EMAIL_LENGTH} characters")
      )
    }
    if (password.length > AuthRules.MAX_PASSWORD_LENGTH) {
      return Result.failure(
          Exception("Password must be at most ${AuthRules.MAX_PASSWORD_LENGTH} characters")
      )
    }
    return try {
      require(username == AuthRules.normalizeUsername(username)) {
        "Username must be trimmed and lowercase"
      }
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
