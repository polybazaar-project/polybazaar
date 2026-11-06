// Co-authored-by: Claude Code (Claude Opus 5.5)
// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.profile.viewmodel

import android.net.Uri
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.profile.model.ProfileRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var repository: FakeProfileRepository
  private lateinit var viewModel: EditProfileViewModel

  private val uid = "user-123"
  private val profile =
      User(
          uid = uid,
          email = "user@example.com",
          username = "student",
          photoPath = "users/user-123/original.jpg",
          bio = "Original bio",
      )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = FakeProfileRepository()
    viewModel = EditProfileViewModel(repository, uid)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initialState_isLoadingAndObservesRequestedUid() = runTest {
    assertEquals(EditProfileUiState(), viewModel.uiState.value)
    testDispatcher.scheduler.runCurrent()
    assertEquals(uid, repository.observedUid)
    assertTrue(viewModel.uiState.value.isLoading)
  }

  @Test
  fun editActions_areIgnoredWhileProfileIsLoading() = runTest {
    val photo = mock<Uri>()

    viewModel.updateBio("Draft")
    viewModel.selectProfilePhoto(photo)
    viewModel.removeProfilePhoto()

    assertEquals(EditProfileUiState(), viewModel.uiState.value)
  }

  @Test
  fun observedProfile_populatesProfileAndInitialBio() = runTest {
    repository.profiles.emit(profile)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(profile, viewModel.uiState.value.profile)
    assertEquals(profile.bio, viewModel.uiState.value.bio)
    assertEquals(profile.photoPath, viewModel.uiState.value.photoPath)
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun missingProfile_isNotFoundAndLaterProfileClearsIt() = runTest {
    repository.profiles.emit(null)
    testDispatcher.scheduler.runCurrent()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(AppError.NOT_FOUND, viewModel.uiState.value.error)
    assertNull(viewModel.uiState.value.profile)

    repository.profiles.emit(profile)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(profile, viewModel.uiState.value.profile)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun profileBecomesMissingAfterLoad_invalidatesProfileAndBlocksSave() = runTest {
    loadProfile()
    viewModel.updateBio("Unsaved draft")

    repository.profiles.emit(null)
    testDispatcher.scheduler.runCurrent()
    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.uiState.value.profile)
    assertEquals(AppError.NOT_FOUND, viewModel.uiState.value.error)
    assertTrue(repository.operations.isEmpty())
    assertFalse(viewModel.uiState.value.isSaving)
  }

  @Test
  fun profileObservationFailure_unrecognised_isUnknown() = runTest {
    // Such as a profile missing its username: the internal text and uid are not shown.
    observeFailure(IllegalStateException("User $uid has no username"))
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(AppError.UNKNOWN, viewModel.uiState.value.error)
  }

  @Test
  fun profileObservationFailure_offline_isNetwork() = runTest {
    observeFailure(
        FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
    )
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
  }

  @Test
  fun profileObservationFailure_unknownHost_isNetwork() = runTest {
    observeFailure(UnknownHostException("firestore.googleapis.com"))
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
  }

  @Test
  fun updateBio_clearsSavedAndErrorStatus() = runTest {
    loadProfile()
    repository.updateException = IllegalStateException("Write failed")
    viewModel.updateBio("Updated bio")
    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(AppError.UNKNOWN, viewModel.uiState.value.error)
    viewModel.updateBio("Try again")

    assertEquals("Try again", viewModel.uiState.value.bio)
    assertFalse(viewModel.uiState.value.isSaved)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun updateBio_acceptsExactlyTheLimitAndTruncatesBeyondIt() = runTest {
    loadProfile()

    viewModel.updateBio("a".repeat(500))
    assertEquals("a".repeat(500), viewModel.uiState.value.bio)

    viewModel.updateBio("b".repeat(501))
    assertEquals("b".repeat(500), viewModel.uiState.value.bio)
  }

  @Test
  fun selectPhoto_clearsRemovalAndMakesPhotoUrlUnavailableUntilSaved() = runTest {
    loadProfile()
    viewModel.removeProfilePhoto()
    val photo = mock<Uri>()

    viewModel.selectProfilePhoto(photo)

    assertSame(photo, viewModel.uiState.value.selectedPhotoUri)
    assertFalse(viewModel.uiState.value.isPhotoRemoved)
    assertNull(viewModel.uiState.value.photoPath)
  }

  @Test
  fun removePhoto_clearsSelectedPhotoAndMarksPhotoForRemoval() = runTest {
    loadProfile()
    viewModel.selectProfilePhoto(mock())

    viewModel.removeProfilePhoto()

    assertNull(viewModel.uiState.value.selectedPhotoUri)
    assertTrue(viewModel.uiState.value.isPhotoRemoved)
    assertNull(viewModel.uiState.value.photoPath)
  }

  @Test
  fun save_withoutLoadedProfile_setsErrorAndDoesNotCallRepository() = runTest {
    repository.profiles.emit(null)
    testDispatcher.scheduler.runCurrent()

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(AppError.NOT_FOUND, viewModel.uiState.value.error)
    assertNull(repository.updateCall)
    assertTrue(repository.operations.isEmpty())
  }

  @Test
  fun saveBioOnly_updatesBioAndPreservesExistingPhoto() = runTest {
    loadProfile()
    viewModel.updateBio("New bio")

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isNewPhotoSaved)
    assertEquals(UpdateCall(uid, photoPath = null, bio = "New bio"), repository.updateCall)
    assertEquals(profile.copy(bio = "New bio"), viewModel.uiState.value.profile)
    assertEquals("New bio", viewModel.uiState.value.bio)
    assertEquals(profile.photoPath, viewModel.uiState.value.photoPath)
    assertNull(viewModel.uiState.value.selectedPhotoUri)
    assertFalse(viewModel.uiState.value.isPhotoRemoved)
    assertFalse(viewModel.uiState.value.isSaving)
    assertTrue(viewModel.uiState.value.isSaved)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun saveSelectedPhoto_uploadsBeforeUpdatingProfile() = runTest {
    loadProfile()
    val photo = mock<Uri>()
    repository.uploadedPhotoPath = "users/user-123/new.jpg"
    viewModel.selectProfilePhoto(photo)
    viewModel.updateBio("Photo and bio")

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value.isNewPhotoSaved)
    assertSame(photo, repository.uploadCall?.second)
    assertEquals(uid, repository.uploadCall?.first)
    assertEquals(
        listOf("upload", "update"),
        repository.operations,
    )
    assertEquals(
        UpdateCall(uid, photoPath = "users/user-123/new.jpg", bio = "Photo and bio"),
        repository.updateCall,
    )
    assertEquals("users/user-123/new.jpg", viewModel.uiState.value.photoPath)
    assertNull(viewModel.uiState.value.selectedPhotoUri)
    assertTrue(viewModel.uiState.value.isSaved)
  }

  @Test
  fun saveRemovedPhoto_updatesProfileBeforeRemovingPhoto() = runTest {
    loadProfile()
    viewModel.removeProfilePhoto()
    viewModel.updateBio("No photo")

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("update", "remove"), repository.operations)
    assertEquals(uid, repository.removedPhotoUid)
    assertEquals(UpdateCall(uid, photoPath = null, bio = "No photo"), repository.updateCall)
    assertNull(viewModel.uiState.value.profile?.photoPath)
    assertNull(viewModel.uiState.value.photoPath)
    assertFalse(viewModel.uiState.value.isPhotoRemoved)
    assertTrue(viewModel.uiState.value.isSaved)
  }

  @Test
  fun saveWhileAnotherSaveIsPending_doesNotStartDuplicateOrAllowEdits() = runTest {
    loadProfile()
    val updateGate = CompletableDeferred<Unit>()
    repository.updateGate = updateGate
    viewModel.updateBio("Saving bio")

    viewModel.saveProfile()
    testDispatcher.scheduler.runCurrent()

    assertTrue(viewModel.uiState.value.isSaving)
    viewModel.updateBio("Ignored bio")
    viewModel.selectProfilePhoto(mock())
    viewModel.removeProfilePhoto()
    viewModel.saveProfile()
    testDispatcher.scheduler.runCurrent()

    assertEquals("Saving bio", viewModel.uiState.value.bio)
    assertEquals(1, repository.updateCallCount)
    assertTrue(viewModel.uiState.value.isSaving)

    updateGate.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value.isSaved)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  @Test
  fun saveFailure_offline_isNetworkAndAllowsRetry() = runTest {
    loadProfile()
    viewModel.updateBio("Retry bio")
    repository.updateException =
        FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isSaving)
    assertFalse(viewModel.uiState.value.isSaved)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    assertEquals("Retry bio", viewModel.uiState.value.bio)

    repository.updateException = null
    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value.isSaved)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun saveFailure_unrecognised_isUnknown() = runTest {
    loadProfile()
    repository.updateException = IllegalArgumentException("Bio for $uid is invalid")

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(AppError.UNKNOWN, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  @Test
  fun uploadFailure_doesNotRemovePhotoOrUpdateProfile() = runTest {
    loadProfile()
    repository.uploadException = UnknownHostException("firebasestorage.googleapis.com")
    viewModel.selectProfilePhoto(mock())

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("upload"), repository.operations)
    assertNull(repository.updateCall)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isSaving)
    assertFalse(viewModel.uiState.value.isSaved)
  }

  @Test
  fun removePhotoFailure_isPartiallySavedWithoutAnError() = runTest {
    loadProfile()
    repository.removeException = IllegalStateException("Removal failed")
    viewModel.removeProfilePhoto()
    viewModel.updateBio("Bio saved before removal failed")

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("update", "remove"), repository.operations)
    assertEquals(
        UpdateCall(uid, photoPath = null, bio = "Bio saved before removal failed"),
        repository.updateCall,
    )
    assertEquals("Bio saved before removal failed", viewModel.uiState.value.bio)
    assertEquals(
        "Bio saved before removal failed",
        viewModel.uiState.value.profile?.bio,
    )
    assertEquals(profile.photoPath, viewModel.uiState.value.profile?.photoPath)
    assertEquals(profile.photoPath, viewModel.uiState.value.photoPath)
    assertTrue(viewModel.uiState.value.isPhotoRemoved)
    assertFalse(viewModel.uiState.value.isSaved)
    assertTrue(viewModel.uiState.value.isPartiallySaved)
    assertNull(viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  @Test
  fun saveAfterPartialSave_retriesThePhotoRemoval() = runTest {
    partiallySave()
    repository.removeException = null
    repository.operations.clear()

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("update", "remove"), repository.operations)
    assertEquals(UpdateCall(uid, photoPath = null, bio = "Kept bio"), repository.updateCall)
    assertTrue(viewModel.uiState.value.isSaved)
    assertFalse(viewModel.uiState.value.isPartiallySaved)
    assertFalse(viewModel.uiState.value.isPhotoRemoved)
    assertNull(viewModel.uiState.value.profile?.photoPath)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun retryAfterPartialSave_thatFailsBeforeTheRemoval_hasTheError() = runTest {
    partiallySave()
    repository.updateException = UnknownHostException("offline")
    repository.operations.clear()

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("update"), repository.operations)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    // Still set, so the screen has to show the error first.
    assertTrue(viewModel.uiState.value.isPartiallySaved)
    assertTrue(viewModel.uiState.value.isPhotoRemoved)
    assertEquals("Kept bio", viewModel.uiState.value.bio)
  }

  @Test
  fun saveStart_clearsThePreviousError() = runTest {
    failSave()
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    repository.updateGate = CompletableDeferred()

    viewModel.saveProfile()
    testDispatcher.scheduler.runCurrent()

    assertTrue(viewModel.uiState.value.isSaving)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun selectPhoto_clearsTheError() = runTest {
    failSave()

    viewModel.selectProfilePhoto(mock())

    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun selectPhoto_clearsThePartialSave() = runTest {
    partiallySave()

    viewModel.selectProfilePhoto(mock())

    assertFalse(viewModel.uiState.value.isPartiallySaved)
  }

  @Test
  fun removePhoto_clearsTheError() = runTest {
    failSave()

    viewModel.removeProfilePhoto()

    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun removePhoto_clearsThePartialSave() = runTest {
    partiallySave()

    viewModel.removeProfilePhoto()

    assertFalse(viewModel.uiState.value.isPartiallySaved)
  }

  @Test
  fun updateBio_clearsThePartialSave() = runTest {
    partiallySave()

    viewModel.updateBio("Another bio")

    assertFalse(viewModel.uiState.value.isPartiallySaved)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun arrivingProfile_clearsAnError() = runTest {
    failSave()
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)

    repository.profiles.emit(profile.copy(username = "renamed"))
    testDispatcher.scheduler.runCurrent()

    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun profileUpdateFailure_doesNotRemovePhoto() = runTest {
    loadProfile()
    repository.updateException =
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    viewModel.removeProfilePhoto()
    viewModel.updateBio("No photo")

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("update"), repository.operations)
    assertEquals(AppError.PERMISSION_DENIED, viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.isPhotoRemoved)
    assertEquals(profile.photoPath, viewModel.uiState.value.profile?.photoPath)
    assertFalse(viewModel.uiState.value.isSaving)
    assertFalse(viewModel.uiState.value.isSaved)
  }

  @Test
  fun profileRefresh_updatesCleanStateButPreservesUnsavedDraft() = runTest {
    loadProfile()
    repository.profiles.emit(profile.copy(bio = "Server bio"))
    testDispatcher.scheduler.runCurrent()
    assertEquals("Server bio", viewModel.uiState.value.bio)

    viewModel.updateBio("Local draft")
    val refreshedProfile = profile.copy(username = "updated-name", bio = "New server bio")
    repository.profiles.emit(refreshedProfile)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(refreshedProfile, viewModel.uiState.value.profile)
    assertEquals("Local draft", viewModel.uiState.value.bio)
  }

  private suspend fun loadProfile() {
    repository.profiles.emit(profile)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  /** Loads the profile, then saves a bio whose photo removal fails. */
  private suspend fun partiallySave() {
    loadProfile()
    repository.removeException = UnknownHostException("offline")
    viewModel.removeProfilePhoto()
    viewModel.updateBio("Kept bio")
    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()
  }

  /** Loads the profile, then runs a save that fails offline before anything is written. */
  private suspend fun failSave() {
    loadProfile()
    repository.updateException = UnknownHostException("offline")
    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()
    repository.updateException = null
  }

  private fun observeFailure(exception: Exception) {
    repository = FakeProfileRepository().apply { observedProfiles = flow { throw exception } }
    viewModel = EditProfileViewModel(repository, uid)
  }

  private data class UpdateCall(val uid: String, val photoPath: String?, val bio: String)

  private class FakeProfileRepository : ProfileRepository {
    val profiles = MutableSharedFlow<User?>(replay = 1)
    var observedProfiles: Flow<User?> = profiles
    var observedUid: String? = null
    var uploadCall: Pair<String, Uri>? = null
    var removedPhotoUid: String? = null
    var updateCall: UpdateCall? = null
    var updateCallCount = 0
    var operations = mutableListOf<String>()
    var uploadedPhotoPath = "users/user-123/uploaded.jpg"
    var uploadException: Exception? = null
    var removeException: Exception? = null
    var updateException: Exception? = null
    var updateGate: CompletableDeferred<Unit>? = null

    override fun observeProfile(uid: String): Flow<User?> {
      observedUid = uid
      return observedProfiles
    }

    override suspend fun uploadProfilePhoto(uid: String, photoUri: Uri): String {
      operations += "upload"
      uploadCall = uid to photoUri
      uploadException?.let { throw it }
      return uploadedPhotoPath
    }

    override suspend fun loadProfilePhoto(photoPath: String) = ByteArray(0)

    override suspend fun removeProfilePhoto(uid: String) {
      operations += "remove"
      removedPhotoUid = uid
      removeException?.let { throw it }
    }

    override suspend fun updateProfile(uid: String, photoPath: String?, bio: String) {
      operations += "update"
      updateCall = UpdateCall(uid, photoPath, bio)
      updateCallCount++
      updateGate?.await()
      updateException?.let { throw it }
    }
  }
}
