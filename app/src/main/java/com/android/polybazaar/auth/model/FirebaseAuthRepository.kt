package com.android.polybazaar.auth.model

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
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

      val authResult = auth.createUserWithEmailAndPassword(email, password).await()
      val firebaseUser = authResult.user ?: throw Exception("Failed to create user account")

      try {
        reserveUsername(usernameDocRef, firebaseUser.uid, username)

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
        if (e is CancellationException || e is UsernameAlreadyTakenException) throw e
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

  private suspend fun reserveUsername(
      usernameDocRef: DocumentReference,
      uid: String,
      username: String,
  ) {
    firestore
        .runTransaction { transaction ->
          if (transaction.get(usernameDocRef).exists()) {
            throw UsernameAlreadyTakenException()
          }
          transaction.set(usernameDocRef, mapOf("uid" to uid, "username" to username))
        }
        .await()
  }

  private class UsernameAlreadyTakenException : Exception("Username is already taken")
}
