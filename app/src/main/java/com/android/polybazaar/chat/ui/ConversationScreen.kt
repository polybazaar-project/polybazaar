// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import com.android.polybazaar.chat.viewmodel.PendingPhoto
import com.android.polybazaar.chat.viewmodel.PendingPhotoStatus
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.ui.message
import com.android.polybazaar.listing.ui.CameraPermissionDialog
import com.android.polybazaar.listing.ui.decodePhotoPreview
import com.android.polybazaar.listing.ui.loadPhotoPreview
import com.android.polybazaar.listing.ui.rememberPhotoCamera
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers

object ConversationScreenTags {
  const val SCREEN = "conversation_screen"
  const val BACK_BUTTON = "conversation_back_button"
  const val LISTING_TITLE = "conversation_listing_title"
  const val OTHER_USERNAME = "conversation_other_username"
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
  const val PHOTO_BUTTON = "conversation_photo_button"
  const val PHOTO_MENU = "conversation_photo_menu"
  const val TAKE_PHOTO = "conversation_take_photo"
  const val CHOOSE_PHOTO = "conversation_choose_photo"
  const val ATTACHED_PHOTO = "conversation_attached_photo"
  const val REMOVE_ATTACHED_PHOTO = "conversation_remove_attached_photo"

  /** Tag of the bubble of the signed-in user's message with [id]. */
  fun myMessage(id: String) = "conversation_my_message_$id"

  /** Tag of the bubble of the other participant's message with [id]. */
  fun theirMessage(id: String) = "conversation_their_message_$id"

  /** Tag of the photo of the message with [id], or of its placeholder while it loads. */
  fun messagePhoto(id: String) = "conversation_message_photo_$id"

  /** Tag of the placeholder of the message with [id] whose photo failed to load; tap to retry. */
  fun photoLoadError(id: String) = "conversation_photo_load_error_$id"

  /** Tag of the bubble of the photo being sent, or that failed, with message ID [id]. */
  fun pendingPhoto(id: String) = "conversation_pending_photo_$id"

  /** Tag of Retry on the failed photo with message ID [id]. */
  fun retryPhoto(id: String) = "conversation_retry_photo_$id"

  /** Tag of Remove on the failed photo with message ID [id]. */
  fun discardPhoto(id: String) = "conversation_discard_photo_$id"
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

/**
 * Height of a photo in its bubble, the same while it loads, so the list does not jump when it
 * arrives.
 */
private val BubblePhotoHeight = 200.dp

/** Longest side, in pixels, a message photo is decoded at: sharp across a bubble. */
private const val BUBBLE_PHOTO_MAX_SIZE = 1024

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
  val galleryPicker =
      rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.attachPhoto(uri)
      }
  // The ViewModel keeps the photo file in its saved state, so it survives the activity being
  // recreated meanwhile, and Android killing the app while the camera app is in front.
  val camera =
      rememberPhotoCamera(
          startTakingPhoto = { _ -> viewModel.startTakingPhoto() },
          finishTakingPhoto = viewModel::finishTakingPhoto,
      )
  ConversationContent(
      uiState = uiState,
      onBack = onBack,
      onDraftChange = viewModel::onDraftChange,
      onSend = viewModel::send,
      onRetry = viewModel::retry,
      onDismissSendError = viewModel::dismissSendError,
      onTakePhoto = camera::takePhoto,
      onChoosePhoto = {
        galleryPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
      },
      onRemoveAttachedPhoto = viewModel::removeAttachedPhoto,
      onLoadPhoto = viewModel::loadPhoto,
      onRetryPhoto = viewModel::retryPhoto,
      onDiscardPhoto = viewModel::discardPhoto,
  )
  if (camera.showPermissionExplanation) {
    CameraPermissionDialog(onDismiss = camera::dismissPermissionExplanation)
  }
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
 * @param onTakePhoto called when the user picks "Take photo" in the photo menu.
 * @param onChoosePhoto called when the user picks "Choose from gallery" in the photo menu.
 * @param onRemoveAttachedPhoto called when the user taps ✕ on the attached photo.
 * @param onLoadPhoto called with a message's imagePath when its bubble shows and the photo is
 *   neither pending, downloaded nor failed, when a pending photo is marked uploaded, and when the
 *   user taps a photo that failed to load.
 * @param onRetryPhoto called with the message ID of a failed photo when the user taps Retry.
 * @param onDiscardPhoto called with the message ID of a failed photo when the user taps Remove.
 * @param zone time zone of the message times.
 */
