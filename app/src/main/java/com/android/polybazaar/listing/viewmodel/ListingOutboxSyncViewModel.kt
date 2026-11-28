// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.booking.model.upcomingDays
import com.android.polybazaar.connectivity.model.ConnectivityRepository
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.listing.model.PendingListing
import com.android.polybazaar.listing.model.PendingListingChange
import com.android.polybazaar.listing.model.PendingListingQueue
import com.android.polybazaar.listing.model.ServiceListing
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The outcome of a sync pass that tried at least one draft or change, to tell the user.
 *
 * @property published drafts published; [failed] drafts kept to try again.
 * @property changesSaved changes saved; [changesFailed] changes kept to try again.
 * @property changesDropped changes forgotten because their listing was deleted elsewhere.
 * @property changesConflicted edits forgotten because their listing was changed elsewhere.
 */
data class ListingSyncNotice(
    val published: Int,
    val failed: Int,
    val changesSaved: Int = 0,
    val changesFailed: Int = 0,
    val changesDropped: Int = 0,
    val changesConflicted: Int = 0,
)

/**
 * Publishes the signed-in user's listings created offline, and saves their changes made offline to
 * published listings, both kept in [pendingListingQueue], when the app starts online, the
 * connection returns or a user signs in.
 *
 * Only drafts whose owner is the signed-in user are published; other accounts' drafts stay on the
 * device. A published draft is removed from the device; one that fails stays, with its photos, and
 * is tried again only once the connection returns or the signed-in user changes, never in a loop. A
 * draft is never published twice: it keeps the id it was given on the device, the repository does
 * not publish an id that already exists, and passes never overlap: a single collector runs them one
 * after the other, and collectLatest waits for a cancelled pass to end before the next starts.
 *
 * Changes follow the same rules: each is tried once per session, removed once saved, and kept when
 * it fails. Saving a pause or a delete again changes nothing, so a change saved but cut short
 * before its removal does no harm, and the repository finds an edit already saved. A change to a
 * listing deleted elsewhere is dropped, and so is an edit of a listing changed elsewhere.
 *
 * A draft whose publishing takes longer than [publishTimeoutMillis] is kept and counted as failed:
 * the device may report being online while Firestore never answers, on a captive portal or a very
 * weak network.
 *
 * @param today the current day, to leave past available days out of a published draft.
 * @param publishTimeoutMillis how long publishing one draft, photos included, or saving one change
 *   may take.
 */
