// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.auth.ui.AuthRoundIconButton

/**
 * The owner's ⋮ button on the photo area and its menu: Pause or Resume, and Delete. Delete only
 * asks [onDeleteRequest] to confirm; the listing is deleted once the owner confirms.
 *
 * @param enabled false while a pause or delete is being saved, so a second one cannot start.
 */
@Composable
internal fun OwnerMenu(
    isPaused: Boolean,
    enabled: Boolean,
    onSetPaused: (Boolean) -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
  // Whether the menu is open is the screen's own state: the ViewModel only hears the choice.
  var expanded by remember { mutableStateOf(false) }
  Box(modifier = modifier) {
    AuthRoundIconButton(
        iconRes = R.drawable.ic_more_vertical,
        contentDescription = stringResource(R.string.listing_detail_owner_menu),
        onClick = { expanded = true },
        enabled = enabled,
        modifier = Modifier.testTag(ListingDetailScreenTags.OWNER_MENU_BUTTON),
    )
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        shape = RoundedCornerShape(14.dp),
        containerColor = AuthColors.CardBackground,
        border = BorderStroke(1.dp, AuthColors.Outline),
    ) {
      // One item that flips, so the menu never offers what the listing already is.
      OwnerMenuItem(
          text =
              stringResource(
                  if (isPaused) R.string.listing_detail_resume else R.string.listing_detail_pause
              ),
          iconRes = if (isPaused) R.drawable.ic_play else R.drawable.ic_pause,
          color = AuthColors.TextPrimary,
          tag =
              if (isPaused) ListingDetailScreenTags.RESUME_ITEM
              else ListingDetailScreenTags.PAUSE_ITEM,
          onClick = {
            expanded = false
            onSetPaused(!isPaused)
          },
      )
      OwnerMenuItem(
          text = stringResource(R.string.listing_detail_delete),
          iconRes = R.drawable.ic_trash,
          color = AuthColors.Danger,
          tag = ListingDetailScreenTags.DELETE_ITEM,
          onClick = {
            expanded = false
            onDeleteRequest()
          },
      )
    }
  }
}

/** A 48dp menu row with its icon before the text, both in [color]. */
@Composable
private fun OwnerMenuItem(
    text: String,
    iconRes: Int,
    color: Color,
    tag: String,
    onClick: () -> Unit,
) {
  DropdownMenuItem(
      text = {
        Text(text = text, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
      },
      // Decorative: the text says what the item does.
      leadingIcon = {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(17.dp),
        )
      },
      onClick = onClick,
      modifier = Modifier.testTag(tag),
  )
}

/**
 * Asks the owner to confirm the delete, which cannot be undone. Both buttons are text buttons (48dp
 * touch target), as in the Figma "Owner delete confirm".
 */
@Composable
internal fun DeleteListingDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
      onDismissRequest = onDismiss,
      containerColor = AuthColors.CardBackground,
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier.testTag(ListingDetailScreenTags.DELETE_DIALOG),
      title = {
        Text(
            text = stringResource(R.string.listing_detail_delete_title),
            color = AuthColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
      },
      text = {
        Text(
            text = stringResource(R.string.listing_detail_delete_text),
            color = AuthColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
      },
      confirmButton = {
        TextButton(
            onClick = onConfirm,
            modifier = Modifier.testTag(ListingDetailScreenTags.DELETE_CONFIRM),
        ) {
          Text(
              text = stringResource(R.string.listing_detail_delete_confirm),
              color = AuthColors.Danger,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
          )
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(ListingDetailScreenTags.DELETE_CANCEL),
        ) {
          Text(
              text = stringResource(R.string.listing_detail_delete_cancel),
              color = AuthColors.Accent,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
          )
        }
      },
  )
}
