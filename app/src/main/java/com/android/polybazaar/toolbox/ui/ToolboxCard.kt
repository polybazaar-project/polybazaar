// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.error.ui.message
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.model.ServiceListing
import com.android.polybazaar.listing.model.formatChf
import com.android.polybazaar.toolbox.viewmodel.ToolboxUiState
import com.android.polybazaar.toolbox.viewmodel.ToolboxViewModel

object ToolboxCardTags {
  const val LISTINGS = "toolbox_listings"
  const val LISTING_ROW = "toolbox_listing_row"
  const val LOADING_INDICATOR = "toolbox_loading_indicator"
  const val ERROR = "toolbox_error"
  const val RETRY_BUTTON = "toolbox_retry_button"
  const val EMPTY = "toolbox_empty"
  const val FILTER_ALL = "toolbox_filter_all"
  const val FILTER_ITEMS = "toolbox_filter_items"
  const val FILTER_SERVICES = "toolbox_filter_services"
  const val FILTER_EMPTY = "toolbox_filter_empty"
}

/** @param onOpenListing called with a listing's id when the user taps its row. */
@Composable
fun ToolboxCard(viewModel: ToolboxViewModel, onOpenListing: (String) -> Unit = {}) {
  val uiState by viewModel.uiState.collectAsState()
  ToolboxContent(
      uiState = uiState,
      onRetry = viewModel::loadListings,
      onTypeSelected = viewModel::selectType,
      onOpenListing = onOpenListing,
  )
}

/**
 * The "My Toolbox" card of the profile, listing the user's own listings. The buttons under the
 * title show all of them (All, selected by default) or only items or services; [onTypeSelected]
 * gets the chosen type, or null for All.
 *
 * Fills the height it is given: the title stays in place and the rows scroll inside the card, so it
 * needs a bounded height.
 *
 * @param onOpenListing called with a listing's id when the user taps its row.
 */
@Composable
fun ToolboxContent(
    uiState: ToolboxUiState,
    onRetry: () -> Unit,
    onTypeSelected: (ListingType?) -> Unit,
    modifier: Modifier = Modifier,
    onOpenListing: (String) -> Unit = {},
) {
  val shape = RoundedCornerShape(20.dp)
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(AuthColors.CardBackground, shape)
              .border(1.dp, AuthColors.Outline, shape)
              .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // A tighter group than the rest of the card, so the header takes less of the profile.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
          text = stringResource(R.string.toolbox_title),
          color = AuthColors.TextPrimary,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      // The filters get their own line, so the title keeps its full width on narrow phones.
      if (uiState.listings.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          TypeFilterButton(
              text = stringResource(R.string.toolbox_filter_all),
              isSelected = uiState.selectedType == null,
              onClick = { onTypeSelected(null) },
              modifier = Modifier.testTag(ToolboxCardTags.FILTER_ALL),
          )
          TypeFilterButton(
              text = stringResource(R.string.toolbox_filter_items),
              isSelected = uiState.selectedType == ListingType.ITEM,
              onClick = { onTypeSelected(ListingType.ITEM) },
              modifier = Modifier.testTag(ToolboxCardTags.FILTER_ITEMS),
          )
          TypeFilterButton(
              text = stringResource(R.string.toolbox_filter_services),
              isSelected = uiState.selectedType == ListingType.SERVICE,
              onClick = { onTypeSelected(ListingType.SERVICE) },
              modifier = Modifier.testTag(ToolboxCardTags.FILTER_SERVICES),
          )
        }
      }
    }
    uiState.error?.let { error ->
      Row(
          modifier = Modifier.fillMaxWidth().testTag(ToolboxCardTags.ERROR),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = error.message(),
            color = AuthColors.Danger,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry, modifier = Modifier.testTag(ToolboxCardTags.RETRY_BUTTON)) {
          Text(
              text = stringResource(R.string.browse_retry),
              color = AuthColors.Accent,
              fontWeight = FontWeight.Bold,
          )
        }
      }
    }
    // Once listings are shown, a reload keeps them on screen instead of a spinner.
    if (uiState.isLoading && uiState.listings.isEmpty()) {
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = AuthColors.Accent,
            modifier = Modifier.size(32.dp).testTag(ToolboxCardTags.LOADING_INDICATOR),
        )
      }
    } else if (uiState.listings.isEmpty() && uiState.error == null) {
      Text(
          text = stringResource(R.string.toolbox_empty),
          color = AuthColors.TextSecondary,
          fontSize = 15.sp,
          modifier = Modifier.testTag(ToolboxCardTags.EMPTY),
      )
    }
    val filteredListings = uiState.filteredListings
    if (filteredListings.isNotEmpty()) {
      LazyColumn(
          modifier = Modifier.fillMaxWidth().weight(1f).testTag(ToolboxCardTags.LISTINGS),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(filteredListings) { listing ->
          // A listing loaded from the repository always has its id.
          val id = listing.listingId
          ToolboxListingRow(listing = listing, onClick = id?.let { { onOpenListing(it) } })
        }
      }
    } else if (uiState.listings.isNotEmpty()) {
      // Only reached with a filter selected: the user has listings, none of that type.
      Text(
          text =
              stringResource(
                  when (uiState.selectedType) {
                    ListingType.SERVICE -> R.string.toolbox_empty_services
                    else -> R.string.toolbox_empty_items
                  }
              ),
          color = AuthColors.TextSecondary,
          fontSize = 15.sp,
          modifier = Modifier.testTag(ToolboxCardTags.FILTER_EMPTY),
      )
    }
  }
}

