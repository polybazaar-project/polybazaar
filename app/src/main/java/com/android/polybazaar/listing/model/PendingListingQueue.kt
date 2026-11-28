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
 * A change to a published listing made offline, kept on the device until it is saved.
 *
 * @property ownerUid the user who made it.
 * @property paused the paused state to save, or null to leave it as it is.
 * @property isDeleted whether the listing is to be deleted, which overrides any other change.
 * @property edit the listing as edited, or null when its fields are left as they are. Its
 *   [Listing.imagePaths] are the stored photos kept, its [Listing.images] the new photos, copies
 *   kept with the change.
 * @property base the listing as stored when the first edit kept was made, set with [edit]: the edit
 *   is only saved while the listing is still like this, so a change made elsewhere is never
 *   overwritten.
 */
data class PendingListingChange(
    val listingId: String,
    val ownerUid: String,
    val paused: Boolean? = null,
    val isDeleted: Boolean = false,
    val edit: Listing? = null,
    val base: Listing? = null,
) {

  /**
   * This change followed by a later one: a later pause, resume or edit wins, an edit keeping the
   * [base] of the first one, and a delete replaces all.
   */
  fun followedBy(
      paused: Boolean?,
      delete: Boolean,
      edit: Listing? = null,
      base: Listing? = null,
  ): PendingListingChange =
      if (delete) PendingListingChange(listingId, ownerUid, isDeleted = true)
      else copy(paused = paused ?: this.paused, edit = edit ?: this.edit, base = this.base ?: base)
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
   * true), resuming it (false), deleting it ([delete]) or editing it ([edit], made from [base], the
   * listing as stored). It is merged into the change already kept for that listing (see
   * [PendingListingChange.followedBy]). The edit's new photos are copied, like a draft's.
   *
   * Throws when nobody is signed in or the change cannot be written; the change kept before stays.
   */
  suspend fun enqueueChange(
      listingId: String,
      paused: Boolean? = null,
      delete: Boolean = false,
      edit: Listing? = null,
      base: Listing? = null,
  )

  /**
   * Forgets [change] once saved or dropped. Does nothing if a later change to the same listing
   * replaced it meanwhile, so that later change is still saved.
   */
  suspend fun removeChange(change: PendingListingChange)
}

