// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.profile.model

import android.net.Uri
import com.android.polybazaar.auth.model.User
import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A [ProfileRepository] for tests.
 *
 * [observeProfile] emits the user with that uid from [users], or null when there is none, like the
 * real repository for a missing profile. For a uid in [failingUids] it fails with [failure]
 * instead, a network error unless the test sets another. [lookups] counts how many times each uid's
 * profile was collected. Set [gate] to keep every lookup waiting until the test completes it, to
 * look at one in progress. [loadProfilePhoto] returns an empty photo, counted per path in
 * [photoLoads]. It waits for [photoGate] when set, and fails with [failure] for a path in
 * [failingPhotoPaths]. The other calls do nothing.
 */
class FakeProfileRepository(private val users: Map<String, User> = emptyMap()) : ProfileRepository {
  val failingUids = mutableSetOf<String>()
  var gate: CompletableDeferred<Unit>? = null
  val failingPhotoPaths = mutableSetOf<String>()
  var photoGate: CompletableDeferred<Unit>? = null
  var failure: Exception = UnknownHostException("offline")

  private val lookupCounts = mutableMapOf<String, Int>()
  private val photoLoadCounts = mutableMapOf<String, Int>()

  /** How many times each uid's profile was collected. */
  val lookups: Map<String, Int>
    get() = lookupCounts

  /** How many times each photo path was downloaded. */
  val photoLoads: Map<String, Int>
    get() = photoLoadCounts

  override fun observeProfile(uid: String): Flow<User?> = flow {
    lookupCounts[uid] = (lookupCounts[uid] ?: 0) + 1
    gate?.await()
    if (uid in failingUids) throw failure
    emit(users[uid])
  }

  override suspend fun uploadProfilePhoto(uid: String, photoUri: Uri) = ""

  override suspend fun loadProfilePhoto(photoPath: String): ByteArray {
    photoLoadCounts[photoPath] = (photoLoadCounts[photoPath] ?: 0) + 1
    photoGate?.await()
    if (photoPath in failingPhotoPaths) throw failure
    return ByteArray(0)
  }

  override suspend fun removeProfilePhoto(uid: String) = Unit

  override suspend fun updateProfile(uid: String, photoPath: String?, bio: String) = Unit
}
