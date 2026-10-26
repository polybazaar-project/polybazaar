// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Turns a listing photo into the smaller bytes that get uploaded. */
fun interface PhotoCompressor {
  suspend fun compress(photo: Uri): ByteArray
}

/**
 * Downscales a photo to at most [maxSize] pixels on its longest side and re-encodes it as a JPEG. A
 * phone camera photo of several megabytes becomes a few hundred kilobytes, which is still sharp
 * enough to judge an item's condition.
 */
class JpegPhotoCompressor(
    private val contentResolver: ContentResolver,
    private val maxSize: Int = MAX_SIZE,
    private val quality: Int = QUALITY,
) : PhotoCompressor {

  override suspend fun compress(photo: Uri): ByteArray =
      withContext(Dispatchers.IO) {
        // ImageDecoder applies the EXIF rotation, so portrait photos stay upright.
        val bitmap =
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, photo)) {
                decoder,
                info,
                _ ->
              // Bitmap.compress needs a software bitmap.
              decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
              val scale = maxSize.toFloat() / maxOf(info.size.width, info.size.height)
              if (scale < 1f) {
                decoder.setTargetSize(
                    (info.size.width * scale).toInt(),
                    (info.size.height * scale).toInt(),
                )
              }
            }
        ByteArrayOutputStream().use { output ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
          bitmap.recycle()
          output.toByteArray()
        }
      }

  companion object {
    const val MAX_SIZE = 1600
    const val QUALITY = 80
  }
}
