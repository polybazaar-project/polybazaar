// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher

/** Matches a node whose click action TalkBack announces as [label]. */
fun hasClickLabel(label: String): SemanticsMatcher =
    SemanticsMatcher("has click label \"$label\"") {
      it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }
