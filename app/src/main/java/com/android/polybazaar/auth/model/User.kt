package com.android.polybazaar.auth.model

data class User(
    val uid: String,
    val email: String,
    val username: String,
    val photoUrl: String? = null,
    val bio: String = "",
    /** Only set by [AuthRepository], for the signed-in user. Other sources leave it false. */
    val isEmailVerified: Boolean = false,
)