@Composable
fun ConversationContent(
    uiState: ConversationUiState,
    onBack: () -> Unit = {},
    onDraftChange: (String) -> Unit = {},
    onSend: () -> Unit = {},
    onRetry: () -> Unit = {},
    onDismissSendError: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onChoosePhoto: () -> Unit = {},
    onRemoveAttachedPhoto: () -> Unit = {},
    onLoadPhoto: (String) -> Unit = {},
    onRetryPhoto: (String) -> Unit = {},
    onDiscardPhoto: (String) -> Unit = {},
    zone: ZoneId = ZoneId.systemDefault(),
) {
  val body = conversationBody(uiState)
  val deleted = uiState.listingStatus == ConversationListingStatus.DELETED
  // Open while loading and after a load error too, so the user can write while waiting;
  // canSend decides when Send works.
  val composerOpen = !deleted
  val placeholder =
      when {
        deleted -> R.string.conversation_placeholder_closed
        uiState.attachedPhoto != null -> R.string.conversation_placeholder_caption
        else -> R.string.conversation_placeholder
      }
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(AuthColors.Background)
              .safeDrawingPadding()
              .testTag(ConversationScreenTags.SCREEN)
  ) {
    Header(listingTitle = uiState.listingTitle, otherUser = uiState.otherUser, onBack = onBack)
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
                onRetry = onRetry,
                photoActions = PhotoActions(onLoadPhoto, onRetryPhoto, onDiscardPhoto),
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
        photo =
            ComposerPhoto(
                attached = uiState.attachedPhoto,
                canAttach = uiState.canAttachPhoto,
                onTake = onTakePhoto,
                onChoose = onChoosePhoto,
                onRemove = onRemoveAttachedPhoto,
            ),
    )
  }
}

/** Back, then the listing's title with the other participant's username under it. */
@Composable
private fun Header(listingTitle: String?, otherUser: User?, onBack: () -> Unit) {
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
        Text(
            text = stringResource(R.string.conversation_other_username, it.username),
            color = AuthColors.TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(ConversationScreenTags.OTHER_USERNAME),
        )
      }
    }
  }
  HorizontalDivider(color = AuthColors.Outline)
}

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
        )
    ConversationListingStatus.DELETED ->
        Notice(
            title = stringResource(R.string.conversation_deleted_title),
            body = stringResource(R.string.conversation_deleted_body),
            tag = ConversationScreenTags.DELETED_NOTICE,
        )
  }
}

@Composable
private fun Notice(title: String, body: String?, tag: String) {
  val shape = RoundedCornerShape(14.dp)
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .padding(start = PageMargin, end = PageMargin, top = 10.dp)
              .background(AuthColors.CardBackground, shape)
              .border(1.dp, AuthColors.Outline, shape)
              .padding(horizontal = 14.dp, vertical = 10.dp)
              // One TalkBack announcement for the title and its explanation.
              .semantics(mergeDescendants = true) {}
              .testTag(tag),
      verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
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
    Icon(
        painter = painterResource(R.drawable.ic_message_circle),
        contentDescription = null,
        tint = AuthColors.Accent,
        modifier = Modifier.size(32.dp),
    )
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
  }
}

/** What the user can do with the photos in the conversation. */
private class PhotoActions(
    val load: (path: String) -> Unit,
    val retry: (messageId: String) -> Unit,
    val discard: (messageId: String) -> Unit,
)

/**
 * The messages, oldest at the top, then the photos being sent, with the load error and Retry above
 * them when a reload failed.
 */
