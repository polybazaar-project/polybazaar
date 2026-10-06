package com.android.polybazaar.model

import com.google.firebase.auth.FirebaseAuth
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

      try {
        val usernameData =
            mapOf(
                "uid" to firebaseUser.uid,
                "username" to username,
            )
        usernameDocRef.set(usernameData).await()

        Result.success(
            User(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: throw Exception("User email cannot be null"),
                username = username,
            )
        )
      } catch (e: Exception) {
        withContext(NonCancellable) {
          try {
            firebaseUser.delete().await()
          } catch (rollbackEx: Exception) {
            e.addSuppressed(rollbackEx)
          }
        }
        throw Exception("Failed to register username. Account creation rolled back.", e)
      }
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
    )
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
