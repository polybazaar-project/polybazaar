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
import com.android.polybazaar.profile.model.ProfileRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the listing a conversation is about has become. */
enum class ListingStatus {
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
 * @property listingTitle title for the header: the last loaded listing's, else the one stored in
 *   the conversation. Null until either is known.
 * @property listingStatus the state of the listing, or null until it is known.
 * @property otherUser profile of the other participant, or null while it loads, after a failure, or
 *   when they have no profile.
 * @property draft the message being typed.
 * @property canSend whether the draft can be sent now.
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
    val listingStatus: ListingStatus? = null,
    val otherUser: User? = null,
    val draft: String = "",
    val canSend: Boolean = false,
    val isLoading: Boolean = true,
    val loadError: AppError? = null,
    val sendError: AppError? = null,
)

/** The listing as last loaded, kept in one reference so other threads never see half of it. */
private data class LoadedListing(val listing: Listing? = null, val status: ListingStatus? = null)

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

  private val _uiState = MutableStateFlow(ConversationUiState(myUid = myUid))
  val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

  // Read by canSend, which a write failure can recompute from a Firestore background thread.
  @Volatile private var loaded = LoadedListing()

  // The fields below are only touched on the main thread.
  private var listingLoad: Job? = null
  private var listingSettled = false
  private var listingFailed = false
  private var conversationSettled = false
  private var conversationFailed = false
  private var messagesSettled = false
  private var messagesFailed = false
  private var otherUserUid: String? = null
  private var sendInFlight = false

  init {
    loadListing()
    observeConversation()
    observeMessages()
    observeOtherUser()
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
        val error = exception.toAppError()
        _uiState.update { it.copy(sendError = error) }
        reloadListingAfter(error)
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
    if (listing) loadListing()
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

  /** Runs on any thread and must not throw: Firestore may call it from a background thread. */
  private fun onWriteFailure(text: String, exception: Exception) {
    val error = exception.toAppError()
    _uiState.update { it.copy(sendError = error, draft = restoredDraft(text, it.draft)).derived() }
    reloadListingAfter(error)
  }

  private fun reloadListingAfter(error: AppError) {
    // Usually the listing was paused or deleted since it was loaded: reloading it disables sending.
    if (error == AppError.NOT_FOUND || error == AppError.PERMISSION_DENIED) {
      viewModelScope.launch { loadListing() }
    }
  }

  private fun restoredDraft(text: String, draft: String): String =
      when {
        draft.isBlank() -> text
        // The rejection arrived before the send returned, so the draft still holds the text.
        draft == text -> draft
        else -> text + "\n" + draft
      }

  /**
   * Loads the listing. A load still running is cancelled, so its older answer cannot overwrite the
   * newer one.
   */
  private fun loadListing() {
    listingLoad?.cancel()
    listingFailed = false
    listingLoad = viewModelScope.launch {
      try {
        listingRepository
            .getListing(listingId)
            .onSuccess { listing ->
              val status = if (listing.isPaused) ListingStatus.PAUSED else ListingStatus.ACTIVE
              loaded = LoadedListing(listing, status)
              _uiState.update { it.derived() }
              observeOtherUser()
            }
            .onFailure { exception ->
              if (exception is CancellationException) throw exception
              val error = exception.toAppError()
              if (error == AppError.NOT_FOUND) {
                // The listing loaded before, if any, keeps the header's title.
                loaded = loaded.copy(status = ListingStatus.DELETED)
                _uiState.update { it.derived() }
              } else {
                listingFailed = true
                _uiState.update { it.copy(loadError = error) }
              }
            }
      } finally {
        // A cancelled load was replaced by a newer one, which settles the listing instead.
        if (isActive) {
          listingSettled = true
          updateLoading()
        }
      }
    }
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
            _uiState.update { it.copy(loadError = exception.toAppError()) }
            updateLoading()
          }
          .collect { conversation ->
            conversationSettled = true
            _uiState.update { it.copy(conversation = conversation).derived() }
            updateLoading()
            observeOtherUser()
          }
    }
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
          .collect { user -> _uiState.update { it.copy(otherUser = user) } }
    }
  }

  private fun updateLoading() {
    val loading = !(listingSettled && conversationSettled && messagesSettled)
    _uiState.update { it.copy(isLoading = loading) }
  }

  /** This state with the fields that derive from the loaded listing and the draft recomputed. */
  private fun ConversationUiState.derived(): ConversationUiState {
    val (listing, status) = loaded
    val sendable =
        ChatRules.isSendable(draft) &&
            status != null &&
            status != ListingStatus.DELETED &&
            (conversation != null ||
                (status == ListingStatus.ACTIVE && firstConversation(listing) != null))
    return copy(
        listingTitle = listing?.title ?: conversation?.listingTitle,
        listingStatus = status,
        canSend = sendable,
    )
  }
}
