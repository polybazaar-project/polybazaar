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
 *   read through the repository. Empty until then. When editing, the stored photos to keep.
 * @property ownerUid the uid of the user who created the listing. Null until it is saved.
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

  /**
   * This listing as saved under [listingId] by [ownerUid], with its photos at [imagePaths]. The
   * photos on the device are uploaded by then, so [images] is empty.
   */
  abstract fun saved(listingId: String, imagePaths: List<String>, ownerUid: String): Listing
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

  override fun saved(listingId: String, imagePaths: List<String>, ownerUid: String) =
      copy(
          listingId = listingId,
          images = emptyList(),
          imagePaths = imagePaths,
          ownerUid = ownerUid,
      )
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

  override fun saved(listingId: String, imagePaths: List<String>, ownerUid: String) =
      copy(
          listingId = listingId,
          images = emptyList(),
          imagePaths = imagePaths,
          ownerUid = ownerUid,
      )
}
