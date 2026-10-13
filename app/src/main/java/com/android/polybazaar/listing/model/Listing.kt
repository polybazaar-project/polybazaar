// Co-authored-by: Copilot GPT-5.6 Terra
package com.android.polybazaar.listing.model

import android.net.Uri

enum class ListingType {
  SERVICE,
  ITEM,
}

sealed class Listing(
    open val category: String,
    open val price: Double,
    open val title: String,
    open val description: String,
    open val images: List<Uri>,
    open val listingId: String?,
) {
  abstract val type: ListingType
}

data class ServiceListing(
    override val category: String,
    override val price: Double,
    override val title: String,
    override val description: String,
    override val images: List<Uri> = emptyList(),
    override val listingId: String? = null,
) : Listing(category, price, title, description, images, listingId) {
  override val type = ListingType.SERVICE
}

data class ItemListing(
    override val category: String,
    override val price: Double,
    override val title: String,
    override val description: String,
    val refundValue: Double = price,
    override val images: List<Uri> = emptyList(),
    override val listingId: String? = null,
) : Listing(category, price, title, description, images, listingId) {
  override val type = ListingType.ITEM
}