@Composable
private fun MessageList(
    uiState: ConversationUiState,
    zone: ZoneId,
    onRetry: () -> Unit,
    photoActions: PhotoActions,
) {
  val messages = uiState.shownMessages
  val pendingPhotos = uiState.pendingPhotos
  val listState = rememberLazyListState()
  // The list keeps its place by key, so a new message or photo would arrive below the view without
  // this.
  LaunchedEffect(messages.lastOrNull()?.id, pendingPhotos.lastOrNull()?.messageId) {
    listState.scrollToItem(0)
  }
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
      // Prefixed: once sent, a photo's message arrives with the same ID.
      items(pendingPhotos.asReversed(), key = { "pending_${it.messageId}" }) { pending ->
        PendingPhotoBubble(
            pending = pending,
            onRetry = { photoActions.retry(pending.messageId) },
            onDiscard = { photoActions.discard(pending.messageId) },
        )
      }
      items(messages.asReversed(), key = { it.id }) { message ->
        MessageBubble(
            message = message,
            isMine = message.senderUid == uiState.myUid,
            otherUsername = uiState.otherUser?.username,
            zone = zone,
            photo =
                message.imagePath?.let { path ->
                  MessagePhoto(
                      bytes = uiState.messagePhotos[path],
                      failed = path in uiState.photoLoadErrors,
                      pending = message.photoPending,
                      load = { photoActions.load(path) },
                  )
                },
        )
      }
    }
  }
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
 * The photo of a message: its downloaded [bytes], or null until they arrive; [failed] when the
 * download failed; [pending] while its sender is still uploading it, so it can't be downloaded yet.
 * [load] asks for it.
 */
private class MessagePhoto(
    val bytes: ByteArray?,
    val failed: Boolean,
    val pending: Boolean,
    val load: () -> Unit,
)

/**
 * One message: the user's own at the end of the row, the other participant's at the start. A photo
 * message shows its photo, then its caption if any. The footer says who sent it and when, or only
 * when while the other participant's profile is unknown.
 */
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    otherUsername: String?,
    zone: ZoneId,
    photo: MessagePhoto?,
) {
  val time = message.sentAt.atZone(zone).format(TimeFormat)
  val footer =
      when {
        isMine -> stringResource(R.string.conversation_my_message_footer, time)
        otherUsername != null ->
            stringResource(R.string.conversation_their_message_footer, otherUsername, time)
        else -> time
      }
  Bubble(
      isMine = isMine,
      tag =
          if (isMine) ConversationScreenTags.myMessage(message.id)
          else ConversationScreenTags.theirMessage(message.id),
      // A photo bubble takes its full width at once, so it does not grow when the photo arrives.
      fixedWidth = photo != null,
  ) {
    photo?.let { BubblePhoto(messageId = message.id, photo = it) }
    // A photo alone has no text.
    if (message.text.isNotEmpty()) {
      Text(
          text = message.text,
          color = AuthColors.TextPrimary,
          fontSize = 14.sp,
          lineHeight = 19.sp,
      )
    }
    BubbleFooter(text = footer)
  }
}

/**
 * A message bubble: the user's own at the end of the row, the other participant's at the start. One
 * TalkBack item: its photo, its text, then who sent it and when.
 *
 * @param fixedWidth whether the bubble is always [MaxBubbleWidth] wide, as for a photo, rather than
 *   as wide as its text.
 */
