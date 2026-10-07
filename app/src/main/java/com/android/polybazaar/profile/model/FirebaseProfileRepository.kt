package com.android.polybazaar.profile.model

import android.net.Uri
import com.android.polybazaar.auth.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseProfileRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : ProfileRepository {

  override fun observeProfile(uid: String): Flow<User?> = callbackFlow {
    val listener =
        profileDocument(uid).addSnapshotListener { snapshot, error ->
          when {
            error != null -> close(error)
            snapshot == null -> close(IllegalStateException("Profile snapshot was null"))
            !snapshot.exists() -> trySend(null)
            else -> {
              try {
                val result = trySend(snapshot.toUser(uid))
                result.exceptionOrNull()?.let { close(it) }
              } catch (e: Exception) {
                close(e)
              }
            }
          }
        }

    awaitClose { listener.remove() }
  }

  override suspend fun uploadProfilePhoto(uid: String, photoUri: Uri): String {
    val photoReference = profilePhoto(uid)
    photoReference.putFile(photoUri).await()
    return photoReference.downloadUrl.await().toString()
  }

  override suspend fun removeProfilePhoto(uid: String) {
    profilePhoto(uid).delete().await()
    profileDocument(uid)
        .set(
            mapOf("photoUrl" to User.DEFAULT_PROFILE_PHOTO_URL),
            SetOptions.merge(),
        )
        .await()
  }

  override suspend fun updateProfile(
      uid: String,
      photoUrl: String,
      bio: String,
      username: String,
  ) {
    profileDocument(uid)
        .set(
            mapOf(
                "photoUrl" to photoUrl,
                "bio" to bio,
                "username" to username,
            ),
            SetOptions.merge(),
        )
        .await()
  }

  private fun profileDocument(uid: String) = firestore.collection("users").document(uid)

  private fun profilePhoto(uid: String) = storage.reference.child("users/$uid/profile.jpg")

  private fun com.google.firebase.firestore.DocumentSnapshot.toUser(uid: String): User {
    val email =
        getString("email")
            ?: auth.currentUser?.takeIf { it.uid == uid }?.email
            ?: throw IllegalStateException("Email is unavailable for profile $uid")
    val username =
        getString("username")
            ?: throw IllegalStateException("Username is missing from profile $uid")

    return User(
        uid = uid,
        email = email,
        username = username,
        photoUrl = getString("photoUrl") ?: User.DEFAULT_PROFILE_PHOTO_URL,
        bio = getString("bio") ?: "",
    )
  }
}
