// Co-authored-by: Claude Code (Claude Fable 5.2)
package com.android.polybazaar.booking.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.booking.model.isSelectable
import com.android.polybazaar.booking.model.lastSelectableDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal object AvailabilityCalendarTags {
  const val CALENDAR = "availability_calendar"
  const val MONTH_TITLE = "availability_calendar_month_title"
  const val PREVIOUS_MONTH_BUTTON = "availability_calendar_previous_month_button"
  const val NEXT_MONTH_BUTTON = "availability_calendar_next_month_button"
  const val WEEKS = "availability_calendar_weeks"

  /** The tag of the cell of [date], such as "availability_calendar_day_2026-11-13". */
  fun day(date: LocalDate) = "availability_calendar_day_$date"

  /** The tag of [date] where it only fills a week of the month before or after its own. */
  fun outsideDay(date: LocalDate) = "availability_calendar_outside_day_$date"
}

/** What the calendar shows for one day. */
internal enum class CalendarDayState {
  /** Cannot be chosen: in the past, or not offered by the lender. */
  DISABLED,
  /** Can be chosen and is not. */
  SELECTABLE,
  /** Chosen by the user of this calendar. */
  SELECTED,
}

/**
 * The state of [day] while choosing days: selected, free to choose, or disabled. See [isSelectable]
 * for [today] and [selectableDays].
 */
internal fun selectionDayState(
    day: LocalDate,
    selection: Set<LocalDate>,
    today: LocalDate,
    selectableDays: Set<LocalDate>? = null,
): CalendarDayState =
    when {
      day in selection -> CalendarDayState.SELECTED
      isSelectable(day, today, selectableDays) -> CalendarDayState.SELECTABLE
      else -> CalendarDayState.DISABLED
    }

private const val DAYS_IN_WEEK = 7

/** Six weeks hold every month, so the calendar keeps its height from one month to the next. */
private const val WEEKS_SHOWN = 6

/** Read as "Friday 13 November 2026" by TalkBack. English, like the rest of the app. */
private val fullDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH)

/**
 * One month of days, Monday first. The month changes with its two buttons or with a sideways swipe
 * over the days. It holds no state: the caller owns the month shown and what each day is.
 *
 * Six weeks are always shown. The days of the months before and after that fall in them are greyed
 * out and cannot be chosen until their own month is shown.
 *
 * @param month the month shown.
 * @param onMonthChange called with the month to show when the user asks for another one. The months
 *   before the one of [today] and after the one of [lastSelectableDay] cannot be reached.
 * @param dayState what each day of the month is.
 * @param onDayClick called when a selectable or selected day is tapped. Null makes the calendar
 *   read-only: no day can be tapped or held.
 * @param onDayLongClick called when a selectable or selected day is held, to start a range.
 * @param rangeAnchor the day a range starts from while its end is being chosen, drawn with a ring.
 */
