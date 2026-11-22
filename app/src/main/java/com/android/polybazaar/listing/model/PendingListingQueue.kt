// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * A listing created offline, kept on the device until it is published.
 *
 * @property id the Firestore document id the listing will be published under.
 * @property ownerUid the user who created it.
 * @property listing the listing, its [Listing.images] pointing to copies kept with the draft.
 */
data class PendingListing(val id: String, val ownerUid: String, val listing: Listing)

/** The listings created offline, kept on the device so they survive the app being closed. */
interface PendingListingQueue {

  /** The drafts kept, loaded from the device when the queue is created. */
  val pending: StateFlow<List<PendingListing>>

  /**
   * Keeps [listing] as a draft of the signed-in user, to publish under [id]. Its photos are copied,
   * so the draft does not depend on the camera files or on gallery read grants.
   *
   * Throws when nobody is signed in or a photo cannot be copied; nothing is kept then.
   *
   * [id] must be new, from [ListingRepository.newListingId]: reusing the id of a kept draft is not
   * supported, as a failure could delete that draft and `File.renameTo` does not replace an
   * existing file on Windows.
   */
  suspend fun enqueue(id: String, listing: Listing)

  /** Deletes the draft [id] and its photo copies. */
  suspend fun remove(id: String)
}

/**
 * Keeps each draft in its own folder of app storage: `pending_listings/<id>/` holds `draft.json`
 * and the photos `photo_0.jpg`, `photo_1.jpg`, and so on.
 *
 * @param currentUid the signed-in user's uid, or null when nobody is signed in.
 * @param ioDispatcher runs the disk work.
 */
