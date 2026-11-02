// Portions of this code were generated with the help of Claude Code.
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.profile.ui

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
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProfileComponentTags {
  const val AVATAR = "profile_avatar"
}

/** Longest side, in pixels, of a photo once decoded for the 72dp avatar. */
private const val AVATAR_DECODE_SIZE_PX = 288

/**
 * The round profile photo with its badge, as in the profile Figma frames.
 *
 * Shows [selectedPhotoUri] (a photo picked on this device) when there is one, then [savedPhoto]
 * (the encoded bytes of the photo stored in Firebase), and the default photo otherwise.
 *
 * @param ioDispatcher runs the decoding, off the main thread.
 */
@Composable
internal fun ProfileAvatar(
    modifier: Modifier = Modifier,
    selectedPhotoUri: Uri? = null,
    savedPhoto: ByteArray? = null,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  val contentResolver = LocalContext.current.contentResolver
  val decodedSelectedPhoto =
      rememberDecodedPhoto(selectedPhotoUri, ioDispatcher) {
        ImageDecoder.createSource(contentResolver, it)
      }
  val decodedSavedPhoto =
      rememberDecodedPhoto(savedPhoto, ioDispatcher) {
        ImageDecoder.createSource(ByteBuffer.wrap(it))
      }
  Box(modifier = modifier.size(72.dp).testTag(ProfileComponentTags.AVATAR)) {
    Image(
        painter =
            (decodedSelectedPhoto ?: decodedSavedPhoto)?.let { BitmapPainter(it) }
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

/** Decodes [photo] off the main thread. Null while loading, or if the photo cannot be read. */
@Composable
private fun <T : Any> rememberDecodedPhoto(
    photo: T?,
    ioDispatcher: CoroutineDispatcher,
    createSource: (T) -> ImageDecoder.Source,
): ImageBitmap? {
  val decoded by
      produceState<ImageBitmap?>(initialValue = null, photo) {
        value = photo?.let { withContext(ioDispatcher) { decodeScaledPhoto(createSource(it)) } }
      }
  return decoded
}

/** Decodes [source], scaled down so its longest side is at most [AVATAR_DECODE_SIZE_PX]. */
private fun decodeScaledPhoto(source: ImageDecoder.Source): ImageBitmap? = runCatching {
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
