// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.chat.model.ChatMessage
import com.android.polybazaar.chat.model.ChatRepository
import com.android.polybazaar.chat.model.ChatRules
import com.android.polybazaar.chat.model.Conversation
import com.android.polybazaar.chat.model.conversationId
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.listing.model.ObservedListing
import com.android.polybazaar.profile.model.ProfileRepository
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the listing a conversation is about has become. */
enum class ConversationListingStatus {
  ACTIVE,
  PAUSED,
  DELETED,
}

/**
 * State of the conversation screen.
 *
 * @property myUid uid of the signed-in user, to tell their messages apart.
 * @property messages the messages, oldest first.
 * @property conversation the conversation, or null while it does not exist yet.
 * @property listingTitle title for the header: the listing's, else the one stored in the
 *   conversation. Null until either is known.
 * @property listingStatus the state of the listing, kept up to date while the screen is open, or
 *   null until it is known.
 * @property listing the listing as last observed. Kept after it is deleted, and null when it was
 *   never read.
 * @property listingPhoto the listing's first photo, or null while it is missing, downloading or
 *   failed to download.
 * @property isRenter whether the signed-in user is the renter, so the other participant is the
 *   lender.
 * @property otherUser profile of the other participant, or null while it loads, after a failure, or
 *   when they have no profile.
 * @property otherUserPhoto the other participant's profile photo, or null while it is missing,
 *   downloading or failed to download.
 * @property draft the message being typed.
 * @property canSend whether the draft can be sent now.
 * @property firstMessageBlockedByPause true when the renter has no conversation yet and the listing
 *   is paused: the rules refuse a first message until it is active again. False while the
 *   conversation loads and after it failed to load, when whether it exists is unknown.
 * @property isLoading true until the listing, the conversation and the messages have each been
 *   loaded or failed, and again while a retry reloads any of them.
 * @property loadError why the conversation could not be loaded; the screen offers Retry. What is
 *   already shown is kept.
 * @property sendError why the last message could not be sent; its text is back in the draft.
 */
data class ConversationUiState(
    val myUid: String,
    val messages: List<ChatMessage> = emptyList(),
    val conversation: Conversation? = null,
    val listingTitle: String? = null,
    val listingStatus: ConversationListingStatus? = null,
    val listing: Listing? = null,
    val listingPhoto: ByteArray? = null,
    val isRenter: Boolean = false,
    val otherUser: User? = null,
    val otherUserPhoto: ByteArray? = null,
    val draft: String = "",
    val canSend: Boolean = false,
    val firstMessageBlockedByPause: Boolean = false,
    val isLoading: Boolean = true,
    val loadError: AppError? = null,
    val sendError: AppError? = null,
)

/** The listing as last observed, kept in one reference so other threads never see half of it. */
private data class LoadedListing(
    val listing: Listing? = null,
    val status: ConversationListingStatus? = null,
)

/**
 * Shows and sends the messages between the renter [renterUid] and the owner of the listing
 * [listingId], as seen by [myUid], the signed-in user.
 *
 * The lender must only be routed here for a conversation that already exists: until then the
 * security rules refuse them the conversation and its messages, which shows as a load error.
 */
