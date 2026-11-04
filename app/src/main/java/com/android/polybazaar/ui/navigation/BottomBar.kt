// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthColors

object BottomBarTags {
  const val BAR = "bottom_bar"
  const val BROWSE_TAB = "bottom_bar_browse_tab"
  const val CREATE_LISTING_TAB = "bottom_bar_create_listing_tab"
  const val PROFILE_TAB = "bottom_bar_profile_tab"
}

private data class BottomBarTab(
    val route: String,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    val testTag: String,
)

private val tabs =
    listOf(
        BottomBarTab(
            Routes.BROWSE,
            R.string.nav_browse,
            R.drawable.ic_nav_browse,
            BottomBarTags.BROWSE_TAB,
        ),
        BottomBarTab(
            Routes.CREATE_LISTING,
            R.string.nav_create_listing,
            R.drawable.ic_nav_create_listing,
            BottomBarTags.CREATE_LISTING_TAB,
        ),
        BottomBarTab(
            Routes.PROFILE,
            R.string.nav_profile,
            R.drawable.ic_nav_profile,
            BottomBarTags.PROFILE_TAB,
        ),
    )

internal val tabRoutes = tabs.map { it.route }

private val barShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/** Whether [route] is one of the top-level destinations that show the bottom bar. */
internal fun showsBottomBar(route: String?): Boolean = route in tabRoutes

/**
 * The app's main navigation bar, with one destination per entry in [tabs].
 *
 * @param currentRoute the route on screen, which decides the selected destination.
 * @param onTabSelected called with the route of the tapped destination.
 */
@Composable
internal fun AppBottomBar(currentRoute: String?, onTabSelected: (String) -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .testTag(BottomBarTags.BAR)
              .dropShadow(
                  shape = barShape,
                  shadow =
                      Shadow(
                          radius = 18.dp,
                          color = AuthColors.TextPrimary.copy(alpha = 0.05f),
                          offset = DpOffset(x = 0.dp, y = (-4).dp),
                      ),
              )
              .background(AuthColors.CardBackground, barShape)
              .navigationBarsPadding()
              .height(80.dp)
              .padding(start = 24.dp, top = 7.dp, end = 24.dp, bottom = 9.dp)
              .selectableGroup(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    tabs.forEach { tab ->
      BottomBarDestination(
          tab = tab,
          selected = tab.route == currentRoute,
          onClick = { onTabSelected(tab.route) },
      )
    }
  }
}

@Composable
private fun BottomBarDestination(tab: BottomBarTab, selected: Boolean, onClick: () -> Unit) {
  val contentColor = if (selected) AuthColors.Accent else AuthColors.TextSecondary
  Column(
      modifier =
          Modifier.widthIn(min = 56.dp)
              .height(64.dp)
              .selectable(selected = selected, role = Role.Tab, onClick = onClick)
              .testTag(tab.testTag),
      verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
        modifier =
            Modifier.size(width = 44.dp, height = 28.dp)
                .background(
                    color = if (selected) AuthColors.AccentContainer else Color.Transparent,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
      // The visible label below names the destination for accessibility services.
      Icon(
          painter = painterResource(tab.icon),
          contentDescription = null,
          modifier = Modifier.size(21.dp),
          tint = contentColor,
      )
    }
    Text(
        text = stringResource(tab.label),
        color = contentColor,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}
