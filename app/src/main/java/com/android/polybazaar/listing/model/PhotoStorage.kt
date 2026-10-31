// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Keeps the photos taken with the camera for a listing until they are uploaded. Photos picked from
 * the gallery belong to the user and are never deleted.
 */
interface PhotoStorage {

  /** Creates an empty photo for the camera app to fill, or returns null when it cannot. */
  fun newPhoto(): TakenPhoto?

  /** Deletes [photo] if it was taken with the camera. */
  fun delete(photo: Uri)

  /** Deletes every taken photo except those in [keep], which may also hold gallery photos. */
  fun deleteAllExcept(keep: List<Uri>)
}

/**
 * A photo taken with the camera.
 *
 * @property uri where the app reads the photo.
 * @property cameraUri where the camera app writes it.
 */
data class TakenPhoto(val uri: Uri, val cameraUri: Uri)

/**
 * Stores photos in app storage, not the cache, which Android can clear before the listing is
 * uploaded. So they are deleted by hand once no longer needed.
 */
class FilePhotoStorage(context: Context) : PhotoStorage {

  private val directory = File(context.filesDir, PHOTO_DIRECTORY)
  private val authority = "${context.packageName}.fileprovider"

  /** Returns null when the file cannot be created, for example when storage is full. */
  override fun newPhoto(): TakenPhoto? =
      try {
        directory.mkdirs()
        val file = File.createTempFile("photo_", ".jpg", directory)
        TakenPhoto(uri = Uri.fromFile(file), cameraUri = cameraUriFor(file))
      } catch (e: IOException) {
        Log.w(LOG_TAG, "Could not create a photo file", e)
        null
      }

  override fun delete(photo: Uri) {
    val file = takenFileOrNull(photo) ?: return
    // Compared as canonical files, so separators and ".." segments do not matter.
    if (file.canonicalFile.parentFile == directory.canonicalFile) deleteFile(file)
  }

  override fun deleteAllExcept(keep: List<Uri>) {
    val kept = keep.mapNotNull(::takenFileOrNull).map { it.canonicalFile }
    directory.listFiles().orEmpty().filterNot { it.canonicalFile in kept }.forEach(::deleteFile)
  }

  /**
   * The URI under which the manifest's FileProvider shares [file] with the camera app. Built by
   * hand because FileProvider.getUriForFile compares paths using "/" separators, which fails in
   * Robolectric tests on Windows.
   */
  internal fun cameraUriFor(file: File): Uri =
      Uri.Builder()
          .scheme(ContentResolver.SCHEME_CONTENT)
          .authority(authority)
          .appendPath(PHOTO_DIRECTORY)
          .appendPath(file.name)
          .build()

  private fun takenFileOrNull(photo: Uri): File? =
      if (photo.scheme == ContentResolver.SCHEME_FILE) photo.path?.let(::File) else null

  /** Deletes [file], only logging a failure: a leftover photo takes space but breaks nothing. */
  private fun deleteFile(file: File) {
    if (file.exists() && !file.delete()) Log.w(LOG_TAG, "Could not delete photo ${file.name}")
  }

  private companion object {
    /** Both the name and the path of the files-path declared in res/xml/file_paths.xml. */
    const val PHOTO_DIRECTORY = "listing_photos"
    const val LOG_TAG = "FilePhotoStorage"
  }
}
