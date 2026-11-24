// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.ui

import com.android.polybazaar.chat.model.ChatMessage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationDaysTest {

  private fun message(id: String, at: String) = ChatMessage(id, "renter", id, Instant.parse(at))

  @Test
  fun conversationItems_putsADividerBeforeTheFirstMessageOfEachDay() {
    val a = message("a", "2026-11-10T08:00:00Z")
    val b = message("b", "2026-11-10T20:00:00Z")
    val c = message("c", "2026-11-11T09:00:00Z")

    assertEquals(
        listOf(
            ConversationItem.DayDivider(LocalDate.parse("2026-11-10")),
            ConversationItem.Message(a),
            ConversationItem.Message(b),
            ConversationItem.DayDivider(LocalDate.parse("2026-11-11")),
            ConversationItem.Message(c),
        ),
        conversationItems(listOf(a, b, c), ZoneOffset.UTC),
    )
  }

  @Test
  fun conversationItems_splitsTheDaysAtMidnightInTheGivenZone() {
    // 22:30 and 23:30 UTC on the 10th are 23:30 on the 10th and 00:30 on the 11th in Zurich.
    val beforeMidnight = message("a", "2026-11-10T22:30:00Z")
    val afterMidnight = message("b", "2026-11-10T23:30:00Z")
    val zurich = ZoneId.of("Europe/Zurich")

    assertEquals(
        listOf(
            ConversationItem.DayDivider(LocalDate.parse("2026-11-10")),
            ConversationItem.Message(beforeMidnight),
            ConversationItem.DayDivider(LocalDate.parse("2026-11-11")),
            ConversationItem.Message(afterMidnight),
        ),
        conversationItems(listOf(beforeMidnight, afterMidnight), zurich),
    )
    assertEquals(
        listOf(
            ConversationItem.DayDivider(LocalDate.parse("2026-11-10")),
            ConversationItem.Message(beforeMidnight),
            ConversationItem.Message(afterMidnight),
        ),
        conversationItems(listOf(beforeMidnight, afterMidnight), ZoneOffset.UTC),
    )
  }

  @Test
  fun conversationItems_neverGoesBackADay() {
    // The pending last message was stamped by a device clock a day behind.
    val older = message("a", "2026-11-16T09:00:00Z")
    val theirs = message("b", "2026-11-17T09:00:00Z")
    val pending = message("c", "2026-11-16T22:00:00Z")

    assertEquals(
        listOf(
            ConversationItem.DayDivider(LocalDate.parse("2026-11-16")),
            ConversationItem.Message(older),
            ConversationItem.DayDivider(LocalDate.parse("2026-11-17")),
            ConversationItem.Message(theirs),
            ConversationItem.Message(pending),
        ),
        conversationItems(listOf(older, theirs, pending), ZoneOffset.UTC),
    )
  }

  @Test
  fun conversationItems_withoutMessages_isEmpty() {
    assertEquals(emptyList<ConversationItem>(), conversationItems(emptyList(), ZoneOffset.UTC))
  }

  @Test
  fun conversationDay_namesTodayYesterdayAndEarlierDays() {
    val today = LocalDate.parse("2026-01-02")

    assertEquals(ConversationDay.Today, conversationDay(today, today))
    // A message from tomorrow, when the device clock is behind the sender's.
    assertEquals(ConversationDay.Today, conversationDay(today.plusDays(1), today))
    assertEquals(ConversationDay.Yesterday, conversationDay(LocalDate.parse("2026-01-01"), today))
    assertEquals(
        ConversationDay.Date(LocalDate.parse("2025-12-31"), withYear = true),
        conversationDay(LocalDate.parse("2025-12-31"), today),
    )
    val later = LocalDate.parse("2026-03-10")
    assertEquals(
        ConversationDay.Date(LocalDate.parse("2026-03-01"), withYear = false),
        conversationDay(LocalDate.parse("2026-03-01"), later),
    )
  }
}