/**
 * A pill that filters the listings by type, styled like the Browse category selector but more
 * compact, so the Toolbox header stays short.
 */
@Composable
private fun TypeFilterButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(24.dp)
  Text(
      text = text,
      color = if (isSelected) AuthColors.Accent else AuthColors.TextSecondary,
      fontSize = 14.sp,
      fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
      modifier =
          modifier
              .semantics { selected = isSelected }
              .clip(shape)
              .background(
                  if (isSelected) AuthColors.SelectedBackground else AuthColors.CardBackground
              )
              .border(1.dp, if (isSelected) AuthColors.Accent else AuthColors.Outline, shape)
              .clickable(onClick = onClick)
              .padding(horizontal = 16.dp, vertical = 6.dp),
  )
}

/** @param onClick called when the user taps the row, or null for a row that cannot be tapped. */
@Composable
private fun ToolboxListingRow(listing: Listing, onClick: (() -> Unit)?) {
  val shape = RoundedCornerShape(18.dp)
  val openLabel = stringResource(R.string.open_listing)
  // Clipped first, so the ripple follows the row's rounded corners.
  val clickModifier =
      if (onClick == null) Modifier
      else
          Modifier.clip(shape)
              // Role.Button and the label let TalkBack announce the row as "Open listing".
              .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onClick)
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .then(clickModifier)
              .background(AuthColors.Background, shape)
              .border(1.dp, AuthColors.Outline, shape)
              .padding(horizontal = 14.dp, vertical = 12.dp)
              .testTag(ToolboxCardTags.LISTING_ROW),
      verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
        text = listing.title,
        color = AuthColors.TextPrimary,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Row {
      Text(
          text = "${listing.category.displayName} · ",
          color = AuthColors.TextSecondary,
          fontSize = 15.sp,
      )
      Text(
          text = formatChf(listing.price),
          color = AuthColors.Accent,
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
          text = stringResource(R.string.browse_price_per_day),
          color = AuthColors.TextSecondary,
          fontSize = 15.sp,
      )
    }
  }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 320)
@Composable
internal fun ToolboxContentPreview() {
  ToolboxContent(
      uiState =
          ToolboxUiState(
              listings =
                  listOf(
                      ItemListing(
                          ListingCategory.ITEM_TOOLS,
                          8.0,
                          "Bosch drill set",
                          "",
                          listingId = "drill",
                      ),
                      ServiceListing(
                          ListingCategory.SERVICE_TUTORING,
                          25.0,
                          "Math tutoring",
                          "",
                          listingId = "math",
                      ),
                  )
          ),
      onRetry = {},
      onTypeSelected = {},
  )
}
