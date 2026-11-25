// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.photo

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The longest side, in pixels, of a photo shown as a thumbnail. */
internal const val PHOTO_PREVIEW_MAX_SIZE = 512

/** Decodes the downloaded [photo] off the main thread. Null while missing or decoding. */
@Composable
internal fun rememberPhoto(photo: ByteArray?): ImageBitmap? {
  val decoded by
      produceState<ImageBitmap?>(initialValue = null, photo) {
        value = photo?.let { decodePhotoPreview(it) }
      }
  return decoded
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
