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
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/**
 * A change to a published listing made offline, kept on the device until it is saved. A later PR
 * adds an edit of the listing's fields.
 *
 * @property ownerUid the user who made it.
 * @property paused the paused state to save, or null to leave it as it is.
 * @property isDeleted whether the listing is to be deleted, which overrides any other change.
 */
data class PendingListingChange(
    val listingId: String,
    val ownerUid: String,
    val paused: Boolean? = null,
    val isDeleted: Boolean = false,
) {

  /** This change followed by a later one: a later pause or resume wins, a delete replaces all. */
  fun followedBy(paused: Boolean?, delete: Boolean): PendingListingChange =
      if (delete) copy(paused = null, isDeleted = true) else copy(paused = paused ?: this.paused)
}

/**
 * The listings created offline and the changes made offline to published ones, kept on the device
 * so they survive the app being closed.
 */
interface PendingListingQueue {

  /** The drafts kept, loaded from the device when the queue is created. */
  val pending: StateFlow<List<PendingListing>>

  /** The changes kept, at most one per listing, loaded from the device with [pending]. */
  val changes: StateFlow<List<PendingListingChange>>

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

  /**
   * Keeps a change of the signed-in user to the published listing [listingId]: pausing it ([paused]
   * true), resuming it (false) or deleting it ([delete]). It is merged into the change already kept
   * for that listing (see [PendingListingChange.followedBy]).
   *
   * Throws when nobody is signed in or the change cannot be written; the change kept before stays.
   */
  suspend fun enqueueChange(listingId: String, paused: Boolean? = null, delete: Boolean = false)

  /**
   * Forgets [change] once saved or dropped. Does nothing if a later change to the same listing
   * replaced it meanwhile, so that later change is still saved.
   */
  suspend fun removeChange(change: PendingListingChange)
}

/**
 * Keeps each draft in its own folder of app storage: `pending_listings/<id>/` holds `draft.json`
 * and the photos `photo_0.jpg`, `photo_1.jpg`, and so on. A change to a published listing is kept
 * as `pending_listings/<listingId>/change.json`.
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

  // After loadAll, which deletes the folders of saves cut short.
  private val _changes = MutableStateFlow(loadChanges())
  override val changes: StateFlow<List<PendingListingChange>> = _changes.asStateFlow()

  /** Makes merging a change into the kept one, and removing it, one step each. */
  private val changeLock = Mutex()

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
        // A photo or the draft could not be written: nothing of this draft is kept.
        deleteDraftFiles(folder)
        throw exception
      }
    }
  }

  override suspend fun remove(id: String) {
    val folder = folderOf(id)
    withContext(ioDispatcher) {
      if (!deleteDraftFiles(folder)) Log.w(LOG_TAG, "Could not delete every file of draft $id")
    }
    _pending.update { drafts -> drafts.filterNot { it.id == id } }
  }

  override suspend fun enqueueChange(listingId: String, paused: Boolean?, delete: Boolean) {
    val ownerUid = currentUid() ?: throw AppException(AppError.UNAUTHENTICATED)
    val folder = folderOf(listingId)
    changeLock.withLock {
      val change =
          (_changes.value.find { it.listingId == listingId }
                  ?: PendingListingChange(listingId, ownerUid))
              .copy(ownerUid = ownerUid)
              .followedBy(paused, delete)
      withContext(ioDispatcher) {
        if (!folder.mkdirs() && !folder.isDirectory) throw IOException("Cannot create $folder")
        val temporary = File(folder, "$CHANGE_FILE.tmp")
        temporary.writeText(change.toJson().toString())
        // Written aside then moved over the earlier change in one atomic step, so a crash leaves
        // either change, never half of one. Unlike File.renameTo, Files.move replaces the earlier
        // file on Windows too.
        Files.move(
            temporary.toPath(),
            File(folder, CHANGE_FILE).toPath(),
            StandardCopyOption.ATOMIC_MOVE,
        )
      }
      _changes.update { changes -> changes.filterNot { it.listingId == listingId } + change }
    }
  }

  override suspend fun removeChange(change: PendingListingChange) {
    val folder = folderOf(change.listingId)
    changeLock.withLock {
      if (change !in _changes.value) return
      withContext(ioDispatcher) {
        if (!File(folder, CHANGE_FILE).delete()) {
          Log.w(LOG_TAG, "Could not delete the change to ${change.listingId}")
        }
        // Only when empty: a draft with the same id keeps it.
        folder.delete()
      }
      _changes.update { changes -> changes - change }
    }
  }

  /**
   * Deletes the draft files in [folder], keeping a change to the same listing; the folder goes once
   * empty. Returns whether every draft file was deleted.
   */
  private fun deleteDraftFiles(folder: File): Boolean {
    val deleted =
        folder
            .listFiles()
            .orEmpty()
            .filterNot { it.name.startsWith(CHANGE_FILE) }
            .all { it.delete() }
    folder.delete()
    return deleted
  }

  /** The folder of listing [id], refusing an id that could point outside [directory]. */
  private fun folderOf(id: String): File {
    require(id.matches(SAFE_ID)) { "Invalid listing id" }
    return File(directory, id)
  }

  private fun photoFile(folder: File, index: Int) = File(folder, "photo_$index.jpg")

  /** Copies [photo], from the gallery (content://) or the camera (file://), to [target]. */
  private fun copy(photo: Uri, target: File): Uri {
    val input = contentResolver.openInputStream(photo) ?: throw IOException("Cannot read $photo")
    input.use { source -> target.outputStream().use { source.copyTo(it) } }
    return Uri.fromFile(target)
  }

  /**
   * Every draft that can be read. One that cannot is skipped, never crashing the app. A folder with
   * neither `draft.json` nor `change.json` is a save cut short, by the app being killed say, as
   * they are written last: it is deleted.
   */
  private fun loadAll(): List<PendingListing> =
      directory
          .listFiles()
          .orEmpty()
          .filter { it.isDirectory }
          .mapNotNull { folder ->
            if (!File(folder, DRAFT_FILE).isFile) {
              if (!File(folder, CHANGE_FILE).isFile) folder.deleteRecursively()
              return@mapNotNull null
            }
            try {
              read(folder)
            } catch (exception: Exception) {
              Log.w(LOG_TAG, "Skipped unreadable draft ${folder.name}", exception)
              null
            }
          }

  /** Every change that can be read; one that cannot is skipped, as in [loadAll]. */
  private fun loadChanges(): List<PendingListingChange> =
      directory
          .listFiles()
          .orEmpty()
          .map { File(it, CHANGE_FILE) }
          .filter { it.isFile }
          .mapNotNull { file ->
            val listingId = file.parentFile!!.name
            try {
              val json = JSONObject(file.readText())
              PendingListingChange(
                  listingId = listingId,
                  ownerUid = json.getString(OWNER_UID),
                  paused = if (json.has(PAUSED)) json.getBoolean(PAUSED) else null,
                  isDeleted = json.getBoolean(DELETED),
              )
            } catch (exception: Exception) {
              Log.w(LOG_TAG, "Skipped unreadable change to $listingId", exception)
              null
            }
          }

  private fun PendingListingChange.toJson(): JSONObject {
    val json = JSONObject().put(OWNER_UID, ownerUid).put(DELETED, isDeleted)
    paused?.let { json.put(PAUSED, it) }
    return json
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
    const val CHANGE_FILE = "change.json"
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
    const val PAUSED = "paused"
    const val DELETED = "deleted"
  }
}