@Composable
private fun Bubble(
    isMine: Boolean,
    tag: String,
    fixedWidth: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(16.dp)
  Box(
      modifier = Modifier.fillMaxWidth(),
      contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart,
  ) {
    Column(
        modifier =
            (if (fixedWidth) Modifier.width(MaxBubbleWidth)
                else Modifier.widthIn(max = MaxBubbleWidth))
                .semantics(mergeDescendants = true) {}
                .testTag(tag)
                .background(
                    if (isMine) AuthColors.AccentContainer else AuthColors.CardBackground,
                    shape,
                )
                // Mine get a border in their own fill colour, so both kinds keep the same size.
                .border(1.dp, if (isMine) AuthColors.AccentContainer else AuthColors.Outline, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      content()
    }
  }
}

@Composable
private fun ColumnScope.BubbleFooter(text: String) {
  Text(
      text = text,
      color = AuthColors.TextSecondary,
      fontSize = 11.sp,
      modifier = Modifier.align(Alignment.End),
  )
}

/** A message photo after it is decoded, or why it is not shown. */
private sealed interface DecodedPhoto {
  data object Loading : DecodedPhoto

  data object Failed : DecodedPhoto

  class Shown(val bitmap: ImageBitmap) : DecodedPhoto
}

/**
 * The photo of a message, asked for when the bubble first shows, or once its sender has uploaded
 * it: a placeholder of the same size while it uploads, downloads and decodes, and one that offers
 * to try again when it could not be loaded.
 */
@Composable
private fun BubblePhoto(messageId: String, photo: MessagePhoto) {
  // Not asked again on its own after a failure: only when the user taps to retry.
  LaunchedEffect(messageId, photo.pending) {
    if (!photo.pending && photo.bytes == null && !photo.failed) photo.load()
  }
  val decoded by
      produceState<DecodedPhoto>(initialValue = DecodedPhoto.Loading, photo.bytes) {
        val bytes = photo.bytes
        value =
            if (bytes == null) DecodedPhoto.Loading
            else
                decodePhotoPreview(bytes, maxSize = BUBBLE_PHOTO_MAX_SIZE)?.let {
                  DecodedPhoto.Shown(it)
                } ?: DecodedPhoto.Failed
      }
  val description = stringResource(R.string.conversation_photo)
  val shape = RoundedCornerShape(10.dp)
  val current = decoded
  val downloadFailed = photo.failed && current !is DecodedPhoto.Shown
  // One tag per state: of two test tags on a node, only the first counts.
  val tag =
      if (downloadFailed) ConversationScreenTags.photoLoadError(messageId)
      else ConversationScreenTags.messagePhoto(messageId)
  val modifier = Modifier.fillMaxWidth().height(BubblePhotoHeight).clip(shape).testTag(tag)
  when {
    current is DecodedPhoto.Shown ->
        Image(
            bitmap = current.bitmap,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    // Undecodable bytes would fail again, so only a failed download offers to retry.
    photo.failed || current is DecodedPhoto.Failed ->
        PhotoLoadError(onRetry = photo.load.takeIf { photo.failed }, modifier = modifier)
    else ->
        Box(
            modifier =
                modifier.background(AuthColors.Outline).semantics {
                  contentDescription = description
                },
            contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(
              color = AuthColors.TextSecondary,
              strokeWidth = 2.dp,
              modifier = Modifier.size(20.dp),
          )
        }
  }
}

/** A photo that could not be loaded, with "tap to retry" when [onRetry] is given. */
@Composable
private fun PhotoLoadError(onRetry: (() -> Unit)?, modifier: Modifier) {
  Column(
      modifier =
          modifier
              .background(AuthColors.Outline)
              .then(if (onRetry != null) Modifier.clickable(onClick = onRetry) else Modifier)
              .padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
  ) {
    // Decorative: the text under it says what happened.
    Icon(
        painter = painterResource(R.drawable.ic_image),
        contentDescription = null,
        tint = AuthColors.TextSecondary,
        modifier = Modifier.size(20.dp),
    )
    Text(
        text =
            stringResource(
                if (onRetry != null) R.string.conversation_photo_load_failed_retry
                else R.string.conversation_photo_load_failed
            ),
        color = AuthColors.TextSecondary,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
    )
  }
}

/**
 * A photo the user sent that is not in the conversation yet, as their own bubble: faded with a
 * spinner while it uploads; faded with why it failed, Retry and Remove once it failed.
 */
@Composable
private fun PendingPhotoBubble(pending: PendingPhoto, onRetry: () -> Unit, onDiscard: () -> Unit) {
  val description = stringResource(R.string.conversation_photo)
  val uploading = pending.status == PendingPhotoStatus.UPLOADING
  Bubble(
      isMine = true,
      tag = ConversationScreenTags.pendingPhoto(pending.messageId),
      fixedWidth = true,
  ) {
    Box(contentAlignment = Alignment.Center) {
      // The placeholder shows when the photo on the device can no longer be read.
      PhotoOrPlaceholder(
          photo = rememberLocalPhoto(pending.photo),
          description = description,
          modifier =
              Modifier.fillMaxWidth()
                  .height(BubblePhotoHeight)
                  .clip(RoundedCornerShape(10.dp))
                  .alpha(0.6f),
      )
      if (uploading) {
        CircularProgressIndicator(
            color = AuthColors.CardBackground,
            strokeWidth = 2.dp,
            modifier = Modifier.size(24.dp),
        )
      }
    }
    pending.caption?.let {
      Text(text = it, color = AuthColors.TextPrimary, fontSize = 14.sp, lineHeight = 19.sp)
    }
    if (uploading) {
      BubbleFooter(text = stringResource(R.string.conversation_photo_sending))
    } else {
      Text(
          text =
              stringResource(
                  R.string.conversation_photo_not_sent,
                  (pending.error ?: AppError.UNKNOWN).message(),
              ),
          color = AuthColors.Danger,
          fontSize = 12.sp,
          // Announced when it fails: TalkBack's focus is elsewhere, on Send or the field.
          modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
      )
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
            onClick = onRetry,
            modifier = Modifier.testTag(ConversationScreenTags.retryPhoto(pending.messageId)),
        ) {
          Text(
              text = stringResource(R.string.conversation_retry),
              color = AuthColors.Accent,
              fontWeight = FontWeight.SemiBold,
          )
        }
        TextButton(
            onClick = onDiscard,
            modifier = Modifier.testTag(ConversationScreenTags.discardPhoto(pending.messageId)),
        ) {
          Text(
              text = stringResource(R.string.conversation_photo_remove),
              color = AuthColors.TextSecondary,
          )
        }
      }
    }
  }
}

