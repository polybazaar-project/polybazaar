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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.auth.ui.AuthColors
import com.android.polybazaar.auth.ui.AuthTitleGroup
import com.android.polybazaar.chat.model.Conversation
import com.android.polybazaar.chat.viewmodel.InboxConversation
import com.android.polybazaar.chat.viewmodel.InboxUiState
import com.android.polybazaar.chat.viewmodel.InboxViewModel
import com.android.polybazaar.chat.viewmodel.ListingStatus
import com.android.polybazaar.listing.ui.decodePhotoPreview
import java.time.Duration
import java.time.Instant

object InboxScreenTags {
  const val LIST = "inbox_list"
  const val PAUSED_BADGE = "inbox_paused_badge"
  const val DELETED_BADGE = "inbox_deleted_badge"

  /** Tag of the row showing the conversation with [conversationId]. */
  fun row(conversationId: String) = "inbox_row_$conversationId"
}

/** What the user can do on the inbox screen. */
data class InboxActions(
    /** Called with the conversation of the row the user tapped. */
    val onConversationClick: (Conversation) -> Unit = {},
)

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
 * shows them.
 *
 * @param onConversationClick called with the conversation of the row the user tapped.
 */
@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    myUid: String,
    onConversationClick: (Conversation) -> Unit,
) {
  // Keyed on the uid, so the screen loads again only when another user signs in.
  LaunchedEffect(myUid) { viewModel.loadInbox(myUid) }
  val state by viewModel.uiState.collectAsState()
  InboxContent(state = state, actions = InboxActions(onConversationClick = onConversationClick))
}

/**
 * The list of the signed-in user's conversations, as in the Figma frame "Conversations — list".
 *
 * @param state what [InboxViewModel] loaded.
 * @param now the time the rows' "5m"-style labels count from.
 */
@Composable
fun InboxContent(state: InboxUiState, actions: InboxActions, now: Instant = Instant.now()) {
  // One lazy list, so the header, the rows and the footer scroll together.
  LazyColumn(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .testTag(InboxScreenTags.LIST),
      contentPadding = PaddingValues(bottom = 14.dp),
  ) {
    item { Header() }
    item { MessagesHeading() }
    items(state.conversations, key = { it.conversation.id }) { row ->
      ConversationRow(
          row = row,
          photos = state.photos,
          now = now,
          onClick = { actions.onConversationClick(row.conversation) },
      )
    }
    item { Footer() }
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
 * time since the last message.
 */
@Composable
private fun ConversationRow(
    row: InboxConversation,
    photos: Map<String, ByteArray>,
    now: Instant,
    onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(20.dp)
  Row(
      modifier =
          Modifier.padding(start = PageMargin, end = PageMargin, bottom = 10.dp)
              .fillMaxWidth()
              .clip(shape)
              .background(AuthColors.CardBackground)
              .border(1.dp, AuthColors.Outline, shape)
              // Role.Button and the label let TalkBack announce the row as "Open conversation".
              .clickable(
                  role = Role.Button,
                  onClickLabel = stringResource(R.string.inbox_open_conversation),
                  onClick = onClick,
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
