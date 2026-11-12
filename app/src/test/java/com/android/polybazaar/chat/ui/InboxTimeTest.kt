// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InboxTimeTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val now = Instant.parse("2026-11-11T12:00:00Z")

  /** The time of a message sent [elapsed] before [now]. */
  private fun timeAgo(elapsed: Duration) = inboxTime(now.minus(elapsed), now, ZoneOffset.UTC)

  @Test
  fun noMessage_hasNoTime() {
    assertNull(inboxTime(null, now))
  }

  @Test
  fun underAMinute_orInTheFuture_isNow() {
    assertEquals(InboxTime.Now, timeAgo(Duration.ZERO))
    assertEquals(InboxTime.Now, timeAgo(Duration.ofSeconds(59)))
    assertEquals(InboxTime.Now, timeAgo(Duration.ofSeconds(-30)))
  }

  @Test
  fun underAnHour_isWholeMinutes() {
    assertEquals(InboxTime.Minutes(1), timeAgo(Duration.ofMinutes(1)))
    assertEquals(InboxTime.Minutes(59), timeAgo(Duration.ofMinutes(60).minusSeconds(1)))
  }

  @Test
  fun underADay_isWholeHours() {
    assertEquals(InboxTime.Hours(1), timeAgo(Duration.ofHours(1)))
    assertEquals(InboxTime.Hours(23), timeAgo(Duration.ofDays(1).minusSeconds(1)))
  }

  @Test
  fun underAWeek_isWholeDays() {
    assertEquals(InboxTime.Days(1), timeAgo(Duration.ofDays(1)))
    assertEquals(InboxTime.Days(6), timeAgo(Duration.ofDays(7).minusSeconds(1)))
  }

  @Test
  fun aWeekOrMore_isTheDayInTheGivenZone() {
    // 23:30 UTC is already the next day in Zurich.
    val sent = Instant.parse("2026-10-31T23:30:00Z")

    assertEquals(InboxTime.Date(LocalDate.of(2026, 10, 31)), inboxTime(sent, now, ZoneOffset.UTC))
    assertEquals(
        InboxTime.Date(LocalDate.of(2026, 11, 1)),
        inboxTime(sent, now, ZoneId.of("Europe/Zurich")),
    )
  }

  /** The labels of [times], rendered in [locale] with [text]. */
  private fun labels(
      locale: Locale,
      vararg times: InboxTime,
      text: @Composable InboxTime.() -> String = { label() },
  ): List<String> {
    val labels = mutableListOf<String>()
    composeTestRule.setContent {
      val configuration = Configuration(LocalConfiguration.current).apply { setLocale(locale) }
      CompositionLocalProvider(LocalConfiguration provides configuration) {
        labels.clear()
        times.forEach { labels += it.text() }
      }
    }
    composeTestRule.waitForIdle()
    return labels
  }

  @Test
  fun label_isShortForEachUnit() {
    assertEquals(
        listOf("now", "5m", "3h", "2d", "4 Nov"),
        labels(
            Locale.UK,
            InboxTime.Now,
            InboxTime.Minutes(5),
            InboxTime.Hours(3),
            InboxTime.Days(2),
            InboxTime.Date(LocalDate.of(2026, 11, 4)),
        ),
    )
  }

  @Test
  fun dateLabel_followsTheConfigurationLocale() {
    assertEquals(listOf("4 nov."), labels(Locale.FRANCE, InboxTime.Date(LocalDate.of(2026, 11, 4))))
  }

  @Test
  fun spokenLabel_spellsOutTheUnitWithItsPlural() {
    assertEquals(
        listOf(
            "now",
            "1 minute ago",
            "5 minutes ago",
            "1 hour ago",
            "3 hours ago",
            "1 day ago",
            "2 days ago",
            "4 Nov",
        ),
        labels(
            Locale.UK,
            InboxTime.Now,
            InboxTime.Minutes(1),
            InboxTime.Minutes(5),
            InboxTime.Hours(1),
            InboxTime.Hours(3),
            InboxTime.Days(1),
            InboxTime.Days(2),
            InboxTime.Date(LocalDate.of(2026, 11, 4)),
        ) {
          spokenLabel()
        },
    )
  }
}
