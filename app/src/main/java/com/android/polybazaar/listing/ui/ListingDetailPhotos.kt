// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthColors

/**
 * Longest side, in pixels, a listing photo is decoded at: the photo area is the screen's width
 * (412dp on the Figma's phone, about 1,150px at its density), so this keeps it sharp without
 * decoding a full camera photo.
 */
internal const val LISTING_PHOTO_MAX_SIZE = 1200

/** Fill of the photo counter, dark enough to read on a light or a dark photo. */
private val CounterBackground = Color.Black.copy(alpha = 0.55f)

/**
 * The listing's photos in the photo area, swiped one at a time, with a "1 / 3" counter at the
 * bottom right when there are several. A photo not downloaded yet, or that failed to download or
 * decode, shows the placeholder on its page; a listing without photos shows the placeholder only.
 *
 * @param imagePaths the listing's photos, in order ([Listing.imagePaths]).
 * @param photos the downloaded ones, by path ([ListingDetailUiState.listingPhotos]).
 */
@Composable
internal fun BoxScope.ListingPhotos(imagePaths: List<String>, photos: Map<String, ByteArray>) {
  if (imagePaths.isEmpty()) {
    PhotoPlaceholderIcon()
    return
  }
  val pagerState = rememberPagerState { imagePaths.size }
  HorizontalPager(
      state = pagerState,
      modifier = Modifier.fillMaxSize().testTag(ListingDetailScreenTags.PHOTOS),
  ) { page ->
    val photo = rememberListingPhoto(photos[imagePaths[page]])
    Box(modifier = Modifier.fillMaxSize()) {
      if (photo == null) {
        PhotoPlaceholderIcon()
      } else {
        Image(
            bitmap = photo,
            contentDescription =
                stringResource(R.string.listing_detail_photo, page + 1, imagePaths.size),
            // Crop: the photo fills the 190dp area whatever its shape, as in the Figma.
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().testTag(ListingDetailScreenTags.PHOTO),
        )
      }
    }
  }
  // One photo needs no counter.
  if (imagePaths.size > 1) {
    Text(
        text =
            stringResource(
                R.string.listing_detail_photo_counter,
                pagerState.currentPage + 1,
                imagePaths.size,
            ),
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier =
            Modifier.align(Alignment.BottomEnd)
                // The page margin from the right, as the rest of the screen.
                .padding(end = 18.dp, bottom = 12.dp)
                .background(CounterBackground, CircleShape)
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .testTag(ListingDetailScreenTags.PHOTO_COUNTER),
    )
  }
}

/** The image icon in the middle of the photo area, while there is no photo to show. */
@Composable
private fun BoxScope.PhotoPlaceholderIcon() {
  // Decorative: the area holds no information without a photo.
  Icon(
      painter = painterResource(R.drawable.ic_image),
      contentDescription = null,
      tint = AuthColors.TextSecondary,
      modifier = Modifier.align(Alignment.Center).size(32.dp),
  )
}

/** [bytes] decoded off the main thread, or null until then or when they are not an image. */
@Composable
private fun rememberListingPhoto(bytes: ByteArray?): ImageBitmap? {
  val photo by
      produceState<ImageBitmap?>(initialValue = null, bytes) {
        value = bytes?.let { decodePhotoPreview(it, maxSize = LISTING_PHOTO_MAX_SIZE) }
      }
  return photo
}
