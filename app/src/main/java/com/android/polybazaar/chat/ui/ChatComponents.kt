// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthColors

/** Text of the "Paused" listing badges, from the Figma. */
internal val PausedBadgeText = Color(0xFF8C491F)

/** Fill of the "Deleted" listing badges, from the Figma. */
internal val DeletedBadgeBackground = Color(0xFFF2EAE5)

/** Text of the "Deleted" listing badges, from the Figma. */
internal val DeletedBadgeText = Color(0xFF67564D)

/** The listing's photo, or a placeholder with an image icon while it is missing. */
@Composable
internal fun ListingPhoto(photo: ImageBitmap?, size: Dp, modifier: Modifier = Modifier) {
  Box(
      modifier =
          modifier
              .size(size)
              .clip(RoundedCornerShape(12.dp))
              .background(AuthColors.AccentContainer),
      contentAlignment = Alignment.Center,
  ) {
    if (photo != null) {
      // Decorative: the text next to it names the listing.
      Image(
          painter = BitmapPainter(photo),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.size(size),
      )
    } else {
      Icon(
          painter = painterResource(R.drawable.ic_image),
          contentDescription = null,
          tint = AuthColors.TextSecondary,
          modifier = Modifier.size(20.dp),
      )
    }
  }
}
