// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5, Claude Sonnet 5.5)
package com.android.polybazaar.listing.model

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

// Robolectric, so that Uri.parse builds the photos to upload.
@RunWith(AndroidJUnit4::class)
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
  private val photoBytes = byteArrayOf(1, 2, 3)
  private val compressedPhotos = mutableListOf<Uri>()

  /** A valid stored ITEM without a rental duration. */
  private val itemFields =
      mapOf(
          "ownerUid" to uid,
          "type" to "ITEM",
          "category" to "Tools",
          "price" to 8.0,
          "title" to "Bosch drill set",
          "description" to "With bits",
          "refundValue" to 120.0,
      )

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

    compressedPhotos.clear()
    val compressor = PhotoCompressor { photo ->
      compressedPhotos += photo
      photoBytes
    }
    repository = FirebaseListingRepository(compressor, firestore, storage, auth)
  }

  @Test
  fun createListing_uploadsImagesAndStoresItemData() = runTest {
    val imageUri = Uri.parse("content://images/0")
    val uploadTask = mock<UploadTask>()
    val uploadSnapshot = mock<UploadTask.TaskSnapshot>()
    val listing =
        ItemListing(
            category = "Electronics",
            price = 100.0,
            title = "Calculator",
            description = "Graphing calculator",
            images = listOf(imageUri),
        )

    whenever(imageReference.putBytes(eq(photoBytes), any())).thenReturn(uploadTask)
    whenever(uploadTask.isComplete).thenReturn(true)
    whenever(uploadTask.isSuccessful).thenReturn(true)
    whenever(uploadTask.result).thenReturn(uploadSnapshot)
    whenever(document.set(any<Map<String, Any>>())).thenReturn(Tasks.forResult(null))

    val result = repository.createListing(listing)

    assertEquals(listingId, result.listingId)
    assertEquals(listOf("listings/$uid/$listingId/images/0"), result.imagePaths)
    // The photos on the phone are uploaded, and may be deleted now.
    assertEquals(emptyList<Uri>(), result.images)
    verify(imageReference).putBytes(eq(photoBytes), any())
    // A download URL would let anyone holding it read the photo without signing in.
    verify(imageReference, never()).downloadUrl
    assertEquals(listOf(imageUri), compressedPhotos)
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
                    "imagePaths" to listOf("listings/$uid/$listingId/images/0"),
                    "refundValue" to 100.0,
                )
            )
        )
  }

  @Test
  fun createListing_whenDocumentSetFails_deletesUploadedImagesAndRethrowsFailure() = runTest {
    val firstImageUri = Uri.parse("content://images/0")
    val secondImageUri = Uri.parse("content://images/1")
    val firstImageReference = mock<StorageReference>()
    val secondImageReference = mock<StorageReference>()
    val uploadTask = mock<UploadTask>()
    val uploadSnapshot = mock<UploadTask.TaskSnapshot>()
    val documentSetFailure = IllegalStateException("Firestore unavailable")
    val listing =
        ServiceListing(
            category = "Tutoring",
            price = 25.0,
            title = "Math tutoring",
            description = "One-hour session",
            images = listOf(firstImageUri, secondImageUri),
        )

    whenever(storageRoot.child("listings/$uid/$listingId/images/0")).thenReturn(firstImageReference)
    whenever(storageRoot.child("listings/$uid/$listingId/images/1"))
        .thenReturn(secondImageReference)
    whenever(uploadTask.isComplete).thenReturn(true)
    whenever(uploadTask.isSuccessful).thenReturn(true)
    whenever(uploadTask.result).thenReturn(uploadSnapshot)
    whenever(firstImageReference.putBytes(eq(photoBytes), any())).thenReturn(uploadTask)
    whenever(secondImageReference.putBytes(eq(photoBytes), any())).thenReturn(uploadTask)
    whenever(firstImageReference.delete()).thenReturn(Tasks.forResult(null))
    whenever(secondImageReference.delete()).thenReturn(Tasks.forResult(null))
    whenever(
            document.set(
                eq(
                    mapOf(
                        "ownerUid" to uid,
                        "category" to "Tutoring",
                        "price" to 25.0,
                        "type" to "SERVICE",
                        "title" to "Math tutoring",
                        "description" to "One-hour session",
                        "imagePaths" to
                            listOf(
                                "listings/$uid/$listingId/images/0",
                                "listings/$uid/$listingId/images/1",
                            ),
                    )
                )
            )
        )
        .thenReturn(Tasks.forException(documentSetFailure))

    val failure = runCatching { repository.createListing(listing) }.exceptionOrNull()

    assertSame(documentSetFailure, failure)
    verify(firstImageReference).delete()
    verify(secondImageReference).delete()
  }

  @Test
  fun createListing_whenSecondUploadFails_deletesFirstImageAndRethrowsFailure() = runTest {
    val firstImageUri = Uri.parse("content://images/0")
    val secondImageUri = Uri.parse("content://images/1")
    val firstImageReference = mock<StorageReference>()
    val secondImageReference = mock<StorageReference>()
    val successfulUploadTask = mock<UploadTask>()
    val failedUploadTask = mock<UploadTask>()
    val uploadSnapshot = mock<UploadTask.TaskSnapshot>()
    val uploadFailure = IllegalStateException("Storage unavailable")
    val listing =
        ServiceListing(
            category = "Tutoring",
            price = 25.0,
            title = "Math tutoring",
            description = "One-hour session",
            images = listOf(firstImageUri, secondImageUri),
        )

    whenever(storageRoot.child("listings/$uid/$listingId/images/0")).thenReturn(firstImageReference)
    whenever(storageRoot.child("listings/$uid/$listingId/images/1"))
        .thenReturn(secondImageReference)
    whenever(successfulUploadTask.isComplete).thenReturn(true)
    whenever(successfulUploadTask.isSuccessful).thenReturn(true)
    whenever(successfulUploadTask.result).thenReturn(uploadSnapshot)
    whenever(failedUploadTask.isComplete).thenReturn(true)
    whenever(failedUploadTask.isSuccessful).thenReturn(false)
    whenever(failedUploadTask.exception).thenReturn(uploadFailure)
    whenever(firstImageReference.putBytes(eq(photoBytes), any())).thenReturn(successfulUploadTask)
    whenever(secondImageReference.putBytes(eq(photoBytes), any())).thenReturn(failedUploadTask)
    whenever(firstImageReference.delete()).thenReturn(Tasks.forResult(null))

    val failure = runCatching { repository.createListing(listing) }.exceptionOrNull()

    assertSame(uploadFailure, failure)
    verify(firstImageReference).delete()
    verify(secondImageReference, never()).delete()
  }

  @Test
  fun createListing_whenCleanupFails_addsCleanupFailureAsSuppressed() = runTest {
    val imageUri = Uri.parse("content://images/0")
    val uploadTask = mock<UploadTask>()
    val uploadSnapshot = mock<UploadTask.TaskSnapshot>()
    val documentSetFailure = IllegalStateException("Firestore unavailable")
    val deleteFailure = IllegalStateException("Storage cleanup unavailable")
    val listing =
        ServiceListing(
            category = "Tutoring",
            price = 25.0,
            title = "Math tutoring",
            description = "One-hour session",
            images = listOf(imageUri),
        )

    whenever(uploadTask.isComplete).thenReturn(true)
    whenever(uploadTask.isSuccessful).thenReturn(true)
    whenever(uploadTask.result).thenReturn(uploadSnapshot)
    whenever(imageReference.putBytes(eq(photoBytes), any())).thenReturn(uploadTask)
    whenever(imageReference.delete()).thenReturn(Tasks.forException(deleteFailure))
    whenever(document.set(any<Map<String, Any>>()))
        .thenReturn(Tasks.forException(documentSetFailure))

    val failure = runCatching { repository.createListing(listing) }.exceptionOrNull()

    assertSame(documentSetFailure, failure)
    assertEquals(listOf(deleteFailure), failure?.suppressed?.toList())
  }

  @Test
  fun createListing_overLimit_failsWithoutTouchingFirebase() = runTest {
    // A photo is needed for the compressor to have something to compress.
    val photos = listOf(Uri.parse("content://images/0"))
    val overLimit =
        listOf(
            ServiceListing("Tutoring", 25.0, "t".repeat(81), "One-hour session", images = photos) to
                "Title must be at most 80 characters",
            ServiceListing("Tutoring", 25.0, "Math tutoring", "d".repeat(1001), images = photos) to
                "Description must be at most 1000 characters",
            ServiceListing(
                "Tutoring",
                10_000.01,
                "Math tutoring",
                "One-hour session",
                images = photos,
            ) to "Enter a price between CHF 0 and CHF 10,000",
            ItemListing(
                "Tools",
                5.0,
                "Drill",
                "Works well",
                refundValue = 100_000.01,
                images = photos,
            ) to "Enter a refund value between CHF 0 and CHF 100,000",
        )

    overLimit.forEach { (listing, message) ->
      val failure = runCatching { repository.createListing(listing) }.exceptionOrNull()

      assertTrue(failure is IllegalArgumentException)
      assertEquals(message, failure?.message)
    }
    verifyNoInteractions(firestore, storage)
    assertTrue(compressedPhotos.isEmpty())
  }

  @Test
  fun createListing_atLimits_succeeds() = runTest {
    val listing =
        ItemListing(
            category = "Tools",
            price = 10_000.0,
            title = "t".repeat(80),
            description = "d".repeat(1000),
            refundValue = 100_000.0,
        )
    whenever(document.set(any<Map<String, Any>>())).thenReturn(Tasks.forResult(null))

    val created = repository.createListing(listing)

    assertEquals(listingId, created.listingId)
  }

  @Test
  fun createListing_storesTrimmedTitleAndDescription() = runTest {
    val listing =
        ServiceListing(
            category = "Tutoring",
            price = 25.0,
            title = "  " + "t".repeat(80),
            description = "  Padded description \n",
        )
    whenever(document.set(any<Map<String, Any>>())).thenReturn(Tasks.forResult(null))

    repository.createListing(listing)

    val stored = argumentCaptor<Map<String, Any>>()
    verify(document).set(stored.capture())
    assertEquals("t".repeat(80), stored.firstValue["title"])
    assertEquals("Padded description", stored.firstValue["description"])
  }

  @Test
  fun createListing_returnsTheListingOwnedByTheSignedInUser() = runTest {
    whenever(document.set(any<Map<String, Any>>())).thenReturn(Tasks.forResult(null))
    val service = ServiceListing("Tutoring", 25.0, "Math tutoring", "One-hour session")
    val item = ItemListing("Tools", 8.0, "Bosch drill set", "With bits")

    val createdService = repository.createListing(service)
    val createdItem = repository.createListing(item)

    assertEquals(service.copy(listingId = listingId, ownerUid = uid), createdService)
    assertEquals(item.copy(listingId = listingId, ownerUid = uid), createdItem)
  }

  @Test
  fun createListing_storesTheRentalDurationOnlyWhenSet() = runTest {
    whenever(document.set(any<Map<String, Any>>())).thenReturn(Tasks.forResult(null))

    repository.createListing(ItemListing("Tools", 8.0, "Drill", "With bits", maxRentalDays = 14))
    repository.createListing(ItemListing("Tools", 8.0, "Drill", "With bits"))

    val stored = argumentCaptor<Map<String, Any>>()
    verify(document, times(2)).set(stored.capture())
    assertEquals(14, stored.firstValue["maxRentalDays"])
    // No limit: the key is left out, like on a listing written before the field existed.
    assertFalse(stored.secondValue.containsKey("maxRentalDays"))
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

  @Test
  fun loadListingImage_downloadsTheImageAtItsPath() = runTest {
    val imagePath = "listings/$uid/$listingId/images/0"
    whenever(storageRoot.child(imagePath)).thenReturn(imageReference)
    whenever(imageReference.getBytes(any())).thenReturn(Tasks.forResult(photoBytes))

    val result = repository.loadListingImage(imagePath)

    assertSame(photoBytes, result)
    // Allows the largest photo the Storage rules accept.
    verify(imageReference).getBytes(10L * 1024 * 1024)
  }

  @Test
  fun getMyListings_queriesOwnListingsAndRebuildsItemsAndServices() = runTest {
    givenMyListings(
        listingDocument(
            "item-1",
            mapOf(
                "ownerUid" to uid,
                "type" to "ITEM",
                "category" to "Tools",
                "price" to 8.5,
                "title" to "Bosch drill set",
                "description" to "With bits",
                "imagePaths" to listOf("listings/$uid/item-1/images/0"),
                "refundValue" to 120.0,
            ),
        ),
        // Firestore returns whole numbers as Long, so the price must still be read.
        listingDocument(
            "service-1",
            mapOf(
                "ownerUid" to uid,
                "type" to "SERVICE",
                "category" to "Tutoring",
                "price" to 25L,
                "title" to "Math tutoring",
                "description" to "One-hour session",
                "imagePaths" to emptyList<String>(),
            ),
        ),
    )

    val result = repository.getMyListings().getOrThrow()

    assertEquals(
        listOf(
            ItemListing(
                category = "Tools",
                price = 8.5,
                title = "Bosch drill set",
                description = "With bits",
                refundValue = 120.0,
                imagePaths = listOf("listings/$uid/item-1/images/0"),
                listingId = "item-1",
                ownerUid = uid,
            ),
            ServiceListing(
                category = "Tutoring",
                price = 25.0,
                title = "Math tutoring",
                description = "One-hour session",
                listingId = "service-1",
                ownerUid = uid,
            ),
        ),
        result,
    )
    verify(listings).whereEqualTo("ownerUid", uid)
  }

  @Test
  fun getMyListings_withNoListings_returnsAnEmptyList() = runTest {
    givenMyListings()

    assertEquals(emptyList<Listing>(), repository.getMyListings().getOrThrow())
  }

  @Test
  fun getMyListings_skipsMalformedDocumentsAndKeepsTheOthers() = runTest {
    val valid =
        mapOf(
            "ownerUid" to uid,
            "type" to "SERVICE",
            "category" to "Tutoring",
            "price" to 25.0,
            "title" to "Math tutoring",
            "description" to "One-hour session",
        )
    givenMyListings(
        listingDocument("missing-title", valid - "title"),
        listingDocument("unknown-type", valid + ("type" to "RENTAL")),
        listingDocument("price-as-text", valid + ("price" to "25")),
        listingDocument("item-without-refund", valid + ("type" to "ITEM")),
        listingDocument("valid", valid),
    )

    val result = repository.getMyListings().getOrThrow()

    assertEquals(listOf("valid"), result.map { it.listingId })
  }

  @Test
  fun getMyListings_withoutAuthenticatedUser_failsAsUnauthenticatedWithoutQuerying() = runTest {
    whenever(auth.currentUser).thenReturn(null)

    val failure = repository.getMyListings().exceptionOrNull()

    assertEquals(AppError.UNAUTHENTICATED, failure?.toAppError())
    verify(listings, never()).whereEqualTo(any<String>(), any())
  }

  @Test
  fun getMyListings_whenTheQueryFails_returnsTheFailure() = runTest {
    // What Firestore raises offline when the listings are not in the local cache.
    val unavailable =
        FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
    givenQueryResult(Tasks.forException(unavailable))

    val failure = repository.getMyListings().exceptionOrNull()

    assertSame(unavailable, failure)
    assertEquals(AppError.NETWORK, failure?.toAppError())
  }

  @Test
  fun getMyListings_whenCancelled_rethrowsInsteadOfReturningAFailure() = runTest {
    givenQueryResult(Tasks.forCanceled())

    val thrown = runCatching { repository.getMyListings() }.exceptionOrNull()

    assertTrue(thrown is CancellationException)
  }

  @Test
  fun getAllListings_readsTheWholeCollectionAndRebuildsItemsAndServices() = runTest {
    givenAllListings(
        listingDocument(
            "item-1",
            mapOf(
                "ownerUid" to "someone-else",
                "type" to "ITEM",
                "category" to "Tools",
                "price" to 8.5,
                "title" to "Bosch drill set",
                "description" to "With bits",
                "imagePaths" to listOf("listings/someone-else/item-1/images/0"),
                "refundValue" to 120.0,
            ),
        ),
        listingDocument(
            "service-1",
            mapOf(
                "ownerUid" to uid,
                "type" to "SERVICE",
                "category" to "Tutoring",
                "price" to 25.0,
                "title" to "Math tutoring",
                "description" to "One-hour session",
            ),
        ),
    )

    val result = repository.getAllListings().getOrThrow()

    assertEquals(
        listOf(
            ItemListing(
                category = "Tools",
                price = 8.5,
                title = "Bosch drill set",
                description = "With bits",
                refundValue = 120.0,
                imagePaths = listOf("listings/someone-else/item-1/images/0"),
                listingId = "item-1",
                ownerUid = "someone-else",
            ),
            ServiceListing(
                category = "Tutoring",
                price = 25.0,
                title = "Math tutoring",
                description = "One-hour session",
                listingId = "service-1",
                ownerUid = uid,
            ),
        ),
        result,
    )
    // Every user's listings, not only the signed-in user's.
    verify(listings, never()).whereEqualTo(any<String>(), any())
  }

  @Test
  fun getAllListings_readsAWholeNumberPriceStoredAsLong() = runTest {
    // Firestore returns whole numbers as Long, for the price and the refund value.
    givenAllListings(
        listingDocument(
            "item-1",
            mapOf(
                "ownerUid" to uid,
                "type" to "ITEM",
                "category" to "Tools",
                "price" to 8L,
                "title" to "Bosch drill set",
                "description" to "With bits",
                "refundValue" to 120L,
            ),
        )
    )

    val result = repository.getAllListings().getOrThrow()

    assertEquals(
        listOf(
            ItemListing(
                "Tools",
                8.0,
                "Bosch drill set",
                "With bits",
                120.0,
                listingId = "item-1",
                ownerUid = uid,
            )
        ),
        result,
    )
  }

  @Test
  fun getAllListings_readsTheRentalDuration() = runTest {
    givenAllListings(
        // Firestore returns whole numbers as Long.
        listingDocument("as-long", itemFields + ("maxRentalDays" to 14L)),
        listingDocument("as-int", itemFields + ("maxRentalDays" to 180)),
        // Written before the field existed, or without a limit.
        listingDocument("absent", itemFields),
    )

    val result = repository.getAllListings().getOrThrow()

    assertEquals(
        listOf(
            "as-long" to 14,
            "as-int" to 180,
            "absent" to null,
        ),
        result.map { it.listingId to (it as ItemListing).maxRentalDays },
    )
  }

  @Test
  fun getAllListings_skipsItemsWhoseRentalDurationIsNotAWholeNumber() = runTest {
    givenAllListings(
        listingDocument("as-text", itemFields + ("maxRentalDays" to "7")),
        listingDocument("fraction", itemFields + ("maxRentalDays" to 1.5)),
        // The rules only accept an int, so even a whole Double is malformed.
        listingDocument("whole-double", itemFields + ("maxRentalDays" to 7.0)),
        listingDocument("valid", itemFields + ("maxRentalDays" to 7L)),
    )

    val result = repository.getAllListings().getOrThrow()

    assertEquals(listOf("valid"), result.map { it.listingId })
  }

  @Test
  fun getAllListings_withNoListings_returnsAnEmptyList() = runTest {
    givenAllListings()

    assertEquals(emptyList<Listing>(), repository.getAllListings().getOrThrow())
  }

  @Test
  fun getAllListings_skipsMalformedDocumentsAndKeepsTheOthers() = runTest {
    val valid =
        mapOf(
            "ownerUid" to uid,
            "type" to "SERVICE",
            "category" to "Tutoring",
            "price" to 25.0,
            "title" to "Math tutoring",
            "description" to "One-hour session",
        )
    givenAllListings(
        listingDocument("missing-category", valid - "category"),
        listingDocument("missing-description", valid - "description"),
        listingDocument("missing-price", valid - "price"),
        listingDocument("unknown-type", valid + ("type" to "RENTAL")),
        listingDocument("item-without-refund", valid + ("type" to "ITEM")),
        // The rules require a string owner on every listing.
        listingDocument("missing-owner", valid - "ownerUid"),
        listingDocument("owner-as-number", valid + ("ownerUid" to 123L)),
        listingDocument("valid", valid),
    )

    val result = repository.getAllListings().getOrThrow()

    assertEquals(listOf("valid"), result.map { it.listingId })
  }

  @Test
  fun getAllListings_whenTheRulesRefuseTheRead_returnsTheFailure() = runTest {
    // What the rules answer to a reader whose email is not verified.
    val denied =
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    whenever(listings.get()).thenReturn(Tasks.forException(denied))

    val failure = repository.getAllListings().exceptionOrNull()

    assertSame(denied, failure)
    assertEquals(AppError.PERMISSION_DENIED, failure?.toAppError())
  }

  @Test
  fun getAllListings_whenCancelled_rethrowsInsteadOfReturningAFailure() = runTest {
    whenever(listings.get()).thenReturn(Tasks.forCanceled())

    val thrown = runCatching { repository.getAllListings() }.exceptionOrNull()

    assertTrue(thrown is CancellationException)
  }

  @Test
  fun getListing_readsTheDocumentAndRebuildsAnItem() = runTest {
    val reference =
        givenListing(
            "item-1",
            Tasks.forResult(
                listingDocument(
                    "item-1",
                    mapOf(
                        "ownerUid" to "someone-else",
                        "type" to "ITEM",
                        "category" to "Tools",
                        "price" to 8.5,
                        "title" to "Bosch drill set",
                        "description" to "With bits",
                        "imagePaths" to listOf("listings/someone-else/item-1/images/0"),
                        "refundValue" to 120.0,
                    ),
                )
            ),
        )

    val result = repository.getListing("item-1").getOrThrow()

    assertEquals(
        ItemListing(
            category = "Tools",
            price = 8.5,
            title = "Bosch drill set",
            description = "With bits",
            refundValue = 120.0,
            imagePaths = listOf("listings/someone-else/item-1/images/0"),
            listingId = "item-1",
            ownerUid = "someone-else",
        ),
        result,
    )
    verify(firestore).collection("listings")
    verify(listings).document("item-1")
    verify(reference).get()
  }

  @Test
  fun getListing_rebuildsAService() = runTest {
    givenListing(
        "service-1",
        Tasks.forResult(
            listingDocument(
                "service-1",
                mapOf(
                    "ownerUid" to uid,
                    "type" to "SERVICE",
                    "category" to "Tutoring",
                    "price" to 25L,
                    "title" to "Math tutoring",
                    "description" to "One-hour session",
                ),
            )
        ),
    )

    val result = repository.getListing("service-1").getOrThrow()

    assertEquals(
        ServiceListing(
            category = "Tutoring",
            price = 25.0,
            title = "Math tutoring",
            description = "One-hour session",
            listingId = "service-1",
            ownerUid = uid,
        ),
        result,
    )
  }

  @Test
  fun getListing_whenTheDocumentDoesNotExist_failsAsNotFound() = runTest {
    // Firestore answers a missing document with a snapshot that has no fields.
    givenListing("missing", Tasks.forResult(listingDocument("missing", emptyMap())))

    val failure = repository.getListing("missing").exceptionOrNull()

    assertTrue(failure is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.NOT_FOUND,
        (failure as FirebaseFirestoreException).code,
    )
    assertEquals(AppError.NOT_FOUND, failure.toAppError())
  }

  @Test
  fun getListing_whenTheDocumentIsMalformed_failsAsNotFound() = runTest {
    // An ITEM without a refund value: toListing() cannot rebuild it.
    givenListing(
        "broken",
        Tasks.forResult(
            listingDocument(
                "broken",
                mapOf(
                    "ownerUid" to uid,
                    "type" to "ITEM",
                    "category" to "Tools",
                    "price" to 8.5,
                    "title" to "Bosch drill set",
                    "description" to "With bits",
                ),
            )
        ),
    )

    val failure = repository.getListing("broken").exceptionOrNull()

    assertEquals(AppError.NOT_FOUND, failure?.toAppError())
  }

  @Test
  fun getListing_whenFirestoreFails_returnsTheFailure() = runTest {
    listOf(
            FirebaseFirestoreException.Code.PERMISSION_DENIED to AppError.PERMISSION_DENIED,
            // What Firestore raises offline when the listing is not in the local cache.
            FirebaseFirestoreException.Code.UNAVAILABLE to AppError.NETWORK,
        )
        .forEach { (code, expected) ->
          val exception = FirebaseFirestoreException(code.name, code)
          givenListing("item-1", Tasks.forException(exception))

          val failure = repository.getListing("item-1").exceptionOrNull()

          assertSame(exception, failure)
          assertEquals(expected, failure?.toAppError())
        }
  }

  @Test
  fun getListing_whenCancelled_rethrowsInsteadOfReturningAFailure() = runTest {
    givenListing("item-1", Tasks.forCanceled())

    val thrown = runCatching { repository.getListing("item-1") }.exceptionOrNull()

    assertTrue(thrown is CancellationException)
  }

  private fun givenListing(
      id: String,
      result: com.google.android.gms.tasks.Task<DocumentSnapshot>,
  ): DocumentReference {
    val reference = mock<DocumentReference>()
    whenever(listings.document(id)).thenReturn(reference)
    whenever(reference.get()).thenReturn(result)
    return reference
  }

  private fun givenAllListings(vararg documents: DocumentSnapshot) {
    val snapshot = mock<QuerySnapshot>()
    whenever(snapshot.documents).thenReturn(documents.toList())
    whenever(listings.get()).thenReturn(Tasks.forResult(snapshot))
  }

  private fun givenMyListings(vararg documents: DocumentSnapshot) {
    val snapshot = mock<QuerySnapshot>()
    whenever(snapshot.documents).thenReturn(documents.toList())
    givenQueryResult(Tasks.forResult(snapshot))
  }

  private fun givenQueryResult(result: com.google.android.gms.tasks.Task<QuerySnapshot>) {
    val query = mock<Query>()
    whenever(listings.whereEqualTo("ownerUid", uid)).thenReturn(query)
    whenever(query.get()).thenReturn(result)
  }

  private fun listingDocument(id: String, fields: Map<String, Any>): DocumentSnapshot {
    val document = mock<DocumentSnapshot>()
    whenever(document.id).thenReturn(id)
    whenever(document.get(any<String>())).thenAnswer { fields[it.getArgument<String>(0)] }
    return document
  }
}
