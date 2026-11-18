// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
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
import com.android.polybazaar.listing.model.PhotoStorage
import com.android.polybazaar.profile.model.ProfileRepository
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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

/** Where a photo being sent is. */
enum class PendingPhotoStatus {
  UPLOADING,
  FAILED,
}

/**
 * A photo the user sent that is not in the conversation yet: still uploading, or failed.
 *
 * @property messageId the ID of its message, kept so a retry reuses what is already on the server.
 * @property photo the photo on the device.
 * @property caption the trimmed caption, or null for a photo alone.
 * @property error why it failed, when [status] is [PendingPhotoStatus.FAILED].
 */
data class PendingPhoto(
    val messageId: String,
    val photo: Uri,
    val caption: String?,
    val status: PendingPhotoStatus,
    val error: AppError? = null,
)

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
 * @property otherUser profile of the other participant, or null while it loads, after a failure, or
 *   when they have no profile.
 * @property draft the message being typed, or the caption of [attachedPhoto] when there is one.
 * @property attachedPhoto the photo picked or taken to send with the draft as its caption, if any.
 * @property canAttachPhoto whether a photo can be attached: only once the conversation exists,
 *   since a photo cannot be its first message, and while the listing is known and not deleted.
 * @property canSend whether the draft, or the attached photo with its caption, can be sent now.
 * @property firstMessageBlockedByPause true when the renter has no conversation yet and the listing
 *   is paused: the rules refuse a first message until it is active again. False while the
 *   conversation loads and after it failed to load, when whether it exists is unknown.
 * @property pendingPhotos the photos sent that are still uploading or failed, oldest first. Their
 *   message may already be in [messages], since it is written before the photo is uploaded.
 * @property messagePhotos the photos of [messages] downloaded so far, by [ChatMessage.imagePath].
 * @property photoLoadErrors why the photo at each path could not be downloaded;
 *   [ConversationViewModel.loadPhoto] tries again.
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
    val otherUser: User? = null,
    val draft: String = "",
    val attachedPhoto: Uri? = null,
    val canAttachPhoto: Boolean = false,
    val canSend: Boolean = false,
    val firstMessageBlockedByPause: Boolean = false,
    val pendingPhotos: List<PendingPhoto> = emptyList(),
    val messagePhotos: Map<String, ByteArray> = emptyMap(),
    val photoLoadErrors: Map<String, AppError> = emptyMap(),
    val isLoading: Boolean = true,
    val loadError: AppError? = null,
    val sendError: AppError? = null,
) {
  /**
   * The [messages] to show: those of [pendingPhotos] are left out, as their pending bubble already
   * shows them, so a photo shows once while it uploads or after it failed.
   */
  val shownMessages: List<ChatMessage> =
      if (pendingPhotos.isEmpty()) messages
      else {
        val pendingIds = pendingPhotos.mapTo(HashSet()) { it.messageId }
        messages.filterNot { it.id in pendingIds }
      }
}

/** The listing as last observed, kept in one reference so other threads never see half of it. */
private data class LoadedListing(
    val listing: Listing? = null,
    val status: ConversationListingStatus? = null,
)

