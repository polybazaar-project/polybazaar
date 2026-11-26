// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Opus 5.5
package com.android.polybazaar.browse.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.browse.viewmodel.BrowseListing
import com.android.polybazaar.browse.viewmodel.BrowseUiState
import com.android.polybazaar.browse.viewmodel.BrowseViewModel
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.ui.message
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.photo.rememberPhoto

object BrowseScreenTags {
  const val SCREEN = "browse_screen"
  const val SEARCH_FIELD = "browse_search_field"
  const val HEADER = "browse_header"
  const val TYPE_SELECTOR = "browse_type_selector"
  const val TYPE_ALL = "browse_type_all"
  const val TYPE_ITEMS = "browse_type_items"
  const val TYPE_SERVICES = "browse_type_services"
  const val CATEGORY_SELECTOR = "browse_category_selector"
  const val LISTING_CARD = "browse_listing_card"
  const val LISTING_PHOTO = "browse_listing_photo"
  const val LOADING_INDICATOR = "browse_loading_indicator"
  const val ERROR = "browse_error"
  const val RETRY_BUTTON = "browse_retry_button"
}

/** Space kept above and below the search field, part of the gaps around it when not pinned. */
private val SEARCH_PINNED_MARGIN = 8.dp

/** @param onOpenListing called with a listing's id when the user taps its card. */
@Composable
fun BrowseScreen(viewModel: BrowseViewModel, onOpenListing: (String) -> Unit = {}) {
  val uiState by viewModel.uiState.collectAsState()
  BrowseContent(
      uiState = uiState,
      onQueryChange = viewModel::updateQuery,
      onTypeSelected = viewModel::selectType,
      onCategorySelected = viewModel::selectCategory,
      onRetry = viewModel::loadListings,
      onOpenListing = onOpenListing,
  )
}

/**
 * @param onTypeSelected called with the type the user picks, or null for All.
 * @param onOpenListing called with a listing's id when the user taps its card.
 */
@Composable
fun BrowseContent(
    uiState: BrowseUiState,
    onQueryChange: (String) -> Unit,
    onTypeSelected: (ListingType?) -> Unit,
    onCategorySelected: (ListingCategory) -> Unit,
    onRetry: () -> Unit,
    onOpenListing: (String) -> Unit = {},
) {
  // Only the search field stays pinned: the full heading would leave the listings no room on a
  // short window, such as a phone in landscape.
  LazyColumn(
      modifier =
          Modifier.fillMaxSize().background(AuthColors.Background).testTag(BrowseScreenTags.SCREEN),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
  ) {
    item { BrowseHeader() }
    stickyHeader {
      // Opaque, with its own margin, so listings pass cleanly behind the pinned field.
      Box(
          modifier =
              Modifier.fillMaxWidth()
                  .background(AuthColors.Background)
                  .padding(vertical = SEARCH_PINNED_MARGIN)
      ) {
        SearchField(query = uiState.filters.query, onQueryChange = onQueryChange)
      }
    }
    item {
      TypeSelector(selectedType = uiState.filters.type, onTypeSelected = onTypeSelected)
      CategorySelector(
          categories = uiState.categories,
          selectedCategory = uiState.filters.category,
          onCategorySelected = onCategorySelected,
      )
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = stringResource(R.string.browse_nearby_title),
            color = AuthColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.browse_nearby_location),
            color = AuthColors.TextSecondary,
            fontSize = 14.sp,
        )
        // The location will be done later using the map etc, for now I would keep it this way.
      }
      uiState.error?.let { BrowseError(error = it, onRetry = onRetry) }
      // Once listings are shown, a reload keeps them on screen instead of a spinner.
      if (uiState.isLoading && uiState.listings.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(
              color = AuthColors.Accent,
              modifier = Modifier.size(32.dp).testTag(BrowseScreenTags.LOADING_INDICATOR),
          )
        }
      }
    }
    itemsIndexed(uiState.filteredListings, key = { _, listing -> listing.id }) { index, listing ->
      Box(
          modifier =
              Modifier.padding(start = 24.dp, end = 24.dp, top = if (index == 0) 0.dp else 14.dp)
      ) {
        ListingCard(
            listing = listing,
            photo = listing.photoPath?.let { uiState.photos[it] },
            onClick = { onOpenListing(listing.id) },
        )
      }
    }
  }
}

