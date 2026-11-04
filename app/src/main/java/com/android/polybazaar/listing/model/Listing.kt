// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import android.net.Uri

enum class ListingType {
  SERVICE,
  ITEM,
}

// These IDs are persisted in Firestore and must match the category lists in firestore.rules.
enum class ListingCategory(
    val id: String,
    val type: ListingType,
    val displayName: String,
) {
  ITEM_TOOLS("item_tools", ListingType.ITEM, "Tools"),
  ITEM_ELECTRONICS("item_electronics", ListingType.ITEM, "Electronics"),
  ITEM_BOOKS("item_books", ListingType.ITEM, "Books"),
  ITEM_SPORTS("item_sports", ListingType.ITEM, "Sports"),
  ITEM_KITCHEN("item_kitchen", ListingType.ITEM, "Kitchen"),
  ITEM_OTHER("item_other", ListingType.ITEM, "Other"),
  SERVICE_TUTORING("service_tutoring", ListingType.SERVICE, "Tutoring"),
  SERVICE_REPAIRS("service_repairs", ListingType.SERVICE, "Repairs"),
  SERVICE_MOVING_HELP("service_moving_help", ListingType.SERVICE, "Moving help"),
  SERVICE_GARDENING("service_gardening", ListingType.SERVICE, "Gardening"),
  SERVICE_CLEANING("service_cleaning", ListingType.SERVICE, "Cleaning"),
  SERVICE_OTHER("service_other", ListingType.SERVICE, "Other");

  companion object {
    fun fromId(id: String): ListingCategory? = entries.find { it.id == id }
  }
}

/**
 * @property images photos on this device, to upload when the listing is created.
 * @property imagePaths where the photos are stored once the listing is saved, as Storage paths to
 *   read through the repository. Empty until then. When editing, the stored photos to keep.
 * @property ownerUid uid of the user who created the listing. Null until the listing is saved; the
 *   repository sets it from the stored `ownerUid`. The security rules only let a user with a
 *   verified EPFL email create a listing, so every owner was verified when creating it.
 */
sealed class Listing(
    open val category: ListingCategory,
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
    override val category: ListingCategory,
    override val price: Double,
    override val title: String,
    override val description: String,
    override val images: List<Uri> = emptyList(),
    override val imagePaths: List<String> = emptyList(),
    override val listingId: String? = null,
    override val ownerUid: String? = null,
) : Listing(category, price, title, description, images, imagePaths, listingId, ownerUid) {
  override val type = ListingType.SERVICE

  init {
    require(category.type == type) { "A service listing requires a service category" }
  }

  override fun saved(listingId: String, imagePaths: List<String>, ownerUid: String) =
      copy(
          listingId = listingId,
          images = emptyList(),
          imagePaths = imagePaths,
          ownerUid = ownerUid,
      )
}

/**
 * @property maxRentalDays the longest a renter can keep the item, in days. Null means the lender
 *   set no limit, which is also what older listings without the field read as. Services don't have
 *   it: they aren't rented by the day.
 */
data class ItemListing(
    override val category: ListingCategory,
    override val price: Double,
    override val title: String,
    override val description: String,
    val refundValue: Double = price,
    val maxRentalDays: Int? = null,
    override val images: List<Uri> = emptyList(),
    override val imagePaths: List<String> = emptyList(),
    override val listingId: String? = null,
    override val ownerUid: String? = null,
) : Listing(category, price, title, description, images, imagePaths, listingId, ownerUid) {
  override val type = ListingType.ITEM

  init {
    require(category.type == type) { "An item listing requires an item category" }
  }

  override fun saved(listingId: String, imagePaths: List<String>, ownerUid: String) =
      copy(
          listingId = listingId,
          images = emptyList(),
          imagePaths = imagePaths,
          ownerUid = ownerUid,
      )
}
