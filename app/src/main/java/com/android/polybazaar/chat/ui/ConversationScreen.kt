// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.ui.AuthBackButton
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.auth.ui.AuthPrimaryButton
import com.android.polybazaar.chat.model.ChatMessage
import com.android.polybazaar.chat.model.ChatRules
import com.android.polybazaar.chat.model.Conversation
import com.android.polybazaar.chat.viewmodel.ConversationListingStatus
import com.android.polybazaar.chat.viewmodel.ConversationUiState
import com.android.polybazaar.chat.viewmodel.ConversationViewModel
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.ui.message
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingLocation
import com.android.polybazaar.listing.model.ServiceListing
import com.android.polybazaar.listing.model.formatChf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers

object ConversationScreenTags {
  const val SCREEN = "conversation_screen"
  const val BACK_BUTTON = "conversation_back_button"
  const val LISTING_TITLE = "conversation_listing_title"
  const val OTHER_USERNAME = "conversation_other_username"
  const val OTHER_AVATAR = "conversation_other_avatar"
  const val LISTING_SUMMARY = "conversation_listing_summary"
  const val LISTING_STATUS_PILL = "conversation_listing_status_pill"
  const val NOTICE_ICON = "conversation_notice_icon"
  const val LOAD_ERROR_ICON = "conversation_load_error_icon"
  const val EMPTY_TAGLINE = "conversation_empty_tagline"
  const val PAUSED_NOTICE = "conversation_paused_notice"
  const val DELETED_NOTICE = "conversation_deleted_notice"
  const val LOADING_INDICATOR = "conversation_loading_indicator"
  const val ERROR_MESSAGE = "conversation_error_message"
  const val RETRY_BUTTON = "conversation_retry_button"
  const val EMPTY_STATE = "conversation_empty_state"
  const val MESSAGE_LIST = "conversation_message_list"
  const val MESSAGE_FIELD = "conversation_message_field"
  const val SEND_BUTTON = "conversation_send_button"
  const val SEND_ERROR = "conversation_send_error"
  const val LENGTH_COUNTER = "conversation_length_counter"

  /** Tag of the bubble of the signed-in user's message with [id]. */
  fun myMessage(id: String) = "conversation_my_message_$id"

  /** Tag of the bubble of the other participant's message with [id]. */
  fun theirMessage(id: String) = "conversation_their_message_$id"

  /** Tag of the divider above the messages sent on [date]. */
  fun dayDivider(date: LocalDate) = "conversation_day_divider_$date"
}

/** What the screen shows between the header and the composer, decided by [conversationBody]. */
internal enum class ConversationBody {
  LOADING,
  ERROR,
  EMPTY,
  MESSAGES,
}

/**
 * Picks the body for [state]. The spinner is only for while nothing is shown yet: messages already
 * shown stay up, with the composer open, when a load fails and while Retry reloads the part that
 * failed, so going offline or retrying never takes away the conversation being read. A load error
 * shows even while the rest is still loading, so Retry is offered at once.
 */
internal fun conversationBody(state: ConversationUiState): ConversationBody =
    when {
      state.messages.isNotEmpty() -> ConversationBody.MESSAGES
      state.loadError != null -> ConversationBody.ERROR
      state.isLoading -> ConversationBody.LOADING
      else -> ConversationBody.EMPTY
    }

/** Side margin of the page, the same as on the listing detail. */
private val PageMargin = 18.dp

/** Widest a bubble gets, so a long message wraps instead of spanning the screen. */
private val MaxBubbleWidth = 300.dp

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Conversation screen: shows what [viewModel] loads, and passes it what the user types and taps.
 *
 * @param onBack called when the user taps the back button.
 */
