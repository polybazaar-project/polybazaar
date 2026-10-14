// Co-authored-by: Claude Opus 5.5
package com.android.polybazaar.auth.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpflEmailTest {

  @Test
  fun epflAddress_isAccepted() {
    assertTrue(isEpflEmail("ada@epfl.ch"))
  }

  @Test
  fun upperCaseAndSurroundingSpaces_areAccepted() {
    assertTrue(isEpflEmail("  Ada.Lovelace@EPFL.CH "))
  }

  @Test
  fun otherDomain_isRejected() {
    assertFalse(isEpflEmail("ada@gmail.com"))
  }

  @Test
  fun lookalikeDomains_areRejected() {
    assertFalse(isEpflEmail("ada@notepfl.ch"))
    assertFalse(isEpflEmail("ada@epfl.ch.evil.com"))
  }

  @Test
  fun domainWithoutName_isRejected() {
    assertFalse(isEpflEmail("@epfl.ch"))
  }
}
