// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.ui

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.listing.model.ListingRules.MAX_PHOTOS
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The "Photos" field wired to the system photo pickers and the camera, with the camera permission
 * explanation when it is needed.
 *
 * @param enabled whether photos can be added, retaken or removed.
 */
@Composable
internal fun ListingPhotosField(
    photos: List<Uri>,
    actions: CreateListingActions,
    enabled: Boolean,
) {
  val remainingPhotos = MAX_PHOTOS - photos.size
  // PickMultipleVisualMedia needs a limit above 1, so the last free slot uses the single picker.
  val singlePhotoPicker =
      rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) actions.onPhotosPicked(listOf(uri))
      }
  val multiplePhotoPicker =
      rememberLauncherForActivityResult(
          ActivityResultContracts.PickMultipleVisualMedia(maxOf(remainingPhotos, 2))
      ) { uris ->
        actions.onPhotosPicked(uris)
      }
  val camera = rememberPhotoCamera(actions.startTakingPhoto, actions.finishTakingPhoto)

  PhotoPicker(
      photos = photos,
      onAddPhotos = {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (remainingPhotos > 1) multiplePhotoPicker.launch(request)
        else singlePhotoPicker.launch(request)
      },
      onTakePhoto = camera::takePhoto,
      onRetakePhoto = camera::retakePhoto,
      onRemovePhoto = actions.onPhotoRemoved,
      enabled = enabled,
  )
  if (camera.showPermissionExplanation) {
    CameraPermissionDialog(onDismiss = camera::dismissPermissionExplanation)
  }
}

/** Loads a downscaled preview of [uri], or null while loading or when it cannot be read. */
@Composable
private fun rememberPhotoPreview(uri: Uri): ImageBitmap? {
  val contentResolver = LocalContext.current.contentResolver
  val preview by
      produceState<ImageBitmap?>(initialValue = null, uri) {
        value = loadPhotoPreview(contentResolver, uri)
      }
  return preview
}

/**
 * Decodes [uri] scaled down to at most [PHOTO_PREVIEW_MAX_SIZE] pixels on its longest side, or
 * returns null when it cannot be read. Internal so its scaling and error handling can be tested.
 */
internal suspend fun loadPhotoPreview(contentResolver: ContentResolver, uri: Uri): ImageBitmap? =
    withContext(Dispatchers.IO) {
      try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) {
                decoder,
                info,
                _ ->
              val scale =
                  PHOTO_PREVIEW_MAX_SIZE.toFloat() / maxOf(info.size.width, info.size.height)
              if (scale < 1f) {
                decoder.setTargetSize(
                    (info.size.width * scale).toInt(),
                    (info.size.height * scale).toInt(),
                )
              }
            }
            .asImageBitmap()
      } catch (e: IOException) {
        // Deleted, offline cloud photo or undecodable: show an empty thumbnail, don't crash.
        null
      } catch (e: SecurityException) {
        // The picker's temporary read access can be lost, e.g. after the app is restored.
        null
      }
    }

internal const val PHOTO_PREVIEW_MAX_SIZE = 512

/**
 * The "Photos" field: "Take photo" and "Gallery" buttons that become a row of thumbnails, followed
 * by "Camera" and "Gallery" tiles until [MAX_PHOTOS] are chosen. Tapping a thumbnail retakes it.
 *
 * @param onAddPhotos opens the gallery.
 * @param onRetakePhoto called with the index of the tapped thumbnail.
 * @param enabled whether photos can be added, retaken or removed.
 * @param preview loads the thumbnail of a photo; replaceable in tests.
 */
