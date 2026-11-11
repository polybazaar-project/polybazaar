// Co-authored-by: Claude Code (Claude Fable 5.2)
package com.android.polybazaar.booking.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.ui.AuthBackButton
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.auth.ui.AuthPrimaryButton
import com.android.polybazaar.booking.model.formatRuns
import com.android.polybazaar.booking.model.groupIntoRuns
import com.android.polybazaar.booking.viewmodel.BookingRequestUiState
import com.android.polybazaar.booking.viewmodel.BookingRequestViewModel
import com.android.polybazaar.booking.viewmodel.BookingUiState
import com.android.polybazaar.booking.viewmodel.BookingViewModel
import com.android.polybazaar.error.ui.message
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.formatChf
import java.time.LocalDate

object BookingRequestScreenTags {
  const val SCREEN = "booking_request_screen"
  const val BACK_BUTTON = "booking_request_back_button"
  const val LOADING_INDICATOR = "booking_request_loading_indicator"
  const val ERROR_MESSAGE = "booking_request_error_message"
  const val RETRY_BUTTON = "booking_request_retry_button"
  const val LISTING_TITLE = "booking_request_listing_title"
  const val DATES_FIELD = "booking_request_dates_field"
  const val DATES_MESSAGE = "booking_request_dates_message"
  const val PRICE_LINE = "booking_request_price_line"
  const val TOTAL = "booking_request_total"
  const val DAMAGE = "booking_request_damage"
  const val PAYMENT = "booking_request_payment"
  const val NOT_ALLOWED = "booking_request_not_allowed"
  const val SUBMIT_ERROR = "booking_request_submit_error"
  const val SUBMIT_BUTTON = "booking_request_submit_button"
  const val SENT_MESSAGE = "booking_request_sent_message"
  const val DONE_BUTTON = "booking_request_done_button"
}

/** What the booking request screen can do; every action does nothing by default. */
class BookingRequestActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSelectionChange: (Set<LocalDate>) -> Unit = {},
    val onRequest: () -> Unit = {},
    val onDone: () -> Unit = {},
)

/**
 * The screen where a renter chooses days on a listing and requests it (#26). [viewModel] loads the
 * listing and holds the days; [bookingViewModel] sends the request.
 *
 * @param onBack leaves the screen, from the back button and from "Done" once the request is sent.
 */
@Composable
fun BookingRequestScreen(
    viewModel: BookingRequestViewModel,
    bookingViewModel: BookingViewModel,
    onBack: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()
  val submitState by bookingViewModel.uiState.collectAsState()
  BookingRequestContent(
      uiState = uiState,
      submitState = submitState,
      actions =
          BookingRequestActions(
              onBack = onBack,
              onRetry = viewModel::loadListing,
              onSelectionChange = viewModel::updateSelection,
              onRequest = { uiState.booking?.let(bookingViewModel::requestBooking) },
              onDone = {
                bookingViewModel.acknowledgeQueuedBooking()
                onBack()
              },
          ),
  )
}

/** Side margin of the page, as on the listing detail. */
private val PageMargin = 18.dp

/**
 * The request as the renter sees it: the listing, the days to choose, the total, the damage
 * compensation of an item and how payment works, then "Request booking". Once the request is sent
 * ([BookingUiState.isQueued]) it shows a confirmation instead.
 */
@Composable
fun BookingRequestContent(
    uiState: BookingRequestUiState,
    submitState: BookingUiState,
    actions: BookingRequestActions,
) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .testTag(BookingRequestScreenTags.SCREEN)
  ) {
    AuthBackButton(
        onClick = actions.onBack,
        modifier =
            Modifier.padding(start = 20.dp, top = 6.dp)
                .testTag(BookingRequestScreenTags.BACK_BUTTON),
    )
    val listing = uiState.listing
    val error = uiState.error
    when {
      // A listing already shown stays shown even if a reload failed.
      listing != null && submitState.isQueued -> RequestSent(uiState, actions.onDone)
      listing != null -> RequestForm(listing, uiState, submitState, actions)
      error != null -> LoadError(text = error.message(), onRetry = actions.onRetry)
      else -> LoadingIndicator()
    }
  }
}

