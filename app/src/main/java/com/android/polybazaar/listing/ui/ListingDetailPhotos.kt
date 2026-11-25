// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.photo.decodePhotoPreview

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
  // Decoded once, for both the detail and the full screen viewer.
  val decoded = rememberListingPhotos(photos)
  // The page opened full screen, or null while the viewer is closed. Saved, so rotating keeps it.
  var fullScreenPage by rememberSaveable { mutableStateOf<Int?>(null) }
  HorizontalPager(
      state = pagerState,
      modifier = Modifier.fillMaxSize().testTag(ListingDetailScreenTags.PHOTOS),
  ) { page ->
    val photo = decoded[imagePaths[page]]
    Box(modifier = Modifier.fillMaxSize()) {
      if (photo == null) {
        PhotoPlaceholderIcon()
      } else {
        Image(
            bitmap = photo,
            contentDescription =
                stringResource(R.string.listing_detail_photo, page + 1, imagePaths.size),
            // Crop: the photo fills the 190dp area whatever its shape, as in the Figma. Tapping it
            // opens the whole photo full screen.
            contentScale = ContentScale.Crop,
            modifier =
                Modifier.fillMaxSize()
                    .clickable { fullScreenPage = page }
                    .testTag(ListingDetailScreenTags.PHOTO),
        )
      }
    }
  }
  PhotoCounter(pagerState, imagePaths.size)
  fullScreenPage?.let { page ->
    FullScreenPhotos(
        imagePaths = imagePaths,
        photos = decoded,
        initialPage = page,
        onClose = { lastPage ->
          fullScreenPage = null
          // The detail shows the photo the viewer was closed on.
          pagerState.requestScrollToPage(lastPage)
        },
    )
  }
}

/** The "1 / 3" counter at the bottom right; one photo needs none. */
@Composable
private fun BoxScope.PhotoCounter(pagerState: PagerState, count: Int) {
  if (count <= 1) return
  Text(
      text =
          stringResource(R.string.listing_detail_photo_counter, pagerState.currentPage + 1, count),
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

/**
 * The listing's photos full screen on black, each shown whole (not cropped), swiped one at a time
 * from [initialPage]. Closed with the close button or the system back, which pass [onClose] the
 * page it was closed on.
 */
@Composable
private fun FullScreenPhotos(
    imagePaths: List<String>,
    photos: Map<String, ImageBitmap>,
    initialPage: Int,
    onClose: (lastPage: Int) -> Unit,
) {
  val pagerState = rememberPagerState(initialPage = initialPage) { imagePaths.size }
  val close = { onClose(pagerState.currentPage) }
  Dialog(
      onDismissRequest = close,
      // Edge to edge, so the black covers the status bar too instead of the dimmed detail.
      properties =
          DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(Color.Black)
                .testTag(ListingDetailScreenTags.FULL_SCREEN_PHOTOS)
    ) {
      HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val photo = photos[imagePaths[page]]
        Box(modifier = Modifier.fillMaxSize()) {
          if (photo == null) {
            // Lighter than on the detail, to show on black.
            PhotoPlaceholderIcon(tint = Color.White.copy(alpha = 0.6f))
          } else {
            Image(
                bitmap = photo,
                contentDescription =
                    stringResource(R.string.listing_detail_photo, page + 1, imagePaths.size),
                // Fit: the whole photo, letterboxed on black.
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
          }
        }
      }
      IconButton(
          onClick = close,
          modifier =
              Modifier.align(Alignment.TopStart)
                  .statusBarsPadding()
                  .padding(8.dp)
                  .background(CounterBackground, CircleShape)
                  .testTag(ListingDetailScreenTags.FULL_SCREEN_CLOSE),
      ) {
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = stringResource(R.string.listing_detail_photo_close),
            tint = Color.White,
        )
      }
      PhotoCounter(pagerState, imagePaths.size)
    }
  }
}

/** The image icon in the middle of the photo area, while there is no photo to show. */
@Composable
private fun BoxScope.PhotoPlaceholderIcon(tint: Color = AuthColors.TextSecondary) {
  // Decorative: the area holds no information without a photo.
  Icon(
      painter = painterResource(R.drawable.ic_image),
      contentDescription = null,
      tint = tint,
      modifier = Modifier.align(Alignment.Center).size(32.dp),
  )
}

/**
 * [photos] decoded off the main thread, by path. A photo is decoded once, and again only when its
 * bytes change; one missing, or that is not an image, is absent.
 */
@Composable
private fun rememberListingPhotos(photos: Map<String, ByteArray>): Map<String, ImageBitmap> {
  val decoded = remember { mutableStateMapOf<String, ImageBitmap>() }
  photos.forEach { (path, bytes) ->
    // One effect per photo: a photo that finishes downloading starts its own decode, without
    // restarting the ones already running.
    key(path) {
      LaunchedEffect(bytes) {
        val photo = decodePhotoPreview(bytes, maxSize = LISTING_PHOTO_MAX_SIZE)
        if (photo == null) decoded.remove(path) else decoded[path] = photo
      }
    }
  }
  // A photo no longer in [photos] is dropped. Its effect has left the composition, so it is
  // cancelled and does not write it back.
  SideEffect { (decoded.keys - photos.keys).forEach(decoded::remove) }
  return decoded
}
