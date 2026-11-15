// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRulesTest {

  @Test
  fun countedLength_ignoresWhitespaceAtBothEnds() {
    assertEquals(5, ChatRules.countedLength("  \n hello \t "))
    assertEquals(0, ChatRules.countedLength("   "))
  }

  @Test
  fun countedLength_countsUtf16Units() {
    assertEquals(2, ChatRules.countedLength("\uD83D\uDE00"))
  }

  @Test
  fun isSendable_appliesTheLimitToTheCountedLength() {
    val max = "a".repeat(ChatRules.MAX_MESSAGE_LENGTH)

    assertTrue(ChatRules.isSendable("$max   "))
    assertFalse(ChatRules.isSendable(max + "a"))
    assertFalse(ChatRules.isSendable("   "))
  }
}
