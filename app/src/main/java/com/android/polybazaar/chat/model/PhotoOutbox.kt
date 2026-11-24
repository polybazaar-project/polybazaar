// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

import android.content.Context
import android.net.Uri
import android.util.Log
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import com.android.polybazaar.photo.PhotoCompressor
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * A chat photo that could not be sent for want of a connection, kept on the device until it is.
 *
 * @property messageId the ID of its message, reused by every send so it is never sent twice.
 * @property conversationId the conversation it is sent in.
 * @property senderUid the user who sent it.
 * @property photo the compressed copy of the photo kept in the outbox.
 * @property caption the trimmed caption, or null for a photo alone.
 * @property sending true while it is being sent; only kept in memory, so it is false again after
 *   the app is restarted.
 * @property error why its last send failed, when it failed for another reason than the connection:
 *   it is then not sent again, and can only be removed.
 */
data class QueuedPhoto(
    val messageId: String,
    val conversationId: String,
    val senderUid: String,
    val photo: Uri,
    val caption: String?,
    val sending: Boolean = false,
    val error: AppError? = null,
)

/** The chat photos waiting for a connection, kept on the device so they survive the app closing. */
interface PhotoOutbox {

  /** Every queued photo, of any user, oldest first. Loaded from the device on creation. */
  val queued: StateFlow<List<QueuedPhoto>>

  /**
   * Keeps [photo] to send later in [conversationId] under [messageId], for the signed-in user. A
   * compressed copy of the photo is kept, so it takes little space and no longer depends on the
   * camera file or a gallery read grant.
   *
   * Throws when nobody is signed in or the photo cannot be read or compressed; nothing is kept
   * then.
   */
  suspend fun enqueue(conversationId: String, messageId: String, photo: Uri, caption: String?)

  /**
   * Marks the photo of [messageId] as [QueuedPhoto.sending], so it is not sent twice at once and
   * can't be removed meanwhile. Returns false, changing nothing, when it is not queued or already
   * sending. Call [release] once the send ends.
   */
  fun claim(messageId: String): Boolean

  /** Ends the [claim] of the photo of [messageId]. */
  fun release(messageId: String)

  /** Saves on the device that the photo of [messageId] failed with [error], see [QueuedPhoto]. */
  suspend fun markFailed(messageId: String, error: AppError)

  /**
   * Deletes the photo of [messageId] and its copy. Returns false and does nothing while it is
   * [QueuedPhoto.sending] or when it is not queued.
   */
  suspend fun remove(messageId: String): Boolean
}

/**
 * Keeps each photo in its own folder of app storage: `photo_outbox/<messageId>/` holds `entry.json`
 * and `photo.jpg`.
 *
 * @param photoCompressor turns the photo into the JPEG kept in `photo.jpg`, a few hundred KB
 *   instead of a camera photo of several MB.
 * @param currentUid the signed-in user's uid, or null when nobody is signed in.
 * @param ioDispatcher runs the disk work.
 * @param now the current time in milliseconds, to keep the photos in the order they were queued.
 */
