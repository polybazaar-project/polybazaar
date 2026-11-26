// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.booking.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthBackButton
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.auth.ui.AuthPrimaryButton
import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingStatus
import com.android.polybazaar.booking.model.formatRuns
import com.android.polybazaar.booking.model.groupIntoRuns
import com.android.polybazaar.booking.model.isFinished
import com.android.polybazaar.booking.viewmodel.BookingParty
import com.android.polybazaar.booking.viewmodel.BookingUiState
import com.android.polybazaar.booking.viewmodel.BookingViewModel
import com.android.polybazaar.booking.viewmodel.ConfirmationAction
import com.android.polybazaar.booking.viewmodel.ConfirmationStage
import com.android.polybazaar.booking.viewmodel.ReceivedRequestsUiState
import com.android.polybazaar.booking.viewmodel.ReceivedRequestsViewModel
import com.android.polybazaar.booking.viewmodel.confirmationStage
import com.android.polybazaar.error.ui.message
import java.time.LocalDate

object ReceivedRequestsScreenTags {
  const val SCREEN = "received_requests_screen"
  const val BACK_BUTTON = "received_requests_back_button"
  const val LOADING_INDICATOR = "received_requests_loading_indicator"
  const val ERROR_MESSAGE = "received_requests_error_message"
  const val RETRY_BUTTON = "received_requests_retry_button"
  const val EMPTY_MESSAGE = "received_requests_empty_message"
  const val ACCEPT_ERROR = "received_requests_accept_error"
  const val ACCEPT_DIALOG = "received_requests_accept_dialog"
  const val ACCEPT_DIALOG_TEXT = "received_requests_accept_dialog_text"
  const val ACCEPT_CONFIRM = "received_requests_accept_confirm"
  const val ACCEPT_CANCEL = "received_requests_accept_cancel"

  /** The card of the waiting request with this id. */
  fun request(id: String) = "received_requests_request_$id"

  /** The "Accept" button of the waiting request with this id. */
  fun acceptButton(id: String) = "received_requests_accept_$id"

  /** The card of the accepted booking with this id. */
  fun accepted(id: String) = "received_requests_accepted_$id"
}

/**
 * What the screen can do; every action does nothing by default.
 *
 * @property onRetry reads the waiting requests again.
 * @property onRetryBookings restarts the live updates of the accepted bookings.
 * @property onCancel cancels the accepted booking with this id.
 * @property onConfirm records the lender's handover or return step on the booking with this id.
 */
class ReceivedRequestsActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onAccept: (Booking) -> Unit = {},
    val onRetryBookings: () -> Unit = {},
    val onCancel: (String) -> Unit = {},
    val onConfirm: (String, ConfirmationAction) -> Unit = { _, _ -> },
)

/**
 * The screen where the owner of the listing [listingId] answers the requests renters made on it and
 * follows the ones they accepted. [viewModel] reads the waiting requests and accepts them;
 * accepting asks for confirmation first, saying how many other requests for the same days it
 * cancels. [bookingsViewModel] keeps the accepted bookings up to date while the screen is shown,
 * with their handover and return steps and Cancel.
 */
@Composable
fun ReceivedRequestsScreen(
    listingId: String,
    viewModel: ReceivedRequestsViewModel,
    bookingsViewModel: BookingViewModel,
    onBack: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()
  val bookings by bookingsViewModel.uiState.collectAsState()
  DisposableEffect(bookingsViewModel, listingId) {
    bookingsViewModel.observeListingBookings(listingId)
    onDispose { bookingsViewModel.stopObservingBookings() }
  }
  ReceivedRequestsContent(
      uiState = uiState,
      bookings = bookings,
      actions =
          ReceivedRequestsActions(
              onBack = onBack,
              onRetry = viewModel::loadRequests,
              onAccept = viewModel::accept,
              onRetryBookings = { bookingsViewModel.observeListingBookings(listingId) },
              onCancel = bookingsViewModel::cancelBooking,
              onConfirm = bookingsViewModel::confirmBooking,
          ),
  )
}

/** Side margin of the page, as on the listing detail. */
private val PageMargin = 18.dp

/**
 * The accepted bookings among [BookingUiState.bookings], earliest first: all the confirmed ones
 * except those whose return is completed and whose days are all before [today].
 */
private fun BookingUiState.acceptedBookings(today: LocalDate): List<Booking> = bookings.filter {
  it.status == BookingStatus.CONFIRMED &&
      !(it.confirmationStage == ConfirmationStage.COMPLETED && it.isFinished(today))
}

/**
 * The waiting requests of [uiState] and the accepted bookings of [bookings], as the lender sees
 * them on [today].
 */
