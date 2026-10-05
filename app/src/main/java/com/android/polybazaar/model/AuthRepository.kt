package com.android.polybazaar.model

interface AuthRepository {

  suspend fun signIn(email: String, password: String): Result<User>

  suspend fun signUp(email: String, password: String, username: String): Result<User>

  suspend fun signOut()

  suspend fun getCurrentUser(): User
}