class ListingOutboxSyncViewModel(
    private val pendingListingQueue: PendingListingQueue,
    private val listingRepository: ListingRepository,
    connectivityRepository: ConnectivityRepository,
    private val today: () -> LocalDate = LocalDate::now,
    private val publishTimeoutMillis: Long = PUBLISH_TIMEOUT_MILLIS,
) : ViewModel() {

  private val signedInUid = MutableStateFlow<String?>(null)

  private val _notice = MutableStateFlow<ListingSyncNotice?>(null)
  /** The outcome of the last pass that tried a draft or a change, until [clearNotice]. */
  val notice: StateFlow<ListingSyncNotice?> = _notice.asStateFlow()

  init {
    viewModelScope.launch {
      combine(connectivityRepository.isOnline, signedInUid, ::Pair)
          .distinctUntilChanged()
          // Going offline or signing out cancels the session's pass. A draft cut short stays, to be
          // published or found published next time.
          .collectLatest { (isOnline, uid) ->
            if (!isOnline || uid == null) return@collectLatest
            // Each draft, and each change, is tried once per session, so removing a saved one or
            // keeping a failed one does not start another pass for it. A change merged with a
            // later one is a new change, and is tried.
            val tried = mutableSetOf<String>()
            val triedChanges = mutableSetOf<PendingListingChange>()
            combine(pendingListingQueue.pending, pendingListingQueue.changes, ::Pair).collect {
                (drafts, changes) ->
              val untried = drafts.filter { it.ownerUid == uid && it.id !in tried }
              val untriedChanges = changes.filter { it.ownerUid == uid && it !in triedChanges }
              if (untried.isEmpty() && untriedChanges.isEmpty()) return@collect
              tried += untried.map { it.id }
              triedChanges += untriedChanges
              sync(untried, untriedChanges)
            }
          }
    }
  }

  /** Sets the signed-in user, or null when nobody is signed in. */
  fun setSignedInUser(uid: String?) {
    signedInUid.value = uid
  }

  fun clearNotice() {
    _notice.value = null
  }

  private suspend fun sync(drafts: List<PendingListing>, changes: List<PendingListingChange>) {
    var published = 0
    var failed = 0
    for (draft in drafts) {
      try {
        // withTimeoutOrNull, not withTimeout: its TimeoutCancellationException would be rethrown
        // below as a cancellation, ending the pass without telling the user.
        val done =
            withTimeoutOrNull(publishTimeoutMillis) {
              listingRepository.publishListing(draft.id, draft.listing.withUpcomingDays())
            }
        if (done == null) {
          Log.w(LOG_TAG, "Publishing draft ${draft.id} timed out")
          failed++
          continue
        }
        pendingListingQueue.remove(draft.id)
        published++
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        Log.w(LOG_TAG, "Could not publish draft ${draft.id}", exception)
        failed++
      }
    }
    val outcomes = changes.map { saveChange(it) }
    _notice.value =
        ListingSyncNotice(
            published,
            failed,
            changesSaved = outcomes.count { it == ChangeOutcome.SAVED },
            changesFailed = outcomes.count { it == ChangeOutcome.FAILED },
            changesDropped = outcomes.count { it == ChangeOutcome.DROPPED },
            changesConflicted = outcomes.count { it == ChangeOutcome.CONFLICTED },
        )
  }

  private enum class ChangeOutcome {
    SAVED,
    FAILED,
    DROPPED,
    CONFLICTED,
  }

  /** Saves [change], removing it unless it failed. */
  private suspend fun saveChange(change: PendingListingChange): ChangeOutcome {
    val result = withTimeoutOrNull(publishTimeoutMillis) { apply(change) }
    val exception = result?.exceptionOrNull()
    if (exception is CancellationException) throw exception
    return when {
      result == null -> ChangeOutcome.FAILED.also { Log.w(LOG_TAG, "A change timed out") }
      exception == null -> ChangeOutcome.SAVED
      else ->
          when (exception.toAppError()) {
            // deleteListing succeeds on a listing already gone, so only a pause, resume or edit
            // gets here.
            AppError.NOT_FOUND -> ChangeOutcome.DROPPED
            AppError.CHANGED_ELSEWHERE -> ChangeOutcome.CONFLICTED
            else ->
                ChangeOutcome.FAILED.also { Log.w(LOG_TAG, "Could not save a change", exception) }
          }
    }.also { if (it != ChangeOutcome.FAILED) pendingListingQueue.removeChange(change) }
  }

  /** Saves [change]'s edit, then its pause or resume, or its delete alone. */
  private suspend fun apply(change: PendingListingChange): Result<Unit> {
    if (change.isDeleted) return listingRepository.deleteListing(change.listingId)
    val edit = change.edit
    val base = change.base
    // A pause or resume failing after the edit was saved keeps the change: trying it again then
    // finds the edit saved.
    if (edit != null && base != null) {
      listingRepository.applyQueuedEdit(base, edit).onFailure {
        return Result.failure(it)
      }
    }
    return change.paused?.let { listingRepository.setPaused(change.listingId, it) }
        ?: Result.success(Unit)
  }

  /** The draft without the available days now past, as the listing form saves it. */
  private fun Listing.withUpcomingDays(): Listing {
    val days = upcomingDays(availableDates, today())
    return when (this) {
      is ItemListing -> copy(availableDates = days)
      is ServiceListing -> copy(availableDates = days)
    }
  }

  private companion object {
    const val LOG_TAG = "ListingOutboxSync"
    // Generous, as it includes uploading the draft's photos.
    const val PUBLISH_TIMEOUT_MILLIS = 60_000L
  }
}
