// Co-authored-by: Claude Code (Claude Fable 5.2)
package com.android.polybazaar.booking.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Consecutive days from [start] to [end], both included. */
data class DateRun(val start: LocalDate, val end: LocalDate) {
  init {
    require(!end.isBefore(start)) { "A run cannot end before it starts" }
  }
}

/**
 * The last day that can be chosen: a year from [today], less a day. A selection of days still to
 * come therefore never holds more than 366 days.
 */
fun lastSelectableDay(today: LocalDate): LocalDate = today.plusYears(1).minusDays(1)

/**
 * Whether [day] can be added to a selection: from [today] to [lastSelectableDay] and, when
 * [selectableDays] is given, one of them. Null means any of those days (a lender choosing their
 * availability); a set restricts the choice to it (a renter choosing among the available days).
 */
fun isSelectable(
    day: LocalDate,
    today: LocalDate,
    selectableDays: Set<LocalDate>? = null,
): Boolean =
    !day.isBefore(today) &&
        !day.isAfter(lastSelectableDay(today)) &&
        (selectableDays == null || day in selectableDays)

/**
 * [selection] with [day] removed if it was selected, or added if it [isSelectable]. A selected day
 * can always be removed, even once it is in the past or no longer selectable.
 */
fun toggleDay(
    selection: Set<LocalDate>,
    day: LocalDate,
    today: LocalDate,
    selectableDays: Set<LocalDate>? = null,
): Set<LocalDate> =
    when {
      day in selection -> selection - day
      isSelectable(day, today, selectableDays) -> selection + day
      else -> selection
    }

/**
 * [selection] plus every selectable day between [from] and [to], both included and in either order.
 * Days in between that are not selectable are skipped, and no day is removed.
 */
fun selectRange(
    selection: Set<LocalDate>,
    from: LocalDate,
    to: LocalDate,
    today: LocalDate,
    selectableDays: Set<LocalDate>? = null,
): Set<LocalDate> {
  val first = minOf(from, to)
  val last = maxOf(from, to)
  val added =
      generateSequence(first) { it.plusDays(1) }
          .takeWhile { !it.isAfter(last) }
          .filter { isSelectable(it, today, selectableDays) }
  return selection + added
}

/**
 * The selection after [day] is tapped. Without a range being made ([rangeStart] is null) the tap
 * toggles [day]. With one, it ends the range and selects the days from [rangeStart] to [day], or
 * changes nothing when [day] is [rangeStart] itself, which gives the range up.
 */
fun selectionAfterTap(
    selection: Set<LocalDate>,
    rangeStart: LocalDate?,
    day: LocalDate,
    today: LocalDate,
    selectableDays: Set<LocalDate>? = null,
): Set<LocalDate> =
    when (rangeStart) {
      null -> toggleDay(selection, day, today, selectableDays)
      day -> selection
      else -> selectRange(selection, rangeStart, day, today, selectableDays)
    }

/**
 * The day a range starts from after [day] is held: [day] itself, or null when [day] cannot be
 * selected or is already the [rangeStart], which gives the range up.
 */
fun rangeStartAfterHold(
    rangeStart: LocalDate?,
    day: LocalDate,
    today: LocalDate,
    selectableDays: Set<LocalDate>? = null,
): LocalDate? = day.takeIf { it != rangeStart && isSelectable(it, today, selectableDays) }

/** [days] without those before [today], which can no longer be lent or rented. */
fun upcomingDays(days: Set<LocalDate>, today: LocalDate): Set<LocalDate> =
    days.filterTo(mutableSetOf()) { !it.isBefore(today) }

/** [days] as runs of consecutive days, earliest first. A day alone is a run of one. */
fun groupIntoRuns(days: Set<LocalDate>): List<DateRun> {
  val runs = mutableListOf<DateRun>()
  for (day in days.sorted()) {
    val last = runs.lastOrNull()
    if (last != null && last.end.plusDays(1) == day) {
      runs[runs.lastIndex] = last.copy(end = day)
    } else {
      runs += DateRun(day, day)
    }
  }
  return runs
}

/** The month this run is in, or null when it runs over two months or more. */
private val DateRun.month: YearMonth?
  get() = YearMonth.from(start).takeIf { it == YearMonth.from(end) }

/**
 * [runs] as text, such as "13–16, 28–29 Nov". Runs of the same month share one month name, a run
 * over two months names both ("30 Nov – 2 Dec"), and the year is only written when the runs reach
 * into two years ("28–29 Dec 2026, 3–5 Jan 2027"). No runs give an empty text.
 *
 * @param runs the runs to write, earliest first, as [groupIntoRuns] returns them.
 */
fun formatRuns(runs: List<DateRun>): String {
  if (runs.isEmpty()) return ""
  val withYear = runs.first().start.year != runs.last().end.year
  fun monthName(day: LocalDate): String {
    val name = day.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    return if (withYear) "$name ${day.year}" else name
  }

  // Neighbouring runs of the same month go together; a run over two months stays alone.
  val groups = mutableListOf<MutableList<DateRun>>()
  for (run in runs) {
    val group = groups.lastOrNull()
    if (group != null && run.month != null && group.first().month == run.month) {
      group += run
    } else {
      groups += mutableListOf(run)
    }
  }
  return groups.joinToString { group ->
    val first = group.first()
    if (first.month == null) {
      "${first.start.dayOfMonth} ${monthName(first.start)} – " +
          "${first.end.dayOfMonth} ${monthName(first.end)}"
    } else {
      val days = group.joinToString { run ->
        if (run.start == run.end) "${run.start.dayOfMonth}"
        else "${run.start.dayOfMonth}–${run.end.dayOfMonth}"
      }
      "$days ${monthName(first.start)}"
    }
  }
}