@Composable
fun ConversationScreen(viewModel: ConversationViewModel, onBack: () -> Unit) {
  // Collected without dispatching, as on the listing forms: a text field that lags behind its input
  // can jump its cursor or drop characters.
  val uiState by viewModel.uiState.collectAsState(context = Dispatchers.Main.immediate)
  // Messages are marked read only while the screen is started, not while the app is in the
  // background with the conversation open.
  LifecycleStartEffect(viewModel) {
    viewModel.setVisible(true)
    onStopOrDispose { viewModel.setVisible(false) }
  }
  ConversationContent(
      uiState = uiState,
      onBack = onBack,
      onDraftChange = viewModel::onDraftChange,
      onSend = viewModel::send,
      onRetry = viewModel::retry,
      onDismissSendError = viewModel::dismissSendError,
  )
}

/**
 * One conversation about a listing: a header with the listing and the other participant, a notice
 * while the listing is paused or deleted, the messages, and the composer at the bottom.
 *
 * The composer stays open in every body, so the user can write while the conversation loads, and
 * closes for good when the listing is deleted. Send is enabled when it is open and
 * [ConversationUiState.canSend] is true.
 *
 * @param onDraftChange called with the new text when the user edits the draft.
 * @param onSend called when the user taps Send.
 * @param onRetry called when the user taps Retry after a load failed.
 * @param onDismissSendError called before [onDraftChange] when the user edits the draft while a
 *   send error shows.
 * @param zone time zone of the message times and days.
 * @param now the current time, which the day dividers count "Today" and "Yesterday" from.
 */
@Composable
fun ConversationContent(
    uiState: ConversationUiState,
    onBack: () -> Unit = {},
    onDraftChange: (String) -> Unit = {},
    onSend: () -> Unit = {},
    onRetry: () -> Unit = {},
    onDismissSendError: () -> Unit = {},
    zone: ZoneId = ZoneId.systemDefault(),
    now: Instant = Instant.now(),
) {
  val body = conversationBody(uiState)
  val deleted = uiState.listingStatus == ConversationListingStatus.DELETED
  // Open while loading and after a load error too, so the user can write while waiting;
  // canSend decides when Send works.
  val composerOpen = !deleted
  val placeholder =
      if (deleted) R.string.conversation_placeholder_closed else R.string.conversation_placeholder
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .testTag(ConversationScreenTags.SCREEN)
  ) {
    Header(
        listingTitle = uiState.listingTitle,
        otherUser = uiState.otherUser,
        otherUserPhoto = rememberPhoto(uiState.otherUserPhoto),
        isRenter = uiState.isRenter,
        onBack = onBack,
    )
    val listingPhoto = rememberPhoto(uiState.listingPhoto)
    uiState.listingStatus?.let {
      ListingSummary(uiState = uiState, status = it, photo = listingPhoto)
    }
    HorizontalDivider(color = AuthColors.Outline)
    uiState.listingStatus?.let { ListingNotice(it, uiState.firstMessageBlockedByPause) }
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      when (body) {
        ConversationBody.LOADING -> LoadingIndicator()
        ConversationBody.ERROR ->
            LoadError(text = uiState.loadError?.message().orEmpty(), onRetry = onRetry)
        ConversationBody.EMPTY ->
            EmptyState(
                otherUser = uiState.otherUser,
                listingTitle = uiState.listingTitle,
                listingStatus = uiState.listingStatus,
            )
        ConversationBody.MESSAGES ->
            MessageList(
                uiState = uiState,
                zone = zone,
                today = now.atZone(zone).toLocalDate(),
                onRetry = onRetry,
            )
      }
    }
    Composer(
        draft = uiState.draft,
        open = composerOpen,
        canSend = uiState.canSend,
        placeholder = stringResource(placeholder),
        sendError = uiState.sendError,
        onDraftChange = { text ->
          if (uiState.sendError != null) onDismissSendError()
          onDraftChange(text)
        },
        onSend = onSend,
    )
  }
}

/**
 * Back, the listing's title with the other participant's username and role under it, then their
 * photo.
 */
