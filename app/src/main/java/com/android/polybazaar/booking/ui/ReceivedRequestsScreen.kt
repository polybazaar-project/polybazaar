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
import androidx.compose.ui.graphics.Color
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
import com.android.polybazaar.booking.model.ConfirmationAction
import com.android.polybazaar.booking.model.canBeCancelled
import com.android.polybazaar.booking.model.formatRuns
import com.android.polybazaar.booking.model.groupIntoRuns
import com.android.polybazaar.booking.model.isFinished
import com.android.polybazaar.booking.viewmodel.BookingParty
import com.android.polybazaar.booking.viewmodel.BookingUiState
import com.android.polybazaar.booking.viewmodel.BookingViewModel
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
  const val CANCEL_DIALOG = "received_requests_cancel_dialog"
  const val CANCEL_DIALOG_TEXT = "received_requests_cancel_dialog_text"
  const val CANCEL_CONFIRM = "received_requests_cancel_confirm"
  const val CANCEL_KEEP = "received_requests_cancel_keep"

  /** The card of the waiting request with this id. */
  fun request(id: String) = "received_requests_request_$id"

  /** The "Accept" button of the waiting request with this id. */
  fun acceptButton(id: String) = "received_requests_accept_$id"

  /** Why the waiting request with this id has no "Accept" button. */
  fun unavailable(id: String) = "received_requests_unavailable_$id"

  /** The card of the accepted booking with this id. */
  fun accepted(id: String) = "received_requests_accepted_$id"
}

/**
 * What the screen can do; every action does nothing by default.
 *
 * @property onRetry reads the waiting requests again.
 * @property onRetryBookings restarts the live updates of the accepted bookings.
 * @property onCancel cancels the accepted booking with this id, once the lender confirmed it, or
 *   again after the cancellation failed.
 * @property onConfirm records the lender's handover or return step on the booking with this id.
 * @property onDismissConfirmation hides the failed confirmation of the booking with this id.
 */
class ReceivedRequestsActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onAccept: (Booking) -> Unit = {},
    val onRetryBookings: () -> Unit = {},
    val onCancel: (String) -> Unit = {},
    val onConfirm: (String, ConfirmationAction) -> Unit = { _, _ -> },
    val onDismissConfirmation: (String) -> Unit = {},
)

/**
 * The screen where the owner of the listing [listingId] answers the requests renters made on it and
 * follows the ones they accepted. [viewModel] reads the waiting requests and accepts them;
 * accepting asks for confirmation first, saying how many other requests for the same days it
 * cancels. [bookingsViewModel] keeps the accepted bookings up to date while the screen is shown,
 * with their handover and return steps and Cancel, which also asks for confirmation.
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
              onDismissConfirmation = bookingsViewModel::dismissConfirmationFailure,
          ),
  )
}

/** Side margin of the page, as on the listing detail. */
private val PageMargin = 18.dp

/**
 * The accepted bookings among [BookingUiState.bookings], earliest first: all the confirmed ones
 * except those whose return is completed and whose days are all before [today]. A booking with a
 * failed confirmation stays, also once cancelled, until the lender dismisses the failure.
 */