class FilePendingListingQueue(
    context: Context,
    private val currentUid: () -> String? = { FirebaseAuth.getInstance().currentUser?.uid },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PendingListingQueue {

  private val contentResolver: ContentResolver = context.contentResolver
  private val directory = File(context.filesDir, DIRECTORY)

  // Read at once: a few small files, and the drafts must be known before anything uses them.
  private val _pending = MutableStateFlow(loadAll())
  override val pending: StateFlow<List<PendingListing>> = _pending.asStateFlow()

  override suspend fun enqueue(id: String, listing: Listing) {
    val ownerUid = currentUid() ?: throw AppException(AppError.UNAUTHENTICATED)
    val folder = folderOf(id)
    withContext(ioDispatcher) {
      try {
        if (!folder.mkdirs() && !folder.isDirectory) throw IOException("Cannot create $folder")
        val photos =
            listing.images.mapIndexed { index, photo -> copy(photo, photoFile(folder, index)) }
        val draft = PendingListing(id, ownerUid, listing.withImages(photos))
        // Written aside then renamed, so a crash never leaves a half-written draft.
        val temporary = File(folder, "$DRAFT_FILE.tmp")
        temporary.writeText(draft.toJson().toString())
        if (!temporary.renameTo(File(folder, DRAFT_FILE))) {
          throw IOException("Cannot save draft $id")
        }
        // Right after the rename, with no suspension between: a draft on disk is always listed.
        _pending.update { drafts -> drafts.filterNot { it.id == id } + draft }
      } catch (exception: Exception) {
        // Also on cancellation: a draft only partly copied must not be loaded later.
        folder.deleteRecursively()
        throw exception
      }
    }
  }

  override suspend fun remove(id: String) {
    val folder = folderOf(id)
    withContext(ioDispatcher) {
      if (!folder.deleteRecursively()) Log.w(LOG_TAG, "Could not delete every file of draft $id")
    }
    _pending.update { drafts -> drafts.filterNot { it.id == id } }
  }

  /** The folder of draft [id], refusing an id that could point outside [directory]. */
  private fun folderOf(id: String): File {
    require(id.matches(SAFE_ID)) { "Invalid draft id" }
    return File(directory, id)
  }

  private fun photoFile(folder: File, index: Int) = File(folder, "photo_$index.jpg")

  /** Copies [photo], from the gallery (content://) or the camera (file://), to [target]. */
  private fun copy(photo: Uri, target: File): Uri {
    val input = contentResolver.openInputStream(photo) ?: throw IOException("Cannot read $photo")
    input.use { source -> target.outputStream().use { source.copyTo(it) } }
    return Uri.fromFile(target)
  }

  /** Every draft that can be read. One that cannot is skipped, never crashing the app. */
  private fun loadAll(): List<PendingListing> =
      directory
          .listFiles()
          .orEmpty()
          .filter { it.isDirectory }
          .mapNotNull { folder ->
            try {
              read(folder)
            } catch (exception: Exception) {
              Log.w(LOG_TAG, "Skipped unreadable draft ${folder.name}", exception)
              null
            }
          }

  private fun read(folder: File): PendingListing {
    val json = JSONObject(File(folder, DRAFT_FILE).readText())
    val photos =
        (0 until json.getInt(PHOTO_COUNT)).map { index ->
          val file = photoFile(folder, index)
          if (!file.isFile) throw IOException("Photo $index is missing")
          Uri.fromFile(file)
        }
    val category =
        ListingCategory.fromId(json.getString(CATEGORY))
            ?: throw IOException("Unknown category ${json.getString(CATEGORY)}")
    val dates = json.getJSONArray(AVAILABLE_DATES)
    val availableDates = (0 until dates.length()).map { LocalDate.ofEpochDay(dates.getLong(it)) }
    val location =
        json.optJSONObject(LOCATION)?.let {
          ListingLocation(it.getDouble(LATITUDE), it.getDouble(LONGITUDE), it.getString(NAME))
        }
    val title = json.getString(TITLE)
    val description = json.getString(DESCRIPTION)
    val price = json.getDouble(PRICE)
    val listing =
        when (ListingType.valueOf(json.getString(TYPE))) {
          ListingType.ITEM ->
              ItemListing(
                  category = category,
                  price = price,
                  title = title,
                  description = description,
                  refundValue = json.getDouble(REFUND_VALUE),
                  maxRentalDays =
                      if (json.has(MAX_RENTAL_DAYS)) json.getInt(MAX_RENTAL_DAYS) else null,
                  images = photos,
                  availableDates = availableDates.toSet(),
                  location = location,
              )
          ListingType.SERVICE ->
              ServiceListing(
                  category = category,
                  price = price,
                  title = title,
                  description = description,
                  images = photos,
                  availableDates = availableDates.toSet(),
                  location = location,
              )
        }
    return PendingListing(folder.name, json.getString(OWNER_UID), listing)
  }

  private fun PendingListing.toJson(): JSONObject {
    val json =
        JSONObject()
            .put(OWNER_UID, ownerUid)
            .put(TYPE, listing.type.name)
            .put(TITLE, listing.title)
            .put(DESCRIPTION, listing.description)
            .put(PRICE, listing.price)
            .put(CATEGORY, listing.category.id)
            .put(PHOTO_COUNT, listing.images.size)
            .put(AVAILABLE_DATES, JSONArray(listing.availableDates.map { it.toEpochDay() }))
    (listing as? ItemListing)?.let { item ->
      json.put(REFUND_VALUE, item.refundValue)
      item.maxRentalDays?.let { json.put(MAX_RENTAL_DAYS, it) }
    }
    listing.location?.let {
      json.put(
          LOCATION,
          JSONObject().put(LATITUDE, it.latitude).put(LONGITUDE, it.longitude).put(NAME, it.name),
      )
    }
    return json
  }

  private fun Listing.withImages(images: List<Uri>): Listing =
      when (this) {
        is ItemListing -> copy(images = images)
        is ServiceListing -> copy(images = images)
      }

  private companion object {
    const val DIRECTORY = "pending_listings"
    const val DRAFT_FILE = "draft.json"
    const val LOG_TAG = "PendingListingQueue"

    /** Firestore's generated ids are letters and digits; no separator or dot can sneak in. */
    val SAFE_ID = Regex("[A-Za-z0-9_-]+")

    const val OWNER_UID = "ownerUid"
    const val TYPE = "type"
    const val TITLE = "title"
    const val DESCRIPTION = "description"
    const val PRICE = "price"
    const val REFUND_VALUE = "refundValue"
    const val CATEGORY = "category"
    const val MAX_RENTAL_DAYS = "maxRentalDays"
    const val AVAILABLE_DATES = "availableDates"
    const val PHOTO_COUNT = "photoCount"
    const val LOCATION = "location"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val NAME = "name"
  }
}
