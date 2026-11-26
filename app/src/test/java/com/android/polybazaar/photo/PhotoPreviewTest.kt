// Co-authored-by: Claude Code (Claude Fable 5.2, Claude Opus 5.5)
package com.android.polybazaar.photo

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class PhotoPreviewTest {

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE) // Decoding needs real bitmaps.
  fun decodePhotoPreview_scalesALargePhotoDown() =
      runTest(StandardTestDispatcher()) {
        val bytes = jpeg(width = 2048, height = 1024)

        val preview = decodePhotoPreview(bytes, StandardTestDispatcher(testScheduler))

        assertNotNull(preview)
        // Halved until one more halving would go below the preview size: 2048 / 4 = 512.
        assertEquals(PHOTO_PREVIEW_MAX_SIZE, preview!!.width)
        assertEquals(PHOTO_PREVIEW_MAX_SIZE / 2, preview.height)
      }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE) // Decoding needs real bitmaps.
  fun decodePhotoPreview_keepsASmallPhotoAsIs() =
      runTest(StandardTestDispatcher()) {
        val preview =
            decodePhotoPreview(jpeg(width = 64, height = 32), StandardTestDispatcher(testScheduler))

        assertEquals(64, preview?.width)
      }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE) // The legacy shadow decodes any bytes.
  fun decodePhotoPreview_returnsNullForBytesThatAreNotAnImage() =
      runTest(StandardTestDispatcher()) {
        assertNull(decodePhotoPreview(byteArrayOf(1, 2, 3), StandardTestDispatcher(testScheduler)))
      }

  private fun jpeg(width: Int, height: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    return ByteArrayOutputStream().use {
      bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
      it.toByteArray()
    }
  }
}
