// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.profile.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileRulesTest {

  @Test
  fun limitBio_keepsBiosWithinTheLimit() {
    assertEquals("", ProfileRules.limitBio(""))
    assertEquals("a".repeat(500), ProfileRules.limitBio("a".repeat(500)))
  }

  @Test
  fun limitBio_truncatesAtTheLimit() {
    assertEquals("a".repeat(500), ProfileRules.limitBio("a".repeat(501)))
  }

  @Test
  fun limitBio_doesNotSplitSurrogatePairs() {
    val bio = "a".repeat(499) + "😀"
    assertEquals("a".repeat(499), ProfileRules.limitBio(bio))
  }
}