@Composable
internal fun BrowseError(error: AppError, onRetry: () -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .padding(horizontal = 24.dp, vertical = 4.dp)
              .testTag(BrowseScreenTags.ERROR),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text = error.message(),
        color = AuthColors.Danger,
        fontSize = 14.sp,
        modifier = Modifier.weight(1f),
    )
    TextButton(onClick = onRetry, modifier = Modifier.testTag(BrowseScreenTags.RETRY_BUTTON)) {
      Text(
          text = stringResource(R.string.browse_retry),
          color = AuthColors.Accent,
          fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
internal fun BrowseHeader() {
  Column(
      modifier =
          Modifier.padding(
                  start = 24.dp,
                  end = 24.dp,
                  top = 34.dp,
                  bottom = 20.dp - SEARCH_PINNED_MARGIN,
              )
              .testTag(BrowseScreenTags.HEADER)
  ) {
    Text(
        text = stringResource(R.string.browse_header_title),
        color = AuthColors.TextPrimary,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.browse_header_subtitle),
        color = AuthColors.TextSecondary,
        fontSize = 16.sp,
        modifier = Modifier.padding(top = 2.dp),
    )
  }
}

@Composable
internal fun SearchField(query: String, onQueryChange: (String) -> Unit) {
  val shape = RoundedCornerShape(20.dp)
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .padding(horizontal = 24.dp)
              .height(66.dp)
              .background(AuthColors.CardBackground, shape)
              .border(1.dp, AuthColors.Outline, shape)
              .padding(horizontal = 18.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = "⌕", color = AuthColors.TextSecondary, fontSize = 30.sp)
    Spacer(modifier = Modifier.width(12.dp))
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.weight(1f).testTag(BrowseScreenTags.SEARCH_FIELD),
        singleLine = true,
        textStyle = TextStyle(color = AuthColors.TextPrimary, fontSize = 16.sp),
        decorationBox = { innerTextField ->
          if (query.isBlank()) {
            Text(
                text = stringResource(R.string.browse_search_placeholder),
                color = AuthColors.TextSecondary,
                fontSize = 16.sp,
            )
          }
          innerTextField()
        },
    )
  }
}

/** The All / Items / Services row: shows both types, or only one, given to [onTypeSelected]. */
@Composable
internal fun TypeSelector(selectedType: ListingType?, onTypeSelected: (ListingType?) -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .padding(start = 24.dp, end = 24.dp, top = 20.dp - SEARCH_PINNED_MARGIN)
              // One group of radio buttons, so TalkBack reads the row as one choice among three.
              .selectableGroup()
              .testTag(BrowseScreenTags.TYPE_SELECTOR),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    BrowseChip(
        text = stringResource(R.string.browse_type_all),
        isSelected = selectedType == null,
        onClick = { onTypeSelected(null) },
        role = Role.RadioButton,
        modifier = Modifier.testTag(BrowseScreenTags.TYPE_ALL),
    )
    BrowseChip(
        text = stringResource(R.string.browse_type_items),
        isSelected = selectedType == ListingType.ITEM,
        onClick = { onTypeSelected(ListingType.ITEM) },
        role = Role.RadioButton,
        modifier = Modifier.testTag(BrowseScreenTags.TYPE_ITEMS),
    )
    BrowseChip(
        text = stringResource(R.string.browse_type_services),
        isSelected = selectedType == ListingType.SERVICE,
        onClick = { onTypeSelected(ListingType.SERVICE) },
        role = Role.RadioButton,
        modifier = Modifier.testTag(BrowseScreenTags.TYPE_SERVICES),
    )
  }
}

/**
 * The category chips. Tapping the selected chip clears it, which [onCategorySelected] handles by
 * getting the same category again.
 */
@Composable
internal fun CategorySelector(
    categories: List<ListingCategory>,
    selectedCategory: ListingCategory?,
    onCategorySelected: (ListingCategory) -> Unit,
) {
  Row(
      modifier =
          // Scrolls so every chip stays reachable; the padding sits inside the scroll so chips
          // align with the search field at rest but scroll to the screen edge.
          Modifier.fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp)
              .testTag(BrowseScreenTags.CATEGORY_SELECTOR),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    categories.forEach { category ->
      BrowseChip(
          text = chipLabel(category),
          isSelected = category == selectedCategory,
          onClick = { onCategorySelected(category) },
          // A checkbox, not a radio button: tapping the selected chip turns it off.
          role = Role.Checkbox,
      )
    }
  }
}

