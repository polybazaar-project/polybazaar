// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CompletableDeferred

/**
 * A [ListingRepository] for tests.
 *
 * [getAllListings] answers with [allListings] and [getMyListings] with [myListings], or either with
 * [failure] when it is set. [getListing] answers with the listing of [allListings] that has the id,
 * fails with a Firestore `NOT_FOUND` error like the real repository when none has it, or with
 * [failure] when it is set. [createListing] adds to [allListings], or throws [failure]. Set [gate]
 * to keep any of these calls waiting until the test completes it, to look at one in progress.
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

  var getListingCalls = 0
    private set

  override suspend fun createListing(listing: Listing): Listing {
    gate?.await()
    failure?.let { throw it }
    val listingId = "created-${allListings.size}"
    val created =
        when (listing) {
          is ItemListing -> listing.copy(listingId = listingId)
          is ServiceListing -> listing.copy(listingId = listingId)
        }
    allListings = allListings + created
    return created
  }

  override suspend fun getMyListings(): Result<List<Listing>> {
    getMyListingsCalls++
    return answer(myListings)
  }

  override suspend fun getAllListings(): Result<List<Listing>> {
    getAllListingsCalls++
    return answer(allListings)
  }

  override suspend fun getListing(listingId: String): Result<Listing> {
    getListingCalls++
    return answer(allListings.find { it.listingId == listingId }).mapCatching {
      it
          ?: throw FirebaseFirestoreException(
              "Listing not found",
              FirebaseFirestoreException.Code.NOT_FOUND,
          )
    }
  }

  override suspend fun loadListingImage(imagePath: String) = ByteArray(0)

  /** Waits for [gate] if set, then fails with [failure] or answers with [value]. */
  private suspend fun <T> answer(value: T): Result<T> {
    gate?.await()
    return failure?.let { Result.failure(it) } ?: Result.success(value)
  }
}
