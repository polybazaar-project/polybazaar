// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** These tests decode and encode real images, which needs Robolectric's native graphics. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JpegPhotoCompressorTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val compressor = JpegPhotoCompressor(context.contentResolver)

  private fun writeImage(width: Int, height: Int): Uri {
    val file = File.createTempFile("photo", ".png", context.cacheDir)
    file.outputStream().use {
      Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
          .compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    return Uri.fromFile(file)
  }

  private fun decode(bytes: ByteArray): Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

  @Test
  fun compress_downscalesLargePhotosKeepingTheirShape() = runTest {
    val photo = decode(compressor.compress(writeImage(4000, 2000)))

    assertEquals(JpegPhotoCompressor.MAX_SIZE, photo.width)
    assertEquals(JpegPhotoCompressor.MAX_SIZE / 2, photo.height)
  }

  @Test
  fun compress_keepsSmallPhotosAtTheirSize() = runTest {
    val photo = decode(compressor.compress(writeImage(300, 200)))

    assertEquals(300, photo.width)
    assertEquals(200, photo.height)
  }

  @Test
  fun compress_encodesAsJpeg() = runTest {
    val bytes = compressor.compress(writeImage(100, 100))

    // Every JPEG file starts with the SOI marker FF D8.
    assertEquals(0xFF.toByte(), bytes[0])
    assertEquals(0xD8.toByte(), bytes[1])
  }

  @Test
  fun compress_failsForUnreadablePhoto() = runTest {
    val missing = Uri.fromFile(File(context.cacheDir, "does-not-exist.png"))

    val failure = runCatching { compressor.compress(missing) }.exceptionOrNull()

    assertTrue(failure is IOException)
  }
}
