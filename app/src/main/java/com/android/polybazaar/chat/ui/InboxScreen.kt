// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.auth.ui.AuthPrimaryButton
import com.android.polybazaar.auth.ui.AuthTitleGroup
import com.android.polybazaar.chat.model.Conversation
import com.android.polybazaar.chat.viewmodel.InboxConversation
import com.android.polybazaar.chat.viewmodel.InboxUiState
import com.android.polybazaar.chat.viewmodel.InboxViewModel
import com.android.polybazaar.chat.viewmodel.ListingStatus
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.ui.message
import com.android.polybazaar.listing.ui.decodePhotoPreview
import java.time.Duration
import java.time.Instant

object InboxScreenTags {
  const val LIST = "inbox_list"
  const val PAUSED_BADGE = "inbox_paused_badge"
  const val DELETED_BADGE = "inbox_deleted_badge"
  const val LOADING_INDICATOR = "inbox_loading_indicator"
  const val ERROR_MESSAGE = "inbox_error_message"
  const val RETRY_BUTTON = "inbox_retry_button"
  const val EMPTY_STATE = "inbox_empty_state"
  const val BROWSE_BUTTON = "inbox_browse_button"

  /** Tag of the row showing the conversation with [conversationId]. */
  fun row(conversationId: String) = "inbox_row_$conversationId"
}

/** What the user can do on the inbox screen. */
data class InboxActions(
    /**
     * Called with the conversation of the row the user tapped; null leaves the rows unclickable.
     */
    val onConversationClick: ((Conversation) -> Unit)? = null,
    /** Called when the user taps Retry after an error. */
    val onRetry: () -> Unit = {},
    /** Called when the user taps "Browse listings" on the empty inbox. */
    val onBrowse: () -> Unit = {},
)

/** What the screen shows under its header, decided by [inboxBody]. */
internal enum class InboxBody {
  LOADING,
  ERROR,
  EMPTY,
  CONVERSATIONS,
}

/**
 * Picks the screen body for [state]. Rows already shown stay, even while loading again or after an
 * error; the error then shows above them.
 */
internal fun inboxBody(state: InboxUiState): InboxBody =
    when {
      state.conversations.isNotEmpty() -> InboxBody.CONVERSATIONS
      state.isLoading -> InboxBody.LOADING
      state.error != null -> InboxBody.ERROR
      else -> InboxBody.EMPTY
    }

/** Side margin of the page, the x of the header and the rows in the Figma. */
private val PageMargin = 18.dp

/** Text of the "Listing paused" badge, from the Figma. */
private val PausedBadgeText = Color(0xFF8C491F)

/** Fill of the "Listing deleted" badge, from the Figma. */
private val DeletedBadgeBackground = Color(0xFFF2EAE5)

/** Text of the "Listing deleted" badge, from the Figma. */
private val DeletedBadgeText = Color(0xFF67564D)

/**
 * The inbox screen: loads the conversations of [myUid], the signed-in user, into [viewModel] and
 * shows them. Retry loads them again.
 *
 * @param onConversationClick called with the conversation of the row the user tapped; null while
 *   there is no conversation screen to open, so the rows are not announced as buttons.
 * @param onBrowse called when the user taps "Browse listings" on the empty inbox.
 */
@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    myUid: String,
    onConversationClick: ((Conversation) -> Unit)?,
    onBrowse: () -> Unit,
) {
  // Runs each time the tab is opened; openInbox keeps a load still running for the same user.
  LaunchedEffect(myUid) { viewModel.openInbox(myUid) }
  val state by viewModel.uiState.collectAsState()
  InboxContent(
      state = state,
      actions =
          InboxActions(
              onConversationClick = onConversationClick,
              onRetry = { viewModel.loadInbox(myUid) },
              onBrowse = onBrowse,
          ),
  )
}

