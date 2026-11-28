// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5, Claude Sonnet 5.5, Claude Fable 5.2)
// Co-authored-by: Copilot Claude Sonnet 5.5
package com.android.polybazaar.listing.model

import android.net.Uri
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import com.android.polybazaar.error.model.attempt
import com.android.polybazaar.photo.PhotoCompressor
import com.android.polybazaar.photo.PhotoStorage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
    return create(firestore.collection(LISTINGS_COLLECTION).document(), ownerUid, listing)
  }

  override fun newListingId(): String = firestore.collection(LISTINGS_COLLECTION).document().id

  override suspend fun publishListing(listingId: String, listing: Listing) {
    val ownerUid = auth.currentUser?.uid ?: throw unauthenticated()
    ListingRules.errorFor(listing)?.let { throw IllegalArgumentException(it) }
    val document = firestore.collection(LISTINGS_COLLECTION).document(listingId)
    // A set() queued by an earlier publish cut short must reach the server before the check below:
    // otherwise the listing would look unpublished, and writing it again could undo an edit or a
    // pause the user made since.
    firestore.waitForPendingWrites().await()
    // From the server: the cache may not know of a publish whose answer the device never got.
    val stored = document.get(Source.SERVER).await()
    if (!stored.exists()) {
      // Photos are kept on failure: a set() cut short may still reach the server and point to them,
      // and a retry overwrites them, their paths being fixed by the listing id.
      create(document, ownerUid, listing, keepPhotosOnFailure = true)
    } else if (stored.get("ownerUid") != ownerUid) {
      throw FirebaseFirestoreException(
          "Listing $listingId belongs to another user",
          FirebaseFirestoreException.Code.PERMISSION_DENIED,
      )
    }
  }

  /**
   * Uploads [listing]'s photos and saves it as [document], owned by [ownerUid]. The photos uploaded
   * are deleted if saving fails, unless [keepPhotosOnFailure].
   */
  private suspend fun create(
      document: DocumentReference,
      ownerUid: String,
      listing: Listing,
      keepPhotosOnFailure: Boolean = false,
  ): Listing {
    val imagePaths = listing.images.indices.map { listingImagePath(ownerUid, document.id, it) }
    val keepOnFailure = if (keepPhotosOnFailure) imagePaths.toSet() else emptySet()

    return uploadThenSave(listing.images, imagePaths, keepOnFailure) {
      // A new listing is always active: only setPaused pauses it.
      document.set(listing.toFirestoreData(ownerUid, imagePaths) + (PAUSED to false)).await()
      // Points to the uploaded photos: the local ones may be deleted once the listing is saved.
      listing.saved(document.id, imagePaths, ownerUid, isPaused = false)
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
    // Only for the listing returned: the edit does not write it, see toFirestoreEdit.
    val storedPaused = stored.get(PAUSED) as? Boolean ?: false
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
          // update(), not set(): a listing deleted meanwhile is not recreated, and a pause made
          // meanwhile on another device is not overwritten.
          document.update(listing.toFirestoreEdit(ownerUid, imagePaths)).await()
          listing.saved(listingId, imagePaths, ownerUid, storedPaused)
        }
    // Only once the listing no longer points to them; a photo left behind is only wasted space.
    deleteImagesQuietly(storedPaths - imagePaths.toSet())
    return saved
  }

  override suspend fun applyQueuedEdit(base: Listing, edit: Listing): Result<Unit> =
      attempt<Unit> {
        val ownerUid = auth.currentUser?.uid ?: throw unauthenticated()
        val listingId = edit.listingId ?: throw IllegalArgumentException("The edit has no id")
        // An update cut short in an earlier session must reach the server before the check below,
        // like in publishListing.
        firestore.waitForPendingWrites().await()
        val stored =
            firestore.collection(LISTINGS_COLLECTION).document(listingId).get(Source.SERVER).await()
        val storedPaths = stored.imagePaths()
        // Compared as written, which leaves out the pause state, ignored here.
        val storedData =
            stored.takeIf { it.exists() }?.toListing()?.toFirestoreData(ownerUid, storedPaths)
                ?: throw notFound(listingId)
        val alreadySaved =
            storedPaths.size == edit.imagePaths.size + edit.images.size &&
                storedPaths.take(edit.imagePaths.size) == edit.imagePaths &&
                storedData == edit.toFirestoreData(ownerUid, storedPaths)
        when {
          storedData == base.toFirestoreData(ownerUid, base.imagePaths) -> updateListing(edit)
          !alreadySaved -> throw AppException(AppError.CHANGED_ELSEWHERE)
        }
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

  override suspend fun setPaused(listingId: String, paused: Boolean): Result<Unit> {
    return try {
      if (auth.currentUser == null) throw unauthenticated()
      // update() fails with NOT_FOUND when the listing does not exist, and the rules refuse it for
      // anyone but the owner.
      firestore.collection(LISTINGS_COLLECTION).document(listingId).update(PAUSED, paused).await()
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

  override fun observeListing(listingId: String): Flow<ObservedListing> = callbackFlow {
    val listener =
        firestore
            .collection(LISTINGS_COLLECTION)
            .document(listingId)
            // The server confirming that an uncached listing is missing only changes the
            // snapshot's metadata, which the default listener does not report.
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
              when {
                error != null -> close(error)
                snapshot == null -> close(IllegalStateException("Listing snapshot was null"))
                // Firestore raises this offline, and online too when it has cached results
                // for the listing; only the server decides that it is deleted.
                !snapshot.exists() && snapshot.metadata.isFromCache ->
                    trySend(ObservedListing.NotCached)
                // A malformed listing reads as a missing one, as in getListing.
                else ->
                    trySend(
                        snapshot.takeIf { it.exists() }?.toListing()?.let(ObservedListing::Found)
                            ?: ObservedListing.Missing
                    )
              }
            }

    awaitClose { listener.remove() }
  }
      // Metadata changes alone, such as a pending write being confirmed, repeat the same value.
      .distinctUntilChanged()

  override suspend fun getMyListings(): Result<List<Listing>> {
    // A Firestore UNAUTHENTICATED error, so toAppError() reports it like an expired session.
    val ownerUid = auth.currentUser?.uid ?: return Result.failure(unauthenticated())
    return getListingsByOwner(ownerUid)
  }

  override suspend fun getListingsByOwner(ownerUid: String): Result<List<Listing>> {
    return try {
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
      // Paused listings are hidden from Browse. Every listing stores the field, so the query
      // misses none.
      val snapshot =
          firestore.collection(LISTINGS_COLLECTION).whereEqualTo(PAUSED, false).get().await()
      Result.success(snapshot.documents.mapNotNull { it.toListing() })
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Result.failure(e)
    }
  }

  override suspend fun loadListingImage(imagePath: String): ByteArray =
      storage.reference.child(imagePath).getBytes(PhotoStorage.MAX_PHOTO_BYTES).await()

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
        imageReference.putBytes(photoCompressor.compress(uri), PhotoStorage.JPEG_METADATA).await()
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
        put("category", category.id)
        put("price", price)
        put("type", type.name)
        put("title", title.trim())
        put("description", description.trim())
        put("imagePaths", imagePaths)
        // As days since 1970, like a booking's dates, and in order. Left out when there is none,
        // so the document looks like one written before the field existed.
        if (availableDates.isNotEmpty()) {
          put(AVAILABLE_DATES, availableDates.map { it.toEpochDay() }.sorted())
        }
        // A map rather than a GeoPoint, so the model stays free of Firebase. Left out when there is
        // none, like the available days.
        location?.let {
          put(
              LOCATION,
              mapOf(
                  LATITUDE to it.latitude,
                  LONGITUDE to it.longitude,
                  LOCATION_NAME to it.name.trim(),
              ),
          )
        }
        if (this@toFirestoreData is ItemListing) {
          put("refundValue", refundValue)
          // Left out when there is no limit, so the document looks like one written before the
          // field existed.
          maxRentalDays?.let { put("maxRentalDays", it) }
        }
      }

  /**
   * The fields an edit writes with update(). "paused" is left out, so only setPaused changes it. A
   * rental limit, available days or location the listing no longer has are deleted, since update()
   * keeps the fields it is not given.
   */
  private fun Listing.toFirestoreEdit(
      ownerUid: String,
      imagePaths: List<String>,
  ): Map<String, Any> = buildMap {
    putAll(toFirestoreData(ownerUid, imagePaths))
    if (this@toFirestoreEdit is ItemListing && maxRentalDays == null) {
      put("maxRentalDays", FieldValue.delete())
    }
    if (availableDates.isEmpty()) put(AVAILABLE_DATES, FieldValue.delete())
    if (location == null) put(LOCATION, FieldValue.delete())
  }

  private fun DocumentSnapshot.imagePaths(): List<String> =
      (get("imagePaths") as? List<*>).orEmpty().filterIsInstance<String>()

  /**
   * The stored available days. Absent on a listing written before the field existed, which reads as
   * no day.
   *
   * An entry that is not a day is left out, where a malformed `maxRentalDays` hides the whole
   * listing. The difference is deliberate: the rules check `maxRentalDays`, so a wrong one means a
   * broken document, but they cannot check each entry of a list, so a wrong one is expected and
   * must not hide the listing from everyone.
   */
  private fun DocumentSnapshot.availableDates(): Set<LocalDate> =
      (get(AVAILABLE_DATES) as? List<*>)
          .orEmpty()
          .filter { it is Long || it is Int }
          .mapNotNull { runCatching { LocalDate.ofEpochDay((it as Number).toLong()) }.getOrNull() }
          .toSet()

  /**
   * The stored location, or null when there is none, as on a listing written before the field
   * existed. A malformed one also reads as null rather than hiding the listing, like the available
   * days: the listing is still worth showing without it. An out-of-range or otherwise invalid
   * location (as [ListingRules.isValidLocation] checks, the name once trimmed) also reads as null:
   * an edit form that kept it would fail every save, while reading it as none keeps the listing
   * editable. The name is trimmed, as the app writes it, since the rules accept one with spaces
   * around it.
   */
  private fun DocumentSnapshot.location(): ListingLocation? {
    val data = get(LOCATION) as? Map<*, *> ?: return null
    // Firestore returns a Long for a whole number such as 46, so read any Number.
    val latitude = (data[LATITUDE] as? Number)?.toDouble() ?: return null
    val longitude = (data[LONGITUDE] as? Number)?.toDouble() ?: return null
    val name = data[LOCATION_NAME] as? String ?: return null
    return ListingLocation(latitude, longitude, name.trim()).takeIf(ListingRules::isValidLocation)
  }

  /**
   * Rebuilds a listing from its Firestore document, the reverse of [toFirestoreData].
   *
   * Returns null for a document that does not have the expected fields, so one malformed listing
   * (for example written before the security rules checked them) does not hide all the others.
   * `get() as?` is used instead of getString/getDouble, which throw on a field of the wrong type.
   */
  private fun DocumentSnapshot.toListing(): Listing? {
    val category = (get("category") as? String)?.let(ListingCategory::fromId)
    val title = get("title") as? String
    val description = get("description") as? String
    // Firestore returns a Long for a whole number such as 8, so read any Number.
    val price = (get("price") as? Number)?.toDouble()
    // The rules require it on every listing, so a document without it is malformed.
    val ownerUid = get("ownerUid") as? String
    // Required by the rules too: Browse only queries listings that store it.
    val paused = get(PAUSED) as? Boolean
    if (
        category == null ||
            title == null ||
            description == null ||
            price == null ||
            ownerUid == null ||
            paused == null
    )
        return null
    val imagePaths = imagePaths()
    val availableDates = availableDates()
    val location = location()

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
              isPaused = paused,
              availableDates = availableDates,
              location = location,
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
            isPaused = paused,
            availableDates = availableDates,
            location = location,
        )
      }
      else -> null
    }
  }

  private companion object {
    const val LISTINGS_COLLECTION = "listings"
    const val PAUSED = "paused"
    const val AVAILABLE_DATES = "availableDates"
    const val LOCATION = "location"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val LOCATION_NAME = "name"

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
