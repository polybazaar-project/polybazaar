// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.ui.AuthBackButton
import com.android.polybazaar.auth.ui.AuthCard
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.auth.ui.AuthTitleGroup
import com.android.polybazaar.browse.ui.ListingCard
import com.android.polybazaar.browse.viewmodel.BrowseListing
import com.android.polybazaar.error.ui.message
import com.android.polybazaar.profile.viewmodel.LenderProfileUiState
import com.android.polybazaar.profile.viewmodel.LenderProfileViewModel

object LenderProfileScreenTags {
  const val SCREEN = "lender_profile_screen"
  const val BACK_BUTTON = "lender_profile_back_button"
  const val LOADING_INDICATOR = "lender_profile_loading_indicator"
  const val ERROR_MESSAGE = "lender_profile_error_message"
  const val NOT_FOUND = "lender_profile_not_found"
  const val NAME = "lender_profile_name"
  const val VERIFIED = "lender_profile_verified"
  const val BIO = "lender_profile_bio"
  const val NO_LISTINGS = "lender_profile_no_listings"

  /** Tag of the card showing the listing with [listingId]. */
  fun listing(listingId: String) = "lender_profile_listing_$listingId"
}

/** What the page shows under its header, decided by [lenderProfileBody]. */
internal enum class LenderProfileBody {
  LOADING,
  ERROR,
  NOT_FOUND,
  PROFILE,
}

/**
 * Picks the page body for [state]. A profile that arrived is shown even if the listings failed, so
 * the error then appears in the listings section instead of hiding the whole page. Without a
 * profile, an error means it could not be read (offline, for example), so it is not reported as
 * "not found".
 */
internal fun lenderProfileBody(state: LenderProfileUiState): LenderProfileBody =
    when {
      state.isLoading -> LenderProfileBody.LOADING
      state.profile != null -> LenderProfileBody.PROFILE
      state.error != null -> LenderProfileBody.ERROR
      else -> LenderProfileBody.NOT_FOUND
    }

/** Gap between two listing cards, the same as on Browse. */
private val ListingSpacing = 14.dp

/** Side margin of the page, the x of the identity card in the Figma profile frames. */
private val PageMargin = 18.dp

/**
 * Lender profile screen: loads the lender with [lenderUid] into [viewModel] and shows it.
 *
 * @param onBack called when the user taps the back button.
 * @param onOpenListing called with a listing's id when the user taps its card.
 */
@Composable
fun LenderProfileScreen(
    viewModel: LenderProfileViewModel,
    lenderUid: String,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit,
) {
  // Keyed on the uid, so the screen loads again only when it is given another lender.
  LaunchedEffect(lenderUid) { viewModel.loadLender(lenderUid) }
  val uiState by viewModel.uiState.collectAsState()
  LenderProfileContent(uiState = uiState, onBack = onBack, onOpenListing = onOpenListing)
}

/**
 * Profile of a lender as another user sees it: their photo, name, bio and listings.
 *
 * The email is deliberately not shown (open question in #231).
 *
 * @param uiState what [LenderProfileViewModel] loaded.
 * @param onBack called when the user taps the back button.
 * @param onOpenListing called with a listing's id when the user taps its card.
 */
@Composable
fun LenderProfileContent(
    uiState: LenderProfileUiState,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit,
) {
  // A single lazy list, so the header, the card and any number of listings scroll together.
  LazyColumn(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .testTag(LenderProfileScreenTags.SCREEN),
      contentPadding = PaddingValues(bottom = 16.dp),
  ) {
    item { Header(onBack = onBack) }
    when (lenderProfileBody(uiState)) {
      LenderProfileBody.LOADING -> item { LoadingIndicator() }
      LenderProfileBody.ERROR ->
          item {
            StatusText(
                text = uiState.error?.message().orEmpty(),
                color = AuthColors.Danger,
                tag = LenderProfileScreenTags.ERROR_MESSAGE,
            )
          }
      LenderProfileBody.NOT_FOUND ->
          item {
            StatusText(
                text = stringResource(R.string.lender_profile_not_found),
                color = AuthColors.TextSecondary,
                tag = LenderProfileScreenTags.NOT_FOUND,
            )
          }
      LenderProfileBody.PROFILE -> profileItems(uiState, onOpenListing)
    }
  }
}

/** The identity card, then the lender's listings, or why there are none to show. */
private fun LazyListScope.profileItems(
    uiState: LenderProfileUiState,
    onOpenListing: (String) -> Unit,
) {
  // Only called for LenderProfileBody.PROFILE, where the profile is never null.
  val profile = uiState.profile ?: return
  item { IdentityCard(profile) }
  item { ListingsTitle() }
  val error = uiState.error
  when {
    // A failed load must not read as "no listings": offline, the lender may well have some.
    error != null ->
        item {
          StatusText(
              text = error.message(),
              color = AuthColors.Danger,
              tag = LenderProfileScreenTags.ERROR_MESSAGE,
          )
        }
    uiState.listings.isEmpty() ->
        item {
          StatusText(
              text = stringResource(R.string.lender_profile_no_listings),
              color = AuthColors.TextSecondary,
              tag = LenderProfileScreenTags.NO_LISTINGS,
          )
        }
    else ->
        items(uiState.listings, key = { it.id }) { listing ->
          LenderListingCard(listing = listing, onClick = { onOpenListing(listing.id) })
        }
  }
}

