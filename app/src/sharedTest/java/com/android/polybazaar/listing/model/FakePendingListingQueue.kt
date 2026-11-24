// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A [PendingListingQueue] in memory, for tests. [enqueue] keeps the listing as it is, owned by
 * [ownerUid], or fails with [failure] when it is set.
 */
class FakePendingListingQueue(private val ownerUid: String = "owner") : PendingListingQueue {
  private val _pending = MutableStateFlow<List<PendingListing>>(emptyList())
  override val pending: StateFlow<List<PendingListing>> = _pending.asStateFlow()

  var failure: Exception? = null

  override suspend fun enqueue(id: String, listing: Listing) {
    failure?.let { throw it }
    _pending.update { drafts -> drafts + PendingListing(id, ownerUid, listing) }
  }

  override suspend fun remove(id: String) {
    _pending.update { drafts -> drafts.filterNot { it.id == id } }
  }
}
