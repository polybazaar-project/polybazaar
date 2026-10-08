package com.android.polybazaar.auth.model

data class User(
    val uid: String,
    val email: String,
    val username: String,
    val photoUrl: String? = null,
    val bio: String = "",
    val isEmailVerified: Boolean = false,
)
