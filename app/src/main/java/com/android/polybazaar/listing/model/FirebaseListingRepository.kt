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
    val ownerUid = auth.currentUser?.uid ?: throw unauthenticated()
    ListingRules.errorFor(listing)?.let { throw IllegalArgumentException(it) }
    // Creates a reference to a new Firestore document.
    // Represents where we want to store the listing.
    val document = firestore.collection(LISTINGS_COLLECTION).document()
    val imagePaths = listing.images.indices.map { listingImagePath(ownerUid, document.id, it) }

    return uploadThenSave(listing.images, imagePaths) {
      document.set(listing.toFirestoreData(ownerUid, imagePaths)).await()
      // Points to the uploaded photos: the local ones may be deleted once the listing is saved.
      listing.saved(document.id, imagePaths, ownerUid)
    }
  }

  override suspend fun updateListing(listing: Listing): Listing {
    val ownerUid = auth.currentUser?.uid ?: throw unauthenticated()
    val listingId =
        listing.listingId ?: throw IllegalArgumentException("Only a saved listing can be updated")
    ListingRules.errorFor(listing)?.let { throw IllegalArgumentException(it) }
    val document = firestore.collection(LISTINGS_COLLECTION).document(listingId)

    // The stored version says which photos exist, rather than trusting the caller. Checked here
    // too, although the rules check it, so nothing is uploaded for an update that would be refused.
    val stored = document.get().await()
    if (!stored.exists()) throw notFound(listingId)
    if (stored.get("ownerUid") != ownerUid) {
      throw FirebaseFirestoreException(
          "Only the owner can edit a listing",
          FirebaseFirestoreException.Code.PERMISSION_DENIED,
      )
    }
    require(stored.get("type") == listing.type.name) { "A listing's type cannot change" }
    val storedPaths = stored.imagePaths()
    val keptPaths = listing.imagePaths
    require(storedPaths.containsAll(keptPaths) && keptPaths.distinct() == keptPaths) {
      "Only the listing's own photos can be kept, each once"
    }

    // Storage only accepts images/0 to images/4, so a new photo takes a slot no kept photo uses.
    // Unused slots come first: overwriting a removed photo changes the listing still saved, if the
    // update then fails.
    val allSlots = List(ListingRules.MAX_PHOTOS) { listingImagePath(ownerUid, listingId, it) }
    val unusedSlots = allSlots - storedPaths.toSet()
    val removedSlots = allSlots.filter { it in storedPaths && it !in keptPaths }
    val newPaths = (unusedSlots + removedSlots).take(listing.images.size)
    val imagePaths = keptPaths + newPaths

    val saved =
        // If the update fails, a photo that overwrote a removed one stays: the listing still saved
        // points to that path, and showing the new photo is better than a missing one.
        uploadThenSave(listing.images, newPaths, keepOnFailure = removedSlots.toSet()) {
          document.set(listing.toFirestoreData(ownerUid, imagePaths)).await()
          listing.saved(listingId, imagePaths, ownerUid)
        }
    // Only once the listing no longer points to them; a photo left behind is only wasted space.
    deleteImagesQuietly(storedPaths - imagePaths.toSet())
    return saved
  }

  override suspend fun deleteListing(listingId: String): Result<Unit> {
    return try {
      if (auth.currentUser == null) throw unauthenticated()
      val document = firestore.collection(LISTINGS_COLLECTION).document(listingId)
      val stored = document.get().await()
      if (stored.exists()) {
        // The rules refuse the delete for anyone but the owner, before any photo is touched.
        document.delete().await()
        // After the document, so a failure never leaves a listing pointing to missing photos.
        deleteImagesQuietly(stored.imagePaths())
      }
      Result.success(Unit)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Result.failure(e)
    }
  }

  override suspend fun getListing(listingId: String): Result<Listing> {
    return try {
      // Offline, get() answers from the local cache, so a listing already seen stays visible.
      val snapshot = firestore.collection(LISTINGS_COLLECTION).document(listingId).get().await()
      val listing = if (snapshot.exists()) snapshot.toListing() else null
      Result.success(listing ?: throw notFound(listingId))
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Result.failure(e)
    }
  }

  override suspend fun getMyListings(): Result<List<Listing>> {
    return try {
      // A Firestore UNAUTHENTICATED error, so toAppError() reports it like an expired session.
      val ownerUid = auth.currentUser?.uid ?: throw unauthenticated()
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

  override suspend fun loadListingImage(imagePath: String): ByteArray =
      storage.reference.child(imagePath).getBytes(MAX_IMAGE_BYTES).await()

  /**
   * Uploads each of [images] to the Storage path at the same index in [paths], then runs [save].
   * Listings store these paths, not download URLs: a download URL works for anyone who has it,
   * signed in or not.
   *
   * If an upload or [save] fails, deletes the photos uploaded so far, except those at
   * [keepOnFailure], and rethrows the failure.
   */
  private suspend fun <T> uploadThenSave(
      images: List<Uri>,
      paths: List<String>,
      keepOnFailure: Set<String> = emptySet(),
      save: suspend () -> T,
  ): T {
    // Serves as a record of successful uploads.
    val uploaded = mutableListOf<Pair<String, StorageReference>>()
    try {
      images.zip(paths).forEach { (uri, path) ->
        val imageReference = storage.reference.child(path)
        // Uploading the compressed image rather than the original, which can weigh megabytes.
        imageReference.putBytes(photoCompressor.compress(uri), JPEG_METADATA).await()
        uploaded += path to imageReference
      }
      return save()
    } catch (exception: Exception) {
      // Cleanup code needs to attempt deletion even if the original coroutine has been canceled.
      withContext(NonCancellable) {
        uploaded
            .filter { (path, _) -> path !in keepOnFailure }
            .forEach { (_, imageReference) ->
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

  /** Deletes photos that nothing points to anymore. A failure only leaves the file behind. */
  private suspend fun deleteImagesQuietly(imagePaths: List<String>) {
    withContext(NonCancellable) {
      imagePaths.forEach { path ->
        try {
          storage.reference.child(path).delete().await()
        } catch (ignored: Exception) {
          // The listing is already saved without it, so the user has nothing to retry.
        }
      }
    }
  }

  private fun listingImagePath(ownerUid: String, listingId: String, index: Int): String =
      "listings/$ownerUid/$listingId/images/$index"

  private fun Listing.toFirestoreData(
      ownerUid: String,
      imagePaths: List<String>,
  ): Map<String, Any> =
      // Convert a Listing to a Map of values
      buildMap {
        put("ownerUid", ownerUid)
        put("category", category)
        put("price", price)
        put("type", type.name)
        put("title", title.trim())
        put("description", description.trim())
        put("imagePaths", imagePaths)
        if (this@toFirestoreData is ItemListing) {
          put("refundValue", refundValue)
          // Left out when there is no limit, so the document looks like one written before the
          // field existed.
          maxRentalDays?.let { put("maxRentalDays", it) }
        }
      }

  private fun DocumentSnapshot.imagePaths(): List<String> =
      (get("imagePaths") as? List<*>).orEmpty().filterIsInstance<String>()

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
    // The rules require it on every listing, so a document without it is malformed.
    val ownerUid = get("ownerUid") as? String
    if (
        category == null ||
            title == null ||
            description == null ||
            price == null ||
            ownerUid == null
    )
        return null
    val imagePaths = imagePaths()

    return when (get("type")) {
      ListingType.SERVICE.name ->
          ServiceListing(
              category,
              price,
              title,
              description,
              imagePaths = imagePaths,
              listingId = id,
              ownerUid = ownerUid,
          )
      ListingType.ITEM.name -> {
        val refundValue = (get("refundValue") as? Number)?.toDouble() ?: return null
        // Optional: absent means no limit. Firestore returns a whole number as a Long, and the
        // rules only accept an int, so anything else (text, a fraction) is malformed.
        val maxRentalDays =
            when (val days = get("maxRentalDays")) {
              null -> null
              is Long -> days.toInt()
              is Int -> days
              else -> return null
            }
        ItemListing(
            category,
            price,
            title,
            description,
            refundValue,
            maxRentalDays,
            imagePaths = imagePaths,
            listingId = id,
            ownerUid = ownerUid,
        )
      }
      else -> null
    }
  }

  private companion object {
    const val LISTINGS_COLLECTION = "listings"
    val JPEG_METADATA: StorageMetadata =
        StorageMetadata.Builder().setContentType("image/jpeg").build()
    // The largest photo the Storage rules accept.
    const val MAX_IMAGE_BYTES = 10L * 1024 * 1024

    fun notFound(listingId: String) =
        FirebaseFirestoreException(
            "Listing $listingId not found",
            FirebaseFirestoreException.Code.NOT_FOUND,
        )

    fun unauthenticated() =
        FirebaseFirestoreException(
            "No authenticated user",
            FirebaseFirestoreException.Code.UNAUTHENTICATED,
        )
  }
}