/**
 * The signed-in user's conversations, as in the Figma frames "Conversations — list" and
 * "Conversations — Empty list", with a spinner while loading and the error with Retry.
 *
 * @param state what [InboxViewModel] loaded.
 * @param now the time the rows' "5m"-style labels count from. It is read again only when the screen
 *   recomposes, such as on a new message or on coming back to the tab, so a label does not tick
 *   while the screen stays unchanged. That is on purpose: an inbox is glanced at, not watched.
 */
@Composable
fun InboxContent(state: InboxUiState, actions: InboxActions, now: Instant = Instant.now()) {
  Column(modifier = Modifier.fillMaxSize().background(AuthColors.Background).safeDrawingPadding()) {
    Header()
    val bodyModifier = Modifier.fillMaxWidth().weight(1f)
    when (inboxBody(state)) {
      InboxBody.LOADING ->
          Box(modifier = bodyModifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                color = AuthColors.Accent,
                modifier = Modifier.size(32.dp).testTag(InboxScreenTags.LOADING_INDICATOR),
            )
          }
      InboxBody.ERROR ->
          Box(modifier = bodyModifier) {
            ErrorLine(error = state.error ?: AppError.UNKNOWN, onRetry = actions.onRetry)
          }
      InboxBody.EMPTY -> EmptyInbox(onBrowse = actions.onBrowse, modifier = bodyModifier)
      InboxBody.CONVERSATIONS ->
          ConversationList(state = state, actions = actions, now = now, modifier = bodyModifier)
    }
  }
}

/** The "Messages" heading, the rows and the footer, with the error above them if any. */
@Composable
private fun ConversationList(
    state: InboxUiState,
    actions: InboxActions,
    now: Instant,
    modifier: Modifier,
) {
  LazyColumn(
      modifier = modifier.testTag(InboxScreenTags.LIST),
      contentPadding = PaddingValues(bottom = 14.dp),
  ) {
    state.error?.let { error -> item { ErrorLine(error = error, onRetry = actions.onRetry) } }
    item { MessagesHeading() }
    items(state.conversations, key = { it.conversation.id }) { row ->
      ConversationRow(
          row = row,
          photos = state.photos,
          now = now,
          onClick = actions.onConversationClick?.let { onClick -> { onClick(row.conversation) } },
      )
    }
    item { Footer() }
  }
}

/** Why the conversations could not be loaded, with a Retry button, as on Browse. */
@Composable
private fun ErrorLine(error: AppError, onRetry: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = PageMargin, vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text = error.message(),
        color = AuthColors.Danger,
        fontSize = 14.sp,
        modifier = Modifier.weight(1f).testTag(InboxScreenTags.ERROR_MESSAGE),
    )
    TextButton(onClick = onRetry, modifier = Modifier.testTag(InboxScreenTags.RETRY_BUTTON)) {
      Text(
          text = stringResource(R.string.inbox_retry),
          color = AuthColors.Accent,
          fontWeight = FontWeight.Bold,
      )
    }
  }
}

/** The Figma frame "Conversations — Empty list": an icon, an explanation and "Browse listings". */
@Composable
private fun EmptyInbox(onBrowse: () -> Unit, modifier: Modifier) {
  Column(
      // 100dp at the bottom, as in the Figma, so the content sits a little above the centre.
      modifier =
          modifier
              .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 100.dp)
              .testTag(InboxScreenTags.EMPTY_STATE),
      verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
        modifier =
            Modifier.size(width = 64.dp, height = 52.dp)
                .background(AuthColors.AccentContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      // Decorative: the title below says what the screen is about.
      Icon(
          painter = painterResource(R.drawable.ic_message_circle),
          contentDescription = null,
          tint = AuthColors.Accent,
          modifier = Modifier.size(21.dp),
      )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
          text = stringResource(R.string.inbox_empty_title),
          color = AuthColors.TextPrimary,
          fontSize = 21.sp,
          lineHeight = 32.sp,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
      )
      Text(
          text = stringResource(R.string.inbox_empty_message),
          color = AuthColors.TextSecondary,
          fontSize = 14.sp,
          lineHeight = 21.sp,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
      )
    }
    AuthPrimaryButton(
        text = stringResource(R.string.inbox_browse_listings),
        onClick = onBrowse,
        enabled = true,
        modifier = Modifier.width(240.dp).height(48.dp).testTag(InboxScreenTags.BROWSE_BUTTON),
    )
  }
}

