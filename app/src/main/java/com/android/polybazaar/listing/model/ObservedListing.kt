// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

/** What following a listing with [ListingRepository.observeListing] says about it. */
sealed interface ObservedListing {

  /** The listing exists, as it is now. */
  data class Found(val listing: Listing) : ObservedListing

  /** The server says the listing does not exist, or it is malformed, as `getListing` treats it. */
  data object Missing : ObservedListing

  /**
   * Only the local cache says the listing is missing, so whether it exists is unknown. The cache's
   * answer is not trusted, because offline or before the server answers it can only mean the cache
   * does not have the listing.
   */
  data object NotCached : ObservedListing
}