@Composable
private fun Header(onBack: () -> Unit) {
  // 54dp: height of the "Profile header" row in the Figma profile frames.
  Row(
      modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = PageMargin),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    AuthBackButton(
        onClick = onBack,
        modifier = Modifier.testTag(LenderProfileScreenTags.BACK_BUTTON),
    )
    AuthTitleGroup(
        title = stringResource(R.string.lender_profile_title),
        eyebrow = stringResource(R.string.lender_profile_eyebrow),
    )
  }
}

/** Avatar, name with the "Verified" mark, and bio, laid out like the profile identity card. */
@Composable
private fun IdentityCard(profile: User) {
  AuthCard {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      // Default photo for now: loading another user's photo needs the ViewModel to fetch it.
      ProfileAvatar()
      Text(
          text = profile.username,
          color = AuthColors.TextPrimary,
          fontSize = 26.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          // Takes what is left of the row, so a long name ends in "…" before the mark.
          modifier = Modifier.weight(1f, fill = false).testTag(LenderProfileScreenTags.NAME),
      )
      // Every listing owner had a verified EPFL email (see Listing.ownerUid), and this page is
      // opened from one of their listings.
      Text(
          text = stringResource(R.string.lender_profile_verified),
          color = AuthColors.Accent,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.testTag(LenderProfileScreenTags.VERIFIED),
      )
    }
    profile.bio
        .takeIf { it.isNotBlank() }
        ?.let { bio ->
          // Not cut to two lines as on "My profile": the bio is what a renter reads to judge them.
          Text(
              text = bio,
              color = AuthColors.TextSecondary,
              fontSize = 12.sp,
              lineHeight = 1.35.em,
              modifier = Modifier.testTag(LenderProfileScreenTags.BIO),
          )
        }
  }
}

@Composable
private fun ListingsTitle() {
  // 2dp on top: the Figma gap of 12dp between cards, minus the 10dp AuthCard keeps below itself.
  Text(
      text = stringResource(R.string.lender_profile_listings_title),
      color = AuthColors.TextPrimary,
      fontSize = 15.sp,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = 2.dp, bottom = 12.dp),
  )
}

/** Browse's listing card, made tappable to open the listing. */
@Composable
private fun LenderListingCard(listing: BrowseListing, onClick: () -> Unit) {
  Box(
      modifier =
          Modifier.padding(start = PageMargin, end = PageMargin, bottom = ListingSpacing)
              // 28dp: ListingCard's corner radius, so the ripple follows the card's shape.
              .clip(RoundedCornerShape(28.dp))
              // Role.Button and the label let TalkBack announce the card as "Open listing".
              .clickable(
                  role = Role.Button,
                  onClickLabel = stringResource(R.string.lender_profile_open_listing),
                  onClick = onClick,
              )
              .testTag(LenderProfileScreenTags.listing(listing.id))
  ) {
    ListingCard(listing)
  }
}

@Composable
private fun LoadingIndicator() {
  Box(
      modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
      contentAlignment = Alignment.Center,
  ) {
    CircularProgressIndicator(
        color = AuthColors.Accent,
        modifier = Modifier.size(32.dp).testTag(LenderProfileScreenTags.LOADING_INDICATOR),
    )
  }
}

/** A short line explaining why there is nothing to show (error, not found, no listings). */
@Composable
private fun StatusText(text: String, color: Color, tag: String) {
  Text(
      text = text,
      color = color,
      fontSize = 14.sp,
      modifier =
          Modifier.fillMaxWidth().padding(horizontal = PageMargin, vertical = 8.dp).testTag(tag),
  )
}

/** A lender with one listing, for the IDE preview. Internal so a test can render it too. */
@Preview(showBackground = true, widthDp = 412, heightDp = 844)
@Composable
internal fun LenderProfileContentPreview() {
  LenderProfileContent(
      uiState =
          LenderProfileUiState(
              profile =
                  User(
                      uid = "preview",
                      email = "",
                      username = "James Richard",
                      bio = "Weekend maker, careful borrower, and always happy to share tools.",
                  ),
              listings =
                  listOf(
                      BrowseListing(
                          id = "drill",
                          title = "Bosch drill set",
                          category = "Tools",
                          price = "CHF 8",
                      )
                  ),
              profileLoaded = true,
              listingsLoaded = true,
          ),
      onBack = {},
      onOpenListing = {},
  )
}
