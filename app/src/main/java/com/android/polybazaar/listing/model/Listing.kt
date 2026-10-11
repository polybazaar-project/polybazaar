// Co-authored-by: Copilot GPT-5.6 Terra
package com.android.polybazaar.listing.model

import android.net.Uri

// For images we use Uri, reference to an image.
// Like String but specifically intended to address resources in Android.
// Android Image API takes URI not String as arg.
// The create-listing UI would use Android Activity Result contracts:
// NBSPPickVisualMediaNBSP for selecting from the photo library.
// NBSPTakePictureNBSP for capturing a photo directly with the camera.
// Both flows produce an Uri.

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
) {
  init {
    require(images.size <= MAXIMUM_IMAGES) { "A listing can have at most $MAXIMUM_IMAGES images." }
  }

  abstract val type: ListingType

  companion object {
    const val MAXIMUM_IMAGES = 10
  }
}

data class ServiceListing(
    override val category: String,
    override val price: Double,
    override val title: String,
    override val description: String,
    override val images: List<Uri> = emptyList(),
) : Listing(category, price, title, description, images) {
  override val type = ListingType.SERVICE
}

data class ItemListing(
    override val category: String,
    override val price: Double,
    override val title: String,
    override val description: String,
    val refundValue: Double = price,
    override val images: List<Uri> = emptyList(),
) : Listing(category, price, title, description, images) {
  override val type = ListingType.ITEM
}
