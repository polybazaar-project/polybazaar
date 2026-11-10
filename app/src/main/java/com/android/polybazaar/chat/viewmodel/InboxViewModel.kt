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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
 */
data class InboxConversation(
    val conversation: Conversation,
    val otherUser: User?,
    val listingTitle: String,
    val listingStatus: ListingStatus,
)

/** State of the inbox screen. */
data class InboxUiState(
    /** The signed-in user's conversations, most recent message first. */
    val conversations: List<InboxConversation> = emptyList(),
    /** True until the first list of conversations arrives or loading fails. */
    val isLoading: Boolean = true,
    /** Why the conversations could not be loaded; the rows already shown are kept. */
    val error: AppError? = null,
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

  /**
   * Loads the conversations of [myUid], the signed-in user, and keeps them updated. Calling it
   * again drops the previous load and starts over, which is how the screen retries after an error.
   * Each person's profile is looked up once per load, so a profile change shows on the next load.
   */
  fun loadInbox(myUid: String) {
    loadJob?.cancel()
    _uiState.value = InboxUiState()
    loadJob = viewModelScope.launch { collectConversations(myUid) }
  }

  /**
   * Collects the conversations of [myUid]. Profiles found during this load are kept, keyed by uid,
   * so a later emission only looks up the people it has not seen yet.
   */
  private suspend fun collectConversations(myUid: String) {
    val profiles = mutableMapOf<String, User?>()
    try {
      chatRepository.observeMyConversations().collect { conversations ->
        val rows = loadRows(myUid, conversations, profiles)
        _uiState.update { it.copy(conversations = rows, isLoading = false) }
      }
    } catch (exception: Exception) {
      if (exception is CancellationException) throw exception
      _uiState.update { it.copy(isLoading = false, error = exception.toAppError()) }
    }
  }

  /**
   * Builds the rows for [conversations], looking up each listing and each other person not yet in
   * [profiles] once, all in parallel. The profiles found are added to [profiles].
   */
  private suspend fun loadRows(
      myUid: String,
      conversations: List<Conversation>,
      profiles: MutableMap<String, User?>,
  ): List<InboxConversation> = coroutineScope {
    val otherUids = conversations.map { it.otherUid(myUid) }.distinct()
    val newProfiles = async { loadProfiles(otherUids.filterNot { it in profiles }) }
    val listings = async { loadListings(conversations.map { it.listingId }.distinct()) }
    profiles += newProfiles.await()
    conversations.map { conversation ->
      val listing = listings.await().getValue(conversation.listingId)
      InboxConversation(
          conversation = conversation,
          otherUser = profiles[conversation.otherUid(myUid)],
          listingTitle = listing.listing?.title ?: conversation.listingTitle,
          listingStatus = listing.status,
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

  private suspend fun loadProfile(uid: String): Result<User?> =
      try {
        Result.success(profileRepository.observeProfile(uid).first())
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        // Not shown to the user, but an unexpected failure is logged, to explain a missing name.
        exception.toAppError()
        Result.failure(exception)
      }

  /** Reads each of [listingIds] once, in parallel. */
  private suspend fun loadListings(listingIds: List<String>): Map<String, ListingLookup> =
      coroutineScope {
        listingIds.map { id -> async { id to loadListing(id) } }.awaitAll().toMap()
      }

  /**
   * Reads one listing. A failure never becomes the screen's error: `NOT_FOUND` means the listing
   * was deleted, anything else leaves its state unknown.
   */
  private suspend fun loadListing(listingId: String): ListingLookup {
    val result =
        try {
          listingRepository.getListing(listingId)
        } catch (exception: Exception) {
          Result.failure(exception)
        }
    val failure = result.exceptionOrNull()
    if (failure is CancellationException) throw failure
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

/** A listing as read for the inbox: [listing] is null when it could not be read. */
private data class ListingLookup(val listing: Listing?, val status: ListingStatus)

/** The participant who is not [myUid]: the lender when [myUid] is the renter, else the renter. */
private fun Conversation.otherUid(myUid: String): String =
    if (renterUid == myUid) lenderUid else renterUid
