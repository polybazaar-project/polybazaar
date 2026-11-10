// Co-authored-by: Claude Code (Claude Fable 5.2)
package com.android.polybazaar.booking.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DateSelectionTest {

  private val today = LocalDate.of(2026, 11, 10)

  private fun nov(day: Int) = LocalDate.of(2026, 11, day)

  private fun days(vararg days: Int) = days.map { nov(it) }.toSet()

  @Test
  fun isSelectable_withoutARestriction_allowsTodayAndLater_butNotThePast() {
    assertTrue(isSelectable(today, today))
    assertTrue(isSelectable(nov(30), today))
    assertFalse(isSelectable(nov(9), today))
  }

  @Test
  fun isSelectable_withARestriction_allowsOnlyThoseDays() {
    val offered = days(13, 14)

    assertTrue(isSelectable(nov(13), today, offered))
    assertFalse(isSelectable(nov(15), today, offered))
    assertFalse(isSelectable(nov(13), today, emptySet()))
  }

  @Test
  fun isSelectable_refusesAnOfferedDayInThePast() {
    assertFalse(isSelectable(nov(9), today, days(9)))
  }

  @Test
  fun toggleDay_addsADayAndRemovesItAgain() {
    val selected = toggleDay(emptySet(), nov(13), today)

    assertEquals(days(13), selected)
    assertEquals(emptySet<LocalDate>(), toggleDay(selected, nov(13), today))
  }

  @Test
  fun toggleDay_ignoresADayThatCannotBeSelected() {
    assertEquals(days(13), toggleDay(days(13), nov(9), today))
    assertEquals(days(13), toggleDay(days(13), nov(20), today, selectableDays = days(13)))
  }

  @Test
  fun toggleDay_removesASelectedDayThatIsNoLongerSelectable() {
    assertEquals(days(13), toggleDay(days(9, 13), nov(9), today))
    assertEquals(days(13), toggleDay(days(13, 20), nov(20), today, selectableDays = days(13)))
  }

  @Test
  fun selectRange_addsEveryDayBetweenTheEnds_inEitherOrder() {
    assertEquals(days(13, 14, 15, 16), selectRange(emptySet(), nov(13), nov(16), today))
    assertEquals(days(13, 14, 15, 16), selectRange(emptySet(), nov(16), nov(13), today))
  }

  @Test
  fun selectRange_keepsTheDaysAlreadySelected() {
    assertEquals(days(14, 15, 16, 28), selectRange(days(15, 28), nov(14), nov(16), today))
  }

  @Test
  fun selectRange_skipsTheDaysThatCannotBeSelected() {
    assertEquals(days(10, 11), selectRange(emptySet(), nov(8), nov(11), today))
    assertEquals(
        days(13, 16),
        selectRange(emptySet(), nov(13), nov(16), today, selectableDays = days(13, 16, 20)),
    )
  }

  @Test
  fun selectRange_crossesTheEndOfAMonth() {
    val expected = setOf(nov(29), nov(30), LocalDate.of(2026, 12, 1))

    assertEquals(expected, selectRange(emptySet(), nov(29), LocalDate.of(2026, 12, 1), today))
  }

  @Test
  fun groupIntoRuns_joinsConsecutiveDays_andKeepsLoneDaysApart() {
    val runs = groupIntoRuns(days(29, 13, 16, 14, 28, 15, 20))

    assertEquals(
        listOf(DateRun(nov(13), nov(16)), DateRun(nov(20), nov(20)), DateRun(nov(28), nov(29))),
        runs,
    )
  }

  @Test
  fun groupIntoRuns_ofNothing_isEmpty() {
    assertEquals(emptyList<DateRun>(), groupIntoRuns(emptySet()))
  }

  @Test
  fun groupIntoRuns_joinsDaysAcrossMonthsYearsAndALeapDay() {
    val newYear = setOf(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1))
    val leap = setOf(LocalDate.of(2028, 2, 28), LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 1))

    assertEquals(
        listOf(DateRun(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1))),
        groupIntoRuns(newYear),
    )
    assertEquals(
        listOf(DateRun(LocalDate.of(2028, 2, 28), LocalDate.of(2028, 3, 1))),
        groupIntoRuns(leap),
    )
  }

  @Test
  fun dateRun_endingBeforeItStarts_isRefused() {
    assertThrows(IllegalArgumentException::class.java) { DateRun(nov(14), nov(13)) }
  }

  private fun format(vararg days: LocalDate) = formatRuns(groupIntoRuns(days.toSet()))

  private fun dec(day: Int) = LocalDate.of(2026, 12, day)

  private fun jan(day: Int) = LocalDate.of(2027, 1, day)

  @Test
  fun formatRuns_ofNothing_isEmpty() {
    assertEquals("", formatRuns(emptyList()))
  }

  @Test
  fun formatRuns_inOneMonth_namesTheMonthOnce() {
    assertEquals("13 Nov", format(nov(13)))
    assertEquals("13–14 Nov", format(nov(13), nov(14)))
    assertEquals("13–14, 28 Nov", format(nov(13), nov(14), nov(28)))
  }

  @Test
  fun formatRuns_inTwoMonths_namesEachMonth() {
    assertEquals("28 Nov, 3–4 Dec", format(nov(28), dec(3), dec(4)))
  }

  @Test
  fun formatRuns_ofARunOverTwoMonths_namesBothEnds() {
    assertEquals("30 Nov – 1 Dec", format(nov(30), dec(1)))
    assertEquals("13 Nov, 30 Nov – 1 Dec, 5 Dec", format(nov(13), nov(30), dec(1), dec(5)))
  }

  @Test
  fun formatRuns_writesTheYear_onlyWhenTheRunsReachIntoTwoYears() {
    assertEquals("5–6 Jan", format(jan(5), jan(6)))
    assertEquals("28 Dec 2026, 3–4 Jan 2027", format(dec(28), jan(3), jan(4)))
    assertEquals("31 Dec 2026 – 1 Jan 2027", format(dec(31), jan(1)))
  }

  @Test
  fun isSelectable_stopsAYearFromToday() {
    assertTrue(isSelectable(LocalDate.of(2027, 11, 9), today))
    assertFalse(isSelectable(LocalDate.of(2027, 11, 10), today))
    assertEquals(LocalDate.of(2027, 11, 9), lastSelectableDay(today))
  }

  @Test
  fun selectRange_neverSelectsMoreThanAYearOfDays() {
    val selected = selectRange(emptySet(), today, LocalDate.of(2030, 1, 1), today)

    assertEquals(365, selected.size)
    assertEquals(LocalDate.of(2027, 11, 9), selected.max())
  }

  @Test
  fun selectionAfterTap_withoutARange_togglesTheDay() {
    assertEquals(days(13), selectionAfterTap(emptySet(), null, nov(13), today))
    assertEquals(emptySet<LocalDate>(), selectionAfterTap(days(13), null, nov(13), today))
  }

  @Test
  fun selectionAfterTap_duringARange_selectsUpToTheDay_orGivesUpOnTheStart() {
    assertEquals(days(13, 14, 15, 28), selectionAfterTap(days(28), nov(15), nov(13), today))
    assertEquals(days(13), selectionAfterTap(days(13), nov(13), nov(13), today))
  }

  @Test
  fun rangeStartAfterHold_isTheDayHeld_unlessItIsTheStartOrCannotBeSelected() {
    assertEquals(nov(13), rangeStartAfterHold(null, nov(13), today))
    assertEquals(nov(14), rangeStartAfterHold(nov(13), nov(14), today))
    assertNull(rangeStartAfterHold(nov(13), nov(13), today))
    assertNull(rangeStartAfterHold(null, nov(9), today))
    assertNull(rangeStartAfterHold(null, nov(20), today, selectableDays = days(13)))
  }

  @Test
  fun upcomingDays_leavesOutThePast_andKeepsToday() {
    assertEquals(days(10, 13), upcomingDays(days(2, 9, 10, 13), today))
  }
}