@Composable
private fun RequestForm(
    listing: Listing,
    uiState: BookingRequestUiState,
    submitState: BookingUiState,
    actions: BookingRequestActions,
) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = PageMargin, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Heading(title = stringResource(R.string.booking_request_title), listingTitle = listing.title)
    DatesField(
        uiState = uiState,
        onChange = actions.onSelectionChange,
        enabled = !submitState.isSubmitting,
    )
    Summary(listing, uiState)
    if (!uiState.canRequest) {
      Message(
          text = stringResource(R.string.booking_request_not_allowed),
          tag = BookingRequestScreenTags.NOT_ALLOWED,
          color = AuthColors.Danger,
      )
    }
    submitState.error?.let {
      Message(
          text = it.message(),
          tag = BookingRequestScreenTags.SUBMIT_ERROR,
          color = AuthColors.Danger,
      )
    }
    AuthPrimaryButton(
        text = stringResource(R.string.booking_request_submit),
        onClick = actions.onRequest,
        enabled = uiState.booking != null && !submitState.isSubmitting,
        modifier =
            Modifier.fillMaxWidth().height(48.dp).testTag(BookingRequestScreenTags.SUBMIT_BUTTON),
    )
  }
}

/** The screen title with the listing's title under it. */
@Composable
private fun Heading(title: String, listingTitle: String) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
        text = title,
        color = AuthColors.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = listingTitle,
        color = AuthColors.TextSecondary,
        fontSize = 14.sp,
        modifier = Modifier.testTag(BookingRequestScreenTags.LISTING_TITLE),
    )
  }
}

/**
 * The period chosen, written as "13–16 Nov". Tapping the field opens the calendar, where only the
 * days the lender offers can be chosen. Under it: why the period cannot be requested, if so.
 */
@Composable
private fun DatesField(
    uiState: BookingRequestUiState,
    onChange: (Set<LocalDate>) -> Unit,
    enabled: Boolean,
) {
  var isChoosing by rememberSaveable { mutableStateOf(false) }
  val shape = RoundedCornerShape(14.dp)
  val label = stringResource(R.string.booking_request_dates_label)
  val hasDays = uiState.selectableDays.isNotEmpty()
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
        text = label,
        color = AuthColors.TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(AuthColors.CardBackground, shape)
                .border(1.dp, AuthColors.Outline, shape)
                .clickable(enabled = enabled && hasDays, role = Role.Button) { isChoosing = true }
                // TalkBack reads "Dates" before the days: the label above is not part of the
                // button.
                .semantics { contentDescription = label }
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .testTag(BookingRequestScreenTags.DATES_FIELD),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text =
              formatRuns(groupIntoRuns(uiState.selection)).ifEmpty {
                stringResource(R.string.booking_request_dates_placeholder)
              },
          color =
              if (uiState.selection.isEmpty()) AuthColors.TextSecondary else AuthColors.TextPrimary,
          fontSize = 13.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
      )
      Icon(
          painter = painterResource(R.drawable.ic_calendar),
          contentDescription = null,
          tint = AuthColors.TextSecondary,
          modifier = Modifier.size(16.dp),
      )
    }
    val message =
        when {
          !hasDays -> stringResource(R.string.booking_request_no_days)
          uiState.isTooLong ->
              uiState.maxRentalDays?.let {
                pluralStringResource(R.plurals.booking_request_too_long, it, it)
              }
          else -> null
        }
    if (message != null) {
      Message(
          text = message,
          tag = BookingRequestScreenTags.DATES_MESSAGE,
          color = if (hasDays) AuthColors.Danger else AuthColors.TextSecondary,
      )
    }
  }
  if (isChoosing) {
    AvailabilityCalendarSheet(
        title = stringResource(R.string.booking_request_dates_sheet_title),
        selection = uiState.selection,
        onSelectionChange = onChange,
        onDismiss = { isChoosing = false },
        selectableDays = uiState.selectableDays,
        today = uiState.today,
    )
  }
}

