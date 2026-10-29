// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Opus 5.5
package com.android.polybazaar.browse.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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

object BrowseScreenTags {
  const val SCREEN = "browse_screen"
  const val SEARCH_FIELD = "browse_search_field"
  const val HEADER = "browse_header"
  const val CATEGORY_SELECTOR = "browse_category_selector"
  const val LISTING_CARD = "browse_listing_card"
}

@Composable
fun BrowseScreen(viewModel: BrowseViewModel) {
  val uiState by viewModel.uiState.collectAsState()
  BrowseContent(
      uiState = uiState,
      onQueryChange = viewModel::updateQuery,
      onCategorySelected = viewModel::selectCategory,
  )
}

@Composable
fun BrowseContent(
    uiState: BrowseUiState,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
) {
  Column(
      modifier =
          Modifier.fillMaxSize().background(AuthColors.Background).testTag(BrowseScreenTags.SCREEN)
  ) {
    BrowseHeader()
    SearchField(query = uiState.query, onQueryChange = onQueryChange)
    CategorySelector(
        categories = uiState.categories,
        selectedCategory = uiState.selectedCategory,
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
    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding =
            androidx.compose.foundation.layout.PaddingValues(
                start = 24.dp,
                end = 24.dp,
                bottom = 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      items(uiState.filteredListings, key = { it.id }) { ListingCard(it) }
    }
  }
}

@Composable
internal fun BrowseHeader() {
  Column(
      modifier =
          Modifier.padding(start = 24.dp, end = 24.dp, top = 34.dp, bottom = 20.dp)
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

@Composable
internal fun CategorySelector(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String) -> Unit,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .padding(horizontal = 24.dp, vertical = 20.dp)
              .testTag(BrowseScreenTags.CATEGORY_SELECTOR),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    categories.forEach { category ->
      val isSelected = category == selectedCategory
      Text(
          text = category,
          color = if (isSelected) AuthColors.Accent else AuthColors.TextSecondary,
          fontSize = 16.sp,
          fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
          modifier =
              Modifier.clip(RoundedCornerShape(24.dp))
                  .background(if (isSelected) Color(0xFFFFE9DC) else AuthColors.CardBackground)
                  .border(
                      1.dp,
                      if (isSelected) AuthColors.Accent else AuthColors.Outline,
                      RoundedCornerShape(24.dp),
                  )
                  .clickable { onCategorySelected(category) }
                  .padding(horizontal = 18.dp, vertical = 11.dp),
      )
    }
  }
}

@Composable
internal fun ListingCard(listing: BrowseListing) {
  val shape = RoundedCornerShape(28.dp)
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .height(118.dp)
              .background(AuthColors.CardBackground, shape)
              .border(1.dp, AuthColors.Outline, shape)
              .padding(14.dp)
              .testTag(BrowseScreenTags.LISTING_CARD),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
        modifier =
            Modifier.size(90.dp).background(AuthColors.Background, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
      androidx.compose.material3.Icon(
          painter = painterResource(R.drawable.ic_item),
          contentDescription = null,
          tint = AuthColors.TextPrimary,
          modifier = Modifier.size(36.dp),
      )
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
          text = listOfNotNull(listing.category, listing.distance).joinToString(" · "),
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
                      BrowseListing("drill", "Bosch drill set", "Tools", "350 m away", "CHF 8"),
                      BrowseListing(
                          "projector",
                          "Mini projector",
                          "Electronics",
                          "600 m away",
                          "CHF 12",
                      ),
                  ),
              categories = listOf("Tools", "Electronics"),
          ),
      onQueryChange = {},
      onCategorySelected = {},
  )
}
