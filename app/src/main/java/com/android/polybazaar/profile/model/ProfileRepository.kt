// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.profile.model

import android.net.Uri
import com.android.polybazaar.auth.model.User
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {

  fun observeProfile(uid: String): Flow<User?>

  /** Uploads the photo and returns its Storage path, to save with [updateProfile]. */
  suspend fun uploadProfilePhoto(uid: String, photoUri: Uri): String

  /** Downloads a photo saved with [updateProfile], as the signed-in user. */
  suspend fun loadProfilePhoto(photoPath: String): ByteArray

  suspend fun removeProfilePhoto(uid: String)

  suspend fun updateProfile(
      uid: String,
      photoPath: String? = null,
      bio: String = "",
  )
}