@Composable
internal fun AvailabilityCalendar(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    dayState: (LocalDate) -> CalendarDayState,
    modifier: Modifier = Modifier,
    onDayClick: ((LocalDate) -> Unit)? = null,
    onDayLongClick: ((LocalDate) -> Unit)? = null,
    rangeAnchor: LocalDate? = null,
    today: LocalDate = LocalDate.now(),
) {
  val canGoBack = month > YearMonth.from(today)
  val canGoForward = month < YearMonth.from(lastSelectableDay(today))
  Column(modifier = modifier.fillMaxWidth().testTag(AvailabilityCalendarTags.CALENDAR)) {
    MonthHeader(month, onMonthChange, canGoBack, canGoForward)
    WeekdayHeader()
    // The Monday on or before the 1st, so the 1st lands under its weekday.
    val firstCell = month.atDay(1).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
    Column(
        modifier =
            Modifier.testTag(AvailabilityCalendarTags.WEEKS).pointerInput(
                month,
                canGoBack,
                canGoForward,
            ) {
              // A swipe to the left shows the next month, as turning a page would.
              val enough = 56.dp.toPx()
              var swiped = 0f
              detectHorizontalDragGestures(
                  onDragStart = { swiped = 0f },
                  onDragEnd = {
                    if (swiped < -enough && canGoForward) onMonthChange(month.plusMonths(1))
                    if (swiped > enough && canGoBack) onMonthChange(month.minusMonths(1))
                  },
              ) { _, amount ->
                swiped += amount
              }
            }
    ) {
      repeat(WEEKS_SHOWN) { week ->
        Row(modifier = Modifier.fillMaxWidth()) {
          repeat(DAYS_IN_WEEK) { weekday ->
            val day = firstCell.plusDays((week * DAYS_IN_WEEK + weekday).toLong())
            val cell = Modifier.weight(1f).height(48.dp)
            if (YearMonth.from(day) == month) {
              DayCell(day, dayState(day), day == rangeAnchor, onDayClick, onDayLongClick, cell)
            } else {
              OutsideDay(day, cell)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    IconButton(
        onClick = { onMonthChange(month.minusMonths(1)) },
        enabled = canGoBack,
        modifier = Modifier.testTag(AvailabilityCalendarTags.PREVIOUS_MONTH_BUTTON),
    ) {
      Icon(
          painter = painterResource(R.drawable.ic_chevron_down),
          contentDescription = stringResource(R.string.calendar_previous_month),
          // The down chevron turned to point left.
          modifier = Modifier.rotate(90f).alpha(if (canGoBack) 1f else 0.3f),
          tint = Color.Unspecified,
      )
    }
    Text(
        text = "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}",
        color = AuthColors.TextPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f).testTag(AvailabilityCalendarTags.MONTH_TITLE),
    )
    IconButton(
        onClick = { onMonthChange(month.plusMonths(1)) },
        enabled = canGoForward,
        modifier = Modifier.testTag(AvailabilityCalendarTags.NEXT_MONTH_BUTTON),
    ) {
      Icon(
          painter = painterResource(R.drawable.ic_chevron_down),
          contentDescription = stringResource(R.string.calendar_next_month),
          modifier = Modifier.rotate(-90f).alpha(if (canGoForward) 1f else 0.3f),
          tint = Color.Unspecified,
      )
    }
  }
}

/** "Mon" to "Sun". Hidden from TalkBack: every day already says its weekday. */
@Composable
private fun WeekdayHeader() {
  Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}) {
    DayOfWeek.entries.forEach { weekday ->
      Text(
          text = weekday.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
          color = AuthColors.TextSecondary,
          fontSize = 11.sp,
          textAlign = TextAlign.Center,
          modifier = Modifier.weight(1f),
      )
    }
  }
}

/**
 * A day of the month before or after the one shown: greyed out whatever its state, never tappable,
 * and silent for TalkBack, which reads it in its own month.
 */
@Composable
private fun OutsideDay(day: LocalDate, modifier: Modifier) {
  Box(
      modifier = modifier.testTag(AvailabilityCalendarTags.outsideDay(day)),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = "${day.dayOfMonth}",
        color = AuthColors.TextSecondary.copy(alpha = 0.3f),
        fontSize = 14.sp,
        modifier = Modifier.clearAndSetSemantics {},
    )
  }
}

/**
 * One day. TalkBack reads its full date, then its state. Only a selectable or selected day can be
 * tapped or held, and only when the calendar was given [onClick].
 */
@Composable
private fun DayCell(
    day: LocalDate,
    state: CalendarDayState,
    isRangeAnchor: Boolean,
    onClick: ((LocalDate) -> Unit)?,
    onLongClick: ((LocalDate) -> Unit)?,
    modifier: Modifier,
) {
  val canBeChosen = state != CalendarDayState.DISABLED
  val description = fullDate.format(day)
  val stateText =
      stringResource(
          when {
            isRangeAnchor -> R.string.calendar_day_range_start
            state == CalendarDayState.DISABLED -> R.string.calendar_day_unavailable
            state == CalendarDayState.SELECTED -> R.string.calendar_day_selected
            // While choosing, a free day is one not chosen yet; when only looking, it is on offer.
            onClick != null -> R.string.calendar_day_not_selected
            else -> R.string.calendar_day_available
          }
      )
  val holdLabel = stringResource(R.string.calendar_start_range)
  val tap =
      if (onClick != null && canBeChosen) {
        Modifier.combinedClickable(
            onClick = { onClick(day) },
            onLongClick = onLongClick?.let { { it(day) } },
            onLongClickLabel = holdLabel.takeIf { onLongClick != null },
        )
      } else {
        Modifier
      }
  Box(
      modifier =
          modifier.testTag(AvailabilityCalendarTags.day(day)).then(tap).semantics {
            contentDescription = description
            stateDescription = stateText
          },
      contentAlignment = Alignment.Center,
  ) {
    Box(modifier = Modifier.size(36.dp).dayShape(state, isRangeAnchor), Alignment.Center) {
      Text(
          text = "${day.dayOfMonth}",
          color = dayTextColor(state, isRangeAnchor),
          fontSize = 14.sp,
          fontWeight = if (state == CalendarDayState.SELECTED) FontWeight.Bold else null,
          // The cell already says the full date.
          modifier = Modifier.clearAndSetSemantics {},
      )
    }
  }
}

/**
 * Selected days and the start of a range can be told apart without colour: a selected day is a
 * filled circle, and the start of a range a thick ring on a tinted circle.
 */
private fun Modifier.dayShape(state: CalendarDayState, isRangeAnchor: Boolean): Modifier =
    when {
      isRangeAnchor ->
          background(AuthColors.AccentContainer, CircleShape)
              .border(2.dp, AuthColors.Accent, CircleShape)
      state == CalendarDayState.SELECTED -> background(AuthColors.Accent, CircleShape)
      else -> this
    }

private fun dayTextColor(state: CalendarDayState, isRangeAnchor: Boolean): Color =
    when {
      // The start of a range sits on a light circle, where a selected day's white would not show.
      isRangeAnchor -> AuthColors.Accent
      state == CalendarDayState.DISABLED -> AuthColors.TextSecondary.copy(alpha = 0.45f)
      state == CalendarDayState.SELECTABLE -> AuthColors.TextPrimary
      else -> Color.White
    }
