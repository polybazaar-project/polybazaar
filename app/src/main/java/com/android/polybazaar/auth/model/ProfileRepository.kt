package com.android.polybazaar.model

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {

  fun observeProfile(uid: String): Flow<User?>

  suspend fun uploadProfilePhoto(uid: String, photoUri: Uri): String

  suspend fun removeProfilePhoto(uid: String)

  suspend fun updateProfile(
      uid: String,
      photoUrl: String,
      bio: String,
      username: String,
  )
}
