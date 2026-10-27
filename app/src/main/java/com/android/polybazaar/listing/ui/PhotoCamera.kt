// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import com.android.polybazaar.auth.ui.AuthColors
import java.io.File
import java.io.IOException

/**
 * Takes listing photos with the system camera app, asking for the camera permission first.
 *
 * @property showPermissionExplanation set once the user refuses the permission, until dismissed.
 */
internal class PhotoCamera(
    val showPermissionExplanation: Boolean,
    private val start: (retakeIndex: Int?) -> Unit,
    private val dismiss: () -> Unit,
) {
  fun takePhoto() = start(null)

  fun retakePhoto(index: Int) = start(index)

  fun dismissPermissionExplanation() = dismiss()
}

/**
 * @param photosInUse the photos the form shows, which are kept when abandoned photos are deleted.
 * @param onPhotoTaken called with the new photo and, for a retake, the index of the photo it
 *   replaces.
 */
@Composable
internal fun rememberPhotoCamera(
    photosInUse: () -> List<Uri>,
    onPhotoTaken: (photo: Uri, retakeIndex: Int?) -> Unit,
): PhotoCamera {
  val context = LocalContext.current
  val currentOnPhotoTaken by rememberUpdatedState(onPhotoTaken)
  // Saveable: our activity can be recreated while the camera app is in front.
  var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
  var retakeIndex by rememberSaveable { mutableStateOf<Int?>(null) }
  var permissionDenied by rememberSaveable { mutableStateOf(false) }

  // Photos of a form the user left are never published, so they are deleted when a form opens.
  // Kept: the photos this form still shows after being recreated, and the one being taken.
  // Run on the main thread, so that no photo can be taken while the directory is cleaned.
  LaunchedEffect(Unit) {
    val pending = pendingPhoto?.let { Uri.fromFile(File(it)) }
    deleteAbandonedPhotos(context, keep = photosInUse() + listOfNotNull(pending))
  }

  val takePicture =
      rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingPhoto?.let(::File)
        pendingPhoto = null
        if (file != null) {
          if (saved) currentOnPhotoTaken(Uri.fromFile(file), retakeIndex) else deletePhotoFile(file)
        }
      }
  val launchCamera = {
    val file = newPhotoFileOrNull(context)
    if (file == null) {
      Toast.makeText(context, "Could not save the photo", Toast.LENGTH_SHORT).show()
    } else {
      pendingPhoto = file.path
      try {
        takePicture.launch(contentUriFor(context, file))
      } catch (e: ActivityNotFoundException) {
        pendingPhoto = null
        deletePhotoFile(file)
        Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show()
      }
    }
  }
  val requestPermission =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else permissionDenied = true
      }

  return PhotoCamera(
      showPermissionExplanation = permissionDenied,
      start = { index ->
        retakeIndex = index
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        ) {
          launchCamera()
        } else {
          requestPermission.launch(Manifest.permission.CAMERA)
        }
      },
      dismiss = { permissionDenied = false },
  )
}

/**
 * Photos live in app storage, not the cache, which Android can clear before the listing is
 * uploaded. So they are deleted by hand with [deleteTakenPhoto] once no longer needed.
 *
 * @return null when the file cannot be created, for example when storage is full.
 */
private fun newPhotoFileOrNull(context: Context): File? =
    try {
      val directory = photoDirectory(context).apply { mkdirs() }
      File.createTempFile("photo_", ".jpg", directory)
    } catch (e: IOException) {
      Log.w(LOG_TAG, "Could not create a photo file", e)
      null
    }

private fun photoDirectory(context: Context) = File(context.filesDir, PHOTO_DIRECTORY)

/** Deletes every camera photo except those in [keep], which may also hold gallery photos. */
internal fun deleteAbandonedPhotos(context: Context, keep: List<Uri>) {
  val kept =
      keep
          .filter { it.scheme == ContentResolver.SCHEME_FILE }
          .mapNotNull { it.path }
          .map { File(it).canonicalFile }
  photoDirectory(context)
      .listFiles()
      .orEmpty()
      .filterNot { it.canonicalFile in kept }
      .forEach(::deletePhotoFile)
}

/**
 * Deletes [photo] if the camera took it for a listing. Photos picked from the gallery belong to the
 * user and are left alone.
 */
internal fun deleteTakenPhoto(context: Context, photo: Uri) {
  if (photo.scheme != ContentResolver.SCHEME_FILE) return
  val file = File(photo.path ?: return)
  // Compared as canonical files, so separators and ".." segments do not matter.
  if (file.canonicalFile.parentFile == photoDirectory(context).canonicalFile) deletePhotoFile(file)
}

/** Deletes [file], only logging a failure: a leftover photo takes space but breaks nothing. */
private fun deletePhotoFile(file: File) {
  if (file.exists() && !file.delete()) Log.w(LOG_TAG, "Could not delete photo ${file.name}")
}

private const val LOG_TAG = "PhotoCamera"

/**
 * The URI under which the manifest's FileProvider shares [file] with the camera app. Built by hand
 * because FileProvider.getUriForFile compares paths using "/" separators, which fails in
 * Robolectric tests on Windows.
 */
internal fun contentUriFor(context: Context, file: File): Uri =
    Uri.Builder()
        .scheme(ContentResolver.SCHEME_CONTENT)
        .authority("${context.packageName}.fileprovider")
        .appendPath(PHOTO_DIRECTORY)
        .appendPath(file.name)
        .build()

/** Both the name and the path of the files-path declared in res/xml/file_paths.xml. */
private const val PHOTO_DIRECTORY = "listing_photos"

/**
 * Explains why the camera is needed after the user refused it. Cancelling only closes the dialog,
 * so nothing typed in the form is lost.
 */
@Composable
internal fun CameraPermissionDialog(onDismiss: () -> Unit) {
  val context = LocalContext.current
  AlertDialog(
      onDismissRequest = onDismiss,
      modifier = Modifier.testTag(CreateListingScreenTags.CAMERA_PERMISSION_DIALOG),
      containerColor = AuthColors.CardBackground,
      title = { Text(text = "Camera access needed", color = AuthColors.TextPrimary) },
      text = {
        Text(
            text =
                "PolyBazaar uses the camera so renters can see your item's real condition. " +
                    "You can allow it in the app settings, or choose photos from your gallery.",
            color = AuthColors.TextSecondary,
        )
      },
      confirmButton = {
        TextButton(
            onClick = {
              onDismiss()
              context.startActivity(
                  Intent(
                      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                      Uri.fromParts("package", context.packageName, null),
                  )
              )
            },
            modifier = Modifier.testTag(CreateListingScreenTags.CAMERA_PERMISSION_SETTINGS),
        ) {
          Text(text = "Open settings", color = AuthColors.Accent)
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(CreateListingScreenTags.CAMERA_PERMISSION_CANCEL),
        ) {
          Text(text = "Cancel", color = AuthColors.TextSecondary)
        }
      },
  )
}
