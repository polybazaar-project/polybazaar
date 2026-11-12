// Co-authored-by: Claude Code (Claude Fable 5.2)
package com.android.polybazaar.booking.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AvailabilityCalendarSheetTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val today = LocalDate.of(2026, 11, 10)

  /** The selection the sheet was last given, as a screen would keep it. */
  private var selection by mutableStateOf(emptySet<LocalDate>())

  private fun nov(day: Int) = LocalDate.of(2026, 11, day)

  private fun day(date: LocalDate) =
      composeTestRule.onNodeWithTag(AvailabilityCalendarTags.day(date))

  private fun tap(date: LocalDate) = day(date).performClick()

  private fun hold(date: LocalDate) = day(date).performTouchInput { longClick() }

  private fun summary() = composeTestRule.onNodeWithTag(AvailabilityCalendarSheetTags.SUMMARY)

  private fun setContent(
      initial: Set<LocalDate> = emptySet(),
      selectableDays: Set<LocalDate>? = null,
      onDone: () -> Unit = {},
  ) {
    selection = initial
    composeTestRule.setContent {
      AvailabilityCalendarSheetContent(
          title = "When can you lend it?",
          selection = selection,
          onSelectionChange = { selection = it },
          onDone = onDone,
          selectableDays = selectableDays,
          today = today,
      )
    }
  }

  @Test
  fun showsTheTitle_andSaysWhenNothingIsSelected() {
    setContent()

    composeTestRule
        .onNodeWithTag(AvailabilityCalendarSheetTags.TITLE)
        .assertTextEquals("When can you lend it?")
    summary()
        .assertTextEquals(
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .getString(R.string.calendar_sheet_no_selection)
        )
  }

  @Test
  fun tappingDays_selectsAndUnselectsThem_andTheSummaryFollows() {
    setContent()

    tap(nov(28))
    tap(nov(29))
    tap(nov(13))
    summary().assertTextEquals("13, 28–29 Nov")

    tap(nov(28))

    assertEquals(setOf(nov(13), nov(29)), selection)
    summary().assertTextEquals("13, 29 Nov")
  }

  @Test
  fun holdingADay_thenTappingAnother_selectsEveryDayInBetween() {
    setContent(initial = setOf(nov(28)))

    hold(nov(16))
    tap(nov(13))

    assertEquals(setOf(nov(13), nov(14), nov(15), nov(16), nov(28)), selection)
    summary().assertTextEquals("13–16, 28 Nov")
  }

  @Test
  fun afterARange_aTapSelectsOneDayAgain() {
    setContent()

    hold(nov(13))
    tap(nov(14))
    tap(nov(20))

    assertEquals(setOf(nov(13), nov(14), nov(20)), selection)
  }

  @Test
  fun tappingOrHoldingTheStartAgain_givesTheRangeUp() {
    setContent(initial = setOf(nov(13)))

    hold(nov(13))
    tap(nov(13))
    hold(nov(20))
    hold(nov(20))
    tap(nov(22))

    // The 13th stays selected, no range was made, and the last tap selected the 22nd alone.
    assertEquals(setOf(nov(13), nov(22)), selection)
  }

  @Test
  fun aRange_skipsTheDaysThatAreNotOffered() {
    val offered = setOf(nov(13), nov(14), nov(16))
    setContent(selectableDays = offered)

    hold(nov(13))
    tap(nov(16))

    assertEquals(offered, selection)
    day(nov(15)).assertHasNoClickAction()
  }

  @Test
  fun opensOnTheMonthOfTheFirstSelectedDayToCome() {
    setContent(initial = setOf(nov(2), LocalDate.of(2026, 12, 3)))

    composeTestRule
        .onNodeWithTag(AvailabilityCalendarTags.MONTH_TITLE)
        .assertTextEquals("December 2026")
  }

  @Test
  fun withNothingSelected_opensOnTheMonthOfTheFirstDayOnOffer() {
    // The 2nd of November is already past, so the first day a renter can choose is in January.
    setContent(selectableDays = setOf(nov(2), LocalDate.of(2027, 1, 8), LocalDate.of(2027, 2, 1)))

    composeTestRule
        .onNodeWithTag(AvailabilityCalendarTags.MONTH_TITLE)
        .assertTextEquals("January 2027")
  }

  @Test
  fun withNothingSelectedAndNothingOnOffer_opensOnThisMonth() {
    setContent(selectableDays = emptySet())

    composeTestRule
        .onNodeWithTag(AvailabilityCalendarTags.MONTH_TITLE)
        .assertTextEquals("November 2026")
  }

  @Test
  fun aSelectedDayNoLongerOnOffer_canBeUnselected_butNotHeld() {
    setContent(initial = setOf(nov(13)), selectableDays = setOf(nov(20)))

    day(nov(13)).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
    day(nov(20)).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
    tap(nov(13))

    assertEquals(emptySet<LocalDate>(), selection)
  }

  @Test
  fun changingMonth_keepsTheSelection() {
    setContent(initial = setOf(nov(13)))

    composeTestRule.onNodeWithTag(AvailabilityCalendarTags.NEXT_MONTH_BUTTON).performClick()
    tap(LocalDate.of(2026, 12, 3))

    summary().assertTextEquals("13 Nov, 3 Dec")
  }

  @Test
  fun done_isReported() {
    var done = false
    setContent(onDone = { done = true })

    composeTestRule.onNodeWithTag(AvailabilityCalendarSheetTags.DONE_BUTTON).performClick()

    assertTrue(done)
  }

  @Test
  fun theSheet_showsItsContent() {
    composeTestRule.setContent {
      AvailabilityCalendarSheet(
          title = "When can you lend it?",
          selection = setOf(nov(13)),
          onSelectionChange = {},
          onDismiss = {},
          today = today,
      )
    }

    composeTestRule.onNodeWithTag(AvailabilityCalendarSheetTags.TITLE).assertIsDisplayed()
    summary().assertTextEquals("13 Nov")
  }

  @Test
  fun doneInTheSheet_closesIt() {
    var dismissed = false
    composeTestRule.setContent {
      AvailabilityCalendarSheet(
          title = "When can you lend it?",
          selection = emptySet(),
          onSelectionChange = {},
          onDismiss = { dismissed = true },
          today = today,
      )
    }

    composeTestRule.onNodeWithTag(AvailabilityCalendarSheetTags.DONE_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertTrue(dismissed)
  }

  @Test
  fun doneTappedTwiceInTheSheet_closesItOnce() {
    var dismissed = 0
    composeTestRule.setContent {
      AvailabilityCalendarSheet(
          title = "When can you lend it?",
          selection = emptySet(),
          onSelectionChange = {},
          onDismiss = { dismissed++ },
          today = today,
      )
    }
    val done = composeTestRule.onNodeWithTag(AvailabilityCalendarSheetTags.DONE_BUTTON)

    // Both taps land before the sheet has slid away.
    composeTestRule.mainClock.autoAdvance = false
    done.performClick()
    done.performClick()
    composeTestRule.mainClock.autoAdvance = true
    composeTestRule.waitForIdle()

    assertEquals(1, dismissed)
  }

  @Test
  fun pastDays_areNotShown_andAreDroppedFromTheNextSelection() {
    setContent(initial = setOf(nov(2), nov(13)))

    summary().assertTextEquals("13 Nov")

    tap(nov(14))

    assertEquals(setOf(nov(13), nov(14)), selection)
  }

  @Test
  fun whileARangeIsBeingMade_theSummarySaysWhereItStarts() {
    setContent(initial = setOf(nov(28)))

    hold(nov(13))
    summary().assertTextEquals(string(R.string.calendar_sheet_range_pending, "13 Nov"))

    tap(nov(14))
    summary().assertTextEquals("13–14, 28 Nov")
  }

  @Test
  fun aRange_canEndInAnotherMonth() {
    setContent()

    hold(nov(29))
    composeTestRule.onNodeWithTag(AvailabilityCalendarTags.NEXT_MONTH_BUTTON).performClick()
    summary().assertTextEquals(string(R.string.calendar_sheet_range_pending, "29 Nov"))
    tap(LocalDate.of(2026, 12, 1))

    summary().assertTextEquals("29 Nov – 1 Dec")
  }

  @Test
  fun theMonthShownAndTheRangeBeingMade_surviveTheScreenBeingRecreated() {
    val restorationTester = StateRestorationTester(composeTestRule)
    restorationTester.setContent {
      AvailabilityCalendarSheetContent(
          title = "When can you lend it?",
          selection = selection,
          onSelectionChange = { selection = it },
          onDone = {},
          today = today,
      )
    }
    composeTestRule.onNodeWithTag(AvailabilityCalendarTags.NEXT_MONTH_BUTTON).performClick()
    hold(LocalDate.of(2026, 12, 3))

    restorationTester.emulateSavedInstanceStateRestore()

    composeTestRule
        .onNodeWithTag(AvailabilityCalendarTags.MONTH_TITLE)
        .assertTextEquals("December 2026")
    summary().assertTextEquals(string(R.string.calendar_sheet_range_pending, "3 Dec"))
  }

  private fun string(id: Int, vararg arguments: Any) =
      ApplicationProvider.getApplicationContext<android.content.Context>().getString(id, *arguments)
}
