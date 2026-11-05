// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.profile.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.app.ActivityOptionsCompat
import java.io.ByteArrayOutputStream

/** An encoded all-red photo, which the avatar's default photo can be told apart from. */
internal fun redPhoto(sizePx: Int = 8): ByteArray {
  val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
  bitmap.eraseColor(AndroidColor.RED)
  return ByteArrayOutputStream().use {
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    it.toByteArray()
  }
}

/**
 * Waits for the avatar to show [redPhoto], or to stop showing it. Needs
 * `@GraphicsMode(GraphicsMode.Mode.NATIVE)`. It has to poll, because the photo is decoded on
 * Dispatchers.IO, which the test clock does not wait for.
 */
internal fun ComposeTestRule.waitForAvatar(isRed: Boolean) {
  waitUntil(5_000) {
    val image = onNodeWithTag(ProfileComponentTags.AVATAR, useUnmergedTree = true).captureToImage()
    (image.toPixelMap()[image.width / 2, image.height / 2] == Color.Red) == isRed
  }
}

/** Stands in for the system photo picker: every pick returns [picked]. */
internal fun photoPicker(picked: Uri, onLaunch: () -> Unit = {}): ActivityResultRegistryOwner =
    object : ActivityResultRegistryOwner {
      override val activityResultRegistry =
          object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
              onLaunch()
              dispatchResult(requestCode, picked)
            }
          }
    }
