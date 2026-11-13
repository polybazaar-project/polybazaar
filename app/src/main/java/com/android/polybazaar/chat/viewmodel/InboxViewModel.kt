// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.chat.model.ChatRepository
import com.android.polybazaar.chat.model.Conversation
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.profile.model.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the inbox knows about a conversation's listing. */
enum class ListingStatus {
  /** The listing exists and is shown on the marketplace. */
  ACTIVE,
  /** The listing exists but its owner paused it. */
  PAUSED,
  /** The listing was deleted: reading it failed with `NOT_FOUND`. */
  DELETED,
  /** The listing could not be read for another reason, such as being offline. */
  UNKNOWN,
}

/**
 * One row of the inbox.
 *
 * @property conversation the conversation, as the repository emitted it.
 * @property otherUser profile of the other participant, null while unknown or when they have no
 *   profile.
 * @property listingTitle the listing's current title, or the title stored on the conversation when
 *   the listing could not be read.
 * @property listingStatus whether the listing is active, paused, deleted or could not be read.
 * @property listingPhotoPath Storage path of the listing's first photo, null when it has none or
 *   could not be read. The other person's photo path is [User.photoPath] of [otherUser].
 * @property isUnread whether the last message is one the signed-in user has not read yet.
 * @property isLastMessageMine whether the signed-in user sent the last message, so its preview
 *   starts with "You: ".
 */
data class InboxConversation(
    val conversation: Conversation,
    val otherUser: User?,
    val listingTitle: String,
    val listingStatus: ListingStatus,
    val listingPhotoPath: String?,
    val isUnread: Boolean = false,
    val isLastMessageMine: Boolean = false,
)

/** State of the inbox screen. */
data class InboxUiState(
    /** The signed-in user's conversations, most recent message first. */
    val conversations: List<InboxConversation> = emptyList(),
    /** True until the first list of conversations arrives or loading fails. */
    val isLoading: Boolean = true,
    /** Why the conversations could not be loaded; the rows already shown are kept. */
    val error: AppError? = null,
    /**
     * The photos downloaded so far, listing and profile photos alike, keyed by Storage path. A
     * photo still downloading or that failed to download is missing. They last for one load: a
     * changed photo keeps its Storage path, so each load downloads them again, failed ones
     * included. Only the photos the current rows use are kept.
     */
    val photos: Map<String, ByteArray> = emptyMap(),
)

