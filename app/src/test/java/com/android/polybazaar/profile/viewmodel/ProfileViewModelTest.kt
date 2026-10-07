package com.android.polybazaar.profile.viewmodel

import android.net.Uri
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.profile.model.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var repository: FakeProfileRepository
  private lateinit var viewModel: ProfileViewModel

  private val profile =
      User(
          uid = "user-1",
          email = "user@example.com",
          username = "Student",
          photoUrl = "https://example.com/profile.jpg",
          bio = "Available for tutoring",
      )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = FakeProfileRepository()
    viewModel = ProfileViewModel(repository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initialState_isIdleWithoutProfile() {
    // Confirms the ViewModel starts with no profile, loading, or error.
    assertEquals(ProfileUiState(), viewModel.uiState.value)
  }

  @Test
  fun observeProfile_emitsRepositoryProfile() = runTest {
    // Confirms a profile from the repository is exposed as ready UI state.
    repository.profiles.value = profile

    viewModel.observeProfile(profile.uid)
    testDispatcher.scheduler.runCurrent()

    assertEquals(profile.uid, repository.observedUid)
    assertEquals(profile, viewModel.uiState.value.profile)
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun observeProfile_showsLoadingWhileWaitingForEmission() = runTest {
    // Confirms loading remains active while the profile flow has not emitted a value.
    repository.observationFlow = flow {
      suspendCancellableCoroutine<Unit> { continuation -> continuation.invokeOnCancellation {} }
    }

    viewModel.observeProfile(profile.uid)
    testDispatcher.scheduler.runCurrent()

    assertTrue(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.profile)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun observeProfile_nullEmissionClearsDisplayedProfile() = runTest {
    // Confirms a null repository emission clears a previously displayed profile.
    repository.profiles.value = profile
    viewModel.observeProfile(profile.uid)
    testDispatcher.scheduler.runCurrent()
    assertEquals(profile, viewModel.uiState.value.profile)

    repository.profiles.value = null
    testDispatcher.scheduler.runCurrent()

    assertNull(viewModel.uiState.value.profile)
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun observeProfile_exposesRepositoryFailure() = runTest {
    // Confirms observation failures stop loading and provide an error message.
    repository.observationFlow = flow { throw IllegalStateException("Profile unavailable") }

    viewModel.observeProfile(profile.uid)
    testDispatcher.scheduler.runCurrent()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals("Profile unavailable", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun observeProfile_cancelsPreviousObservation() = runTest {
    // Confirms starting a new observation cancels the previous user's profile flow.
    val firstUid = "first-user"
    repository.observationFlowForUid = { uid ->
      if (uid == firstUid) {
        flow {
          suspendCancellableCoroutine<Unit> { continuation ->
            continuation.invokeOnCancellation { repository.cancelledObservationUids += uid }
          }
        }
      } else {
        MutableStateFlow<User?>(profile)
      }
    }

    viewModel.observeProfile(firstUid)
    testDispatcher.scheduler.runCurrent()
    viewModel.observeProfile(profile.uid)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf(firstUid), repository.cancelledObservationUids)
    assertEquals(profile, viewModel.uiState.value.profile)
  }

  private class FakeProfileRepository : ProfileRepository {
    val profiles = MutableStateFlow<User?>(null)
    var observationFlow: Flow<User?> = profiles
    var observationFlowForUid: (String) -> Flow<User?> = { observationFlow }
    val cancelledObservationUids = mutableListOf<String>()
    var observedUid: String? = null

    override fun observeProfile(uid: String): Flow<User?> {
      observedUid = uid
      return observationFlowForUid(uid)
    }

    override suspend fun uploadProfilePhoto(uid: String, photoUri: Uri) = ""

    override suspend fun removeProfilePhoto(uid: String) = Unit

    override suspend fun updateProfile(
        uid: String,
        photoUrl: String,
        bio: String,
        username: String,
    ) = Unit
  }
}