/**
 * Shows and sends the messages between the renter [renterUid] and the owner of the listing
 * [listingId], as seen by [myUid], the signed-in user.
 *
 * [photoStorage] keeps the photos taken with the camera until they are sent; photos picked from the
 * gallery belong to the user and are never deleted.
 *
 * [savedStateHandle] keeps the photo the camera app is taking when Android kills the app, which can
 * happen while the camera app is in front. Pass `createSavedStateHandle()` from the factory: a
 * handle made by hand is not saved by Android.
 *
 * [uploadScope] runs the photo uploads, so that closing the screen does not cancel them: pass
 * [photoUploadScope]. Once the screen is closed, an upload that ends deletes its photo if it was
 * taken with the camera, whether it was sent or not, as nothing can retry it any more.
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
    private val photoStorage: PhotoStorage,
    private val savedStateHandle: SavedStateHandle,
    private val uploadScope: CoroutineScope,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ConversationUiState(myUid = myUid))
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
  // Whether the screen is closed, so an upload that fails afterwards deletes its photo.
  private var cleared = false
  // The photo the camera app is taking, until it closes. Saved, so that it is still attached when
  // the camera app returns to an app that Android killed meanwhile.
  private var photoBeingTaken: Uri?
    get() = savedStateHandle[PHOTO_BEING_TAKEN]
    set(value) {
      savedStateHandle[PHOTO_BEING_TAKEN] = value
    }

  // Photos taken with the camera and not deleted yet, so gallery photos are never deleted.
  private val takenPhotos = mutableSetOf<Uri>()
  // The message photos being downloaded, so that no photo is downloaded twice at once.
  private val photosDownloading = mutableSetOf<String>()

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
   * Attaches [photo], picked from the gallery, replacing the photo attached before. Ignored when a
   * photo cannot be attached; while the screen is still loading, it is attached and can be sent
   * once the conversation is known to allow it.
   */
  fun attachPhoto(photo: Uri) {
    if (!_uiState.value.mayReceivePhoto()) return
    replaceAttachedPhoto(photo)
  }

  /**
   * Prepares a photo for the camera app to take and returns where it should write it, or null when
   * a photo cannot be attached or the file cannot be created. Call [finishTakingPhoto] once the
   * camera app closes.
   */
  fun startTakingPhoto(): Uri? {
    if (!_uiState.value.canAttachPhoto) return null
    // A second tap before the camera app opened replaces the first photo, which stays empty.
    photoBeingTaken?.let(photoStorage::delete)
    photoBeingTaken = null
    val photo = photoStorage.newPhoto() ?: return null
    photoBeingTaken = photo.uri
    return photo.cameraUri
  }

  /**
   * Attaches the photo the camera app took, or deletes its empty file if [saved] is false or a
   * photo can no longer be attached. While the screen is still loading, it is attached as in
   * [attachPhoto].
   */
  fun finishTakingPhoto(saved: Boolean) {
    val photo = photoBeingTaken ?: return
    photoBeingTaken = null
    if (!saved || !_uiState.value.mayReceivePhoto()) {
      photoStorage.delete(photo)
      return
    }
    takenPhotos += photo
    replaceAttachedPhoto(photo)
  }

  /** Removes the attached photo, deleting it if it was taken with the camera. */
  fun removeAttachedPhoto() {
    val photo = _uiState.value.attachedPhoto ?: return
    _uiState.update { it.copy(attachedPhoto = null).derived() }
    deleteIfTaken(photo)
  }

  /**
   * Queues the draft as a message: the first one creates the conversation, later ones reply in it.
   * With a photo attached, sends the photo with the draft as its caption instead. Ignored when it
   * cannot be sent or while the previous text send call is still running.
   */
  fun send() {
    val state = _uiState.value
    // Also for a photo: until the text sent before returns, the draft still holds it, and the photo
    // would send it again as its caption.
    if (!state.canSend || sendInFlight) return
    val photo = state.attachedPhoto
    if (photo != null) {
      sendPhoto(state, photo)
      return
    }
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

  /**
   * Sends the attached [photo] with the draft as its caption. Both are cleared at once, so a second
   * tap on Send finds nothing to send, and the photo shows as uploading until it is sent.
   */
  private fun sendPhoto(state: ConversationUiState, photo: Uri) {
    // canSend only allows a photo once the conversation exists.
    val conversation = state.conversation ?: return
    val pending =
        PendingPhoto(
            messageId = chatRepository.newMessageId(conversation.id),
            photo = photo,
            caption = state.draft.trim().ifEmpty { null },
            status = PendingPhotoStatus.UPLOADING,
        )
    _uiState.update {
      it.copy(draft = "", attachedPhoto = null, pendingPhotos = it.pendingPhotos + pending)
          .derived()
    }
    upload(conversation, pending)
  }

  /**
   * Sends [pending], then removes it once the server has its message and photo, or marks it failed.
   */
  private fun upload(conversation: Conversation, pending: PendingPhoto) {
    uploadScope.launch {
      try {
        chatRepository.sendPhoto(conversation, pending.messageId, pending.photo, pending.caption)
        _uiState.update {
          it.copy(pendingPhotos = it.pendingPhotos.filterNot { p -> p.isSameAs(pending) })
        }
        // Sent: nothing can fail any more, so the taken photo is no longer needed.
        deleteIfTaken(pending.photo)
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        // A refusal from a pause or deletion of the listing also reaches its observation, which
        // then stops new photos from being attached.
        _uiState.update { it.withPendingPhoto(pending.failed(exception.toAppError())) }
        // Closed: no one can retry it, so it is removed as by Remove.
        if (cleared) abandon(pending)
      }
    }
  }

  /**
   * Sends the failed photo of [messageId] again, with the same message ID: a message or photo
   * already on the server is reused, since the rules refuse to write either twice. Ignored unless
   * it failed.
   */
  fun retryPhoto(messageId: String) {
    val state = _uiState.value
    val failed = state.failedPhoto(messageId) ?: return
    val conversation = state.conversation ?: return
    val retried = failed.copy(status = PendingPhotoStatus.UPLOADING, error = null)
    _uiState.update { it.withPendingPhoto(retried) }
    upload(conversation, retried)
  }

  /**
   * Removes the failed photo of [messageId], deleting it if it was taken with the camera, and
   * deletes its message, which may already be on the server with its photo pending, so the other
   * participant no longer sees it. Ignored unless it failed: an upload under way cannot be taken
   * back.
   */
  fun discardPhoto(messageId: String) {
    val failed = _uiState.value.failedPhoto(messageId) ?: return
    _uiState.update {
      it.copy(pendingPhotos = it.pendingPhotos.filterNot { p -> p.isSameAs(failed) })
    }
    abandon(failed)
  }

  /**
   * Downloads the photo of a message at [path], its [ChatMessage.imagePath], unless it is already
   * downloaded or downloading. A message's photo never changes, so it is downloaded once. Ignored
   * while the message's photo is still pending ([ChatMessage.photoPending]): it is not in Storage
   * yet, so call it again once the message says it is uploaded. After a failure, calling it again
   * clears the error and tries again.
   */
  fun loadPhoto(path: String) {
    val state = _uiState.value
    if (state.messages.any { it.imagePath == path && it.photoPending }) return
    if (path in state.messagePhotos || !photosDownloading.add(path)) return
    _uiState.update { it.copy(photoLoadErrors = it.photoLoadErrors - path) }
    viewModelScope.launch {
      try {
        val photo = chatRepository.loadMessagePhoto(path)
        _uiState.update { it.copy(messagePhotos = it.messagePhotos + (path to photo)) }
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        val error = exception.toAppError()
        _uiState.update { it.copy(photoLoadErrors = it.photoLoadErrors + (path to error)) }
      } finally {
        photosDownloading.remove(path)
      }
    }
  }

  fun dismissSendError() {
    _uiState.update { it.copy(sendError = null) }
  }

  /**
   * Deletes the photos taken with the camera that were not sent: the one being taken, the attached
   * one and the failed ones. Gallery photos are left alone. A failed photo can no longer be
   * retried, so it is removed as by [discardPhoto], deleting its message too: it would otherwise
   * stay pending for the other participant. The uploads under way keep running in [uploadScope]
   * and, when they end, delete their photo, and their message too if they failed.
   */
  override fun onCleared() {
    cleared = true
    photoBeingTaken?.let(photoStorage::delete)
    val pending = _uiState.value.pendingPhotos
    pending.filter { it.status == PendingPhotoStatus.FAILED }.forEach(::abandon)
    val uploading =
        pending.filter { it.status == PendingPhotoStatus.UPLOADING }.mapTo(HashSet()) { it.photo }
    takenPhotos.filterNot { it in uploading }.forEach(::deleteIfTaken)
  }

  /**
   * Gives up on [photo], which failed: deletes its message, which may be on the server with its
   * photo pending, and its file if it was taken with the camera.
   */
  private fun abandon(photo: PendingPhoto) {
    chatRepository.deletePhotoMessage(conversationId(listingId, renterUid), photo.messageId)
    deleteIfTaken(photo.photo)
  }

  private fun replaceAttachedPhoto(photo: Uri) {
    val replaced = _uiState.value.attachedPhoto
    _uiState.update { it.copy(attachedPhoto = photo).derived() }
    if (replaced != null && replaced != photo) deleteIfTaken(replaced)
  }

  /**
   * Whether a photo picked or taken can be attached: when it can be, and also while the screen is
   * loading, as when it was just recreated and the result arrives before the conversation. Then
   * [ConversationUiState.canSend] waits for the conversation to allow it.
   */
  private fun ConversationUiState.mayReceivePhoto(): Boolean =
      canAttachPhoto || (isLoading && listingStatus != ConversationListingStatus.DELETED)

  private fun ConversationUiState.failedPhoto(messageId: String): PendingPhoto? =
      pendingPhotos.find {
        it.messageId == messageId && it.status == PendingPhotoStatus.FAILED
      }

  private fun PendingPhoto.isSameAs(other: PendingPhoto) = messageId == other.messageId

  private fun PendingPhoto.failed(error: AppError) =
      copy(status = PendingPhotoStatus.FAILED, error = error)

  /**
   * This state with [photo] in place of the pending photo with its message ID, or added after the
   * others when it was already removed, as after a late rejection.
   */
  private fun ConversationUiState.withPendingPhoto(photo: PendingPhoto): ConversationUiState {
    val photos =
        if (pendingPhotos.any { it.isSameAs(photo) }) {
          pendingPhotos.map { if (it.isSameAs(photo)) photo else it }
        } else {
          pendingPhotos + photo
        }
    return copy(pendingPhotos = photos)
  }

  /** Deletes [photo] if it was taken with the camera; a gallery photo is left alone. */
  private fun deleteIfTaken(photo: Uri) {
    if (takenPhotos.remove(photo)) photoStorage.delete(photo)
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
          .collect { user -> _uiState.update { it.copy(otherUser = user) } }
    }
  }

  private fun updateLoading() {
    val loading = !(listingSettled && conversationSettled && messagesSettled)
    _uiState.update { it.copy(isLoading = loading) }
  }

  /**
   * This state with the fields that derive from the observed listing, the conversation, the draft
   * and the attached photo recomputed.
   */
  private fun ConversationUiState.derived(): ConversationUiState {
    val (listing, status) = loaded
    val listingOpen = status != null && status != ConversationListingStatus.DELETED
    val canAttach = listingOpen && conversation != null
    val sendable =
        if (attachedPhoto != null) {
          // A photo can be sent alone: a blank caption is no caption.
          canAttach && (draft.isBlank() || ChatRules.isSendable(draft))
        } else {
          ChatRules.isSendable(draft) &&
              listingOpen &&
              (conversation != null ||
                  (status == ConversationListingStatus.ACTIVE &&
                      firstConversation(listing) != null))
        }
    return copy(
        listingTitle = listing?.title ?: conversation?.listingTitle,
        listingStatus = status,
        canAttachPhoto = canAttach,
        canSend = sendable,
        firstMessageBlockedByPause =
            status == ConversationListingStatus.PAUSED &&
                myUid == renterUid &&
                conversation == null &&
                conversationKnown,
    )
  }

  private companion object {
    const val PHOTO_BEING_TAKEN = "photoBeingTaken"
  }
}
