package com.android.polybazaar.auth.model

data class User(
    val uid: String,
    val email: String,
    val username: String,
    val photoUrl: String = DEFAULT_PROFILE_PHOTO_URL,
    val bio: String = "",
) {
  companion object {
    const val DEFAULT_PROFILE_PHOTO_URL =
        "android.resource://com.android.polybazaar/drawable/default_profile_photo"
  }
}
