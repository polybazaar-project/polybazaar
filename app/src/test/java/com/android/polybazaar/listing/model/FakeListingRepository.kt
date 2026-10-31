// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import kotlinx.coroutines.CompletableDeferred

/**
 * A [ListingRepository] for tests that read listings.
 *
 * [getAllListings] answers with [allListings] and [getMyListings] with [myListings], or either with
 * [failure] when it is set. Set [gate] to keep the call waiting until the test completes it, to
 * look at a load in progress.
 */
class FakeListingRepository(
    var allListings: List<Listing> = emptyList(),
    var myListings: List<Listing> = emptyList(),
) : ListingRepository {
  var failure: Exception? = null
  var gate: CompletableDeferred<Unit>? = null
  var getAllListingsCalls = 0
    private set

  var getMyListingsCalls = 0
    private set

  override suspend fun createListing(listing: Listing): Listing = listing

  override suspend fun getMyListings(): Result<List<Listing>> {
    getMyListingsCalls++
    return answer(myListings)
  }

  override suspend fun getAllListings(): Result<List<Listing>> {
    getAllListingsCalls++
    return answer(allListings)
  }

  override suspend fun loadListingImage(imagePath: String) = ByteArray(0)

  /** Waits for [gate] if set, then fails with [failure] or answers with [listings]. */
  private suspend fun answer(listings: List<Listing>): Result<List<Listing>> {
    gate?.await()
    return failure?.let { Result.failure(it) } ?: Result.success(listings)
  }
}
