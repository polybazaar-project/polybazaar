package com.android.polybazaar.model

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) :
    AuthRepository {

  override suspend fun signIn(email: String, password: String): Result<User> {
    return try {
      val authResult = auth.signInWithEmailAndPassword(email, password).await()
      val firebaseUser = authResult.user ?: throw Exception("Sign in failed")
      Result.success(
          User(
              uid = firebaseUser.uid,
              email = firebaseUser.email ?: throw Exception("User email cannot be null"),
              username = firebaseUser.displayName ?: "",
          )
      )
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun signUp(email: String, password: String, username: String): Result<User> {
    return try {
      val authResult = auth.createUserWithEmailAndPassword(email, password).await()
      val firebaseUser = authResult.user ?: throw Exception("Sign up failed")

      val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(username).build()

      firebaseUser.updateProfile(profileUpdates).await()

      Result.success(
          User(
              uid = firebaseUser.uid,
              email = firebaseUser.email ?: throw Exception("User email cannot be null"),
              username = username,
          )
      )
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun signOut() {
    auth.signOut()
  }

  override fun getCurrentUser(): User {
    val firebaseUser = auth.currentUser ?: throw IllegalStateException("No user signed in")
    return User(
        uid = firebaseUser.uid,
        email = firebaseUser.email ?: throw IllegalStateException("User email cannot be null"),
        username = firebaseUser.displayName ?: "",
    )
  }
}
