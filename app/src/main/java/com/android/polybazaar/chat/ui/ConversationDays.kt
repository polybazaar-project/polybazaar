// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.android.polybazaar.R
import com.android.polybazaar.chat.model.ChatMessage
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One row of the message list: a message, or the day the messages under it were sent on. */
internal sealed interface ConversationItem {
  data class DayDivider(val date: LocalDate) : ConversationItem

  data class Message(val message: ChatMessage) : ConversationItem
}

/**
 * [messages], oldest first, with a [ConversationItem.DayDivider] before the first message of each
 * day in [zone]. The days only move forward: a message dated before the day above it, such as a
 * pending one stamped by a device clock that is behind, stays under that day, so no day is listed
 * twice.
 */
internal fun conversationItems(messages: List<ChatMessage>, zone: ZoneId): List<ConversationItem> {
  val items = mutableListOf<ConversationItem>()
  var day: LocalDate? = null
  for (message in messages) {
    val date = message.sentAt.atZone(zone).toLocalDate()
    if (day == null || date.isAfter(day)) {
      items += ConversationItem.DayDivider(date)
      day = date
    }
    items += ConversationItem.Message(message)
  }
  return items
}

/** How a day divider names its day. */
internal sealed interface ConversationDay {
  /** Today, or later when the device clock is behind the sender's. */
  data object Today : ConversationDay

  data object Yesterday : ConversationDay

  /** An earlier day, with its year when it is not this year. */
  data class Date(val date: LocalDate, val withYear: Boolean) : ConversationDay
}

/** How the divider of [date] names it, seen on [today]. */
internal fun conversationDay(date: LocalDate, today: LocalDate): ConversationDay =
    when {
      !date.isBefore(today) -> ConversationDay.Today
      date == today.minusDays(1) -> ConversationDay.Yesterday
      else -> ConversationDay.Date(date, withYear = date.year != today.year)
    }

/** "Today", "Yesterday", "4 May" or "4 May 2025", in the app's locale. */
@Composable
internal fun ConversationDay.label(): String =
    when (this) {
      ConversationDay.Today -> stringResource(R.string.conversation_day_today)
      ConversationDay.Yesterday -> stringResource(R.string.conversation_day_yesterday)
      is ConversationDay.Date -> {
        // From the configuration, so the date follows a change of locale.
        val locale = LocalConfiguration.current.locales[0]
        date.format(DateTimeFormatter.ofPattern(if (withYear) "d MMM yyyy" else "d MMM", locale))
      }
    }
