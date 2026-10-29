// Co-authored-by: Claude Code (Claude Sonnet 5.5, Claude Opus 5.5)
package com.android.polybazaar.auth.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRulesTest {

  @Test
  fun usernameError_validUsernamesAndBoundaries_returnNull() {
    listOf("ada", "ada_lovelace", "a".repeat(3), "a".repeat(30), "  ada  ", "ada.lovelace")
        .forEach { assertNull(it, AuthRules.usernameError(it)) }
  }

  @Test
  fun usernameError_blank_returnsNull() {
    assertNull(AuthRules.usernameError(""))
    assertNull(AuthRules.usernameError("   "))
  }

  @Test
  fun usernameError_tooShort_returnsMinMessage() {
    listOf("a", "ab", " ab ").forEach {
      assertEquals("Username must be at least 3 characters", AuthRules.usernameError(it))
    }
  }

  @Test
  fun usernameError_tooLong_returnsMaxMessage() {
    assertEquals(
        "Username must be at most 30 characters",
        AuthRules.usernameError("a".repeat(31)),
    )
  }

  @Test
  fun usernameError_slash_returnsSlashMessage() {
    assertEquals("Username cannot contain /", AuthRules.usernameError("ada/lovelace"))
  }

  @Test
  fun usernameError_reservedIds_returnNotAllowed() {
    listOf(".", "..", " .. ", "__name__", "____").forEach {
      assertEquals("This username is not allowed", AuthRules.usernameError(it))
    }
  }

  @Test
  fun limitTo_underAndExactlyMax_returnsInputUnchanged() {
    assertEquals("abc", AuthRules.limitTo("abc", 5))
    assertEquals("abcde", AuthRules.limitTo("abcde", 5))
  }

  @Test
  fun limitTo_overMax_truncates() {
    assertEquals("abcde", AuthRules.limitTo("abcdefgh", 5))
  }

  @Test
  fun limitTo_surrogatePairStraddlingBoundary_isNotSplit() {
    val emoji = "😀"
    val result = AuthRules.limitTo("abcd$emoji", 5)

    assertEquals("abcd", result)
    assertFalse(result.last().isHighSurrogate())
  }

  @Test
  fun limitTo_surrogatePairEndingAtBoundary_isKept() {
    val emoji = "😀"

    assertEquals("abc$emoji", AuthRules.limitTo("abc${emoji}d", 5))
  }

  @Test
  fun limitInput_dropsLeadingWhitespaceBeforeCapping() {
    assertEquals("abcde", AuthRules.limitInput("   abcdefgh", 5))
    assertEquals("ab  ", AuthRules.limitInput("  ab  ", 5))
  }

  @Test
  fun normalizeUsername_trimsAndLowercases() {
    assertEquals("ada", AuthRules.normalizeUsername("  AdA "))
  }

  @Test
  fun normalizeUsername_canLengthenTheString() {
    assertEquals(40, AuthRules.normalizeUsername("\u0130".repeat(20)).length)
  }

  @Test
  fun usernameError_normalizesInternally() {
    assertEquals(
        "Username must be at most 30 characters",
        AuthRules.usernameError("\u0130".repeat(20)),
    )
    assertNull(AuthRules.usernameError("ABC"))
  }

  @Test
  fun emailLengthError_countsTrimmedLength() {
    val atLimit = "e".repeat(AuthRules.MAX_EMAIL_LENGTH)

    assertNull(AuthRules.emailLengthError(atLimit))
    assertNull(AuthRules.emailLengthError("  $atLimit  "))
    assertEquals(
        "Email must be at most 254 characters",
        AuthRules.emailLengthError(atLimit + "e"),
    )
  }

  @Test
  fun meetsPasswordRules_needsTheMinimumLengthALetterAndADigit() {
    assertTrue(AuthRules.meetsPasswordRules("abcdefg1"))
    // Any Unicode letter and digit count: Cyrillic letters and Arabic-Indic digits.
    assertTrue(AuthRules.meetsPasswordRules("пароль\u0661\u0662"))
    assertFalse(AuthRules.meetsPasswordRules("abcdef1"))
    assertFalse(AuthRules.meetsPasswordRules("abcdefgh"))
    assertFalse(AuthRules.meetsPasswordRules("12345678"))
    assertFalse(AuthRules.meetsPasswordRules(""))
  }

  @Test
  fun passwordLengthError_flagsOnlyOverTheMax() {
    assertNull(AuthRules.passwordLengthError("p".repeat(AuthRules.MAX_PASSWORD_LENGTH)))
    assertEquals(
        "Password must be at most 128 characters",
        AuthRules.passwordLengthError("p".repeat(AuthRules.MAX_PASSWORD_LENGTH + 1)),
    )
  }
}