@Composable
private fun Header(
    listingTitle: String?,
    otherUser: User?,
    otherUserPhoto: ImageBitmap?,
    isRenter: Boolean,
    onBack: () -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = PageMargin, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    AuthBackButton(
        onClick = onBack,
        modifier = Modifier.testTag(ConversationScreenTags.BACK_BUTTON),
    )
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
          text = listingTitle.orEmpty(),
          color = AuthColors.TextPrimary,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag(ConversationScreenTags.LISTING_TITLE),
      )
      otherUser?.let {
        val role =
            if (isRenter) R.string.conversation_role_lender else R.string.conversation_role_renter
        Text(
            text =
                stringResource(
                    R.string.conversation_other_username,
                    it.username,
                    stringResource(role),
                ),
            color = AuthColors.TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(ConversationScreenTags.OTHER_USERNAME),
        )
      }
    }
    // Decorative: the username next to it says who it is.
    Image(
        painter =
            otherUserPhoto?.let { BitmapPainter(it) }
                ?: painterResource(R.drawable.default_profile_photo),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier.size(38.dp).clip(CircleShape).testTag(ConversationScreenTags.OTHER_AVATAR),
    )
  }
}

/**
 * The listing the conversation is about: its photo, its lender, its price and place, and whether it
 * is a rental or a service, paused or deleted. Without the listing, as when it was deleted before
 * the screen opened, only the lender and the status show.
 */
@Composable
private fun ListingSummary(
    uiState: ConversationUiState,
    status: ConversationListingStatus,
    photo: ImageBitmap?,
) {
  val listing = uiState.listing
  // The other participant is the lender when the signed-in user is the renter.
  val lender =
      if (uiState.isRenter) uiState.otherUser?.username
      else stringResource(R.string.conversation_summary_you)
  // Replaces the lender's name in the card's merged semantics, so TalkBack says whose listing it
  // is,
  // then its details, without also saying the bare name.
  val spokenLender =
      if (uiState.isRenter)
          uiState.otherUser?.let {
            stringResource(R.string.conversation_summary_spoken_lender, it.username)
          }
      else stringResource(R.string.conversation_summary_spoken_mine)
  val perDay = stringResource(R.string.browse_price_per_day)
  // Only items are rented by the day; a service has a price alone.
  val details = listing?.let {
    val price = if (it is ItemListing) "${formatChf(it.price)} $perDay" else formatChf(it.price)
    listOfNotNull(price, it.location?.name).joinToString(" · ")
  }
  val shape = RoundedCornerShape(14.dp)
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .padding(start = PageMargin, end = PageMargin, top = 4.dp, bottom = 12.dp)
              .background(AuthColors.CardBackground, shape)
              .border(1.dp, AuthColors.Outline, shape)
              .padding(10.dp)
              // One TalkBack item for the whole card.
              .semantics(mergeDescendants = true) {}
              .testTag(ConversationScreenTags.LISTING_SUMMARY),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    ListingPhoto(photo = photo, size = 46.dp)
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      lender?.let {
        Text(
            text = it,
            color = AuthColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier.clearAndSetSemantics { spokenLender?.let { contentDescription = it } },
        )
      }
      details?.let {
        Text(
            text = it,
            color = AuthColors.TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
      }
    }
    StatusPill(status = status, isService = listing is ServiceListing)
  }
}

/** Rental or Service while the listing is active, else Paused or Deleted. */
@Composable
private fun StatusPill(status: ConversationListingStatus, isService: Boolean) {
  val (text, background, color) =
      when (status) {
        ConversationListingStatus.ACTIVE ->
            PillStyle(
                stringResource(
                    if (isService) R.string.conversation_status_service
                    else R.string.conversation_status_rental
                ),
                AuthColors.AccentContainer,
                AuthColors.Accent,
            )
        ConversationListingStatus.PAUSED ->
            PillStyle(
                stringResource(R.string.conversation_status_paused),
                AuthColors.AccentContainer,
                PausedBadgeText,
            )
        ConversationListingStatus.DELETED ->
            PillStyle(
                stringResource(R.string.conversation_status_deleted),
                DeletedBadgeBackground,
                DeletedBadgeText,
            )
      }
  Text(
      text = text,
      color = color,
      fontSize = 11.sp,
      fontWeight = FontWeight.SemiBold,
      modifier =
          Modifier.background(background, RoundedCornerShape(12.dp))
              .padding(horizontal = 9.dp, vertical = 5.dp)
              .testTag(ConversationScreenTags.LISTING_STATUS_PILL),
  )
}

