// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import android.net.Uri

enum class ListingType {
  SERVICE,
  ITEM,
}

/**
 * @property images photos on this device, to upload when the listing is created.
 * @property imagePaths where the photos are stored once the listing is saved, as Storage paths to
 *   read through the repository. Empty until then.
 * @property ownerUid uid of the user who created the listing. Null until the listing is saved; the
 *   repository sets it from the stored `ownerUid`. The security rules only let a user with a
 *   verified EPFL email create a listing, so every owner was verified when creating it.
 */
sealed class Listing(
    open val category: String,
    open val price: Double,
    open val title: String,
    open val description: String,
    open val images: List<Uri>,
    open val imagePaths: List<String>,
    open val listingId: String?,
    open val ownerUid: String?,
) {
  abstract val type: ListingType
}

data class ServiceListing(
    override val category: String,
    override val price: Double,
    override val title: String,
    override val description: String,
    override val images: List<Uri> = emptyList(),
    override val imagePaths: List<String> = emptyList(),
    override val listingId: String? = null,
    override val ownerUid: String? = null,
) : Listing(category, price, title, description, images, imagePaths, listingId, ownerUid) {
  override val type = ListingType.SERVICE
}

data class ItemListing(
    override val category: String,
    override val price: Double,
    override val title: String,
    override val description: String,
    val refundValue: Double = price,
    override val images: List<Uri> = emptyList(),
    override val imagePaths: List<String> = emptyList(),
    override val listingId: String? = null,
    override val ownerUid: String? = null,
) : Listing(category, price, title, description, images, imagePaths, listingId, ownerUid) {
  override val type = ListingType.ITEM
}
