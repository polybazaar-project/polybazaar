// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A [PendingListingQueue] in memory, for tests. [enqueue] and [enqueueChange] keep the listing or
 * change as it is, owned by [ownerUid], or fail with [failure] when it is set. Change [ownerUid]
 * between calls to keep drafts of several accounts, as on a device several users signed in to.
 */
class FakePendingListingQueue(var ownerUid: String = "owner") : PendingListingQueue {
  private val _pending = MutableStateFlow<List<PendingListing>>(emptyList())
  override val pending: StateFlow<List<PendingListing>> = _pending.asStateFlow()

  private val _changes = MutableStateFlow<List<PendingListingChange>>(emptyList())
  override val changes: StateFlow<List<PendingListingChange>> = _changes.asStateFlow()

  var failure: Exception? = null

  override suspend fun enqueue(id: String, listing: Listing) {
    failure?.let { throw it }
    _pending.update { drafts -> drafts + PendingListing(id, ownerUid, listing) }
  }

  override suspend fun remove(id: String) {
    _pending.update { drafts -> drafts.filterNot { it.id == id } }
  }

  override suspend fun enqueueChange(listingId: String, paused: Boolean?, delete: Boolean) {
    failure?.let { throw it }
    _changes.update { changes ->
      val earlier = changes.find { it.listingId == listingId }
      val change =
          (earlier ?: PendingListingChange(listingId, ownerUid))
              .copy(ownerUid = ownerUid)
              .followedBy(paused, delete)
      changes.filterNot { it.listingId == listingId } + change
    }
  }

  override suspend fun removeChange(change: PendingListingChange) {
    _changes.update { changes -> changes - change }
  }
}