/** The text and colors of the listing's status pill. */
private data class PillStyle(val text: String, val background: Color, val color: Color)

/**
 * What happened to the listing, while it is paused or deleted. [firstMessageBlockedByPause] adds
 * why Send stays off on a paused listing without a conversation.
 */
@Composable
private fun ListingNotice(status: ConversationListingStatus, firstMessageBlockedByPause: Boolean) {
  when (status) {
    ConversationListingStatus.ACTIVE -> Unit
    // The body is only for a first message, the one the rules refuse: an existing conversation
    // can still send.
    ConversationListingStatus.PAUSED ->
        Notice(
            title = stringResource(R.string.conversation_paused_title),
            body =
                if (firstMessageBlockedByPause) {
                  stringResource(R.string.conversation_paused_first_message_body)
                } else null,
            tag = ConversationScreenTags.PAUSED_NOTICE,
            icon = R.drawable.ic_pause_circle,
            iconTint = AuthColors.Accent,
            background = AuthColors.AccentContainer,
        )
    ConversationListingStatus.DELETED ->
        Notice(
            title = stringResource(R.string.conversation_deleted_title),
            body = stringResource(R.string.conversation_deleted_body),
            tag = ConversationScreenTags.DELETED_NOTICE,
            icon = R.drawable.ic_archive,
            iconTint = DeletedBadgeText,
            background = DeletedBadgeBackground,
        )
  }
}

@Composable
private fun Notice(
    title: String,
    body: String?,
    tag: String,
    @DrawableRes icon: Int,
    iconTint: Color,
    background: Color,
) {
  val shape = RoundedCornerShape(14.dp)
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .padding(start = PageMargin, end = PageMargin, top = 10.dp)
              .background(background, shape)
              .padding(12.dp)
              // One TalkBack announcement for the title and its explanation.
              .semantics(mergeDescendants = true) {}
              .testTag(tag),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Decorative: the title says what happened to the listing.
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(18.dp).testTag(ConversationScreenTags.NOTICE_ICON),
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
          text = title,
          color = AuthColors.TextPrimary,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
      )
      body?.let {
        Text(text = it, color = AuthColors.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
      }
    }
  }
}

/** A decorative [icon] in a tinted circle, above the text of an empty or failed conversation. */
@Composable
private fun CircledIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
  Box(
      modifier = modifier.size(64.dp).background(AuthColors.AccentContainer, CircleShape),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = AuthColors.Accent,
        modifier = Modifier.size(28.dp),
    )
  }
}

@Composable
private fun LoadingIndicator() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(
        color = AuthColors.Accent,
        modifier = Modifier.size(32.dp).testTag(ConversationScreenTags.LOADING_INDICATOR),
    )
  }
}

/** Why no message could be loaded, with Retry. */
@Composable
private fun LoadError(text: String, onRetry: () -> Unit) {
  Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = PageMargin),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
  ) {
    // Decorative: the title under it says what failed.
    CircledIcon(
        icon = R.drawable.ic_wifi_off,
        modifier = Modifier.testTag(ConversationScreenTags.LOAD_ERROR_ICON),
    )
    Text(
        text = stringResource(R.string.conversation_load_error_title),
        color = AuthColors.TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    Text(
        text = text,
        color = AuthColors.Danger,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(ConversationScreenTags.ERROR_MESSAGE),
    )
    AuthPrimaryButton(
        text = stringResource(R.string.conversation_retry),
        onClick = onRetry,
        enabled = true,
        modifier = Modifier.padding(top = 8.dp).testTag(ConversationScreenTags.RETRY_BUTTON),
    )
  }
}