class ConversationViewModel(
    private val listingId: String,
    private val renterUid: String,
    private val myUid: String,
    private val chatRepository: ChatRepository,
    private val profileRepository: ProfileRepository,
    private val listingRepository: ListingRepository,
) : ViewModel() {

  private val _uiState =
      MutableStateFlow(ConversationUiState(myUid = myUid, isRenter = myUid == renterUid))
  val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

  // Read by derived(), which a write failure can run from a Firestore background thread.
  @Volatile private var loaded = LoadedListing()
  // Whether the conversation's observation has answered, so that a null conversation means none.
  @Volatile private var conversationKnown = false

  // The fields below are only touched on the main thread.
  private var listingJob: Job? = null
  private var listingSettled = false
  private var listingFailed = false
  private var conversationSettled = false
  private var conversationFailed = false
  private var messagesSettled = false
  private var messagesFailed = false
  private var otherUserUid: String? = null
  private var sendInFlight = false
  private var lastMarkedReadAt: Instant? = null
  private var isVisible = false
  private val listingPhoto =
      PhotoDownload(listingRepository::loadListingImage) { state, photo ->
        state.copy(listingPhoto = photo)
      }
  private val otherUserPhoto =
      PhotoDownload(profileRepository::loadProfilePhoto) { state, photo ->
        state.copy(otherUserPhoto = photo)
      }

  init {
    observeListing()
    observeConversation()
    observeMessages()
    observeOtherUser()
  }

  /**
   * Tells whether the screen is started, so the user can see the conversation. Messages are only
   * marked read while it is: one that arrives while the app is in the background stays unread, and
   * is marked read when the screen is started again.
   */
  fun setVisible(visible: Boolean) {
    isVisible = visible
    if (visible) markReadIfUnread(_uiState.value.conversation)
  }

  fun onDraftChange(text: String) {
    _uiState.update { it.copy(draft = text).derived() }
  }

  /**
   * Queues the draft as a message: the first one creates the conversation, later ones reply in it.
   * Ignored when it cannot be sent or while the previous send call is still running.
   */
  fun send() {
    val state = _uiState.value
    if (!state.canSend || sendInFlight) return
    // Before the conversation has loaded, a renter's message is sent as a first one: the merge
    // write becomes an update when the conversation exists, and the rules accept it.
    val target = state.conversation ?: firstConversation() ?: return
    val text = state.draft
    sendInFlight = true
    _uiState.update { it.copy(sendError = null) }
    viewModelScope.launch {
      val rejected = AtomicBoolean(false)
      try {
        chatRepository.sendMessage(target, text) { exception ->
          rejected.set(true)
          onWriteFailure(text, exception)
        }
        // Checked inside update so a rejection racing with this clear cannot lose the text, and
        // only clears the text that was sent: the user may have typed during the call.
        _uiState.update {
          if (!rejected.get() && it.draft == text) it.copy(draft = "").derived() else it
        }
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        _uiState.update { it.copy(sendError = exception.toAppError()) }
      } finally {
        sendInFlight = false
      }
    }
  }

  /**
   * Clears [ConversationUiState.loadError] and restarts whatever failed to load, which is loading
   * again until it answers. What is shown is kept meanwhile.
   */
  fun retry() {
    val listing = listingFailed
    val conversation = conversationFailed
    val messages = messagesFailed
    if (listing) listingSettled = false
    if (conversation) conversationSettled = false
    if (messages) messagesSettled = false
    // Before the restarts: on the main dispatcher a restart can fail synchronously, and clearing
    // loadError after it would hide that new failure.
    _uiState.update { it.copy(loadError = null) }
    updateLoading()
    if (listing) observeListing()
    if (conversation) observeConversation()
    if (messages) observeMessages()
  }

  fun dismissSendError() {
    _uiState.update { it.copy(sendError = null) }
  }

  /**
   * The conversation the first message creates, or null when it cannot be created: the rules only
   * let the renter named in its ID create it. The repository sends the listing's current title in
   * place of this one.
   */
  private fun firstConversation(listing: Listing? = loaded.listing): Conversation? {
    val ownerUid = listing?.ownerUid ?: return null
    if (myUid != renterUid) return null
    return Conversation(listingId, listing.title, renterUid, ownerUid, lastMessageAt = null)
  }

  /**
   * Runs on any thread and must not throw: Firestore may call it from a background thread. When the
   * refusal comes from a pause or deletion of the listing, its observation delivers that change and
   * disables sending.
   */
  private fun onWriteFailure(text: String, exception: Exception) {
    val error = exception.toAppError()
    _uiState.update { it.copy(sendError = error, draft = restoredDraft(text, it.draft)).derived() }
  }

  private fun restoredDraft(text: String, draft: String): String =
      when {
        draft.isBlank() -> text
        // The rejection arrived before the send returned, so the draft still holds the text.
        draft == text -> draft
        else -> text + "\n" + draft
      }

  /** Follows the listing, so that pausing, resuming or deleting it updates the screen. */
  private fun observeListing() {
    listingFailed = false
    // A retry may restart an observation that is still open, waiting for the server.
    listingJob?.cancel()
    listingJob = viewModelScope.launch {
      listingRepository
          .observeListing(listingId)
          // catch never sees a cancellation, so closing the screen is not shown as an error.
          .catch { exception ->
            listingFailed = true
            listingSettled = true
            _uiState.update { it.copy(loadError = exception.toAppError()) }
            updateLoading()
          }
          .collect { observed ->
            when (observed) {
              ObservedListing.NotCached -> onListingNotCached()
              is ObservedListing.Found -> onListingAnswered(observed.listing)
              ObservedListing.Missing -> onListingAnswered(null)
            }
          }
    }
  }

  /**
   * Offline with the listing missing from the cache. A listing already known stays as it is; an
   * unknown one shows the network error, which the server's answer clears when it arrives.
   */
  private fun onListingNotCached() {
    if (loaded.status == null) {
      listingFailed = true
      _uiState.update { it.copy(loadError = AppError.NETWORK) }
    }
    listingSettled = true
    updateLoading()
  }

  /** The listing as it is now, or null when it is deleted or malformed. */
  private fun onListingAnswered(listing: Listing?) {
    loaded =
        when {
          // The listing observed before, if any, keeps the header's title.
          listing == null -> loaded.copy(status = ConversationListingStatus.DELETED)
          listing.isPaused -> LoadedListing(listing, ConversationListingStatus.PAUSED)
          else -> LoadedListing(listing, ConversationListingStatus.ACTIVE)
        }
    listingSettled = true
    // The error stays while the conversation or the messages still need a retry.
    val clearError = listingFailed && !conversationFailed && !messagesFailed
    listingFailed = false
    _uiState.update { (if (clearError) it.copy(loadError = null) else it).derived() }
    updateLoading()
    observeOtherUser()
    listingPhoto.show(loaded.listing?.imagePaths?.firstOrNull())
  }

  private fun observeConversation() {
    conversationFailed = false
    viewModelScope.launch {
      chatRepository
          .observeConversation(conversationId(listingId, renterUid))
          // catch never sees a cancellation, so closing the screen is not shown as an error.
          .catch { exception ->
            conversationFailed = true
            conversationSettled = true
            conversationKnown = false
            _uiState.update { it.copy(loadError = exception.toAppError()).derived() }
            updateLoading()
          }
          .collect { conversation ->
            conversationSettled = true
            conversationKnown = true
            _uiState.update { it.copy(conversation = conversation).derived() }
            updateLoading()
            observeOtherUser()
            markReadIfUnread(conversation)
          }
    }
  }

  /**
   * Marks the open conversation read whenever its latest message is unread for me while the screen
   * is visible, also for messages that arrive while it is open. Only once per lastMessageAt: the
   * conversation can be emitted again before the server confirms the write, and that must not write
   * again.
   */
  private fun markReadIfUnread(conversation: Conversation?) {
    if (!isVisible || conversation == null || !conversation.isUnreadFor(myUid)) return
    val lastMessageAt = conversation.lastMessageAt
    if (lastMessageAt == lastMarkedReadAt) return
    lastMarkedReadAt = lastMessageAt
    chatRepository.markRead(conversation.id)
  }

  private fun observeMessages() {
    messagesFailed = false
    viewModelScope.launch {
      chatRepository
          .observeMessages(conversationId(listingId, renterUid))
          .catch { exception ->
            messagesFailed = true
            messagesSettled = true
            _uiState.update { it.copy(loadError = exception.toAppError()) }
            updateLoading()
          }
          .collect { messages ->
            messagesSettled = true
            _uiState.update { it.copy(messages = messages) }
            updateLoading()
          }
    }
  }

  /**
   * Keeps [ConversationUiState.otherUser] up to date once the other participant is known. Started
   * once, so a failed observation is not retried on every new message.
   */
  private fun observeOtherUser() {
    val uid =
        if (myUid != renterUid) renterUid
        else _uiState.value.conversation?.lenderUid ?: loaded.listing?.ownerUid
    if (uid == null || otherUserUid != null) return
    otherUserUid = uid
    viewModelScope.launch {
      profileRepository
          .observeProfile(uid)
          // The chat works without the profile, so no error is shown; toAppError() still logs
          // unrecognised failures.
          .catch { exception -> exception.toAppError() }
          .collect { user ->
            _uiState.update { it.copy(otherUser = user) }
            otherUserPhoto.show(user?.photoPath)
          }
    }
  }

  private fun updateLoading() {
    val loading = !(listingSettled && conversationSettled && messagesSettled)
    _uiState.update { it.copy(isLoading = loading) }
  }

  /**
   * This state with the fields that derive from the observed listing, the conversation and the
   * draft recomputed.
   */
  private fun ConversationUiState.derived(): ConversationUiState {
    val (listing, status) = loaded
    val sendable =
        ChatRules.isSendable(draft) &&
            status != null &&
            status != ConversationListingStatus.DELETED &&
            (conversation != null ||
                (status == ConversationListingStatus.ACTIVE && firstConversation(listing) != null))
    return copy(
        listingTitle = listing?.title ?: conversation?.listingTitle,
        listingStatus = status,
        listing = listing,
        canSend = sendable,
        firstMessageBlockedByPause =
            status == ConversationListingStatus.PAUSED &&
                myUid == renterUid &&
                conversation == null &&
                conversationKnown,
    )
  }

  /**
   * One photo of the screen, set into the state by [set]. It is downloaded with [download] once per
   * Storage path, and again only when the path changes, which drops the older photo and any
   * download of it still running. A failed download, a stray [CancellationException] included,
   * leaves the photo missing without an error: the screen shows a placeholder. Only used on the
   * main thread.
   */
  private inner class PhotoDownload(
      private val download: suspend (String) -> ByteArray,
      private val set: (ConversationUiState, ByteArray?) -> ConversationUiState,
  ) {
    private var path: String? = null
    private var job: Job? = null

    fun show(newPath: String?) {
      if (newPath == path) return
      path = newPath
      job?.cancel()
      _uiState.update { set(it, null) }
      if (newPath == null) return
      job = viewModelScope.launch {
        attempt { download(newPath) }
            .onSuccess { photo ->
              // A download that ignored its cancellation must not replace a newer photo.
              if (path == newPath) _uiState.update { set(it, photo) }
            }
      }
    }
  }
}
