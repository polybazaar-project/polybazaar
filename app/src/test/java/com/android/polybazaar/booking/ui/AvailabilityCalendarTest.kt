// Co-authored-by: Claude Code (Claude Fable 5.2)
package com.android.polybazaar.booking.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AvailabilityCalendarTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val today = LocalDate.of(2026, 11, 10)
  private val november = YearMonth.of(2026, 11)

  private fun nov(day: Int) = LocalDate.of(2026, 11, day)

  private fun day(date: LocalDate) =
      composeTestRule.onNodeWithTag(AvailabilityCalendarTags.day(date))

  private fun string(id: Int) =
      ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

  private fun SemanticsNodeInteraction.assertState(id: Int) =
      assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, string(id)))

  /** The 13th is selected and the past is disabled. */
  private val mixedStates = { date: LocalDate ->
    when {
      date == nov(13) -> CalendarDayState.SELECTED
      date.isBefore(today) -> CalendarDayState.DISABLED
      else -> CalendarDayState.SELECTABLE
    }
  }

  private fun setCalendar(
      month: YearMonth = november,
      onMonthChange: (YearMonth) -> Unit = {},
      onDayClick: ((LocalDate) -> Unit)? = {},
      onDayLongClick: ((LocalDate) -> Unit)? = null,
      canStartRange: (LocalDate) -> Boolean = { true },
      rangeAnchor: LocalDate? = null,
  ) {
    composeTestRule.setContent {
      AvailabilityCalendar(
          month = month,
          onMonthChange = onMonthChange,
          dayState = mixedStates,
          onDayClick = onDayClick,
          onDayLongClick = onDayLongClick,
          canStartRange = canStartRange,
          rangeAnchor = rangeAnchor,
          today = today,
      )
    }
  }

  @Test
  fun showsTheMonth_andGreysOutTheDaysOfItsNeighbours() {
    setCalendar()
    val october31 = LocalDate.of(2026, 10, 31)
    val december6 = LocalDate.of(2026, 12, 6)

    composeTestRule
        .onNodeWithTag(AvailabilityCalendarTags.MONTH_TITLE)
        .assertTextEquals("November 2026")
    day(nov(1)).assertExists()
    day(nov(30)).assertExists()
    // The weeks around November are filled, but those days are not November's to choose.
    day(october31).assertDoesNotExist()
    day(december6).assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(AvailabilityCalendarTags.outsideDay(october31))
        .assertHasNoClickAction()
    composeTestRule
        .onNodeWithTag(AvailabilityCalendarTags.outsideDay(december6))
        .assertHasNoClickAction()
  }

  @Test
  fun eachDay_saysItsFullDateAndItsState() {
    setCalendar()

    day(nov(13)).assertContentDescriptionEquals("Friday 13 November 2026")
    day(nov(9)).assertState(R.string.calendar_day_unavailable)
    day(nov(13)).assertState(R.string.calendar_day_selected)
    // With a tap handler, a free day is one the user has not chosen yet.
    day(nov(20)).assertState(R.string.calendar_day_not_selected)
  }

  @Test
  fun tappingAnAvailableOrSelectedDay_reportsIt() {
    val tapped = mutableListOf<LocalDate>()
    setCalendar(onDayClick = { tapped += it })

    day(nov(20)).performClick()
    day(nov(13)).performClick()

    assertEquals(listOf(nov(20), nov(13)), tapped)
  }

  @Test
  fun disabledDays_cannotBeTapped() {
    setCalendar()

    day(nov(9)).assertHasNoClickAction()
    day(nov(1)).assertHasNoClickAction()
  }

  @Test
  fun withoutATapHandler_noDayCanBeTapped_andFreeDaysAreAvailable() {
    setCalendar(onDayClick = null, onDayLongClick = {})

    day(nov(20)).assertHasNoClickAction()
    day(nov(13)).assertHasNoClickAction()
    day(nov(20)).assertState(R.string.calendar_day_available)
  }

  @Test
  fun holdingADay_reportsIt() {
    val held = mutableListOf<LocalDate>()
    setCalendar(onDayLongClick = { held += it })

    day(nov(20)).performTouchInput { longClick() }

    assertEquals(listOf(nov(20)), held)
  }

  @Test
  fun theStartOfARange_saysSo() {
    setCalendar(rangeAnchor = nov(20))

    day(nov(20)).assertState(R.string.calendar_day_range_start)
  }

  @Test
  fun aSelectedDayARangeStartsFrom_saysBoth() {
    setCalendar(rangeAnchor = nov(13))

    day(nov(13)).assertState(R.string.calendar_day_selected_range_start)
  }

  @Test
  fun aDayThatCannotStartARange_isNotOfferedTheHold() {
    val held = mutableListOf<LocalDate>()
    setCalendar(onDayLongClick = { held += it }, canStartRange = { it != nov(13) })

    day(nov(13)).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
    day(nov(13)).assertHasClickAction()
    day(nov(20)).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
    day(nov(13)).performTouchInput { longClick() }
    assertEquals(emptyList<LocalDate>(), held)
  }

  @Test
  fun theMonthButtons_reportTheNextAndThePreviousMonth() {
    val december = YearMonth.of(2026, 12)
    var shown by mutableStateOf(december)
    val asked = mutableListOf<YearMonth>()
    composeTestRule.setContent {
      AvailabilityCalendar(
          month = shown,
          onMonthChange = { asked += it },
          dayState = mixedStates,
          today = today,
      )
    }

    composeTestRule.onNodeWithTag(AvailabilityCalendarTags.NEXT_MONTH_BUTTON).performClick()
    composeTestRule.onNodeWithTag(AvailabilityCalendarTags.PREVIOUS_MONTH_BUTTON).performClick()

    assertEquals(listOf(YearMonth.of(2027, 1), november), asked)
  }

  @Test
  fun thePreviousMonthButton_stopsAtTheCurrentMonth() {
    setCalendar()

    composeTestRule
        .onNodeWithTag(AvailabilityCalendarTags.PREVIOUS_MONTH_BUTTON)
        .assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(AvailabilityCalendarTags.NEXT_MONTH_BUTTON)
        .assertIsEnabled()
        .assertHasClickAction()
  }

  @Test
  fun theNextMonthButton_stopsAtTheMonthOfTheLastSelectableDay() {
    setCalendar(month = YearMonth.of(2027, 11))

    composeTestRule.onNodeWithTag(AvailabilityCalendarTags.NEXT_MONTH_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun keepsItsHeightFromOneMonthToTheNext() {
    // February 2027 fills four weeks exactly; November 2026 reaches into six.
    var shown by mutableStateOf(YearMonth.of(2027, 2))
    composeTestRule.setContent {
      AvailabilityCalendar(month = shown, onMonthChange = {}, dayState = mixedStates, today = today)
    }
    val calendar = composeTestRule.onNodeWithTag(AvailabilityCalendarTags.CALENDAR)
    val inFebruary = calendar.getUnclippedBoundsInRoot().height

    shown = november

    calendar.assertHeightIsEqualTo(inFebruary)
  }

  @Test
  fun swipingOverTheDays_reportsTheNextAndThePreviousMonth() {
    val december = YearMonth.of(2026, 12)
    val asked = mutableListOf<YearMonth>()
    composeTestRule.setContent {
      AvailabilityCalendar(
          month = december,
          onMonthChange = { asked += it },
          dayState = mixedStates,
          onDayClick = {},
          today = today,
      )
    }
    val weeks = composeTestRule.onNodeWithTag(AvailabilityCalendarTags.WEEKS)

    weeks.performTouchInput { swipeLeft() }
    weeks.performTouchInput { swipeRight() }

    assertEquals(listOf(YearMonth.of(2027, 1), november), asked)
  }

  @Test
  fun aSwipe_reportsToTheCallbackGivenLast() {
    val december = YearMonth.of(2026, 12)
    val first = mutableListOf<YearMonth>()
    val second = mutableListOf<YearMonth>()
    var useSecond by mutableStateOf(false)
    composeTestRule.setContent {
      AvailabilityCalendar(
          month = december,
          onMonthChange = if (useSecond) ({ second += it }) else ({ first += it }),
          dayState = mixedStates,
          onDayClick = {},
          today = today,
      )
    }

    useSecond = true
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(AvailabilityCalendarTags.WEEKS).performTouchInput { swipeLeft() }

    assertEquals(emptyList<YearMonth>(), first)
    assertEquals(listOf(YearMonth.of(2027, 1)), second)
  }

  @Test
  fun swipingBack_stopsAtTheCurrentMonth() {
    val asked = mutableListOf<YearMonth>()
    setCalendar(onMonthChange = { asked += it })

    composeTestRule.onNodeWithTag(AvailabilityCalendarTags.WEEKS).performTouchInput { swipeRight() }

    assertEquals(emptyList<YearMonth>(), asked)
  }

  @Test
  fun selectionDayState_isSelectedThenAvailableThenDisabled() {
    val selection = setOf(nov(9), nov(13))
    val offered = setOf(nov(13), nov(14))

    assertEquals(CalendarDayState.SELECTED, selectionDayState(nov(13), selection, today))
    // Still shown as selected once past, so the user can see it and remove it.
    assertEquals(CalendarDayState.SELECTED, selectionDayState(nov(9), selection, today))
    assertEquals(CalendarDayState.SELECTABLE, selectionDayState(nov(20), selection, today))
    assertEquals(CalendarDayState.DISABLED, selectionDayState(nov(8), selection, today))
    assertEquals(CalendarDayState.SELECTABLE, selectionDayState(nov(14), selection, today, offered))
    assertEquals(CalendarDayState.DISABLED, selectionDayState(nov(20), selection, today, offered))
  }
}
