// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.android.polybazaar.R
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How long ago a conversation's last message was sent, as an inbox row shows it. */
internal sealed interface InboxTime {
  /** Less than a minute ago, or in the future when the device clock is behind. */
  data object Now : InboxTime

  /** [count] whole minutes ago, under an hour. */
  data class Minutes(val count: Long) : InboxTime

  /** [count] whole hours ago, under a day. */
  data class Hours(val count: Long) : InboxTime

  /** [count] whole days ago, under a week. */
  data class Days(val count: Long) : InboxTime

  /** A week ago or more: the day it was sent on. */
  data class Date(val date: LocalDate) : InboxTime
}

/**
 * The time since [lastMessageAt], seen at [now]: under a minute is "now", then minutes, hours and
 * days, and from 7 days the date in [zone]. Null when there is no message yet.
 */
internal fun inboxTime(
    lastMessageAt: Instant?,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): InboxTime? {
  if (lastMessageAt == null) return null
  val elapsed = Duration.between(lastMessageAt, now)
  return when {
    elapsed < Duration.ofMinutes(1) -> InboxTime.Now
    elapsed < Duration.ofHours(1) -> InboxTime.Minutes(elapsed.toMinutes())
    elapsed < Duration.ofDays(1) -> InboxTime.Hours(elapsed.toHours())
    elapsed < Duration.ofDays(7) -> InboxTime.Days(elapsed.toDays())
    else -> InboxTime.Date(lastMessageAt.atZone(zone).toLocalDate())
  }
}

/** The short label of this time: "now", "5m", "3h", "2d" or "12 Nov" in the app's locale. */
@Composable
internal fun InboxTime.label(): String =
    when (this) {
      InboxTime.Now -> stringResource(R.string.inbox_time_now)
      is InboxTime.Minutes -> stringResource(R.string.inbox_time_minutes, count)
      is InboxTime.Hours -> stringResource(R.string.inbox_time_hours, count)
      is InboxTime.Days -> stringResource(R.string.inbox_time_days, count)
      is InboxTime.Date ->
          // From the configuration, so the date follows a change of locale.
          date.format(DateTimeFormatter.ofPattern("d MMM", LocalConfiguration.current.locales[0]))
    }

/**
 * What TalkBack reads for this time: "5 minutes ago" rather than "5m", which it may read as meters.
 * "now" and the date read well already, so they keep their [label].
 */
@Composable
internal fun InboxTime.spokenLabel(): String =
    when (this) {
      is InboxTime.Minutes ->
          pluralStringResource(R.plurals.inbox_time_minutes_spoken, count.toInt(), count)
      is InboxTime.Hours ->
          pluralStringResource(R.plurals.inbox_time_hours_spoken, count.toInt(), count)
      is InboxTime.Days ->
          pluralStringResource(R.plurals.inbox_time_days_spoken, count.toInt(), count)
      InboxTime.Now,
      is InboxTime.Date -> label()
    }
