// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

/**
 * Where a listing is offered: a point on the map and the name shown for it.
 *
 * @property latitude in degrees, from -90 to 90.
 * @property longitude in degrees, from -180 to 180.
 * @property name what the listing detail shows, for example "Rolex Learning Center". At most
 *   [ListingRules.MAX_LOCATION_NAME_LENGTH] characters once trimmed.
 */
data class ListingLocation(val latitude: Double, val longitude: Double, val name: String)
