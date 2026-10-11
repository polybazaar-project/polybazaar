// Co-authored-by: Copilot GPT-5.6 Terra
package com.android.polybazaar.listing.model

import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FirebaseListingRepositoryTest {

  private lateinit var firestore: FirebaseFirestore
  private lateinit var storage: FirebaseStorage
  private lateinit var auth: FirebaseAuth
  private lateinit var listings: CollectionReference
  private lateinit var document: DocumentReference
  private lateinit var storageRoot: StorageReference
  private lateinit var imageReference: StorageReference
  private lateinit var repository: FirebaseListingRepository

  private val uid = "user-123"
  private val listingId = "listing-456"

  @Before
  fun setUp() {
    firestore = mock()
    storage = mock()
    auth = mock()
    listings = mock()
    document = mock()
    storageRoot = mock()
    imageReference = mock()

    val user = mock<FirebaseUser>()
    whenever(user.uid).thenReturn(uid)
    whenever(auth.currentUser).thenReturn(user)
    whenever(firestore.collection("listings")).thenReturn(listings)
    whenever(listings.document()).thenReturn(document)
    whenever(document.id).thenReturn(listingId)
    whenever(storage.reference).thenReturn(storageRoot)
    whenever(storageRoot.child("listings/$uid/$listingId/images/0")).thenReturn(imageReference)

    repository = FirebaseListingRepository(firestore, storage, auth)
  }

  @Test
  fun createListing_uploadsImagesAndStoresItemData() = runTest {
    val imageUri = Uri.parse("content://images/0")
    val uploadTask = mock<UploadTask>()
    val uploadSnapshot = mock<UploadTask.TaskSnapshot>()
    val downloadUri = Uri.parse("https://example.com/listing.jpg")
    val listing =
        ItemListing(
            category = "Electronics",
            price = 100.0,
            title = "Calculator",
            description = "Graphing calculator",
            images = listOf(imageUri),
        )

    whenever(imageReference.putFile(imageUri)).thenReturn(uploadTask)
    whenever(uploadTask.isComplete).thenReturn(true)
    whenever(uploadTask.isSuccessful).thenReturn(true)
    whenever(uploadTask.result).thenReturn(uploadSnapshot)
    whenever(imageReference.downloadUrl).thenReturn(Tasks.forResult(downloadUri))
    whenever(document.set(any<Map<String, Any>>())).thenReturn(Tasks.forResult(null))

    val result = repository.createListing(listing)

    assertEquals(listingId, result.listingId)
    verify(imageReference).putFile(imageUri)
    verify(document)
        .set(
            eq(
                mapOf(
                    "ownerUid" to uid,
                    "category" to "Electronics",
                    "price" to 100.0,
                    "type" to "ITEM",
                    "title" to "Calculator",
                    "description" to "Graphing calculator",
                    "imageUrls" to listOf("https://example.com/listing.jpg"),
                    "refundValue" to 100.0,
                )
            )
        )
  }

  @Test
  fun createListing_withoutAuthenticatedUser_throwsError() = runTest {
    whenever(auth.currentUser).thenReturn(null)

    val failure = runCatching {
      repository.createListing(
          ServiceListing(
              category = "Tutoring",
              price = 25.0,
              title = "Math tutoring",
              description = "One-hour session",
          )
      )
    }
        .exceptionOrNull()

    assertTrue(failure is IllegalStateException)
    assertEquals("No authenticated user", failure?.message)
  }
}
