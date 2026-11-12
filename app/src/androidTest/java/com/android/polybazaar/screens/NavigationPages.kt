// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.screens

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import com.android.polybazaar.browse.ui.BrowseScreenTags
import com.android.polybazaar.ui.navigation.BottomBarTags
import com.kaspersky.components.composesupport.core.KNode
import io.github.kakaocup.compose.node.element.ComposeScreen

class BrowsePage(semanticsProvider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<BrowsePage>(
        semanticsProvider,
        viewBuilderAction = { hasTestTag(BrowseScreenTags.SCREEN) },
    )

class BottomBarPage(semanticsProvider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<BottomBarPage>(
        semanticsProvider,
        viewBuilderAction = { hasTestTag(BottomBarTags.BAR) },
    ) {
  val browseTab = KNode(semanticsProvider) { hasTestTag(BottomBarTags.BROWSE_TAB) }
  val createListingTab = KNode(semanticsProvider) { hasTestTag(BottomBarTags.CREATE_LISTING_TAB) }
  val profileTab = KNode(semanticsProvider) { hasTestTag(BottomBarTags.PROFILE_TAB) }
}
