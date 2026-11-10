// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.screens

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import com.android.polybazaar.listing.ui.CreateListingScreenTags
import com.kaspersky.components.composesupport.core.KNode
import io.github.kakaocup.compose.node.element.ComposeScreen

class CreateListingPage(semanticsProvider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<CreateListingPage>(
        semanticsProvider,
        viewBuilderAction = { hasTestTag(CreateListingScreenTags.SCREEN) },
    ) {
  val takePhoto = KNode(semanticsProvider) { hasTestTag(CreateListingScreenTags.TAKE_PHOTO) }
  val cameraPermissionDialog =
      KNode(semanticsProvider) { hasTestTag(CreateListingScreenTags.CAMERA_PERMISSION_DIALOG) }
  val cameraPermissionCancel =
      KNode(semanticsProvider) { hasTestTag(CreateListingScreenTags.CAMERA_PERMISSION_CANCEL) }
}
