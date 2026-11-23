// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.map.viewmodel.MapMarker

/**
 * The previewed listing at the bottom of the map (#342): its marker's pin, whether it's an item or
 * a skill, its title and its price per day. The whole card opens the listing; the cross closes it.
 */
@Composable
internal fun MarkerPreview(marker: MapMarker, onOpen: () -> Unit, onClose: () -> Unit) {
  val shape = RoundedCornerShape(20.dp)
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .padding(horizontal = 18.dp)
              .shadow(4.dp, shape)
              .background(AuthColors.CardBackground, shape)
              .border(1.dp, AuthColors.Outline, shape)
              // Role and label: TalkBack reads "Open Bosch drill set", then the card's text.
              .clickable(
                  role = Role.Button,
                  onClickLabel = stringResource(R.string.map_preview_open, marker.title),
                  onClick = onOpen,
              )
              .heightIn(min = 72.dp)
              .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
              .testTag(MapScreenTags.PREVIEW),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    MarkerPin(icon = markerIcon(marker.type))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      TypePill(marker.type)
      Text(
          text = marker.title,
          color = AuthColors.TextPrimary,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          // One line, so a long title doesn't push the price off the card.
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      Text(
          text = "${marker.price} ${stringResource(R.string.browse_price_per_day)}",
          color = AuthColors.TextSecondary,
          fontSize = 12.sp,
      )
    }
    // 48dp: Material 3's IconButton is 40dp, under the touch target the app keeps everywhere.
    IconButton(
        onClick = onClose,
        modifier = Modifier.size(48.dp).testTag(MapScreenTags.PREVIEW_CLOSE),
    ) {
      Icon(
          painter = painterResource(R.drawable.ic_close),
          contentDescription = stringResource(R.string.map_preview_close),
          tint = AuthColors.TextSecondary,
          modifier = Modifier.size(18.dp),
      )
    }
  }
}

/** "Item" or "Skill" in a small pill, as the markers distinguish them. */
@Composable
private fun TypePill(type: ListingType) {
  Text(
      text =
          stringResource(
              when (type) {
                ListingType.ITEM -> R.string.map_preview_item
                ListingType.SERVICE -> R.string.map_preview_skill
              }
          ),
      color = AuthColors.Accent,
      fontSize = 11.sp,
      fontWeight = FontWeight.SemiBold,
      modifier =
          Modifier.background(AuthColors.AccentContainer, CircleShape)
              .padding(horizontal = 8.dp, vertical = 2.dp),
  )
}