@Composable
internal fun PhotoPicker(
    photos: List<Uri>,
    onAddPhotos: () -> Unit,
    onTakePhoto: () -> Unit,
    onRetakePhoto: (Int) -> Unit,
    onRemovePhoto: (Uri) -> Unit,
    enabled: Boolean = true,
    preview: @Composable (Uri) -> ImageBitmap? = { rememberPhotoPreview(it) },
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      FieldLabel(text = "Photos", modifier = Modifier.weight(1f))
      Text(
          text = if (photos.isEmpty()) "A clear photo works best" else "${photos.size}/$MAX_PHOTOS",
          color = AuthColors.TextSecondary,
          fontSize = 11.sp,
      )
    }
    if (photos.isEmpty()) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EmptyPhotoOption(
            title = "Take photo",
            subtitle = "Show its real condition",
            icon = R.drawable.ic_camera,
            onClick = onTakePhoto,
            enabled = enabled,
            tag = CreateListingScreenTags.TAKE_PHOTO,
            modifier = Modifier.weight(1f),
        )
        EmptyPhotoOption(
            title = "Gallery",
            subtitle = "Up to $MAX_PHOTOS photos",
            icon = R.drawable.ic_image,
            onClick = onAddPhotos,
            enabled = enabled,
            tag = CreateListingScreenTags.PHOTO_PICKER,
            modifier = Modifier.weight(1f),
        )
      }
    } else {
      Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        photos.forEachIndexed { index, uri ->
          PhotoThumbnail(
              preview = preview(uri),
              index = index,
              onRetake = { onRetakePhoto(index) },
              onRemove = { onRemovePhoto(uri) },
              enabled = enabled,
          )
        }
        if (photos.size < MAX_PHOTOS) {
          AddPhotoTile(
              text = "Camera",
              icon = R.drawable.ic_camera,
              onClick = onTakePhoto,
              enabled = enabled,
              tag = CreateListingScreenTags.TAKE_PHOTO,
          )
          AddPhotoTile(
              text = "Gallery",
              icon = R.drawable.ic_image,
              onClick = onAddPhotos,
              enabled = enabled,
              tag = CreateListingScreenTags.ADD_PHOTO,
          )
        }
      }
      Text(text = "Tap a photo to retake it", color = AuthColors.TextSecondary, fontSize = 11.sp)
    }
  }
}

private val PhotoShape = RoundedCornerShape(14.dp)
private val ThumbnailSize = 88.dp

@Composable
private fun EmptyPhotoOption(
    title: String,
    subtitle: String,
    icon: Int,
    onClick: () -> Unit,
    enabled: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .clip(PhotoShape)
              .background(AuthColors.Background)
              .border(1.dp, AuthColors.Outline, PhotoShape)
              .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
              .padding(14.dp)
              .testTag(tag),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    PhotoBadge(icon = icon)
    Text(
        text = title,
        color = AuthColors.Accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Text(text = subtitle, color = AuthColors.TextSecondary, fontSize = 11.sp)
  }
}

@Composable
private fun PhotoThumbnail(
    preview: ImageBitmap?,
    index: Int,
    onRetake: () -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean,
) {
  Box(
      modifier =
          Modifier.size(ThumbnailSize)
              .clip(PhotoShape)
              .background(AuthColors.Background)
              .border(1.dp, AuthColors.Outline, PhotoShape)
              .clickable(
                  enabled = enabled,
                  onClickLabel = "Retake photo ${index + 1}",
                  role = Role.Button,
                  onClick = onRetake,
              )
              .testTag(CreateListingScreenTags.photo(index))
  ) {
    if (preview != null) {
      Image(
          bitmap = preview,
          contentDescription = "Photo ${index + 1}",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
      )
    }
    Box(
        modifier =
            Modifier.align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(AuthColors.CardBackground)
                .clickable(enabled = enabled, role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = "Remove photo ${index + 1}" }
                .testTag(CreateListingScreenTags.removePhoto(index)),
        contentAlignment = Alignment.Center,
    ) {
      Text(text = "×", color = AuthColors.TextPrimary, fontSize = 14.sp)
    }
  }
}

@Composable
private fun AddPhotoTile(
    text: String,
    icon: Int,
    onClick: () -> Unit,
    enabled: Boolean,
    tag: String,
) {
  Column(
      modifier =
          Modifier.size(ThumbnailSize)
              .clip(PhotoShape)
              .background(AuthColors.Background)
              .border(1.dp, AuthColors.Outline, PhotoShape)
              .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
              .testTag(tag),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
  ) {
    PhotoBadge(icon = icon)
    Text(
        text = text,
        color = AuthColors.Accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
private fun PhotoBadge(icon: Int) {
  Box(
      modifier =
          Modifier.size(36.dp)
              .background(AuthColors.Accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = AuthColors.Accent,
        modifier = Modifier.size(20.dp),
    )
  }
}
