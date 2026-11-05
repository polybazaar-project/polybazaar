// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CompletableDeferred

/**
 * A [ListingRepository] for tests.
 *
 * [getAllListings] answers with the [allListings] that are not paused, like the real repository,
 * and [getMyListings] with [myListings]. [createListing] adds to [allListings], owned by [ownerUid]
 * like the real repository stores the creator's uid (leave it null when the owner does not matter),
 * [getListing] looks the id up in both lists and fails with a Firestore `NOT_FOUND` error like the
 * real repository when neither has it, [updateListing] replaces the listing in both,
 * [deleteListing] removes it from both, and [setPaused] changes its pause state in both. Every call
 * except [loadListingImage] fails with [failure] when it is set. Set [gate] to keep any of these
 * calls waiting until the test completes it, to look at one in progress.
 */
class FakeListingRepository(
    var allListings: List<Listing> = emptyList(),
    var myListings: List<Listing> = emptyList(),
    private val ownerUid: String? = null,
) : ListingRepository {
  var failure: Exception? = null
  var gate: CompletableDeferred<Unit>? = null
  var getAllListingsCalls = 0
    private set

  var getMyListingsCalls = 0
    private set

  /** The listings passed to [updateListing], in order. */
  val updatedListings = mutableListOf<Listing>()

  /** The ids passed to [deleteListing], in order. */
  val deletedListingIds = mutableListOf<String>()

  /** The calls to [setPaused], as (listingId, paused), in order. */
  val pauseCalls = mutableListOf<Pair<String, Boolean>>()

  override suspend fun createListing(listing: Listing): Listing {
    gate?.await()
    failure?.let { throw it }
    val listingId = "created-${allListings.size}"
    val created =
        when (listing) {
          is ItemListing -> listing.copy(listingId = listingId, ownerUid = ownerUid)
          is ServiceListing -> listing.copy(listingId = listingId, ownerUid = ownerUid)
        }
    allListings = allListings + created
    return created
  }

  override suspend fun getMyListings(): Result<List<Listing>> {
    getMyListingsCalls++
    return answer(myListings)
  }

  override suspend fun getListingsByOwner(ownerUid: String): Result<List<Listing>> =
      answer(allListings.filter { it.ownerUid == ownerUid })

  override suspend fun getAllListings(): Result<List<Listing>> {
    getAllListingsCalls++
    return answer(allListings.filterNot { it.isPaused })
  }

  override suspend fun getListing(listingId: String): Result<Listing> =
      answer(Unit).mapCatching {
        (allListings + myListings).firstOrNull { it.listingId == listingId }
            ?: throw FirebaseFirestoreException(
                "Listing $listingId not found",
                FirebaseFirestoreException.Code.NOT_FOUND,
            )
      }

  /** Stores the new photos under fake paths after the kept ones, like the real repository. */
  override suspend fun updateListing(listing: Listing): Listing {
    answer(Unit).getOrThrow()
    updatedListings += listing
    val newPaths = listing.images.indices.map { "fake/${listing.listingId}/new-$it" }
    val saved =
        when (listing) {
          is ServiceListing ->
              listing.copy(images = emptyList(), imagePaths = listing.imagePaths + newPaths)
          is ItemListing ->
              listing.copy(images = emptyList(), imagePaths = listing.imagePaths + newPaths)
        }
    allListings = allListings.map { if (it.listingId == saved.listingId) saved else it }
    myListings = myListings.map { if (it.listingId == saved.listingId) saved else it }
    return saved
  }

  override suspend fun deleteListing(listingId: String): Result<Unit> =
      answer(Unit).onSuccess {
        deletedListingIds += listingId
        allListings = allListings.filterNot { it.listingId == listingId }
        myListings = myListings.filterNot { it.listingId == listingId }
      }

  override suspend fun setPaused(listingId: String, paused: Boolean): Result<Unit> =
      answer(Unit).onSuccess {
        pauseCalls += listingId to paused
        allListings = allListings.map { if (it.listingId == listingId) it.paused(paused) else it }
        myListings = myListings.map { if (it.listingId == listingId) it.paused(paused) else it }
      }

  override suspend fun loadListingImage(imagePath: String) = ByteArray(0)

  private fun Listing.paused(paused: Boolean): Listing =
      when (this) {
        is ServiceListing -> copy(isPaused = paused)
        is ItemListing -> copy(isPaused = paused)
      }

  /** Waits for [gate] if set, then fails with [failure] or answers with [value]. */
  private suspend fun <T> answer(value: T): Result<T> {
    gate?.await()
    return failure?.let { Result.failure(it) } ?: Result.success(value)
  }
}
