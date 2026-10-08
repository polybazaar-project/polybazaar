// Portions of this code were generated with the help of Claude Code.
package com.android.polybazaar.profile.ui

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProfileComponentTags {
  const val AVATAR = "profile_avatar"
}

/** Longest side, in pixels, of a picked photo once decoded for the 72dp avatar. */
private const val AVATAR_DECODE_SIZE_PX = 288

/**
 * The round profile photo with its badge, as in the profile Figma frames.
 *
 * Shows [selectedPhotoUri] (a photo picked on this device) when there is one, and the default photo
 * otherwise. Photos stored in Firebase are not shown yet, as that needs an image loading library.
 */
@Composable
internal fun ProfileAvatar(selectedPhotoUri: Uri? = null, modifier: Modifier = Modifier) {
  val selectedPhoto = rememberDecodedPhoto(selectedPhotoUri)
  Box(modifier = modifier.size(72.dp).testTag(ProfileComponentTags.AVATAR)) {
    Image(
        painter =
            selectedPhoto?.let { BitmapPainter(it) }
                ?: painterResource(R.drawable.default_profile_photo),
        contentDescription = "Profile photo",
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(72.dp).clip(CircleShape),
    )
    Box(
        modifier =
            Modifier.align(Alignment.TopStart)
                .offset(x = 50.dp, y = 49.dp)
                .size(24.dp)
                .background(AuthColors.Accent, CircleShape)
                .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          painter = painterResource(R.drawable.ic_check),
          contentDescription = null,
          tint = Color.Unspecified,
          modifier = Modifier.size(13.dp),
      )
    }
  }
}

/** Decodes [uri] off the main thread. Null while loading, or if the photo cannot be read. */
@Composable
private fun rememberDecodedPhoto(uri: Uri?): ImageBitmap? {
  val contentResolver = LocalContext.current.contentResolver
  val photo by
      produceState<ImageBitmap?>(initialValue = null, uri) {
        value = uri?.let { withContext(Dispatchers.IO) { decodeScaledPhoto(contentResolver, it) } }
      }
  return photo
}

/** Decodes [uri], scaled down so its longest side is at most [AVATAR_DECODE_SIZE_PX]. */
private fun decodeScaledPhoto(contentResolver: ContentResolver, uri: Uri): ImageBitmap? =
    runCatching {
      val source = ImageDecoder.createSource(contentResolver, uri)
      ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val longestSide = maxOf(info.size.width, info.size.height)
        if (longestSide > AVATAR_DECODE_SIZE_PX) {
          val scale = AVATAR_DECODE_SIZE_PX.toFloat() / longestSide
          decoder.setTargetSize(
              (info.size.width * scale).toInt().coerceAtLeast(1),
              (info.size.height * scale).toInt().coerceAtLeast(1),
          )
        }
      }
    }
    .getOrNull()
    ?.asImageBitmap()
