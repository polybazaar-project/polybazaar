// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.model

data class User(
    val uid: String,
    /** Empty when read from another user's public profile, which does not store it. */
    val email: String,
    val username: String,
    /** Storage path of the profile photo, read with ProfileRepository.loadProfilePhoto. */
    val photoPath: String? = null,
    val bio: String = "",
    /** Only set by [AuthRepository], for the signed-in user. Other sources leave it false. */
    val isEmailVerified: Boolean = false,
)