/** Lists the signed-in user's conversations for the inbox, with each one's listing state. */
class InboxViewModel(
    private val chatRepository: ChatRepository,
    private val profileRepository: ProfileRepository,
    private val listingRepository: ListingRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(InboxUiState())
  val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null
  private var loadedUid: String? = null

  /**
   * Loads the conversations of [myUid] when the screen appears, unless a load for [myUid] is still
   * running without error. Coming back to the tab then keeps the rows and photos instead of loading
   * them again. After an error, or for another user, it loads again like [loadInbox].
   */
  fun openInbox(myUid: String) {
    val running = loadJob?.isActive == true && myUid == loadedUid && _uiState.value.error == null
    if (!running) loadInbox(myUid)
  }

  /**
   * Loads the conversations of [myUid], the signed-in user, and keeps them updated. Calling it
   * again drops the previous load and starts over, which is how the screen retries after an error.
   * Each person's profile and each listing are looked up once per load, so a change to them, such
   * as a listing being paused, shows on the next load. The photos are downloaded again too, since a
   * changed photo keeps its Storage path; within one load, each photo is downloaded once.
   */
  fun loadInbox(myUid: String) {
    loadJob?.cancel()
    loadedUid = myUid
    _uiState.value = InboxUiState()
    loadJob = viewModelScope.launch { collectConversations(myUid) }
  }

  /**
   * Shows each emission's rows as soon as they are built, dropping the photos no row uses any more,
   * then downloads their missing photos in the background. A download runs until it ends even if
   * the flow fails, so the rows kept on error still get their photos. Profiles and listings found
   * during this load are kept, so a later emission only looks up the people and listings it has not
   * seen yet.
   */
  private suspend fun collectConversations(myUid: String) = coroutineScope {
    val profiles = mutableMapOf<String, User?>()
    val listings = mutableMapOf<String, ListingLookup>()
    // Paths downloading or failed in this load, so a later emission does not start them again.
    val downloadingOrFailed = mutableSetOf<String>()
    try {
      chatRepository.observeMyConversations().collect { conversations ->
        val rows = loadRows(myUid, conversations, profiles, listings)
        val used =
            rows.flatMap { listOfNotNull(it.listingPhotoPath, it.otherUser?.photoPath) }.toSet()
        // In the same update as the rows, so a row never shows without a photo already downloaded.
        _uiState.update {
          it.copy(
              conversations = rows,
              isLoading = false,
              photos = it.photos.filterKeys { path -> path in used },
          )
        }
        downloadPhotos(rows, downloadingOrFailed)
      }
    } catch (exception: Exception) {
      if (exception is CancellationException) throw exception
      _uiState.update { it.copy(isLoading = false, error = exception.toAppError()) }
    }
  }

  /**
   * Starts downloading, in parallel, each photo of [rows] not downloaded yet nor in
   * [downloadingOrFailed].
   */
  private fun CoroutineScope.downloadPhotos(
      rows: List<InboxConversation>,
      downloadingOrFailed: MutableSet<String>,
  ) {
    rows.forEach { row ->
      row.listingPhotoPath?.let { path ->
        downloadPhoto(path, downloadingOrFailed) { listingRepository.loadListingImage(path) }
      }
      row.otherUser?.photoPath?.let { path ->
        downloadPhoto(path, downloadingOrFailed) { profileRepository.loadProfilePhoto(path) }
      }
    }
  }

  /**
   * Downloads the photo at [path] with [download] and adds it to [InboxUiState.photos]. A failure,
   * a stray [CancellationException] included, leaves it missing, without an error, and keeps [path]
   * in [downloadingOrFailed], so it is not tried again before the next load.
   */
  private fun CoroutineScope.downloadPhoto(
      path: String,
      downloadingOrFailed: MutableSet<String>,
      download: suspend () -> ByteArray,
  ) {
    if (path in _uiState.value.photos || !downloadingOrFailed.add(path)) return
    launch {
      attempt(download).onSuccess { photo ->
        _uiState.update { it.copy(photos = it.photos + (path to photo)) }
        // Downloaded again if a later row needs it after it was dropped.
        downloadingOrFailed -= path
      }
    }
  }

  /**
   * Builds the rows for [conversations], looking up each other person not yet in [profiles] and
   * each listing not yet in [listings] once, all in parallel. What is found is added to [profiles]
   * and [listings]; a listing that could not be read is not, so the next emission reads it again.
   */
  private suspend fun loadRows(
      myUid: String,
      conversations: List<Conversation>,
      profiles: MutableMap<String, User?>,
      listings: MutableMap<String, ListingLookup>,
  ): List<InboxConversation> = coroutineScope {
    val otherUids = conversations.map { it.otherUid(myUid) }.distinct()
    val newProfiles = async { loadProfiles(otherUids.filterNot { it in profiles }) }
    val listingIds = conversations.map { it.listingId }.distinct()
    val newListings = async { loadListings(listingIds.filterNot { it in listings }) }
    profiles += newProfiles.await()
    val found = newListings.await()
    val lookups = listings + found
    listings += found.filterValues { it.status != ListingStatus.UNKNOWN }
    conversations.map { conversation ->
      val listing = lookups.getValue(conversation.listingId)
      InboxConversation(
          conversation = conversation,
          otherUser = profiles[conversation.otherUid(myUid)],
          listingTitle = listing.listing?.title ?: conversation.listingTitle,
          listingStatus = listing.status,
          listingPhotoPath = listing.listing?.imagePaths?.firstOrNull(),
          isUnread = conversation.isUnreadFor(myUid),
          isLastMessageMine = conversation.lastMessageSenderUid == myUid,
      )
    }
  }

  /**
   * Looks up each of [uids] once, in parallel, and returns the lookups that succeeded, with null
   * for a missing profile. A failed lookup is left out, so the next emission tries it again, and it
   * never hides the conversations.
   */
  private suspend fun loadProfiles(uids: List<String>): Map<String, User?> = coroutineScope {
    uids
        .map { uid -> async { uid to loadProfile(uid) } }
        .awaitAll()
        .filter { (_, result) -> result.isSuccess }
        .associate { (uid, result) -> uid to result.getOrNull() }
  }

  private suspend fun loadProfile(uid: String): Result<User?> = attempt {
    profileRepository.observeProfile(uid).first()
  }
      // Not shown to the user, but an unexpected failure is logged, to explain a missing name.
      .onFailure { it.toAppError() }

  /** Reads each of [listingIds] once, in parallel. */
  private suspend fun loadListings(listingIds: List<String>): Map<String, ListingLookup> =
      coroutineScope {
        listingIds.map { id -> async { id to loadListing(id) } }.awaitAll().toMap()
      }

  /**
   * Reads one listing. A failure never becomes the screen's error: `NOT_FOUND` means the listing
   * was deleted, anything else, a stray [CancellationException] included, leaves its state unknown.
   */
  private suspend fun loadListing(listingId: String): ListingLookup {
    val result = attempt { listingRepository.getListing(listingId).getOrThrow() }
    val failure = result.exceptionOrNull()
    if (failure == null) {
      val listing = result.getOrThrow()
      return ListingLookup(
          listing,
          if (listing.isPaused) ListingStatus.PAUSED else ListingStatus.ACTIVE,
      )
    }
    val status =
        if (failure.toAppError() == AppError.NOT_FOUND) ListingStatus.DELETED
        else ListingStatus.UNKNOWN
    return ListingLookup(null, status)
  }
}

/**
 * Runs [block] and returns its result, or its failure. A [CancellationException] is rethrown only
 * when the calling coroutine is itself cancelled, so loading again still drops the old load. One
 * thrown by a call that was cancelled on its own, such as a cancelled Firebase task, is returned as
 * a failure like any other, so it cannot leave the inbox loading forever.
 */
private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
    try {
      Result.success(block())
    } catch (exception: Exception) {
      if (exception is CancellationException) currentCoroutineContext().ensureActive()
      Result.failure(exception)
    }

/** A listing as read for the inbox: [listing] is null when it could not be read. */
private data class ListingLookup(val listing: Listing?, val status: ListingStatus)

/** The participant who is not [myUid]: the lender when [myUid] is the renter, else the renter. */
private fun Conversation.otherUid(myUid: String): String =
    if (renterUid == myUid) lenderUid else renterUid
