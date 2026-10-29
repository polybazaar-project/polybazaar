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
   * Returns every listing on the marketplace, for the Browse screen.
   *
   * Fails when Firestore cannot answer or the security rules deny the read; the caller maps the
   * failure with `toAppError()`.
   */
  suspend fun getAllListings(): Result<List<Listing>>

  /** Downloads one of a listing's images by its Storage path, as the signed-in user. */
  suspend fun loadListingImage(imagePath: String): ByteArray
}
