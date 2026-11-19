// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.viewmodel

import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.model.formatChf
import com.android.polybazaar.map.model.Coordinates

/**
 * A listing as a marker on the map (#341).
 *
 * @property id the listing's id, to open its detail.
 * @property type an item or a service: the map draws them with different icons.
 * @property coordinates where the owner placed it (`Listing.location`).
 * @property price formatted by [formatChf], like the Browse cards; the screen adds the unit.
 */
data class MapMarker(
    val id: String,
    val title: String,
    val type: ListingType,
    val coordinates: Coordinates,
    val price: String,
)

/**
 * The marker of this listing, or null when it can't be placed: not saved yet (no id), or without a
 * location, which the owner may not have set.
 */
internal fun Listing.toMapMarker(): MapMarker? {
  val id = listingId ?: return null
  val location = location ?: return null
  return MapMarker(
      id = id,
      title = title,
      type = type,
      coordinates = Coordinates(location.latitude, location.longitude),
      price = formatChf(price),
  )
}
