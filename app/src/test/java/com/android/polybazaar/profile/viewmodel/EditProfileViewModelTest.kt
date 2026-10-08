package com.android.polybazaar.profile.viewmodel

import android.net.Uri
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.profile.model.ProfileRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
          photoUrl = "https://example.com/original.jpg",
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
    assertEquals(profile.photoUrl, viewModel.uiState.value.photoUrl)
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun missingProfile_exposesErrorAndLaterProfileRecovers() = runTest {
    repository.profiles.emit(null)
    testDispatcher.scheduler.runCurrent()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals("Profile not found", viewModel.uiState.value.errorMessage)
    assertNull(viewModel.uiState.value.profile)

    repository.profiles.emit(profile)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(profile, viewModel.uiState.value.profile)
    assertNull(viewModel.uiState.value.errorMessage)
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
    assertEquals("Profile is not available to save", viewModel.uiState.value.errorMessage)
    assertTrue(repository.operations.isEmpty())
    assertFalse(viewModel.uiState.value.isSaving)
  }

  @Test
  fun profileObservationFailure_usesExceptionMessage() = runTest {
    observeFailure(IllegalStateException("Load failed"))
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals("Load failed", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun profileObservationFailure_usesFallbackWhenExceptionHasNoMessage() = runTest {
    observeFailure(IllegalStateException())
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals("Unable to load profile", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun updateBio_clearsSavedAndErrorStatus() = runTest {
    loadProfile()
    repository.updateException = IllegalStateException("Write failed")
    viewModel.updateBio("Updated bio")
    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals("Write failed", viewModel.uiState.value.errorMessage)
    viewModel.updateBio("Try again")

    assertEquals("Try again", viewModel.uiState.value.bio)
    assertFalse(viewModel.uiState.value.isSaved)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun selectPhoto_clearsRemovalAndMakesPhotoUrlUnavailableUntilSaved() = runTest {
    loadProfile()
    viewModel.removeProfilePhoto()
    val photo = mock<Uri>()

    viewModel.selectProfilePhoto(photo)

    assertSame(photo, viewModel.uiState.value.selectedPhotoUri)
    assertFalse(viewModel.uiState.value.isPhotoRemoved)
    assertNull(viewModel.uiState.value.photoUrl)
  }

  @Test
  fun removePhoto_clearsSelectedPhotoAndMarksPhotoForRemoval() = runTest {
    loadProfile()
    viewModel.selectProfilePhoto(mock())

    viewModel.removeProfilePhoto()

    assertNull(viewModel.uiState.value.selectedPhotoUri)
    assertTrue(viewModel.uiState.value.isPhotoRemoved)
    assertNull(viewModel.uiState.value.photoUrl)
  }

  @Test
  fun save_withoutLoadedProfile_setsErrorAndDoesNotCallRepository() = runTest {
    repository.profiles.emit(null)
    testDispatcher.scheduler.runCurrent()

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals("Profile is not available to save", viewModel.uiState.value.errorMessage)
    assertNull(repository.updateCall)
    assertTrue(repository.operations.isEmpty())
  }

  @Test
  fun saveBioOnly_updatesBioAndPreservesExistingPhoto() = runTest {
    loadProfile()
    viewModel.updateBio("New bio")

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(UpdateCall(uid, photoUrl = null, bio = "New bio"), repository.updateCall)
    assertEquals(profile.copy(bio = "New bio"), viewModel.uiState.value.profile)
    assertEquals("New bio", viewModel.uiState.value.bio)
    assertEquals(profile.photoUrl, viewModel.uiState.value.photoUrl)
    assertNull(viewModel.uiState.value.selectedPhotoUri)
    assertFalse(viewModel.uiState.value.isPhotoRemoved)
    assertFalse(viewModel.uiState.value.isSaving)
    assertTrue(viewModel.uiState.value.isSaved)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun saveSelectedPhoto_uploadsBeforeUpdatingProfile() = runTest {
    loadProfile()
    val photo = mock<Uri>()
    repository.uploadedPhotoUrl = "https://example.com/new.jpg"
    viewModel.selectProfilePhoto(photo)
    viewModel.updateBio("Photo and bio")

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertSame(photo, repository.uploadCall?.second)
    assertEquals(uid, repository.uploadCall?.first)
    assertEquals(
        listOf("upload", "update"),
        repository.operations,
    )
    assertEquals(
        UpdateCall(uid, photoUrl = "https://example.com/new.jpg", bio = "Photo and bio"),
        repository.updateCall,
    )
    assertEquals("https://example.com/new.jpg", viewModel.uiState.value.photoUrl)
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
    assertEquals(UpdateCall(uid, photoUrl = null, bio = "No photo"), repository.updateCall)
    assertNull(viewModel.uiState.value.profile?.photoUrl)
    assertNull(viewModel.uiState.value.photoUrl)
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
  fun saveFailure_exposesMessageAndAllowsRetry() = runTest {
    loadProfile()
    viewModel.updateBio("Retry bio")
    repository.updateException = IllegalStateException("Write failed")

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isSaving)
    assertFalse(viewModel.uiState.value.isSaved)
    assertEquals("Write failed", viewModel.uiState.value.errorMessage)
    assertEquals("Retry bio", viewModel.uiState.value.bio)

    repository.updateException = null
    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value.isSaved)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun saveFailureWithoutMessage_usesFallbackMessage() = runTest {
    loadProfile()
    repository.updateException = IllegalStateException()

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals("Unable to save profile", viewModel.uiState.value.errorMessage)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  @Test
  fun uploadFailure_doesNotRemovePhotoOrUpdateProfile() = runTest {
    loadProfile()
    repository.uploadException = IllegalStateException("Upload failed")
    viewModel.selectProfilePhoto(mock())

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("upload"), repository.operations)
    assertNull(repository.updateCall)
    assertEquals("Upload failed", viewModel.uiState.value.errorMessage)
    assertFalse(viewModel.uiState.value.isSaving)
    assertFalse(viewModel.uiState.value.isSaved)
  }

  @Test
  fun removePhotoFailure_exposesErrorAfterProfileUpdate() = runTest {
    loadProfile()
    repository.removeException = IllegalStateException("Removal failed")
    viewModel.removeProfilePhoto()
    viewModel.updateBio("Bio saved before removal failed")

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("update", "remove"), repository.operations)
    assertEquals(
        UpdateCall(uid, photoUrl = null, bio = "Bio saved before removal failed"),
        repository.updateCall,
    )
    assertEquals("Bio saved before removal failed", viewModel.uiState.value.bio)
    assertEquals(
        "Bio saved before removal failed",
        viewModel.uiState.value.profile?.bio,
    )
    assertEquals(profile.photoUrl, viewModel.uiState.value.profile?.photoUrl)
    assertEquals(profile.photoUrl, viewModel.uiState.value.photoUrl)
    assertTrue(viewModel.uiState.value.isPhotoRemoved)
    assertFalse(viewModel.uiState.value.isSaved)
    assertTrue(viewModel.uiState.value.isPartiallySaved)
    assertEquals(
        "Bio was saved, but the profile photo could not be removed: Removal failed",
        viewModel.uiState.value.errorMessage,
    )
    assertFalse(viewModel.uiState.value.isSaving)
  }

  @Test
  fun profileUpdateFailure_doesNotRemovePhoto() = runTest {
    loadProfile()
    repository.updateException = IllegalStateException("Write failed")
    viewModel.removeProfilePhoto()
    viewModel.updateBio("No photo")

    viewModel.saveProfile()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("update"), repository.operations)
    assertEquals("Write failed", viewModel.uiState.value.errorMessage)
    assertTrue(viewModel.uiState.value.isPhotoRemoved)
    assertEquals(profile.photoUrl, viewModel.uiState.value.profile?.photoUrl)
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

  private fun observeFailure(exception: Exception) {
    repository = FakeProfileRepository().apply { observedProfiles = flow { throw exception } }
    viewModel = EditProfileViewModel(repository, uid)
  }

  private data class UpdateCall(val uid: String, val photoUrl: String?, val bio: String)

  private class FakeProfileRepository : ProfileRepository {
    val profiles = MutableSharedFlow<User?>(replay = 1)
    var observedProfiles: Flow<User?> = profiles
    var observedUid: String? = null
    var uploadCall: Pair<String, Uri>? = null
    var removedPhotoUid: String? = null
    var updateCall: UpdateCall? = null
    var updateCallCount = 0
    var operations = mutableListOf<String>()
    var uploadedPhotoUrl = "https://example.com/uploaded.jpg"
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
      return uploadedPhotoUrl
    }

    override suspend fun removeProfilePhoto(uid: String) {
      operations += "remove"
      removedPhotoUid = uid
      removeException?.let { throw it }
    }

    override suspend fun updateProfile(uid: String, photoUrl: String?, bio: String) {
      operations += "update"
      updateCall = UpdateCall(uid, photoUrl, bio)
      updateCallCount++
      updateGate?.await()
      updateException?.let { throw it }
    }
  }
}