/**
 * A conversation without messages. Only an active listing invites a first message, with the listing
 * to ask about when its title is known: the rules refuse a first message about a paused or deleted
 * listing.
 */
@Composable
private fun EmptyState(
    otherUser: User?,
    listingTitle: String?,
    listingStatus: ConversationListingStatus?,
) {
  val invites = listingStatus == null || listingStatus == ConversationListingStatus.ACTIVE
  Column(
      modifier =
          Modifier.fillMaxSize()
              .padding(horizontal = PageMargin)
              .testTag(ConversationScreenTags.EMPTY_STATE),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
  ) {
    // Decorative: the text under it says what the empty conversation is for.
    CircledIcon(icon = R.drawable.ic_message_circle)
    if (invites) {
      Text(
          text =
              otherUser?.let { stringResource(R.string.conversation_empty_title, it.username) }
                  ?: stringResource(R.string.conversation_empty_title_unknown),
          color = AuthColors.TextPrimary,
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center,
      )
    }
    Text(
        text =
            listingTitle
                ?.takeIf { invites }
                ?.let { stringResource(R.string.conversation_empty_body, it) }
                ?: stringResource(R.string.conversation_empty_body_no_title),
        color = AuthColors.TextSecondary,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        textAlign = TextAlign.Center,
    )
    if (invites) {
      Text(
          text = stringResource(R.string.conversation_empty_tagline),
          color = AuthColors.Accent,
          fontSize = 12.sp,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 8.dp).testTag(ConversationScreenTags.EMPTY_TAGLINE),
      )
    }
  }
}

/**
 * The messages, oldest at the top, under the day they were sent on, with the load error and Retry
 * above them when a reload failed.
 */
@Composable
private fun MessageList(
    uiState: ConversationUiState,
    zone: ZoneId,
    today: LocalDate,
    onRetry: () -> Unit,
) {
  val messages = uiState.messages
  val items = remember(messages, zone) { conversationItems(messages, zone).asReversed() }
  val listState = rememberLazyListState()
  // The list keeps its place by key, so a new message would arrive below the view without this.
  LaunchedEffect(messages.lastOrNull()?.id) { listState.scrollToItem(0) }
  Column(modifier = Modifier.fillMaxSize()) {
    uiState.loadError?.let { LoadErrorRow(text = it.message(), onRetry = onRetry) }
    // Laid out from the bottom, newest first, so the newest message stays in view, footer
    // included, however long it is and whatever shrinks the list.
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth().testTag(ConversationScreenTags.MESSAGE_LIST),
        contentPadding = PaddingValues(horizontal = PageMargin, vertical = 12.dp),
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(
          items,
          key = { item ->
            when (item) {
              is ConversationItem.Message -> item.message.id
              // Firestore document ids cannot contain "/", so a day never shares a message's key.
              is ConversationItem.DayDivider -> "day/${item.date}"
            }
          },
      ) { item ->
        when (item) {
          is ConversationItem.Message ->
              MessageBubble(
                  message = item.message,
                  isMine = item.message.senderUid == uiState.myUid,
                  otherUsername = uiState.otherUser?.username,
                  zone = zone,
              )
          is ConversationItem.DayDivider -> DayDivider(date = item.date, today = today)
        }
      }
    }
  }
}

/** The day the messages under it were sent on: "Today", "Yesterday" or the date. */
@Composable
private fun DayDivider(date: LocalDate, today: LocalDate) {
  Text(
      text = conversationDay(date, today).label(),
      color = AuthColors.TextSecondary,
      fontSize = 12.sp,
      textAlign = TextAlign.Center,
      modifier =
          Modifier.fillMaxWidth()
              .padding(vertical = 4.dp)
              .semantics { heading() }
              .testTag(ConversationScreenTags.dayDivider(date)),
  )
}

