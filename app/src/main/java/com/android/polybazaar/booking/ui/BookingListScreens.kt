// Co-authored-by: Copilot (Auto)
package com.android.polybazaar.booking.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthBackButton
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingStatus
import com.android.polybazaar.booking.model.formatRuns
import com.android.polybazaar.booking.model.groupIntoRuns
import com.android.polybazaar.booking.model.isFinished
import com.android.polybazaar.booking.viewmodel.BookingUiState
import com.android.polybazaar.booking.viewmodel.BookingViewModel
import com.android.polybazaar.error.ui.message
import java.time.LocalDate

object BookingListScreenTags {
  const val SCREEN = "booking_list_screen"
  const val BACK_BUTTON = "booking_list_back_button"
  const val LOADING_INDICATOR = "booking_list_loading_indicator"
  const val ERROR_MESSAGE = "booking_list_error_message"
  const val RETRY_BUTTON = "booking_list_retry_button"
  const val EMPTY = "booking_list_empty"
  const val STATUS = "booking_list_status"
  const val CANCEL_BUTTON = "booking_list_cancel_button"
  const val CANCELLING = "booking_list_cancelling"
  const val LABEL = "booking_list_label"
}

/** The renter's own bookings, loaded by [viewModel], each cancellable while still active. */
@Composable
fun RenterBookingsScreen(viewModel: BookingViewModel, onBack: () -> Unit) {
  val uiState by viewModel.uiState.collectAsState()
  LaunchedEffect(viewModel) { viewModel.loadRenterBookings() }
  BookingListContent(
      title = stringResource(R.string.renter_bookings_title),
      uiState = uiState,
      onBack = onBack,
      onRetry = viewModel::loadRenterBookings,
      onCancel = viewModel::cancelBooking,
  )
}

/** The bookings of [listingId] as its lender sees them, each cancellable while still active. */
@Composable
fun LenderBookingsScreen(listingId: String, viewModel: BookingViewModel, onBack: () -> Unit) {
  val uiState by viewModel.uiState.collectAsState()
  LaunchedEffect(viewModel, listingId) { viewModel.loadListingBookings(listingId) }
  BookingListContent(
      title = stringResource(R.string.lender_bookings_title),
      uiState = uiState,
      onBack = onBack,
      onRetry = { viewModel.loadListingBookings(listingId) },
      onCancel = viewModel::cancelBooking,
      showRenter = true,
  )
}

/**
 * Loading, error with Retry, empty and list states of bookings. A cancelled booking stays listed as
 * cancelled; a booking whose cancellation is being queued shows "Cancelling…". An error from a
 * cancellation the server rejected shows above the list, which keeps the restored booking; its
 * Retry cancels that booking again instead of reloading. A booking whose days are all before
 * [today] is history and cannot be cancelled.
 *
 * @param showRenter names each booking after its renter (the lender's view) instead of its listing.
 */
@Composable
fun BookingListContent(
    title: String,
    uiState: BookingUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCancel: (String) -> Unit,
    showRenter: Boolean = false,
    today: LocalDate = LocalDate.now(),
) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .testTag(BookingListScreenTags.SCREEN)
  ) {
    AuthBackButton(
        onClick = onBack,
        modifier =
            Modifier.padding(start = 20.dp, top = 6.dp).testTag(BookingListScreenTags.BACK_BUTTON),
    )
    Text(
        text = title,
        color = AuthColors.TextPrimary,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
    )
    val error = uiState.error
    if (error != null) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = error.message(),
            color = AuthColors.Danger,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f).testTag(BookingListScreenTags.ERROR_MESSAGE),
        )
        TextButton(
            onClick = { uiState.failedCancelId?.let(onCancel) ?: onRetry() },
            modifier = Modifier.testTag(BookingListScreenTags.RETRY_BUTTON),
        ) {
          Text(
              text = stringResource(R.string.booking_request_retry),
              color = AuthColors.Accent,
              fontWeight = FontWeight.Bold,
          )
        }
      }
    }
    when {
      uiState.isLoadingBookings && uiState.bookings.isEmpty() ->
          Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                color = AuthColors.Accent,
                modifier = Modifier.size(32.dp).testTag(BookingListScreenTags.LOADING_INDICATOR),
            )
          }
      uiState.bookings.isEmpty() && error == null ->
          Text(
              text = stringResource(R.string.bookings_empty),
              color = AuthColors.TextSecondary,
              fontSize = 14.sp,
              modifier = Modifier.padding(horizontal = 18.dp).testTag(BookingListScreenTags.EMPTY),
          )
      else ->
          LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            items(uiState.bookings, key = { it.id }) { booking ->
              BookingRow(
                  booking = booking,
                  label =
                      if (showRenter) uiState.renterNames[booking.renterUid]
                      else uiState.listingTitles[booking.listingId],
                  isCancelling = booking.id in uiState.cancellingBookingIds,
                  canCancel =
                      booking.status != BookingStatus.CANCELLED && !booking.isFinished(today),
                  onCancel = { onCancel(booking.id) },
              )
            }
          }
    }
  }
}

@Composable
private fun BookingRow(
    booking: Booking,
    label: String?,
    isCancelling: Boolean,
    canCancel: Boolean,
    onCancel: () -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth().background(AuthColors.CardBackground).padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      if (label != null) {
        Text(
            text = label,
            color = AuthColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.testTag(BookingListScreenTags.LABEL),
        )
      }
      Text(
          text = formatRuns(groupIntoRuns(booking.days)),
          color = AuthColors.TextPrimary,
          fontWeight = if (label == null) FontWeight.Bold else FontWeight.Normal,
          fontSize = if (label == null) 15.sp else 13.sp,
      )
      Text(
          text = stringResource(booking.status.labelRes),
          color =
              if (booking.status == BookingStatus.CANCELLED) AuthColors.Danger
              else AuthColors.TextSecondary,
          fontSize = 13.sp,
          modifier = Modifier.testTag(BookingListScreenTags.STATUS),
      )
    }
    when {
      isCancelling ->
          Text(
              text = stringResource(R.string.bookings_cancelling),
              color = AuthColors.TextSecondary,
              fontSize = 13.sp,
              modifier = Modifier.testTag(BookingListScreenTags.CANCELLING),
          )
      canCancel ->
          TextButton(
              onClick = onCancel,
              modifier = Modifier.testTag(BookingListScreenTags.CANCEL_BUTTON),
          ) {
            Text(
                text = stringResource(R.string.bookings_cancel),
                color = AuthColors.Danger,
                fontWeight = FontWeight.Bold,
            )
          }
    }
  }
}

private val BookingStatus.labelRes: Int
  get() =
      when (this) {
        BookingStatus.REQUESTED -> R.string.bookings_status_requested
        BookingStatus.CONFIRMED -> R.string.bookings_status_confirmed
        BookingStatus.CANCELLED -> R.string.bookings_status_cancelled
      }

/** Three bookings, one being cancelled, for the IDE preview. */
@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun BookingListContentPreview() {
  fun day(d: Int) = LocalDate.of(2026, 11, d)
  val bookings =
      listOf(
          Booking("l", "renter", "lender", setOf(day(13), day(14), day(15)), id = "a"),
          Booking("l", "renter", "lender", setOf(day(28)), BookingStatus.CONFIRMED, id = "b"),
          Booking("l", "renter", "lender", setOf(day(2)), BookingStatus.CANCELLED, id = "c"),
      )
  BookingListContent(
      title = "My bookings",
      uiState = BookingUiState(bookings = bookings, cancellingBookingIds = setOf("a")),
      onBack = {},
      onRetry = {},
      onCancel = {},
  )
}
