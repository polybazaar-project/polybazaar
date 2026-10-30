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
class FakeListingRepository(var allListings: List<Listing> = emptyList()) : ListingRepository {
  var failure: Exception? = null
  var gate: CompletableDeferred<Unit>? = null
  var myListings: List<Listing> = emptyList()
  var getAllListingsCalls = 0
    private set

  var getMyListingsCalls = 0
    private set

  override suspend fun createListing(listing: Listing): Listing = listing

  override suspend fun getMyListings(): Result<List<Listing>> {
    getMyListingsCalls++
    gate?.await()
    return failure?.let { Result.failure(it) } ?: Result.success(myListings)
  }

  override suspend fun getAllListings(): Result<List<Listing>> {
    getAllListingsCalls++
    gate?.await()
    return failure?.let { Result.failure(it) } ?: Result.success(allListings)
  }

  override suspend fun loadListingImage(imagePath: String) = ByteArray(0)
}