private fun BookingUiState.acceptedBookings(today: LocalDate): List<Booking> = bookings.filter {
  it.id in confirmationFailures ||
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
  // The ids of the request and the booking waiting for confirmation. Saveable, so the dialogs
  // survive a rotation.
  var confirmingId by rememberSaveable { mutableStateOf<String?>(null) }
  var cancellingId by rememberSaveable { mutableStateOf<String?>(null) }
  // One answer at a time: no accept while a cancellation is being queued, and no cancellation,
  // nor its Retry, while an accept runs or another cancellation is being queued.
  val answering = uiState.acceptingId != null || bookings.cancellingBookingIds.isNotEmpty()
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
                enabled = !answering && !uiState.isLoading,
                modifier =
                    Modifier.fillMaxWidth()
                        .height(44.dp)
                        .testTag(ReceivedRequestsScreenTags.acceptButton(request.id)),
            )
          } else {
            Text(
                text = stringResource(R.string.received_requests_unavailable),
                color = AuthColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.testTag(ReceivedRequestsScreenTags.unavailable(request.id)),
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
          retryEnabled = !answering,
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
            onCancel = { cancellingId = it },
            onConfirm = actions.onConfirm,
            onDismissConfirmation = actions.onDismissConfirmation,
            modifier = Modifier.testTag(ReceivedRequestsScreenTags.accepted(booking.id)),
            cancelEnabled = !answering,
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
  // Closes if the booking can no longer be cancelled, e.g. once the renter cancelled it.
  val cancelling = accepted.find { it.id == cancellingId && it.canBeCancelled(today) }
  if (cancelling != null) {
    CancelDialog(
        renterName = bookings.renterNames[cancelling.renterUid],
        days = formatRuns(groupIntoRuns(cancelling.days)),
        onConfirm = {
          cancellingId = null
          actions.onCancel(cancelling.id)
        },
        onDismiss = { cancellingId = null },
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
  ConfirmDialog(
      tags =
          DialogTags(
              dialog = ReceivedRequestsScreenTags.ACCEPT_DIALOG,
              text = ReceivedRequestsScreenTags.ACCEPT_DIALOG_TEXT,
              confirm = ReceivedRequestsScreenTags.ACCEPT_CONFIRM,
              dismiss = ReceivedRequestsScreenTags.ACCEPT_CANCEL,
          ),
      title = stringResource(R.string.received_requests_confirm_title),
      text = text,
      confirmLabel = stringResource(R.string.received_requests_accept),
      confirmColor = AuthColors.Accent,
      dismissLabel = stringResource(R.string.received_requests_cancel),
      onConfirm = onConfirm,
      onDismiss = onDismiss,
  )
}

/**
 * Asks the lender to confirm cancelling the booking of [renterName] for [days], and warns that the
 * days are not offered again by itself.
 */
@Composable
private fun CancelDialog(
    renterName: String?,
    days: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
  val renter = renterName ?: stringResource(R.string.received_requests_unknown_renter)
  ConfirmDialog(
      tags =
          DialogTags(
              dialog = ReceivedRequestsScreenTags.CANCEL_DIALOG,
              text = ReceivedRequestsScreenTags.CANCEL_DIALOG_TEXT,
              confirm = ReceivedRequestsScreenTags.CANCEL_CONFIRM,
              dismiss = ReceivedRequestsScreenTags.CANCEL_KEEP,
          ),
      title = stringResource(R.string.received_requests_cancel_title),
      text = stringResource(R.string.received_requests_cancel_text, renter, days),
      confirmLabel = stringResource(R.string.received_requests_cancel_booking),
      confirmColor = AuthColors.Danger,
      dismissLabel = stringResource(R.string.received_requests_keep),
      onConfirm = onConfirm,
      onDismiss = onDismiss,
  )
}

/** The test tags of a [ConfirmDialog] and its parts. */
private class DialogTags(
    val dialog: String,
    val text: String,
    val confirm: String,
    val dismiss: String,
)

/** A dialog asking the lender to confirm an answer, titled [title] and explaining it in [text]. */
@Composable
private fun ConfirmDialog(
    tags: DialogTags,
    title: String,
    text: String,
    confirmLabel: String,
    confirmColor: Color,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      containerColor = AuthColors.CardBackground,
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier.testTag(tags.dialog),
      title = {
        Text(
            text = title,
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
            modifier = Modifier.testTag(tags.text),
        )
      },
      confirmButton = {
        TextButton(onClick = onConfirm, modifier = Modifier.testTag(tags.confirm)) {
          Text(
              text = confirmLabel,
              color = confirmColor,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
          )
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss, modifier = Modifier.testTag(tags.dismiss)) {
          Text(
              text = dismissLabel,
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
