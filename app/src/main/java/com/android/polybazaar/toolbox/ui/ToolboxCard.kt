// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
}

@Composable
fun ToolboxCard(viewModel: ToolboxViewModel) {
  val uiState by viewModel.uiState.collectAsState()
  ToolboxContent(uiState = uiState, onRetry = viewModel::loadListings)
}

/**
 * The "My toolbox" card of the profile, listing the user's own listings.
 *
 * Fills the height it is given: the title stays in place and the rows scroll inside the card, so it
 * needs a bounded height. Opening a listing, its status and the user's rentals are follow-ups
 * for #20.
 */
@Composable
fun ToolboxContent(uiState: ToolboxUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
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
    Text(
        text = stringResource(R.string.toolbox_title),
        color = AuthColors.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
    )
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
    if (uiState.listings.isNotEmpty()) {
      LazyColumn(
          modifier = Modifier.fillMaxWidth().weight(1f).testTag(ToolboxCardTags.LISTINGS),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(uiState.listings) { ToolboxListingRow(it) }
      }
    }
  }
}

@Composable
private fun ToolboxListingRow(listing: Listing) {
  Row(
      modifier = Modifier.fillMaxWidth().testTag(ToolboxCardTags.LISTING_ROW),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = listing.title,
          color = AuthColors.TextPrimary,
          fontSize = 17.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      Text(
          text =
              stringResource(
                  when (listing.type) {
                    ListingType.ITEM -> R.string.toolbox_type_item
                    ListingType.SERVICE -> R.string.toolbox_type_service
                  }
              ),
          color = AuthColors.TextSecondary,
          fontSize = 14.sp,
      )
    }
    Spacer(modifier = Modifier.width(12.dp))
    // Same format as the Browse cards: whole prices stay short, and Locale.ROOT keeps the point.
    Text(
        text = "CHF " + String.format(Locale.ROOT, "%.2f", listing.price).removeSuffix(".00"),
        color = AuthColors.Accent,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(
        text = stringResource(R.string.browse_price_per_day),
        color = AuthColors.TextSecondary,
        fontSize = 16.sp,
    )
  }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 240)
@Composable
internal fun ToolboxContentPreview() {
  ToolboxContent(
      uiState =
          ToolboxUiState(
              listings =
                  listOf(
                      ItemListing("Tools", 8.0, "Bosch drill set", "", listingId = "drill"),
                      ServiceListing("Tutoring", 25.0, "Math tutoring", "", listingId = "math"),
                  )
          ),
      onRetry = {},
  )
}