/**
 * The composer's photo: the one attached, whether one can be attached, and what the user does with
 * it.
 */
private class ComposerPhoto(
    val attached: Uri?,
    val canAttach: Boolean,
    val onTake: () -> Unit,
    val onChoose: () -> Unit,
    val onRemove: () -> Unit,
)

/**
 * The photo button, the message field and Send, with the attached photo above them, and under them
 * why the last send failed and, once the draft is too long, a counter that explains why Send is
 * disabled. The text is kept as typed.
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
    photo: ComposerPhoto,
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
    photo.attached?.let { AttachedPhoto(photo = it, onRemove = photo.onRemove) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
      PhotoButton(enabled = photo.canAttach, onTake = photo.onTake, onChoose = photo.onChoose)
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

/** The camera button left of the field, opening a menu to take a photo or choose one. */
@Composable
private fun PhotoButton(enabled: Boolean, onTake: () -> Unit, onChoose: () -> Unit) {
  var menuOpen by remember { mutableStateOf(false) }
  val shape = RoundedCornerShape(14.dp)
  Box {
    IconButton(
        onClick = { menuOpen = true },
        enabled = enabled,
        modifier =
            Modifier.size(48.dp)
                .border(1.dp, AuthColors.Outline, shape)
                .testTag(ConversationScreenTags.PHOTO_BUTTON),
    ) {
      Icon(
          painter = painterResource(R.drawable.ic_camera),
          contentDescription = stringResource(R.string.conversation_photo_button),
          tint = if (enabled) AuthColors.TextPrimary else AuthColors.TextSecondary,
          modifier = Modifier.size(20.dp),
      )
    }
    DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = { menuOpen = false },
        containerColor = AuthColors.CardBackground,
        modifier = Modifier.testTag(ConversationScreenTags.PHOTO_MENU),
    ) {
      PhotoMenuItem(
          text = stringResource(R.string.conversation_take_photo),
          icon = R.drawable.ic_camera,
          tag = ConversationScreenTags.TAKE_PHOTO,
          onClick = {
            menuOpen = false
            onTake()
          },
      )
      PhotoMenuItem(
          text = stringResource(R.string.conversation_choose_photo),
          icon = R.drawable.ic_image,
          tag = ConversationScreenTags.CHOOSE_PHOTO,
          onClick = {
            menuOpen = false
            onChoose()
          },
      )
    }
  }
}

@Composable
private fun PhotoMenuItem(text: String, icon: Int, tag: String, onClick: () -> Unit) {
  DropdownMenuItem(
      text = { Text(text = text, color = AuthColors.TextPrimary, fontSize = 14.sp) },
      // Decorative: the text says what the item does.
      leadingIcon = {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = AuthColors.TextPrimary,
            modifier = Modifier.size(20.dp),
        )
      },
      onClick = onClick,
      modifier = Modifier.testTag(tag),
  )
}

