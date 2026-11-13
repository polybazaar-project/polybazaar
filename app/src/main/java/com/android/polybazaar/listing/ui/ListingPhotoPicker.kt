// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.ui

import android.content.ContentResolver
import android.graphics.BitmapFactory
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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The "Photos" field wired to the system photo pickers and the camera, with the camera permission
 * explanation when it is needed.
 *
 * @param storedPhotos when editing, the Storage paths of the saved photos, shown before [photos].
 * @param enabled whether photos can be added, retaken or removed.
 * @param storedPhotoBytes the saved photos downloaded so far, by Storage path. A missing photo is
 *   asked for through [ListingFormActions.onStoredPhotoShown].
 */
@Composable
internal fun ListingPhotosField(
    photos: List<Uri>,
    actions: ListingFormActions,
    enabled: Boolean,
    storedPhotos: List<String> = emptyList(),
    storedPhotoBytes: Map<String, ByteArray> = emptyMap(),
) {
  val remainingPhotos = MAX_PHOTOS - storedPhotos.size - photos.size
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
      storedPhotos = storedPhotos,
      onRemoveStoredPhoto = actions.onStoredPhotoRemoved,
      storedPreview = { path ->
        rememberStoredPhotoPreview(path, storedPhotoBytes[path], actions.onStoredPhotoShown)
      },
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
 *
 * @param ioDispatcher runs the decoding, off the main thread.
 */
internal suspend fun loadPhotoPreview(
    contentResolver: ContentResolver,
    uri: Uri,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): ImageBitmap? =
    withContext(ioDispatcher) {
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
 * The thumbnail of the saved photo at [path], decoded from its downloaded [bytes], or null until
 * they are there. Asks for them with [download] when the thumbnail first shows.
 */
@Composable
private fun rememberStoredPhotoPreview(
    path: String,
    bytes: ByteArray?,
    download: (String) -> Unit,
): ImageBitmap? {
  LaunchedEffect(path) { if (bytes == null) download(path) }
  val preview by
      produceState<ImageBitmap?>(initialValue = null, bytes) {
        value = bytes?.let { decodePhotoPreview(it) }
      }
  return preview
}

/**
 * Decodes the downloaded photo [bytes] scaled down by a power of two until its longest side is at
 * most twice [maxSize], or returns null when they are not an image.
 *
 * @param dispatcher runs the decoding, off the main thread.
 * @param maxSize the longest side, in pixels, the photo is shown at: [PHOTO_PREVIEW_MAX_SIZE] for a
 *   thumbnail, more for a photo across the screen.
 */
internal suspend fun decodePhotoPreview(
    bytes: ByteArray,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    maxSize: Int = PHOTO_PREVIEW_MAX_SIZE,
): ImageBitmap? =
    withContext(dispatcher) {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      var sampleSize = 1
      while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSize) {
        sampleSize *= 2
      }
      val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }

/**
 * The "Photos" field: "Take photo" and "Gallery" buttons that become a row of thumbnails, followed
 * by "Camera" and "Gallery" tiles until [MAX_PHOTOS] are chosen. Tapping a thumbnail retakes it.
 *
 * When editing, the saved [storedPhotos] come first. They can be removed but not retaken: a retake
 * replaces a photo on this device.
 *
 * @param onAddPhotos opens the gallery.
 * @param onRetakePhoto called with the index of the tapped thumbnail in [photos].
 * @param enabled whether photos can be added, retaken or removed.
 * @param preview loads the thumbnail of a photo; replaceable in tests.
 * @param storedPreview loads the thumbnail of a saved photo from its Storage path.
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
    storedPhotos: List<String> = emptyList(),
    onRemoveStoredPhoto: (String) -> Unit = {},
    storedPreview: @Composable (String) -> ImageBitmap? = { null },
) {
  val photoCount = storedPhotos.size + photos.size
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      FieldLabel(text = "Photos", modifier = Modifier.weight(1f))
      Text(
          text = if (photoCount == 0) "A clear photo works best" else "$photoCount/$MAX_PHOTOS",
          color = AuthColors.TextSecondary,
          fontSize = 11.sp,
      )
    }
    if (photoCount == 0) {
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
        storedPhotos.forEachIndexed { index, path ->
          PhotoThumbnail(
              preview = storedPreview(path),
              number = index + 1,
              onRetake = null,
              onRemove = { onRemoveStoredPhoto(path) },
              enabled = enabled,
              tag = CreateListingScreenTags.storedPhoto(index),
              removeTag = CreateListingScreenTags.removeStoredPhoto(index),
          )
        }
        photos.forEachIndexed { index, uri ->
          PhotoThumbnail(
              preview = preview(uri),
              number = storedPhotos.size + index + 1,
              onRetake = { onRetakePhoto(index) },
              onRemove = { onRemovePhoto(uri) },
              enabled = enabled,
              tag = CreateListingScreenTags.photo(index),
              removeTag = CreateListingScreenTags.removePhoto(index),
          )
        }
        if (photoCount < MAX_PHOTOS) {
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
      if (photos.isNotEmpty()) {
        Text(
            text =
                if (storedPhotos.isEmpty()) "Tap a photo to retake it"
                else "Tap a new photo to retake it",
            color = AuthColors.TextSecondary,
            fontSize = 11.sp,
        )
      }
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

/**
 * A photo with its remove button, numbered from 1 in the row.
 *
 * @param onRetake called when the photo is tapped, or null for a saved photo, which cannot be
 *   retaken.
 */
@Composable
private fun PhotoThumbnail(
    preview: ImageBitmap?,
    number: Int,
    onRetake: (() -> Unit)?,
    onRemove: () -> Unit,
    enabled: Boolean,
    tag: String,
    removeTag: String,
) {
  val retake =
      if (onRetake == null) Modifier
      else
          Modifier.clickable(
              enabled = enabled,
              onClickLabel = "Retake photo $number",
              role = Role.Button,
              onClick = onRetake,
          )
  Box(
      modifier =
          Modifier.size(ThumbnailSize)
              .clip(PhotoShape)
              .background(AuthColors.Background)
              .border(1.dp, AuthColors.Outline, PhotoShape)
              .then(retake)
              .testTag(tag)
  ) {
    if (preview != null) {
      Image(
          bitmap = preview,
          contentDescription = "Photo $number",
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
                .semantics { contentDescription = "Remove photo $number" }
                .testTag(removeTag),
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