@Composable
fun ReceivedRequestsContent(
    uiState: ReceivedRequestsUiState,
    bookings: BookingUiState,
    actions: ReceivedRequestsActions,
    today: LocalDate = LocalDate.now(),
) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .testTag(ReceivedRequestsScreenTags.SCREEN)
  ) {
    AuthBackButton(
        onClick = actions.onBack,
        modifier =
            Modifier.padding(start = 20.dp, top = 6.dp)
                .testTag(ReceivedRequestsScreenTags.BACK_BUTTON),
    )
    val error = uiState.error
    when {
      // Requests already shown stay shown if reading them again fails.
      uiState.hasLoaded -> RequestLists(uiState, bookings, actions, today)
      error != null -> LoadError(text = error.message(), onRetry = actions.onRetry)
      else -> LoadingIndicator()
    }
  }
}

@Composable
private fun RequestLists(
    uiState: ReceivedRequestsUiState,
    bookings: BookingUiState,
    actions: ReceivedRequestsActions,
    today: LocalDate,
) {
  val accepted = bookings.acceptedBookings(today)
  // The id of the request waiting for confirmation. Saveable, so the dialog survives a rotation.
  var confirmingId by rememberSaveable { mutableStateOf<String?>(null) }
  Column(
      modifier =
          Modifier.fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = PageMargin, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(
        text = stringResource(R.string.received_requests_title),
        color = AuthColors.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
    )
    uiState.acceptError?.let {
      Text(
          text = it.message(),
          color = AuthColors.Danger,
          fontSize = 13.sp,
          lineHeight = 18.sp,
          modifier = Modifier.testTag(ReceivedRequestsScreenTags.ACCEPT_ERROR),
      )
    }
    // Not while the accepted bookings are still being read, or could not be.
    if (
        uiState.requests.isEmpty() &&
            accepted.isEmpty() &&
            !bookings.isLoadingBookings &&
            bookings.observationError == null
    ) {
      Text(
          text = stringResource(R.string.received_requests_empty),
          color = AuthColors.TextSecondary,
          fontSize = 13.sp,
          modifier = Modifier.testTag(ReceivedRequestsScreenTags.EMPTY_MESSAGE),
      )
    }
    if (uiState.requests.isNotEmpty()) {
      SectionTitle(stringResource(R.string.received_requests_waiting))
      uiState.requests.forEach { request ->
        RequestCard(
            booking = request,
            renterName = uiState.renterNames[request.renterUid],
            tag = ReceivedRequestsScreenTags.request(request.id),
        ) {
          // No Accept when one of its days is no longer offered: the rules would refuse it.
          if (uiState.canAccept(request)) {
            AuthPrimaryButton(
                text = stringResource(R.string.received_requests_accept),
                onClick = { confirmingId = request.id },
                enabled = uiState.acceptingId == null && !uiState.isLoading,
                modifier =
                    Modifier.fillMaxWidth()
                        .height(44.dp)
                        .testTag(ReceivedRequestsScreenTags.acceptButton(request.id)),
            )
          }
        }
      }
    }
    if (accepted.isNotEmpty() || bookings.observationError != null || bookings.error != null) {
      SectionTitle(stringResource(R.string.received_requests_accepted))
      BookingErrors(
          uiState = bookings,
          onRetry = actions.onRetryBookings,
          onCancel = actions.onCancel,
      )
      accepted.forEach { booking ->
        BookingCard(
            booking = booking,
            uiState = bookings,
            party = BookingParty.LENDER,
            label =
                bookings.renterNames[booking.renterUid]
                    ?: stringResource(R.string.received_requests_unknown_renter),
            today = today,
            onCancel = actions.onCancel,
            onConfirm = actions.onConfirm,
            modifier = Modifier.testTag(ReceivedRequestsScreenTags.accepted(booking.id)),
        )
      }
    }
  }
  // Looked up again on every change: the request may have been answered meanwhile.
  val confirming = uiState.requests.find { it.id == confirmingId }
  if (confirming != null) {
    AcceptDialog(
        renterName = uiState.renterNames[confirming.renterUid],
        days = formatRuns(groupIntoRuns(confirming.days)),
        conflicts = uiState.conflictsOf(confirming).size,
        onConfirm = {
          confirmingId = null
          actions.onAccept(confirming)
        },
        onDismiss = { confirmingId = null },
    )
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(
      text = text,
      color = AuthColors.TextPrimary,
      fontSize = 14.sp,
      fontWeight = FontWeight.SemiBold,
  )
}

/**
 * One booking: who asked, for which days and how many. [actions] adds what the lender can do with
 * it below.
 */
@Composable
private fun RequestCard(
    booking: Booking,
    renterName: String?,
    tag: String,
    actions: @Composable () -> Unit = {},
) {
  val shape = RoundedCornerShape(14.dp)
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .background(AuthColors.CardBackground, shape)
              .border(1.dp, AuthColors.Outline, shape)
              .padding(12.dp)
              .testTag(tag),
      verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
        text = renterName ?: stringResource(R.string.received_requests_unknown_renter),
        color = AuthColors.TextPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = formatRuns(groupIntoRuns(booking.days)),
        color = AuthColors.TextPrimary,
        fontSize = 13.sp,
    )
    Text(
        text =
            pluralStringResource(
                R.plurals.received_requests_day_count,
                booking.days.size,
                booking.days.size,
            ),
        color = AuthColors.TextSecondary,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
    actions()
  }
}

