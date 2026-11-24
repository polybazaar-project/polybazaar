// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * A [ListingRepository] for tests.
 *
 * [getAllListings] answers with the [allListings] that are not paused, like the real repository,
 * and [getMyListings] with [myListings]. [createListing] adds to [allListings], owned by [ownerUid]
 * like the real repository stores the creator's uid (leave it null when the owner does not matter),
 * [getListing] looks the id up in both lists and fails with a Firestore `NOT_FOUND` error like the
 * real repository when neither has it, [publishListing] adds to [allListings] under the given id
 * unless a listing already has it, recording each id in [publishedListingIds], and fails for the
 * ids in [failingPublishIds], [updateListing] replaces the listing in both, [deleteListing] removes
 * it from both, and [setPaused] changes its pause state in both. Every call except
 * [loadListingImage] fails with [failure] when it is set. Set [gate] to keep any of these calls
 * waiting until the test completes it, to look at one in progress.
 *
 * [observeListing] follows the listing in both lists, emitting `Missing` while neither has it, like
 * the real repository once the server says it does not exist. Changing either list emits again, and
 * setting [failure] makes every active and new collection fail, like Firestore ending a listener
 * with an error. While [notCached] is set, every active and new collection emits `NotCached`, for
 * every listing, whether the lists have it or not, like the real repository when only the cache
 * answers; clearing it emits each listing's state again, like the server answering. A [gate] set
 * before a collection starts holds back its first emission. [observeListingCalls] counts the
 * collections started.
 *
 * [loadListingImage] answers from [images], or empty bytes for a path it does not have, fails with
 * [imageFailure] when it is set, waits for [imageGate] when it is set, and records each path in
 * [loadedImagePaths].
 */
class FakeListingRepository(
    allListings: List<Listing> = emptyList(),
    myListings: List<Listing> = emptyList(),
    private val ownerUid: String? = null,
) : ListingRepository {
  // Flows, so that observeListing sees every change a test or a write makes.
  private val allListingsFlow = MutableStateFlow(allListings)
  private val myListingsFlow = MutableStateFlow(myListings)
  private val failureFlow = MutableStateFlow<Exception?>(null)
  private val notCachedFlow = MutableStateFlow(false)

  var allListings: List<Listing>
    get() = allListingsFlow.value
    set(value) {
      allListingsFlow.value = value
    }

  var myListings: List<Listing>
    get() = myListingsFlow.value
    set(value) {
      myListingsFlow.value = value
    }

  var failure: Exception?
    get() = failureFlow.value
    set(value) {
      failureFlow.value = value
    }

  var notCached: Boolean
    get() = notCachedFlow.value
    set(value) {
      notCachedFlow.value = value
    }

  var gate: CompletableDeferred<Unit>? = null
  var getAllListingsCalls = 0
    private set

  var getMyListingsCalls = 0
    private set

  var getListingCalls = 0
    private set

  var observeListingCalls = 0
    private set

  /** The listings passed to [updateListing], in order. */
  val updatedListings = mutableListOf<Listing>()

  /** The ids passed to [deleteListing], in order. */
  val deletedListingIds = mutableListOf<String>()

  /** The calls to [setPaused], as (listingId, paused), in order. */
  val pauseCalls = mutableListOf<Pair<String, Boolean>>()

  /** The photos [loadListingImage] answers with, by Storage path. */
  val images = mutableMapOf<String, ByteArray>()
  var imageFailure: Exception? = null
  var imageGate: CompletableDeferred<Unit>? = null

  /** The paths passed to [loadListingImage], in order. */
  val loadedImagePaths = mutableListOf<String>()

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

  private var newListingIds = 0

  override fun newListingId(): String = "new-${newListingIds++}"

  /** The ids passed to [publishListing], in order. */
  val publishedListingIds = mutableListOf<String>()

  /** The ids [publishListing] fails for, like a publish the network cuts short. */
  val failingPublishIds = mutableSetOf<String>()

  override suspend fun publishListing(listingId: String, listing: Listing) {
    gate?.await()
    failure?.let { throw it }
    if (listingId in failingPublishIds) {
      throw FirebaseFirestoreException("Unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
    }
    publishedListingIds += listingId
    if (allListings.none { it.listingId == listingId }) {
      allListings =
          allListings + listing.saved(listingId, emptyList(), ownerUid.orEmpty(), isPaused = false)
    }
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

  override suspend fun getListing(listingId: String): Result<Listing> {
    getListingCalls++
    return answer(Unit).mapCatching {
      (allListings + myListings).firstOrNull { it.listingId == listingId }
          ?: throw FirebaseFirestoreException(
              "Listing $listingId not found",
              FirebaseFirestoreException.Code.NOT_FOUND,
          )
    }
  }

  override fun observeListing(listingId: String): Flow<ObservedListing> {
    observeListingCalls++
    val gate = gate
    return flow {
      gate?.await()
      emitAll(
          combine(allListingsFlow, myListingsFlow, failureFlow, notCachedFlow) {
              all,
              mine,
              failure,
              notCached ->
            failure?.let { throw it }
            if (notCached) return@combine ObservedListing.NotCached
            (all + mine).firstOrNull { it.listingId == listingId }?.let(ObservedListing::Found)
                ?: ObservedListing.Missing
          }
      )
    }
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
        allListings = allListings.map {
          if (it.listingId == listingId) it.withPaused(paused) else it
        }
        myListings = myListings.map { if (it.listingId == listingId) it.withPaused(paused) else it }
      }

  override suspend fun loadListingImage(imagePath: String): ByteArray {
    loadedImagePaths += imagePath
    imageGate?.await()
    imageFailure?.let { throw it }
    return images[imagePath] ?: ByteArray(0)
  }

  /** Waits for [gate] if set, then fails with [failure] or answers with [value]. */
  private suspend fun <T> answer(value: T): Result<T> {
    gate?.await()
    return failure?.let { Result.failure(it) } ?: Result.success(value)
  }
}