class FilePhotoOutbox(
    context: Context,
    private val photoCompressor: PhotoCompressor,
    private val currentUid: () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) : PhotoOutbox {

  private val directory = File(context.filesDir, DIRECTORY)

  // Read at once: a few small files, and the queue must be known before the sender starts.
  private val _queued = MutableStateFlow(loadAll())
  override val queued: StateFlow<List<QueuedPhoto>> = _queued.asStateFlow()

  override suspend fun enqueue(
      conversationId: String,
      messageId: String,
      photo: Uri,
      caption: String?,
  ) {
    val senderUid = currentUid() ?: throw AppException(AppError.UNAUTHENTICATED)
    val folder = folderOf(messageId)
    withContext(ioDispatcher) {
      try {
        if (!folder.mkdirs() && !folder.isDirectory) throw IOException("Cannot create $folder")
        val copy = File(folder, PHOTO_FILE)
        copy.writeBytes(photoCompressor.compress(photo))
        val entry =
            Entry(
                QueuedPhoto(messageId, conversationId, senderUid, Uri.fromFile(copy), caption),
                queuedAt = now(),
            )
        write(folder, entry)
        // Right after the rename, with no suspension between: a photo on disk is always listed.
        _queued.update { photos -> photos.filterNot { it.messageId == messageId } + entry.photo }
      } catch (exception: Exception) {
        // The photo could not be compressed, or it or its entry written: nothing of it is kept.
        folder.deleteRecursively()
        throw exception
      }
    }
  }

  override fun claim(messageId: String): Boolean {
    var claimed = false
    _queued.update { photos ->
      val photo = photos.find { it.messageId == messageId }
      claimed = photo != null && !photo.sending
      if (claimed) photos.map { if (it.messageId == messageId) it.copy(sending = true) else it }
      else photos
    }
    return claimed
  }

  override fun release(messageId: String) {
    _queued.update { photos ->
      photos.map { if (it.messageId == messageId) it.copy(sending = false) else it }
    }
  }

  override suspend fun markFailed(messageId: String, error: AppError) {
    val folder = folderOf(messageId)
    withContext(ioDispatcher) {
      try {
        // The order on disk is kept: the entry is read back for its queuedAt.
        val entry = read(folder)
        write(folder, entry.copy(photo = entry.photo.copy(error = error)))
      } catch (exception: IOException) {
        // Still shown as failed until the app is closed; it is tried again after a restart.
        Log.w(LOG_TAG, "Could not save the failure of $messageId", exception)
      }
    }
    _queued.update { photos ->
      photos.map { if (it.messageId == messageId) it.copy(error = error) else it }
    }
  }

  override suspend fun remove(messageId: String): Boolean {
    var removed = false
    // Checked and removed at once, so a claim can't start a send of a photo being deleted.
    _queued.update { photos ->
      val photo = photos.find { it.messageId == messageId }
      removed = photo != null && !photo.sending
      if (removed) photos.filterNot { it.messageId == messageId } else photos
    }
    if (!removed) return false
    val folder = folderOf(messageId)
    withContext(ioDispatcher) {
      if (!folder.deleteRecursively()) Log.w(LOG_TAG, "Could not delete every file of $messageId")
    }
    return true
  }

  /** The folder of [messageId], refusing an ID that could point outside [directory]. */
  private fun folderOf(messageId: String): File {
    require(messageId.matches(SAFE_ID)) { "Invalid message id" }
    return File(directory, messageId)
  }

  /**
   * Every queued photo that can be read, oldest first. One that cannot is skipped, never crashing
   * the app. A folder without `entry.json` is an enqueue cut short, by the app being killed say, as
   * the entry is written last: it is deleted. A missing photo file is not checked here: its send
   * fails as for any unreadable photo, and the user can remove it.
   */
  private fun loadAll(): List<QueuedPhoto> =
      directory
          .listFiles()
          .orEmpty()
          .filter { it.isDirectory }
          .mapNotNull { folder ->
            if (!File(folder, ENTRY_FILE).isFile) {
              folder.deleteRecursively()
              return@mapNotNull null
            }
            try {
              read(folder)
            } catch (exception: Exception) {
              Log.w(LOG_TAG, "Skipped unreadable queued photo ${folder.name}", exception)
              null
            }
          }
          .sortedBy { it.queuedAt }
          .map { it.photo }

  /** Writes [entry] aside, then renames it, so a crash never leaves a half-written entry. */
  private fun write(folder: File, entry: Entry) {
    val temporary = File(folder, "$ENTRY_FILE.tmp")
    temporary.writeText(entry.toJson().toString())
    if (!temporary.renameTo(File(folder, ENTRY_FILE))) {
      throw IOException("Cannot save queued photo ${entry.photo.messageId}")
    }
  }

  private fun read(folder: File): Entry {
    val json =
        try {
          JSONObject(File(folder, ENTRY_FILE).readText())
        } catch (exception: org.json.JSONException) {
          throw IOException("Malformed entry", exception)
        }
    return try {
      Entry(
          QueuedPhoto(
              messageId = folder.name,
              conversationId = json.getString(CONVERSATION_ID),
              senderUid = json.getString(SENDER_UID),
              photo = Uri.fromFile(File(folder, PHOTO_FILE)),
              caption = if (json.has(CAPTION)) json.getString(CAPTION) else null,
              error =
                  if (json.has(ERROR)) {
                    AppError.entries.find { it.name == json.getString(ERROR) } ?: AppError.UNKNOWN
                  } else null,
          ),
          queuedAt = json.getLong(QUEUED_AT),
      )
    } catch (exception: org.json.JSONException) {
      throw IOException("Malformed entry", exception)
    }
  }

  private fun Entry.toJson(): JSONObject {
    val json =
        JSONObject()
            .put(CONVERSATION_ID, photo.conversationId)
            .put(SENDER_UID, photo.senderUid)
            .put(QUEUED_AT, queuedAt)
    photo.caption?.let { json.put(CAPTION, it) }
    photo.error?.let { json.put(ERROR, it.name) }
    return json
  }

  /** A queued photo as kept on disk, with when it was queued to keep the order. */
  private data class Entry(val photo: QueuedPhoto, val queuedAt: Long)

  private companion object {
    const val DIRECTORY = "photo_outbox"
    const val ENTRY_FILE = "entry.json"
    const val PHOTO_FILE = "photo.jpg"
    const val LOG_TAG = "PhotoOutbox"

    /** Firestore's generated ids are letters and digits; no separator or dot can sneak in. */
    val SAFE_ID = Regex("[A-Za-z0-9_-]+")

    const val CONVERSATION_ID = "conversationId"
    const val SENDER_UID = "senderUid"
    const val CAPTION = "caption"
    const val ERROR = "error"
    const val QUEUED_AT = "queuedAt"
  }
}
