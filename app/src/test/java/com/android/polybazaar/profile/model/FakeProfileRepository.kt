// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.profile.model

import android.net.Uri
import com.android.polybazaar.auth.model.User
import java.net.UnknownHostException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A [ProfileRepository] for tests.
 *
 * [observeProfile] emits the user with that uid from [users], or null when there is none, like the
 * real repository for a missing profile. For a uid in [failingUids] it fails with a network error
 * instead. [lookups] counts how many times each uid's profile was collected. The other calls do
 * nothing.
 */
class FakeProfileRepository(private val users: Map<String, User> = emptyMap()) : ProfileRepository {
  val failingUids = mutableSetOf<String>()

  private val lookupCounts = mutableMapOf<String, Int>()

  /** How many times each uid's profile was collected. */
  val lookups: Map<String, Int>
    get() = lookupCounts

  override fun observeProfile(uid: String): Flow<User?> = flow {
    lookupCounts[uid] = (lookupCounts[uid] ?: 0) + 1
    if (uid in failingUids) throw UnknownHostException("offline")
    emit(users[uid])
  }

  override suspend fun uploadProfilePhoto(uid: String, photoUri: Uri) = ""

  override suspend fun loadProfilePhoto(photoPath: String) = ByteArray(0)

  override suspend fun removeProfilePhoto(uid: String) = Unit

  override suspend fun updateProfile(uid: String, photoPath: String?, bio: String) = Unit
}