/**
 * What the request costs: the price for each day and the total once a period is chosen, the damage
 * compensation of an item, and how payment works.
 */
@Composable
private fun Summary(listing: Listing, uiState: BookingRequestUiState) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val total = uiState.total
    if (total != null) {
      Message(
          text =
              pluralStringResource(
                  R.plurals.booking_request_price_line,
                  uiState.dayCount,
                  formatChf(listing.price),
                  uiState.dayCount,
              ),
          tag = BookingRequestScreenTags.PRICE_LINE,
      )
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.booking_request_total_label),
            color = AuthColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatChf(total),
            color = AuthColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(BookingRequestScreenTags.TOTAL),
        )
      }
    }
    if (listing is ItemListing) {
      Message(
          text = stringResource(R.string.booking_request_damage, formatChf(listing.refundValue)),
          tag = BookingRequestScreenTags.DAMAGE,
      )
    }
    Message(
        text = stringResource(R.string.booking_request_payment),
        tag = BookingRequestScreenTags.PAYMENT,
    )
  }
}

/** Shown once the request is sent: the days asked for, and "Done" to leave. */
@Composable
private fun RequestSent(uiState: BookingRequestUiState, onDone: () -> Unit) {
  Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = PageMargin, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Heading(
        title = stringResource(R.string.booking_request_sent_title),
        listingTitle = uiState.listing?.title.orEmpty(),
    )
    Message(
        text =
            stringResource(
                R.string.booking_request_sent_message,
                formatRuns(groupIntoRuns(uiState.selection)),
            ),
        tag = BookingRequestScreenTags.SENT_MESSAGE,
        color = AuthColors.TextPrimary,
    )
    AuthPrimaryButton(
        text = stringResource(R.string.booking_request_done),
        onClick = onDone,
        enabled = true,
        modifier =
            Modifier.fillMaxWidth().height(48.dp).testTag(BookingRequestScreenTags.DONE_BUTTON),
    )
  }
}

/** A 13sp line of text, grey unless [color] says otherwise. */
@Composable
private fun Message(
    text: String,
    tag: String,
    color: Color = AuthColors.TextSecondary,
) {
  Text(
      text = text,
      color = color,
      fontSize = 13.sp,
      lineHeight = 18.sp,
      modifier = Modifier.testTag(tag),
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
        modifier = Modifier.size(32.dp).testTag(BookingRequestScreenTags.LOADING_INDICATOR),
    )
  }
}

/** Why the listing could not be loaded, with Retry. */
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
        modifier = Modifier.weight(1f).testTag(BookingRequestScreenTags.ERROR_MESSAGE),
    )
    TextButton(
        onClick = onRetry,
        modifier = Modifier.testTag(BookingRequestScreenTags.RETRY_BUTTON),
    ) {
      Text(
          text = stringResource(R.string.booking_request_retry),
          color = AuthColors.Accent,
          fontWeight = FontWeight.Bold,
      )
    }
  }
}

/** An item with four days chosen, for the IDE preview. */
@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun BookingRequestContentPreview() {
  val first = LocalDate.of(2026, 11, 13)
  val days = (0L..3L).mapTo(mutableSetOf()) { first.plusDays(it) }
  BookingRequestContent(
      uiState =
          BookingRequestUiState(
              listing =
                  ItemListing(
                      category = ListingCategory.ITEM_TOOLS,
                      price = 8.0,
                      title = "Bosch drill set",
                      description = "Cordless drill with two batteries and a set of bits.",
                      refundValue = 120.0,
                      maxRentalDays = 7,
                      listingId = "preview",
                      ownerUid = "lender",
                  ),
              renterUid = "renter",
              selectableDays = days,
              selection = days,
              today = first,
          ),
      submitState = BookingUiState(),
      actions = BookingRequestActions(),
  )
}