/** A reload error above messages that stay shown, with Retry as a text button. */
@Composable
private fun LoadErrorRow(text: String, onRetry: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(start = PageMargin, end = 8.dp, top = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text = text,
        color = AuthColors.Danger,
        fontSize = 13.sp,
        modifier =
            Modifier.weight(1f)
                // Announced when it appears: TalkBack's focus is elsewhere, on a message or the
                // composer.
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(ConversationScreenTags.ERROR_MESSAGE),
    )
    TextButton(
        onClick = onRetry,
        modifier = Modifier.testTag(ConversationScreenTags.RETRY_BUTTON),
    ) {
      Text(
          text = stringResource(R.string.conversation_retry),
          color = AuthColors.Accent,
          fontWeight = FontWeight.Bold,
      )
    }
  }
}

/**
 * One message: the user's own at the end of the row, the other participant's at the start. The
 * footer says who sent it and when, or only when while the other participant's profile is unknown.
 */
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    otherUsername: String?,
    zone: ZoneId,
) {
  val time = message.sentAt.atZone(zone).format(TimeFormat)
  val footer =
      when {
        isMine -> stringResource(R.string.conversation_my_message_footer, time)
        otherUsername != null ->
            stringResource(R.string.conversation_their_message_footer, otherUsername, time)
        else -> time
      }
  val shape = RoundedCornerShape(16.dp)
  Box(
      modifier = Modifier.fillMaxWidth(),
      contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart,
  ) {
    Column(
        modifier =
            Modifier.widthIn(max = MaxBubbleWidth)
                // One TalkBack item per message, the whole bubble: its text, then who sent it and
                // when.
                .semantics(mergeDescendants = true) {}
                .testTag(
                    if (isMine) ConversationScreenTags.myMessage(message.id)
                    else ConversationScreenTags.theirMessage(message.id)
                )
                .background(
                    if (isMine) AuthColors.AccentContainer else AuthColors.CardBackground,
                    shape,
                )
                // Mine get a border in their own fill colour, so both kinds keep the same size.
                .border(1.dp, if (isMine) AuthColors.AccentContainer else AuthColors.Outline, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
          text = message.text,
          color = AuthColors.TextPrimary,
          fontSize = 14.sp,
          lineHeight = 19.sp,
      )
      Text(
          text = footer,
          color = AuthColors.TextSecondary,
          fontSize = 11.sp,
          modifier = Modifier.align(Alignment.End),
      )
    }
  }
}

/**
 * The message field and Send, with why the last send failed under them and, once the draft is too
 * long, a counter that explains why Send is disabled. The text is kept as typed.
 *
 * @param open whether the user can type; Send also needs [canSend].
 */