/**
 * Keeps each draft in its own folder of app storage: `pending_listings/<id>/` holds `draft.json`
 * and the photos `photo_0.jpg`, `photo_1.jpg`, and so on. A change to a published listing is kept
 * as `pending_listings/<listingId>/change.json`, the new photos of its edit beside it as
 * `change_photo_<n>.jpg`.
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

  override suspend fun enqueueChange(
      listingId: String,
      paused: Boolean?,
      delete: Boolean,
      edit: Listing?,
      base: Listing?,
  ) {
    val ownerUid = currentUid() ?: throw AppException(AppError.UNAUTHENTICATED)
    val folder = folderOf(listingId)
    changeLock.withLock {
      val earlier = _changes.value.find { it.listingId == listingId }
      withContext(ioDispatcher) {
        if (!folder.mkdirs() && !folder.isDirectory) throw IOException("Cannot create $folder")
        val change =
            try {
              // Photos already kept with the change are not copied again.
              val photos =
                  edit?.images?.map { if (isIn(folder, it)) it else copyChangePhoto(it, folder) }
              (earlier ?: PendingListingChange(listingId, ownerUid))
                  .copy(ownerUid = ownerUid)
                  .followedBy(paused, delete, edit?.withImages(photos.orEmpty()), base)
                  .also { writeChange(folder, it) }
            } catch (exception: Exception) {
              // The earlier change stays, with its photos only.
              deleteUnusedChangePhotos(folder, earlier)
              throw exception
            }
        // Right after the move, with no suspension between, like a draft.
        _changes.update { changes -> changes.filterNot { it.listingId == listingId } + change }
        // Only once the new change no longer points to them. Left behind by a crash, they are
        // deleted when the app starts again.
        deleteUnusedChangePhotos(folder, change)
      }
    }
  }

  /**
   * Writes [change] aside then moves it over the earlier change in one atomic step, so a crash
   * leaves either change, never half of one. Unlike File.renameTo, Files.move replaces the earlier
   * file on Windows too.
   */
  private fun writeChange(folder: File, change: PendingListingChange) {
    val temporary = File(folder, "$CHANGE_FILE.tmp")
    temporary.writeText(change.toJson().toString())
    Files.move(
        temporary.toPath(),
        File(folder, CHANGE_FILE).toPath(),
        StandardCopyOption.ATOMIC_MOVE,
    )
  }

  /** Copies an edit's new [photo] into [folder], under a name no other photo there has. */
  private fun copyChangePhoto(photo: Uri, folder: File): Uri =
      copy(photo, File.createTempFile(CHANGE_PHOTO_PREFIX, ".jpg", folder))

  /** Whether [photo] is a file directly in [folder], such as a photo kept with a change. */
  private fun isIn(folder: File, photo: Uri): Boolean =
      photo.scheme == "file" && photo.path?.let { File(it).parentFile } == folder

  /** Deletes the edit photos in [folder] that [change] does not use, all of them for null. */
  private fun deleteUnusedChangePhotos(folder: File, change: PendingListingChange?) {
    val used = change?.edit?.images.orEmpty().mapNotNull { it.path?.let(::File) }.toSet()
    folder
        .listFiles()
        .orEmpty()
        .filter { it.name.startsWith(CHANGE_PHOTO_PREFIX) && it !in used }
        .forEach { if (!it.delete()) Log.w(LOG_TAG, "Could not delete ${it.name}") }
  }

  override suspend fun removeChange(change: PendingListingChange) {
    val folder = folderOf(change.listingId)
    changeLock.withLock {
      if (change !in _changes.value) return
      withContext(ioDispatcher) {
        // Its photos only once it no longer points to them.
        if (File(folder, CHANGE_FILE).delete()) deleteUnusedChangePhotos(folder, null)
        else Log.w(LOG_TAG, "Could not delete the change to ${change.listingId}")
        // Only when empty: a draft with the same id keeps it.
        folder.delete()
      }
      _changes.update { changes -> changes - change }
    }
  }

  /**
   * Deletes the draft files in [folder], keeping a change to the same listing; the folder goes once
   * empty. Returns whether every draft file, and the folder unless it keeps a change, was deleted.
   */
  private fun deleteDraftFiles(folder: File): Boolean {
    val deleted =
        folder
            .listFiles()
            .orEmpty()
            .filterNot {
              listOf(CHANGE_FILE, CHANGE_PHOTO_PREFIX).any { p -> it.name.startsWith(p) }
            }
            .map { it.delete() } // Every file is tried, even after one fails.
            .all { it }
    return deleted && (folder.delete() || !folder.list().isNullOrEmpty())
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

  /**
   * Every change that can be read; one that cannot is skipped, as in [loadAll]. The edit photos a
   * replaced change left behind, the app being killed before deleting them, are deleted.
   */
  private fun loadChanges(): List<PendingListingChange> =
      directory
          .listFiles()
          .orEmpty()
          .map { File(it, CHANGE_FILE) }
          .filter { it.isFile }
          .mapNotNull { file ->
            val folder = file.parentFile!!
            try {
              val json = JSONObject(file.readText())
              PendingListingChange(
                      listingId = folder.name,
                      ownerUid = json.getString(OWNER_UID),
                      paused = if (json.has(PAUSED)) json.getBoolean(PAUSED) else null,
                      isDeleted = json.getBoolean(DELETED),
                      edit = json.optJSONObject(EDIT)?.let { readEdit(it, folder) },
                      base =
                          json.optJSONObject(BASE)?.let {
                            readListing(it, emptyList(), folder.name)
                          },
                  )
                  .also { deleteUnusedChangePhotos(folder, it) }
            } catch (exception: Exception) {
              Log.w(LOG_TAG, "Skipped unreadable change to ${folder.name}", exception)
              null
            }
          }

  /** The edit kept in [folder], its new photos the files [json] names there. */
  private fun readEdit(json: JSONObject, folder: File): Listing {
    val names = json.getJSONArray(PHOTOS)
    val photos =
        (0 until names.length()).map { index ->
          val file = File(folder, names.getString(index))
          // A name with a separator would point outside the folder.
          if (!file.isFile || file.parentFile != folder)
              throw IOException("Photo $index is missing")
          Uri.fromFile(file)
        }
    return readListing(json, photos, folder.name)
  }

  private fun PendingListingChange.toJson(): JSONObject {
    val json = JSONObject().put(OWNER_UID, ownerUid).put(DELETED, isDeleted)
    paused?.let { json.put(PAUSED, it) }
    edit?.let { edit ->
      val names = edit.images.map { photo -> photo.path?.let { File(it).name } }
      json.put(EDIT, edit.toJson().put(PHOTOS, JSONArray(names)))
    }
    base?.let { json.put(BASE, it.toJson()) }
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
    return PendingListing(folder.name, json.getString(OWNER_UID), readListing(json, photos, null))
  }

  /** The listing [toJson] kept in [json], with [photos] as its new photos and [listingId]. */
  private fun readListing(json: JSONObject, photos: List<Uri>, listingId: String?): Listing {
    val category =
        ListingCategory.fromId(json.getString(CATEGORY))
            ?: throw IOException("Unknown category ${json.getString(CATEGORY)}")
    val dates = json.getJSONArray(AVAILABLE_DATES)
    val availableDates = (0 until dates.length()).map { LocalDate.ofEpochDay(dates.getLong(it)) }
    val location =
        json.optJSONObject(LOCATION)?.let {
          ListingLocation(it.getDouble(LATITUDE), it.getDouble(LONGITUDE), it.getString(NAME))
        }
    // Absent from a draft, which has no stored photo.
    val paths = json.optJSONArray(IMAGE_PATHS) ?: JSONArray()
    val imagePaths = (0 until paths.length()).map { paths.getString(it) }
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
                  imagePaths = imagePaths,
                  listingId = listingId,
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
                  imagePaths = imagePaths,
                  listingId = listingId,
                  availableDates = availableDates.toSet(),
                  location = location,
              )
        }
    return listing
  }

  private fun PendingListing.toJson(): JSONObject =
      listing.toJson().put(OWNER_UID, ownerUid).put(PHOTO_COUNT, listing.images.size)

  /** The fields of the listing and its stored photos, read back by [readListing]. */
  private fun Listing.toJson(): JSONObject {
    val json =
        JSONObject()
            .put(TYPE, type.name)
            .put(TITLE, title)
            .put(DESCRIPTION, description)
            .put(PRICE, price)
            .put(CATEGORY, category.id)
            .put(IMAGE_PATHS, JSONArray(imagePaths))
            .put(AVAILABLE_DATES, JSONArray(availableDates.map { it.toEpochDay() }))
    (this as? ItemListing)?.let { item ->
      json.put(REFUND_VALUE, item.refundValue)
      item.maxRentalDays?.let { json.put(MAX_RENTAL_DAYS, it) }
    }
    location?.let {
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
    /** Kept by deleteDraftFiles, like [CHANGE_FILE]. */
    const val CHANGE_PHOTO_PREFIX = "change_photo_"
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
    const val EDIT = "edit"
    const val BASE = "base"
    const val PHOTOS = "photos"
    const val IMAGE_PATHS = "imagePaths"
  }
}