/** The photo attached to the draft, as a thumbnail with ✕ in its corner to remove it. */
@Composable
private fun AttachedPhoto(photo: Uri, onRemove: () -> Unit) {
  val shape = RoundedCornerShape(12.dp)
  val description = stringResource(R.string.conversation_attached_photo)
  // The thumbnail with room around it for ✕, which overlaps its top-right corner.
  Box(modifier = Modifier.size(80.dp)) {
    PhotoOrPlaceholder(
        photo = rememberLocalPhoto(photo),
        description = description,
        modifier =
            Modifier.align(Alignment.BottomStart)
                .size(64.dp)
                .clip(shape)
                .testTag(ConversationScreenTags.ATTACHED_PHOTO),
    )
    IconButton(
        onClick = onRemove,
        modifier =
            Modifier.align(Alignment.TopEnd)
                .size(32.dp)
                .testTag(ConversationScreenTags.REMOVE_ATTACHED_PHOTO),
    ) {
      Box(
          modifier =
              Modifier.size(20.dp)
                  .background(AuthColors.TextPrimary.copy(alpha = 0.7f), CircleShape),
          contentAlignment = Alignment.Center,
      ) {
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = stringResource(R.string.conversation_remove_attached_photo),
            tint = AuthColors.CardBackground,
            modifier = Modifier.size(12.dp),
        )
      }
    }
  }
}

/** [photo] filling its bounds, or a plain placeholder of the same size until it is decoded. */
@Composable
private fun PhotoOrPlaceholder(photo: ImageBitmap?, description: String, modifier: Modifier) {
  if (photo != null) {
    Image(
        bitmap = photo,
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
  } else {
    Box(
        modifier =
            modifier.background(AuthColors.Outline).semantics { contentDescription = description }
    )
  }
}

/**
 * A photo on the device, decoded off the main thread and scaled down, or null while it decodes or
 * when it cannot be read.
 */
@Composable
private fun rememberLocalPhoto(photo: Uri): ImageBitmap? {
  val contentResolver = LocalContext.current.contentResolver
  val bitmap by
      produceState<ImageBitmap?>(initialValue = null, photo) {
        value = loadPhotoPreview(contentResolver, photo)
      }
  return bitmap
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

/** A paused listing with a few messages, for the IDE preview. Internal so a test can render it. */
@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
internal fun ConversationContentPreview() {
  val start = Instant.parse("2026-05-04T09:12:00Z")
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
                      ChatMessage("3", "renter", "Perfect, see you then.", start.plusSeconds(300)),
                  ),
              conversation =
                  Conversation(
                      listingId = "drill",
                      listingTitle = "Bosch drill set",
                      renterUid = "renter",
                      lenderUid = "lender",
                      lastMessageAt = start.plusSeconds(300),
                  ),
              listingTitle = "Bosch drill set",
              listingStatus = ConversationListingStatus.PAUSED,
              otherUser = User(uid = "lender", email = "", username = "james"),
              isLoading = false,
          ),
      zone = ZoneOffset.UTC,
  )
}

/** A photo from the other participant still loading, and one of mine that failed to send. */
@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
internal fun ConversationPhotosPreview() {
  val start = Instant.parse("2026-05-04T09:12:00Z")
  ConversationContent(
      uiState =
          ConversationUiState(
              myUid = "renter",
              messages =
                  listOf(
                      ChatMessage("1", "renter", "Could you send a photo of the drill?", start),
                      ChatMessage(
                          "2",
                          "lender",
                          "Here it is, with both batteries.",
                          start.plusSeconds(120),
                          imagePath = "conversations/drill_renter/2.jpg",
                      ),
                  ),
              conversation =
                  Conversation(
                      listingId = "drill",
                      listingTitle = "Bosch drill set",
                      renterUid = "renter",
                      lenderUid = "lender",
                      lastMessageAt = start.plusSeconds(120),
                  ),
              listingTitle = "Bosch drill set",
              listingStatus = ConversationListingStatus.ACTIVE,
              otherUser = User(uid = "lender", email = "", username = "james"),
              canAttachPhoto = true,
              pendingPhotos =
                  listOf(
                      PendingPhoto(
                          messageId = "3",
                          photo = Uri.parse("file:///preview/photo_3.jpg"),
                          caption = "And the shelf I mean",
                          status = PendingPhotoStatus.FAILED,
                          error = AppError.NETWORK,
                      )
                  ),
              isLoading = false,
          ),
      zone = ZoneOffset.UTC,
  )
}