/**
 * Asks the lender to confirm the accept, which cannot be undone and cancels the [conflicts] other
 * requests for the same days.
 */
@Composable
private fun AcceptDialog(
    renterName: String?,
    days: String,
    conflicts: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
  val renter = renterName ?: stringResource(R.string.received_requests_unknown_renter)
  val text = buildString {
    append(stringResource(R.string.received_requests_confirm_text, renter, days))
    if (conflicts > 0) {
      append(" ")
      append(
          pluralStringResource(R.plurals.received_requests_confirm_cancels, conflicts, conflicts)
      )
    }
  }
  AlertDialog(
      onDismissRequest = onDismiss,
      containerColor = AuthColors.CardBackground,
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier.testTag(ReceivedRequestsScreenTags.ACCEPT_DIALOG),
      title = {
        Text(
            text = stringResource(R.string.received_requests_confirm_title),
            color = AuthColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
      },
      text = {
        Text(
            text = text,
            color = AuthColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.testTag(ReceivedRequestsScreenTags.ACCEPT_DIALOG_TEXT),
        )
      },
      confirmButton = {
        TextButton(
            onClick = onConfirm,
            modifier = Modifier.testTag(ReceivedRequestsScreenTags.ACCEPT_CONFIRM),
        ) {
          Text(
              text = stringResource(R.string.received_requests_accept),
              color = AuthColors.Accent,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
          )
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(ReceivedRequestsScreenTags.ACCEPT_CANCEL),
        ) {
          Text(
              text = stringResource(R.string.received_requests_cancel),
              color = AuthColors.TextSecondary,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
          )
        }
      },
  )
}

@Composable
private fun LoadingIndicator() {
  Box(
      modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
      contentAlignment = Alignment.Center,
  ) {
    CircularProgressIndicator(
        color = AuthColors.Accent,
        modifier = Modifier.size(32.dp).testTag(ReceivedRequestsScreenTags.LOADING_INDICATOR),
    )
  }
}

/** Why the requests could not be read, with Retry. */
@Composable
private fun LoadError(text: String, onRetry: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = PageMargin, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text = text,
        color = AuthColors.Danger,
        fontSize = 14.sp,
        modifier = Modifier.weight(1f).testTag(ReceivedRequestsScreenTags.ERROR_MESSAGE),
    )
    TextButton(
        onClick = onRetry,
        modifier = Modifier.testTag(ReceivedRequestsScreenTags.RETRY_BUTTON),
    ) {
      Text(
          text = stringResource(R.string.booking_request_retry),
          color = AuthColors.Accent,
          fontWeight = FontWeight.Bold,
      )
    }
  }
}

/** Two requests sharing a day and one accepted booking, for the IDE preview. */
@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun ReceivedRequestsContentPreview() {
  val first = LocalDate.of(2026, 11, 23)
  fun booking(id: String, renter: String, days: IntRange, status: BookingStatus) =
      Booking(
          listingId = "preview",
          renterUid = renter,
          lenderUid = "lender",
          days = days.mapTo(mutableSetOf()) { first.plusDays(it.toLong()) },
          status = status,
          id = id,
      )
  ReceivedRequestsContent(
      uiState =
          ReceivedRequestsUiState(
              requests =
                  listOf(
                      booking("b1", "alice", 0..2, BookingStatus.REQUESTED),
                      booking("b2", "bob", 2..3, BookingStatus.REQUESTED),
                  ),
              renterNames = mapOf("alice" to "alice_m", "bob" to "bob.k", "carol" to "carol"),
              offeredDays = (0..6).mapTo(mutableSetOf()) { first.plusDays(it.toLong()) },
              hasLoaded = true,
          ),
      bookings =
          BookingUiState(bookings = listOf(booking("b3", "carol", 7..8, BookingStatus.CONFIRMED))),
      actions = ReceivedRequestsActions(),
      today = first,
  )
}