@Composable
private fun Header() {
  // 54dp: height of the "Inbox header" row in the Figma.
  Box(
      modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = PageMargin),
      contentAlignment = Alignment.CenterStart,
  ) {
    AuthTitleGroup(
        title = stringResource(R.string.inbox_title),
        eyebrow = stringResource(R.string.inbox_eyebrow),
    )
  }
}

@Composable
private fun MessagesHeading() {
  Text(
      text = stringResource(R.string.inbox_messages_heading),
      color = AuthColors.TextPrimary,
      fontSize = 15.sp,
      fontWeight = FontWeight.SemiBold,
      modifier =
          Modifier.padding(start = PageMargin, end = PageMargin, top = 10.dp, bottom = 12.dp),
  )
}

/**
 * One conversation: the listing photo, the other person, the listing title with its badge, and the
 * time since the last message. Without [onClick], the row is not clickable but TalkBack still reads
 * it as one item.
 */
@Composable
private fun ConversationRow(
    row: InboxConversation,
    photos: Map<String, ByteArray>,
    now: Instant,
    onClick: (() -> Unit)?,
) {
  val shape = RoundedCornerShape(20.dp)
  Row(
      modifier =
          Modifier.padding(start = PageMargin, end = PageMargin, bottom = 10.dp)
              .fillMaxWidth()
              .clip(shape)
              .background(AuthColors.CardBackground)
              .border(1.dp, AuthColors.Outline, shape)
              .then(
                  if (onClick == null) Modifier.semantics(mergeDescendants = true) {}
                  // Role.Button and the label let TalkBack announce the row as "Open conversation".
                  else
                      Modifier.clickable(
                          role = Role.Button,
                          onClickLabel = stringResource(R.string.inbox_open_conversation),
                          onClick = onClick,
                      )
              )
              .padding(12.dp)
              .testTag(InboxScreenTags.row(row.conversation.id)),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    ListingPhoto(photo = rememberPhoto(row.listingPhotoPath?.let { photos[it] }))
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      OtherPerson(
          user = row.otherUser,
          photo = rememberPhoto(row.otherUser?.photoPath?.let { photos[it] }),
      )
      Text(
          text = row.listingTitle,
          color = AuthColors.TextPrimary,
          fontSize = 14.sp,
          lineHeight = 19.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      ListingBadge(row.listingStatus)
    }
    inboxTime(row.conversation.lastMessageAt, now)?.let { time ->
      Text(text = time.label(), color = AuthColors.TextSecondary, fontSize = 11.sp)
    }
  }
}

