// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
 * Launches the camera app and the permission request. The photo files themselves are handled by the
 * ViewModel, which survives the activity being recreated while the camera app is in front.
 *
 * @param startTakingPhoto prepares a photo for a retake index and returns the URI the camera app
 *   writes it to, or null when no photo can be created.
 * @param finishTakingPhoto called once the camera app closes, with whether it saved the photo.
 */
@Composable
internal fun rememberPhotoCamera(
    startTakingPhoto: (retakeIndex: Int?) -> Uri?,
    finishTakingPhoto: (saved: Boolean) -> Unit,
): PhotoCamera {
  val context = LocalContext.current
  val currentFinishTakingPhoto by rememberUpdatedState(finishTakingPhoto)
  // The photo to retake once the permission is granted.
  var requestedRetake by rememberSaveable { mutableStateOf<Int?>(null) }
  var permissionDenied by rememberSaveable { mutableStateOf(false) }

  val takePicture =
      rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        currentFinishTakingPhoto(saved)
      }
  val launchCamera = { retakeIndex: Int? ->
    val cameraUri = startTakingPhoto(retakeIndex)
    if (cameraUri == null) {
      Toast.makeText(context, "Could not save the photo", Toast.LENGTH_SHORT).show()
    } else {
      try {
        takePicture.launch(cameraUri)
      } catch (e: ActivityNotFoundException) {
        finishTakingPhoto(false)
        Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show()
      }
    }
  }
  val requestPermission =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera(requestedRetake) else permissionDenied = true
      }

  return PhotoCamera(
      showPermissionExplanation = permissionDenied,
      start = { index ->
        requestedRetake = index
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        ) {
          launchCamera(index)
        } else {
          requestPermission.launch(Manifest.permission.CAMERA)
        }
      },
      dismiss = { permissionDenied = false },
  )
}

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
