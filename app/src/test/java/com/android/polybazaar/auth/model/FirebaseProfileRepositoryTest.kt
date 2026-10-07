package com.android.polybazaar.model

import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FirebaseProfileRepositoryTest {

  private lateinit var firestore: FirebaseFirestore
  private lateinit var storage: FirebaseStorage
  private lateinit var auth: FirebaseAuth
  private lateinit var usersCollection: CollectionReference
  private lateinit var profileDocument: DocumentReference
  private lateinit var storageRoot: StorageReference
  private lateinit var photoReference: StorageReference
  private lateinit var repository: FirebaseProfileRepository

  private val uid = "user-123"
  private val email = "user@example.com"
  private val username = "student"

  @Before
  fun setup() {
    firestore = mock()
    storage = mock()
    auth = mock()
    usersCollection = mock()
    profileDocument = mock()
    storageRoot = mock()
    photoReference = mock()

    whenever(firestore.collection("users")).thenReturn(usersCollection)
    whenever(usersCollection.document(uid)).thenReturn(profileDocument)
    whenever(storage.reference).thenReturn(storageRoot)
    whenever(storageRoot.child("users/$uid/profile.jpg")).thenReturn(photoReference)

    repository = FirebaseProfileRepository(firestore, storage, auth)
  }

  @Test
  fun observeProfile_existingDocument_emitsMappedUser() = runTest {
    // Confirms Firestore profile fields are mapped into the app's User model.
    val snapshot = mock<DocumentSnapshot>()
    val listenerCaptor =
        argumentCaptor<com.google.firebase.firestore.EventListener<DocumentSnapshot>>()
    whenever(profileDocument.addSnapshotListener(listenerCaptor.capture())).thenReturn(mock())
    whenever(snapshot.exists()).thenReturn(true)
    whenever(snapshot.getString("email")).thenReturn(email)
    whenever(snapshot.getString("username")).thenReturn(username)
    whenever(snapshot.getString("photoUrl")).thenReturn("https://example.com/photo.jpg")
    whenever(snapshot.getString("bio")).thenReturn("Hello")

    val observed = async { repository.observeProfile(uid).first() }
    runCurrent()
    listenerCaptor.firstValue.onEvent(snapshot, null)

    assertEquals(
        User(
            uid = uid,
            email = email,
            username = username,
            photoUrl = "https://example.com/photo.jpg",
            bio = "Hello",
        ),
        observed.await(),
    )
  }

  @Test
  fun observeProfile_missingDocument_emitsNull() = runTest {
    // Confirms a user with no Firestore profile document is reported as absent.
    val snapshot = mock<DocumentSnapshot>()
    val listenerCaptor =
        argumentCaptor<com.google.firebase.firestore.EventListener<DocumentSnapshot>>()
    whenever(profileDocument.addSnapshotListener(listenerCaptor.capture())).thenReturn(mock())
    whenever(snapshot.exists()).thenReturn(false)

    val observed = async { repository.observeProfile(uid).first() }
    runCurrent()
    listenerCaptor.firstValue.onEvent(snapshot, null)

    assertNull(observed.await())
  }

  @Test
  fun observeProfile_usesAuthenticatedEmailAndDefaultsForOptionalFields() = runTest {
    // Confirms missing email and optional fields use the authenticated user's data and defaults.
    val snapshot = mock<DocumentSnapshot>()
    val firebaseUser = mock<FirebaseUser>()
    val listenerCaptor =
        argumentCaptor<com.google.firebase.firestore.EventListener<DocumentSnapshot>>()
    whenever(profileDocument.addSnapshotListener(listenerCaptor.capture())).thenReturn(mock())
    whenever(snapshot.exists()).thenReturn(true)
    whenever(snapshot.getString("email")).thenReturn(null)
    whenever(snapshot.getString("username")).thenReturn(username)
    whenever(snapshot.getString("photoUrl")).thenReturn(null)
    whenever(snapshot.getString("bio")).thenReturn(null)
    whenever(auth.currentUser).thenReturn(firebaseUser)
    whenever(firebaseUser.uid).thenReturn(uid)
    whenever(firebaseUser.email).thenReturn(email)

    val observed = async { repository.observeProfile(uid).first() }
    runCurrent()
    listenerCaptor.firstValue.onEvent(snapshot, null)

    assertEquals(
        User(
            uid = uid,
            email = email,
            username = username,
            photoUrl = User.DEFAULT_PROFILE_PHOTO_URL,
            bio = "",
        ),
        observed.await(),
    )
  }

  @Test
  fun uploadProfilePhoto_success_returnsDownloadUrl() = runTest {
    // Confirms a completed upload returns the photo's Firebase download URL.
    val photoUri = mock<Uri>()
    val uploadTask = mock<UploadTask>()
    val uploadSnapshot = mock<UploadTask.TaskSnapshot>()
    val downloadUri = mock<Uri>()
    val downloadUrl = "https://example.com/profile.jpg"
    whenever(photoReference.putFile(photoUri)).thenReturn(uploadTask)
    whenever(uploadTask.isComplete).thenReturn(true)
    whenever(uploadTask.isSuccessful).thenReturn(true)
    whenever(uploadTask.result).thenReturn(uploadSnapshot)
    whenever(photoReference.downloadUrl).thenReturn(Tasks.forResult(downloadUri))
    whenever(downloadUri.toString()).thenReturn(downloadUrl)

    val result = repository.uploadProfilePhoto(uid, photoUri)

    assertEquals(downloadUrl, result)
    verify(storageRoot).child("users/$uid/profile.jpg")
    verify(photoReference).putFile(photoUri)
  }

  @Test
  fun uploadProfilePhoto_storageFails_propagatesFailure() = runTest {
    // Confirms upload failures reach the caller and the user's Storage path is used.
    val photoUri = mock<Uri>()
    val failure = IllegalStateException("Storage unavailable")
    whenever(photoReference.putFile(photoUri)).thenThrow(failure)

    val result = runCatching { repository.uploadProfilePhoto(uid, photoUri) }.exceptionOrNull()

    assertTrue(result === failure)
    verify(storageRoot).child("users/$uid/profile.jpg")
    verify(photoReference).putFile(photoUri)
  }

  @Test
  fun removeProfilePhoto_deletesPhotoAndResetsFirestoreUrl() = runTest {
    // Confirms removing a photo also resets its Firestore URL to the default image.
    whenever(photoReference.delete()).thenReturn(Tasks.forResult(null))
    whenever(profileDocument.set(any<Map<String, Any>>(), any<SetOptions>()))
        .thenReturn(Tasks.forResult(null))

    repository.removeProfilePhoto(uid)

    verify(photoReference).delete()
    verify(profileDocument)
        .set(
            eq(mapOf("photoUrl" to User.DEFAULT_PROFILE_PHOTO_URL)),
            any<SetOptions>(),
        )
  }

  @Test
  fun updateProfile_mergesProfileFieldsIntoFirestore() = runTest {
    // Confirms profile edits update the intended fields without replacing the whole document.
    whenever(profileDocument.set(any<Map<String, Any>>(), any<SetOptions>()))
        .thenReturn(Tasks.forResult(null))

    repository.updateProfile(uid, "https://example.com/photo.jpg", "Hello", username)

    verify(profileDocument)
        .set(
            eq(
                mapOf(
                    "photoUrl" to "https://example.com/photo.jpg",
                    "bio" to "Hello",
                    "username" to username,
                )
            ),
            any<SetOptions>(),
        )
  }

  @Test
  fun observeProfile_firestoreError_isPropagated() = runTest {
    // Confirms Firestore listener failures reach the profile observer instead of being hidden.
    val listenerCaptor =
        argumentCaptor<com.google.firebase.firestore.EventListener<DocumentSnapshot>>()
    whenever(profileDocument.addSnapshotListener(listenerCaptor.capture())).thenReturn(mock())
    val failure = mock<FirebaseFirestoreException>()

    val observed = async {
      runCatching { repository.observeProfile(uid).first() }.exceptionOrNull()
    }
    runCurrent()
    listenerCaptor.firstValue.onEvent(null, failure)

    assertTrue(observed.await() === failure)
  }

  @Test
  fun observeProfile_nullSnapshot_emitsError() = runTest {
    // Confirms a null Firestore snapshot is surfaced as an explicit error.
    val listenerCaptor =
        argumentCaptor<com.google.firebase.firestore.EventListener<DocumentSnapshot>>()
    whenever(profileDocument.addSnapshotListener(listenerCaptor.capture())).thenReturn(mock())

    val observed = async {
      runCatching { repository.observeProfile(uid).first() }.exceptionOrNull()
    }
    runCurrent()
    listenerCaptor.firstValue.onEvent(null, null)

    assertEquals("Profile snapshot was null", observed.await()?.message)
  }

  @Test
  fun observeProfile_emailUnavailable_emitsError() = runTest {
    // Confirms a profile without email fails when no matching authenticated user can supply it.
    val snapshot = mock<DocumentSnapshot>()
    val listenerCaptor =
        argumentCaptor<com.google.firebase.firestore.EventListener<DocumentSnapshot>>()
    whenever(profileDocument.addSnapshotListener(listenerCaptor.capture())).thenReturn(mock())
    whenever(snapshot.exists()).thenReturn(true)
    whenever(snapshot.getString("email")).thenReturn(null)
    whenever(auth.currentUser).thenReturn(null)

    val observed = async {
      runCatching { repository.observeProfile(uid).first() }.exceptionOrNull()
    }
    runCurrent()
    listenerCaptor.firstValue.onEvent(snapshot, null)

    assertEquals("Email is unavailable for profile $uid", observed.await()?.message)
  }

  @Test
  fun observeProfile_usernameMissing_emitsError() = runTest {
    // Confirms a profile without the required username is reported as an error.
    val snapshot = mock<DocumentSnapshot>()
    val listenerCaptor =
        argumentCaptor<com.google.firebase.firestore.EventListener<DocumentSnapshot>>()
    whenever(profileDocument.addSnapshotListener(listenerCaptor.capture())).thenReturn(mock())
    whenever(snapshot.exists()).thenReturn(true)
    whenever(snapshot.getString("email")).thenReturn(email)
    whenever(snapshot.getString("username")).thenReturn(null)

    val observed = async {
      runCatching { repository.observeProfile(uid).first() }.exceptionOrNull()
    }
    runCurrent()
    listenerCaptor.firstValue.onEvent(snapshot, null)

    assertEquals("Username is missing from profile $uid", observed.await()?.message)
  }
}
