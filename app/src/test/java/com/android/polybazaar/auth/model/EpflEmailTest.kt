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
  fun usualLocalPartCharacters_areAccepted() {
    assertTrue(isEpflEmail("ada.lovelace@epfl.ch"))
    assertTrue(isEpflEmail("ada_lovelace-2@epfl.ch"))
    assertTrue(isEpflEmail("ada+market@epfl.ch"))
  }

  @Test
  fun otherDomain_isRejected() {
    assertFalse(isEpflEmail("ada@gmail.com"))
  }

  @Test
  fun lookalikeDomains_areRejected() {
    assertFalse(isEpflEmail("ada@notepfl.ch"))
    assertFalse(isEpflEmail("ada@epfl.ch.evil.com"))
    assertFalse(isEpflEmail("ada@epflxch"))
  }

  @Test
  fun domainWithoutName_isRejected() {
    assertFalse(isEpflEmail("@epfl.ch"))
  }

  // Regression tests for PR #106 review: these used to pass the suffix-only check.

  @Test
  fun doubleAt_isRejected() {
    assertFalse(isEpflEmail("user@@epfl.ch"))
    assertFalse(isEpflEmail("@@epfl.ch"))
    assertFalse(isEpflEmail("ada@epfl.ch@epfl.ch"))
  }

  @Test
  fun whitespaceInsideAddress_isRejected() {
    assertFalse(isEpflEmail("a   @epfl.ch"))
    assertFalse(isEpflEmail("ada lovelace@epfl.ch"))
    assertFalse(isEpflEmail("ada\t@epfl.ch"))
    assertFalse(isEpflEmail("ada@ epfl.ch"))
  }

  @Test
  fun misplacedDots_areRejected() {
    assertFalse(isEpflEmail(".ada@epfl.ch"))
    assertFalse(isEpflEmail("ada.@epfl.ch"))
    assertFalse(isEpflEmail("ada..lovelace@epfl.ch"))
  }

  @Test
  fun blank_isRejected() {
    assertFalse(isEpflEmail(""))
    assertFalse(isEpflEmail("   "))
  }
}
