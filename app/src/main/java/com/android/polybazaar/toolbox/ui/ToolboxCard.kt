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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import com.android.polybazaar.toolbox.viewmodel.ToolboxUiState
import com.android.polybazaar.toolbox.viewmodel.ToolboxViewModel
import java.util.Locale

object ToolboxCardTags {
  const val LISTINGS = "toolbox_listings"
  const val LISTING_ROW = "toolbox_listing_row"
  const val LOADING_INDICATOR = "toolbox_loading_indicator"
  const val ERROR = "toolbox_error"
  const val RETRY_BUTTON = "toolbox_retry_button"
  const val EMPTY = "toolbox_empty"
  const val FILTER_ITEMS = "toolbox_filter_items"
  const val FILTER_SERVICES = "toolbox_filter_services"
  const val FILTER_EMPTY = "toolbox_filter_empty"
}

@Composable
fun ToolboxCard(viewModel: ToolboxViewModel) {
  val uiState by viewModel.uiState.collectAsState()
  ToolboxContent(
      uiState = uiState,
      onRetry = viewModel::loadListings,
      onTypeSelected = viewModel::selectType,
  )
}

/**
 * The "My Toolbox" card of the profile, listing the user's own listings, which can be filtered to
 * items or services with [onTypeSelected] from the buttons next to the title.
 *
 * Fills the height it is given: the title stays in place and the rows scroll inside the card, so it
 * needs a bounded height.
 */
@Composable
fun ToolboxContent(
    uiState: ToolboxUiState,
    onRetry: () -> Unit,
    onTypeSelected: (ListingType) -> Unit,
    modifier: Modifier = Modifier,
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
    // The filters sit on the title's line, at its end.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = stringResource(R.string.toolbox_title),
          color = AuthColors.TextPrimary,
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
      )
      if (uiState.listings.isNotEmpty()) {
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
        items(filteredListings) { ToolboxListingRow(it) }
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

/** A pill that filters the listings by type, styled like the Browse category selector. */
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
      fontSize = 16.sp,
      fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
      modifier =
          modifier
              .semantics { selected = isSelected }
              .clip(shape)
              .background(if (isSelected) Color(0xFFFFE9DC) else AuthColors.CardBackground)
              .border(1.dp, if (isSelected) AuthColors.Accent else AuthColors.Outline, shape)
              .clickable(onClick = onClick)
              .padding(horizontal = 18.dp, vertical = 11.dp),
  )
}

@Composable
private fun ToolboxListingRow(listing: Listing) {
  val shape = RoundedCornerShape(18.dp)
  Column(
      modifier =
          Modifier.fillMaxWidth()
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
      // Whole prices stay short, and Locale.ROOT keeps the decimal point.
      Text(
          text = "CHF " + String.format(Locale.ROOT, "%.2f", listing.price).removeSuffix(".00"),
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
