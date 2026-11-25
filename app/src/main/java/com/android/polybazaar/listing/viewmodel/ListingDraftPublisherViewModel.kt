// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.booking.model.upcomingDays
import com.android.polybazaar.connectivity.model.ConnectivityRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.listing.model.PendingListing
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

/** The outcome of a publish pass that tried at least one draft, to tell the user. */
data class DraftPublishNotice(val published: Int, val failed: Int)

/**
 * Publishes the signed-in user's listings created offline, kept in [pendingListingQueue], when the
 * app starts online, the connection returns or a user signs in.
 *
 * Only drafts whose owner is the signed-in user are published; other accounts' drafts stay on the
 * device. A published draft is removed from the device; one that fails stays, with its photos, and
 * is tried again only once the connection returns or the signed-in user changes, never in a loop. A
 * draft is never published twice: it keeps the id it was given on the device, the repository does
 * not publish an id that already exists, and passes never overlap: a single collector runs them one
 * after the other, and collectLatest waits for a cancelled pass to end before the next starts.
 *
 * A draft whose publishing takes longer than [publishTimeoutMillis] is kept and counted as failed:
 * the device may report being online while Firestore never answers, on a captive portal or a very
 * weak network.
 *
 * @param today the current day, to leave past available days out of a published draft.
 * @param publishTimeoutMillis how long publishing one draft, photos included, may take.
 */
class ListingDraftPublisherViewModel(
    private val pendingListingQueue: PendingListingQueue,
    private val listingRepository: ListingRepository,
    connectivityRepository: ConnectivityRepository,
    private val today: () -> LocalDate = LocalDate::now,
    private val publishTimeoutMillis: Long = PUBLISH_TIMEOUT_MILLIS,
) : ViewModel() {

  private val signedInUid = MutableStateFlow<String?>(null)

  private val _notice = MutableStateFlow<DraftPublishNotice?>(null)
  /** The outcome of the last pass that tried a draft, until [clearNotice]. */
  val notice: StateFlow<DraftPublishNotice?> = _notice.asStateFlow()

  init {
    viewModelScope.launch {
      combine(connectivityRepository.isOnline, signedInUid, ::Pair)
          .distinctUntilChanged()
          // Going offline or signing out cancels the session's pass. A draft cut short stays, to be
          // published or found published next time.
          .collectLatest { (isOnline, uid) ->
            if (!isOnline || uid == null) return@collectLatest
            // Each draft is tried once per session, so removing a published one or keeping a
            // failed one does not start another pass for it.
            val tried = mutableSetOf<String>()
            pendingListingQueue.pending.collect { drafts ->
              val untried = drafts.filter { it.ownerUid == uid && it.id !in tried }
              if (untried.isEmpty()) return@collect
              tried += untried.map { it.id }
              publish(untried)
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

  private suspend fun publish(drafts: List<PendingListing>) {
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
    _notice.value = DraftPublishNotice(published, failed)
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
    const val LOG_TAG = "DraftPublisher"
    // Generous, as it includes uploading the draft's photos.
    const val PUBLISH_TIMEOUT_MILLIS = 60_000L
  }
}
