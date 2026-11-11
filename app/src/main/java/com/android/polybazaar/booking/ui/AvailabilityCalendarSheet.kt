// Co-authored-by: Claude Code (Claude Fable 5.2)
package com.android.polybazaar.booking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.booking.model.DateRun
import com.android.polybazaar.booking.model.formatRuns
import com.android.polybazaar.booking.model.groupIntoRuns
import com.android.polybazaar.booking.model.isSelectable
import com.android.polybazaar.booking.model.rangeStartAfterHold
import com.android.polybazaar.booking.model.selectionAfterTap
import com.android.polybazaar.booking.model.upcomingDays
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

internal object AvailabilityCalendarSheetTags {
  const val TITLE = "availability_calendar_sheet_title"
  const val SUMMARY = "availability_calendar_sheet_summary"
  const val DONE_BUTTON = "availability_calendar_sheet_done_button"
}

/**
 * The calendar in a sheet that slides up from the bottom, to choose days one by one or as a range.
 *
 * Every tap changes the selection straight away through [onSelectionChange], so "Done", a swipe
 * down and a tap outside all only close the sheet. Days of [selection] that are in the past are not
 * shown, and are left out of every selection reported.
 *
 * @param title what the days are chosen for, shown above the calendar.
 * @param selection the days chosen so far.
 * @param onSelectionChange called with the new selection after each change.
 * @param onDismiss called once the sheet has slid away, for the caller to stop showing it.
 * @param selectableDays the only days that can be chosen, or null for any day from [today] on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AvailabilityCalendarSheet(
    title: String,
    selection: Set<LocalDate>,
    onSelectionChange: (Set<LocalDate>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    selectableDays: Set<LocalDate>? = null,
    today: LocalDate = LocalDate.now(),
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  // "Done" tapped twice while the sheet slides away must not dismiss twice.
  var isClosing by remember { mutableStateOf(false) }
  ModalBottomSheet(
      onDismissRequest = onDismiss,
      modifier = modifier,
      sheetState = sheetState,
      containerColor = AuthColors.CardBackground,
  ) {
    AvailabilityCalendarSheetContent(
        title = title,
        selection = selection,
        onSelectionChange = onSelectionChange,
        // Slides the sheet down before the caller removes it, as a swipe down does.
        onDone = {
          if (!isClosing) {
            isClosing = true
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
          }
        },
        selectableDays = selectableDays,
        today = today,
    )
  }
}

/**
 * What the sheet holds: the title, the calendar, the days chosen, how to choose and "Done".
 *
 * A tap selects or unselects a day. Holding a day starts a range from it, and the next tap ends the
 * range and selects every day in between that can be chosen. Tapping or holding the start again
 * gives the range up.
 */
@Composable
internal fun AvailabilityCalendarSheetContent(
    title: String,
    selection: Set<LocalDate>,
    onSelectionChange: (Set<LocalDate>) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    selectableDays: Set<LocalDate>? = null,
    today: LocalDate = LocalDate.now(),
) {
  // Past days cannot be reached in the calendar, so they could not be unselected: they are dropped.
  val chosen = remember(selection, today) { upcomingDays(selection, today) }
  // Opens on the first chosen day, else on the first day that can be chosen, so that a renter does
  // not land on a month without any day on offer. Without either, on this month.
  var month by rememberSaveable {
    val firstDay =
        chosen.minOrNull()
            ?: selectableDays?.filter { isSelectable(it, today, selectableDays) }?.minOrNull()
            ?: today
    mutableStateOf(YearMonth.from(firstDay))
  }
  var rangeStart by rememberSaveable { mutableStateOf<LocalDate?>(null) }
  // The calendar keeps 12dp margins, so that seven days of 48dp fit a 360dp phone; the rest 20dp.
  val text = Modifier.padding(horizontal = 8.dp)

  Column(
      modifier =
          modifier
              .fillMaxWidth()
              // On a small or sideways screen the sheet is taller than the screen.
              .verticalScroll(rememberScrollState())
              .padding(start = 12.dp, end = 12.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
        text = title,
        color = AuthColors.TextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = text.testTag(AvailabilityCalendarSheetTags.TITLE),
    )
    AvailabilityCalendar(
        month = month,
        onMonthChange = { month = it },
        dayState = { selectionDayState(it, chosen, today, selectableDays) },
        // A tap always ends the range being made, whether it selects days or gives the range up.
        onDayClick = { day ->
          onSelectionChange(selectionAfterTap(chosen, rangeStart, day, today, selectableDays))
          rangeStart = null
        },
        onDayLongClick = { day ->
          rangeStart = rangeStartAfterHold(rangeStart, day, today, selectableDays)
        },
        // A day still selected that is no longer on offer can be unselected, not extended.
        canStartRange = { isSelectable(it, today, selectableDays) },
        rangeAnchor = rangeStart,
        today = today,
    )
    // While a range is being made, its start may be in a month no longer shown: say it here.
    val start = rangeStart
    Text(
        text =
            if (start != null) {
              stringResource(
                  R.string.calendar_sheet_range_pending,
                  formatRuns(listOf(DateRun(start, start))),
              )
            } else {
              formatRuns(groupIntoRuns(chosen)).ifEmpty {
                stringResource(R.string.calendar_sheet_no_selection)
              }
            },
        color = if (start != null) AuthColors.Accent else AuthColors.TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = text.testTag(AvailabilityCalendarSheetTags.SUMMARY),
    )
    Text(
        text = stringResource(R.string.calendar_sheet_hint),
        color = AuthColors.TextSecondary,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = text,
    )
    Button(
        onClick = onDone,
        colors = ButtonDefaults.buttonColors(containerColor = AuthColors.Accent),
        modifier = text.fillMaxWidth().testTag(AvailabilityCalendarSheetTags.DONE_BUTTON),
    ) {
      Text(stringResource(R.string.calendar_sheet_done))
    }
  }
}
