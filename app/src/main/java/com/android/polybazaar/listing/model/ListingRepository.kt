// Co-authored-by: Copilot GPT-5.6 Terra
package com.android.polybazaar.listing.model

interface ListingRepository {

  suspend fun createListing(listing: Listing): Listing
}
