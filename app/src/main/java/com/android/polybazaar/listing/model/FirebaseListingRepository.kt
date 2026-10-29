// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5, Claude Sonnet 5.5)
package com.android.polybazaar.listing.model

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseListingRepository(
    private val photoCompressor: PhotoCompressor,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : ListingRepository {

  override suspend fun createListing(listing: Listing): Listing {
    val ownerUid = auth.currentUser?.uid ?: throw IllegalStateException("No authenticated user")
    ListingRules.errorFor(listing)?.let { throw IllegalArgumentException(it) }
    // Creates a reference to a new Firestore document.
    // Represents where we want to store the listing.
    val document = firestore.collection(LISTINGS_COLLECTION).document()
    // Serves as a record of successful uploads.
    val uploadedImages = mutableListOf<StorageReference>()

    try {
      val imageUrls =
          listing.images.mapIndexed { index, uri ->
            // We construct the Storage path using the index, listingId and ownerUid
            val imageReference = listingImage(ownerUid, document.id, index)
            // Uploading the compressed image rather than the original, which can weigh megabytes
            imageReference.putBytes(photoCompressor.compress(uri), JPEG_METADATA).await()
            // After the image is successfully uploaded we add imageReference to uploadedImages.
            uploadedImages += imageReference
            // We retrieve the image URL to display it.
            imageReference.downloadUrl.await().toString()
          }

      document.set(listing.toFirestoreData(ownerUid, imageUrls)).await()
      // Points to the uploaded photos: the local ones may be deleted once the listing is saved.
      return listing.saved(document.id, imageUrls.map(Uri::parse))
    } catch (exception: Exception) {
      // If listing creation fails, undo image uploads
      // Cleanup code needs to attempt deletion even if the original coroutine has been canceled.
      withContext(NonCancellable) {
        uploadedImages.forEach { imageReference ->
          try {
            imageReference.delete().await()
          } catch (cleanupException: Exception) {
            exception.addSuppressed(cleanupException)
          }
        }
      }
      throw exception
    }
  }

  override suspend fun getMyListings(): Result<List<Listing>> {
    return try {
      // A Firestore UNAUTHENTICATED error, so toAppError() reports it like an expired session.
      val ownerUid =
          auth.currentUser?.uid
              ?: throw FirebaseFirestoreException(
                  "No authenticated user",
                  FirebaseFirestoreException.Code.UNAUTHENTICATED,
              )
      // Offline, get() answers from the local cache, so listings already seen stay visible.
      val snapshot =
          firestore.collection(LISTINGS_COLLECTION).whereEqualTo("ownerUid", ownerUid).get().await()
      Result.success(snapshot.documents.mapNotNull { it.toListing() })
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Result.failure(e)
    }
  }

  override suspend fun getAllListings(): Result<List<Listing>> {
    return try {
      // No auth check here: the security rules reject unverified readers with PERMISSION_DENIED.
      // Offline, get() answers from the local cache, so listings already seen stay visible.
      val snapshot = firestore.collection(LISTINGS_COLLECTION).get().await()
      Result.success(snapshot.documents.mapNotNull { it.toListing() })
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Result.failure(e)
    }
  }

  private fun listingImage(ownerUid: String, listingId: String, index: Int): StorageReference =
      // storage.reference: Returns a reference to the root of the Firestore Storage
      // .child: Returns a reference to a location beneath that root
      storage.reference.child("listings/$ownerUid/$listingId/images/$index")

  private fun Listing.toFirestoreData(ownerUid: String, imageUrls: List<String>): Map<String, Any> =
      // Convert a Listing to a Map of values
      buildMap {
        put("ownerUid", ownerUid)
        put("category", category)
        put("price", price)
        put("type", type.name)
        put("title", title.trim())
        put("description", description.trim())
        put("imageUrls", imageUrls)
        if (this@toFirestoreData is ItemListing) {
          put("refundValue", refundValue)
        }
      }

  private fun Listing.saved(listingId: String, images: List<Uri>): Listing =
      when (this) {
        is ServiceListing -> copy(listingId = listingId, images = images)
        is ItemListing -> copy(listingId = listingId, images = images)
      }

  /**
   * Rebuilds a listing from its Firestore document, the reverse of [toFirestoreData].
   *
   * Returns null for a document that does not have the expected fields, so one malformed listing
   * (for example written before the security rules checked them) does not hide all the others.
   * `get() as?` is used instead of getString/getDouble, which throw on a field of the wrong type.
   */
  private fun DocumentSnapshot.toListing(): Listing? {
    val category = get("category") as? String
    val title = get("title") as? String
    val description = get("description") as? String
    // Firestore returns a Long for a whole number such as 8, so read any Number.
    val price = (get("price") as? Number)?.toDouble()
    if (category == null || title == null || description == null || price == null) return null
    val images = (get("imageUrls") as? List<*>).orEmpty().filterIsInstance<String>().map(Uri::parse)

    return when (get("type")) {
      ListingType.SERVICE.name ->
          ServiceListing(category, price, title, description, images = images, listingId = id)
      ListingType.ITEM.name -> {
        val refundValue = (get("refundValue") as? Number)?.toDouble() ?: return null
        ItemListing(category, price, title, description, refundValue, images, listingId = id)
      }
      else -> null
    }
  }

  private companion object {
    const val LISTINGS_COLLECTION = "listings"
    val JPEG_METADATA: StorageMetadata =
        StorageMetadata.Builder().setContentType("image/jpeg").build()
  }
}