/**
 * The name on a category's chip. Items and services both have an "Other" category, so their chips
 * say which one they are; every other category keeps its name.
 */
@Composable
internal fun chipLabel(category: ListingCategory): String =
    when (category) {
      ListingCategory.ITEM_OTHER -> stringResource(R.string.browse_category_other_items)
      ListingCategory.SERVICE_OTHER -> stringResource(R.string.browse_category_other_services)
      else -> category.displayName
    }

/**
 * A filter chip of Browse, highlighted and announced as selected when [isSelected].
 *
 * @param role how TalkBack announces the chip, such as a radio button or a checkbox.
 */
@Composable
private fun BrowseChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    role: Role,
    modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(24.dp)
  Text(
      text = text,
      color = if (isSelected) AuthColors.Accent else AuthColors.TextSecondary,
      fontSize = 16.sp,
      fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
      maxLines = 1,
      softWrap = false,
      modifier =
          modifier
              .clip(shape)
              .background(
                  if (isSelected) AuthColors.SelectedBackground else AuthColors.CardBackground
              )
              .border(1.dp, if (isSelected) AuthColors.Accent else AuthColors.Outline, shape)
              .selectable(selected = isSelected, role = role, onClick = onClick)
              .padding(horizontal = 18.dp, vertical = 11.dp),
  )
}

/**
 * A listing's card in Browse.
 *
 * @param photo the listing's first photo, downloaded; null shows a placeholder icon instead, for a
 *   listing without photos or while the photo is downloading.
 * @param onClick called when the user taps the card, or null for a card that cannot be tapped.
 */
@Composable
internal fun ListingCard(
    listing: BrowseListing,
    photo: ByteArray? = null,
    onClick: (() -> Unit)? = null,
) {
  val shape = RoundedCornerShape(28.dp)
  val openLabel = stringResource(R.string.open_listing)
  // Clipped first, so the ripple follows the card's rounded corners.
  val clickModifier =
      if (onClick == null) Modifier
      else
          Modifier.clip(shape)
              // Role.Button and the label let TalkBack announce the card as "Open listing".
              .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onClick)
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .height(118.dp)
              .then(clickModifier)
              .background(AuthColors.CardBackground, shape)
              .border(1.dp, AuthColors.Outline, shape)
              .padding(14.dp)
              .testTag(BrowseScreenTags.LISTING_CARD),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    val decoded = rememberPhoto(photo)
    Box(
        modifier =
            Modifier.size(90.dp).clip(RoundedCornerShape(18.dp)).background(AuthColors.Background),
        contentAlignment = Alignment.Center,
    ) {
      if (decoded != null) {
        // Decorative: the title next to it names the listing.
        Image(
            painter = BitmapPainter(decoded),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().testTag(BrowseScreenTags.LISTING_PHOTO),
        )
      } else {
        androidx.compose.material3.Icon(
            painter = painterResource(R.drawable.ic_item),
            contentDescription = null,
            tint = AuthColors.TextPrimary,
            modifier = Modifier.size(36.dp),
        )
      }
    }
    Spacer(modifier = Modifier.width(18.dp))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
          text = listing.title,
          color = AuthColors.TextPrimary,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      Text(
          text = listOfNotNull(listing.category.displayName, listing.distance).joinToString(" · "),
          color = AuthColors.TextSecondary,
          fontSize = 15.sp,
          maxLines = 1,
      )
      Row {
        Text(
            text = listing.price,
            color = AuthColors.Accent,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.browse_price_per_day),
            color = AuthColors.TextSecondary,
            fontSize = 17.sp,
        )
      }
    }
  }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 844)
@Composable
internal fun BrowseScreenPreview() {
  BrowseContent(
      uiState =
          BrowseUiState(
              listings =
                  listOf(
                      BrowseListing(
                          id = "drill",
                          title = "Bosch drill set",
                          category = ListingCategory.ITEM_TOOLS,
                          price = "CHF 8",
                      ),
                      BrowseListing(
                          id = "projector",
                          title = "Mini projector",
                          category = ListingCategory.ITEM_ELECTRONICS,
                          price = "CHF 12",
                      ),
                  ),
          ),
      onQueryChange = {},
      onTypeSelected = {},
      onCategorySelected = {},
      onRetry = {},
  )
}
