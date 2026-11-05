// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

interface ListingRepository {

  /**
   * Uploads the listing's images, saves it, and returns it with its id and [Listing.imagePaths].
   */
  suspend fun createListing(listing: Listing): Listing

  /**
   * Returns the listings created by the signed-in user, for the Toolbox (#20).
   *
   * Fails when nobody is signed in or Firestore cannot answer; the caller maps the failure with
   * `toAppError()`.
   */
  suspend fun getMyListings(): Result<List<Listing>>

  /**
   * Returns the listings created by [ownerUid], for a lender's profile. Paused listings are
   * included, so the caller decides whether to show them.
   *
   * Fails when Firestore cannot answer; the caller maps the failure with `toAppError()`.
   */
  suspend fun getListingsByOwner(ownerUid: String): Result<List<Listing>>

  /**
   * Returns every listing on the marketplace that is not paused, for the Browse screen.
   *
   * Fails when Firestore cannot answer or the security rules deny the read; the caller maps the
   * failure with `toAppError()`.
   */
  suspend fun getAllListings(): Result<List<Listing>>

  /**
   * Returns the listing with this id, for the listing detail (#22) and edit screens.
   *
   * Offline, the answer comes from the local cache. Fails with a Firestore `NOT_FOUND` error when
   * the listing does not exist or is malformed, and when Firestore cannot answer or the security
   * rules deny the read; the caller maps the failure with `toAppError()`.
   */
  suspend fun getListing(listingId: String): Result<Listing>

  /**
   * Saves the signed-in owner's changes to an existing [listing], identified by its
   * [Listing.listingId], and returns it as saved.
   *
   * [Listing.imagePaths] are the stored photos to keep, [Listing.images] the new photos to upload.
   * Stored photos left out are deleted once the changes are saved. The type cannot change.
   */
  suspend fun updateListing(listing: Listing): Listing

  /**
   * Deletes the signed-in owner's listing and its photos. Succeeds if it was already deleted.
   *
   * Fails when Firestore cannot answer or the security rules refuse the delete; the caller maps the
   * failure with `toAppError()`.
   */
  suspend fun deleteListing(listingId: String): Result<Unit>

  /**
   * Pauses the signed-in owner's listing, hiding it from Browse and from the lender's profile, or
   * resumes it when [paused] is false. Its other fields are left as they are.
   *
   * Fails when nobody is signed in, the listing does not exist, Firestore cannot answer or the
   * security rules refuse the change; the caller maps the failure with `toAppError()`.
   */
  suspend fun setPaused(listingId: String, paused: Boolean): Result<Unit>

  /** Downloads one of a listing's images by its Storage path, as the signed-in user. */
  suspend fun loadListingImage(imagePath: String): ByteArray
}