@Composable
private fun Composer(
    draft: String,
    open: Boolean,
    canSend: Boolean,
    placeholder: String,
    sendError: AppError?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
  val length = ChatRules.countedLength(draft)
  val tooLong = open && length > ChatRules.MAX_MESSAGE_LENGTH
  val lengthError =
      if (tooLong)
          pluralStringResource(
              R.plurals.conversation_length_too_long,
              ChatRules.MAX_MESSAGE_LENGTH,
              ChatRules.MAX_MESSAGE_LENGTH,
          )
      else null
  HorizontalDivider(color = AuthColors.Outline)
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .background(AuthColors.CardBackground)
              .padding(horizontal = PageMargin, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
      MessageField(
          value = draft,
          onValueChange = onDraftChange,
          enabled = open,
          placeholder = placeholder,
          modifier = Modifier.weight(1f),
      )
      AuthPrimaryButton(
          text = stringResource(R.string.conversation_send),
          onClick = onSend,
          enabled = open && canSend,
          modifier = Modifier.height(48.dp).testTag(ConversationScreenTags.SEND_BUTTON),
      )
    }
    if (sendError != null || tooLong) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (sendError != null) {
          Text(
              text = sendError.message(),
              color = AuthColors.Danger,
              fontSize = 12.sp,
              modifier =
                  Modifier.weight(1f)
                      // Announced when it appears: TalkBack's focus stays on Send.
                      .semantics { liveRegion = LiveRegionMode.Polite }
                      .testTag(ConversationScreenTags.SEND_ERROR),
          )
        } else {
          Spacer(Modifier.weight(1f))
        }
        if (lengthError != null) {
          Text(
              text =
                  stringResource(
                      R.string.conversation_length_counter,
                      length,
                      ChatRules.MAX_MESSAGE_LENGTH,
                  ),
              color = AuthColors.Danger,
              fontSize = 12.sp,
              modifier =
                  Modifier.testTag(ConversationScreenTags.LENGTH_COUNTER)
                      // Its semantics are only the fixed sentence, not the number: the polite live
                      // region speaks once when the counter appears and stays quiet as the number
                      // changes.
                      .clearAndSetSemantics {
                        contentDescription = lengthError
                        liveRegion = LiveRegionMode.Polite
                      },
          )
        }
      }
    }
  }
}

/** A field styled like LabeledField's, that grows up to five lines as the message gets longer. */
@Composable
private fun MessageField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
  val textStyle = TextStyle(fontSize = 14.sp, lineHeight = 19.sp)
  val shape = RoundedCornerShape(14.dp)
  BasicTextField(
      value = value,
      onValueChange = onValueChange,
      enabled = enabled,
      maxLines = 5,
      textStyle = textStyle.copy(color = AuthColors.TextPrimary),
      cursorBrush = SolidColor(AuthColors.Accent),
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
      modifier = modifier.testTag(ConversationScreenTags.MESSAGE_FIELD),
      decorationBox = { innerTextField ->
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    // 48dp: the height of Send next to it, and the minimum touch target.
                    .heightIn(min = 48.dp)
                    .background(AuthColors.Background, shape)
                    .border(1.dp, AuthColors.Outline, shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
          if (value.isEmpty()) {
            Text(text = placeholder, style = textStyle.copy(color = AuthColors.TextSecondary))
          }
          innerTextField()
        }
      },
  )
}

/**
 * A paused listing with messages over two days, for the IDE preview. Internal so a test can render
 * it.
 */
@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
internal fun ConversationContentPreview() {
  val start = Instant.parse("2026-05-04T09:12:00Z")
  val nextDay = start.plusSeconds(24 * 60 * 60)
  ConversationContent(
      uiState =
          ConversationUiState(
              myUid = "renter",
              messages =
                  listOf(
                      ChatMessage("1", "renter", "Hi! Is the drill free this Saturday?", start),
                      ChatMessage(
                          "2",
                          "lender",
                          "Yes, from 10:00. I can add a second battery if you need one.",
                          start.plusSeconds(120),
                      ),
                      ChatMessage("3", "renter", "Perfect, see you then.", nextDay),
                  ),
              conversation =
                  Conversation(
                      listingId = "drill",
                      listingTitle = "Bosch drill set",
                      renterUid = "renter",
                      lenderUid = "lender",
                      lastMessageAt = nextDay,
                  ),
              listingTitle = "Bosch drill set",
              listingStatus = ConversationListingStatus.PAUSED,
              listing =
                  ItemListing(
                      ListingCategory.ITEM_TOOLS,
                      8.0,
                      "Bosch drill set",
                      "Cordless drill with bits.",
                      listingId = "drill",
                      ownerUid = "lender",
                      isPaused = true,
                      location = ListingLocation(46.5376, 6.5804, "Renens VD"),
                  ),
              isRenter = true,
              otherUser = User(uid = "lender", email = "", username = "james"),
              isLoading = false,
          ),
      zone = ZoneOffset.UTC,
      now = nextDay,
  )
}