/** The listing's photo, or a placeholder with an image icon while it is missing. */
@Composable
private fun ListingPhoto(photo: ImageBitmap?) {
  Box(
      modifier =
          Modifier.size(48.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(AuthColors.AccentContainer),
      contentAlignment = Alignment.Center,
  ) {
    if (photo != null) {
      // Decorative: the title next to it names the listing.
      Image(
          painter = BitmapPainter(photo),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.size(48.dp),
      )
    } else {
      Icon(
          painter = painterResource(R.drawable.ic_image),
          contentDescription = null,
          tint = AuthColors.TextSecondary,
          modifier = Modifier.size(20.dp),
      )
    }
  }
}

/**
 * The other person's photo (the default one while missing) and "@username". Without a profile, only
 * the default photo shows.
 */
@Composable
private fun OtherPerson(user: User?, photo: ImageBitmap?) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    // Decorative: the username next to it says who it is.
    Image(
        painter =
            photo?.let { BitmapPainter(it) } ?: painterResource(R.drawable.default_profile_photo),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(20.dp).clip(CircleShape),
    )
    user?.let {
      Text(
          text = stringResource(R.string.inbox_username, it.username),
          color = AuthColors.TextSecondary,
          fontSize = 11.sp,
          lineHeight = 15.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/** "Listing paused" or "Listing deleted" under the title; nothing for any other status. */
@Composable
private fun ListingBadge(status: ListingStatus) {
  val (text, background, color, tag) =
      when (status) {
        ListingStatus.PAUSED ->
            BadgeStyle(
                stringResource(R.string.inbox_listing_paused),
                AuthColors.AccentContainer,
                PausedBadgeText,
                InboxScreenTags.PAUSED_BADGE,
            )
        ListingStatus.DELETED ->
            BadgeStyle(
                stringResource(R.string.inbox_listing_deleted),
                DeletedBadgeBackground,
                DeletedBadgeText,
                InboxScreenTags.DELETED_BADGE,
            )
        ListingStatus.ACTIVE,
        ListingStatus.UNKNOWN -> return
      }
  Text(
      text = text,
      color = color,
      fontSize = 10.sp,
      lineHeight = 14.sp,
      fontWeight = FontWeight.SemiBold,
      modifier =
          Modifier.background(background, RoundedCornerShape(6.dp))
              .padding(horizontal = 7.dp, vertical = 2.dp)
              .testTag(tag),
  )
}

/** The text, colors and test tag of a listing badge. */
private data class BadgeStyle(
    val text: String,
    val background: Color,
    val color: Color,
    val tag: String,
)

@Composable
private fun Footer() {
  Text(
      text = stringResource(R.string.inbox_footer),
      color = AuthColors.TextSecondary,
      fontSize = 11.sp,
      lineHeight = 17.sp,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().padding(horizontal = PageMargin, vertical = 24.dp),
  )
}

/** Decodes the downloaded [photo] off the main thread. Null while missing or decoding. */
@Composable
private fun rememberPhoto(photo: ByteArray?): ImageBitmap? {
  val decoded by
      produceState<ImageBitmap?>(initialValue = null, photo) {
        value = photo?.let { decodePhotoPreview(it) }
      }
  return decoded
}

/** A row in each listing status, for the IDE preview. Internal so a test can render it too. */
@Preview(showBackground = true, widthDp = 412, heightDp = 844)
@Composable
internal fun InboxContentPreview() {
  val now = Instant.parse("2026-11-11T12:00:00Z")
  val james = User(uid = "james", email = "", username = "jamesrichard")
  fun row(listingId: String, title: String, status: ListingStatus, minutesAgo: Long) =
      InboxConversation(
          conversation =
              Conversation(
                  listingId = listingId,
                  listingTitle = title,
                  renterUid = "me",
                  lenderUid = james.uid,
                  lastMessageAt = now.minus(Duration.ofMinutes(minutesAgo)),
              ),
          otherUser = james,
          listingTitle = title,
          listingStatus = status,
          listingPhotoPath = null,
      )
  InboxContent(
      state =
          InboxUiState(
              conversations =
                  listOf(
                      row("projector", "Mini projector", ListingStatus.ACTIVE, 5),
                      row("tent", "Weekend camping tent", ListingStatus.PAUSED, 17),
                      row("drill", "Bosch drill set", ListingStatus.UNKNOWN, 23),
                      row("sewing", "Sewing machine", ListingStatus.DELETED, 60 * 24),
                  ),
              isLoading = false,
          ),
      actions = InboxActions(),
      now = now,
  )
}

/** The empty inbox, for the IDE preview. Internal so a test can render it too. */
@Preview(showBackground = true, widthDp = 412, heightDp = 844)
@Composable
internal fun InboxEmptyPreview() {
  InboxContent(state = InboxUiState(isLoading = false), actions = InboxActions())
}
