// Co-authored-by: Claude Code (Claude Opus 5.5)
// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.profile.model

import android.content.ContentResolver
import android.net.Uri
import com.android.polybazaar.auth.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseProfileRepository(
    private val contentResolver: ContentResolver,
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
    // The Storage rules only accept images, so the upload has to state the photo's type.
    val metadata =
        StorageMetadata.Builder().setContentType(contentResolver.getType(photoUri)).build()
    photoReference.putFile(photoUri, metadata).await()
    // The path, not a download URL: a download URL works for anyone who has it, signed in or not.
    return profilePhotoPath(uid)
  }

  override suspend fun loadProfilePhoto(photoPath: String): ByteArray =
      storage.reference.child(photoPath).getBytes(MAX_PHOTO_BYTES).await()

  override suspend fun removeProfilePhoto(uid: String) {
    val profile = profileDocument(uid).get().await()
    if (profile.getString("photoPath") == null) return

    profilePhoto(uid).delete().await()
    profileDocument(uid).update("photoPath", FieldValue.delete()).await()
  }

  override suspend fun updateProfile(
      uid: String,
      photoPath: String?,
      bio: String,
  ) {
    require(bio.length <= ProfileRules.MAX_BIO_LENGTH) { ProfileRules.BIO_TOO_LONG_MESSAGE }
    val updates = mutableMapOf<String, Any?>()
    if (photoPath != null) updates["photoPath"] = photoPath
    // Stored as typed: spacing and line breaks are part of how the user wrote the bio.
    updates["bio"] = bio

    val data = updates.filterValues { it != null }.mapValues { (_, value) -> value as Any }

    profileDocument(uid)
        .set(
            data,
            SetOptions.merge(),
        )
        .await()
  }

  private fun profileDocument(uid: String) = firestore.collection("users").document(uid)

  private fun profilePhotoPath(uid: String) = "users/$uid/profile.jpg"

  private fun profilePhoto(uid: String) = storage.reference.child(profilePhotoPath(uid))

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
        photoPath = getString("photoPath"),
        bio = getString("bio") ?: "",
    )
  }

  private companion object {
    // The largest photo the Storage rules accept.
    const val MAX_PHOTO_BYTES = 10L * 1024 * 1024
  }
}
