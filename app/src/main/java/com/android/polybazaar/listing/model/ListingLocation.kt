// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import com.android.polybazaar.map.model.Campus
import com.android.polybazaar.map.model.Coordinates
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Where a listing is offered: a point on the map and the name shown for it.
 *
 * @property latitude in degrees, from -90 to 90.
 * @property longitude in degrees, from -180 to 180.
 * @property name what the listing detail shows, for example "Rolex Learning Center". At most
 *   [ListingRules.MAX_LOCATION_NAME_LENGTH] characters once trimmed.
 */
data class ListingLocation(val latitude: Double, val longitude: Double, val name: String)

/** Names for the points the lender picks on the map for a listing's location. */
object ListingLocations {

  /** How far from a campus centre a point still counts as on that campus. */
  private const val CAMPUS_RADIUS_METERS = 600.0

  private const val EARTH_RADIUS_METERS = 6_371_000.0

  /** The name stored for [campus]: listing data, not a string resource. */
  private fun nameOf(campus: Campus): String =
      when (campus) {
        Campus.EPFL -> "EPFL campus"
        Campus.UNIL -> "UNIL campus"
      }

  /**
   * The name of the campus [point] is on, the nearest one if it is close to both, or null when it
   * is on neither.
   */
  fun campusNameFor(point: Coordinates): String? =
      Campus.entries
          .map { it to distanceMeters(point, it.centre) }
          .filter { (_, distance) -> distance <= CAMPUS_RADIUS_METERS }
          .minByOrNull { (_, distance) -> distance }
          ?.let { (campus, _) -> nameOf(campus) }

  /** Whether [name] is one [campusNameFor] gives, rather than one the lender typed. */
  fun isCampusName(name: String): Boolean = Campus.entries.any { nameOf(it) == name }

  /** Equirectangular distance: accurate enough over the few hundred meters of a campus. */
  private fun distanceMeters(a: Coordinates, b: Coordinates): Double {
    val meanLatitude = Math.toRadians((a.latitude + b.latitude) / 2)
    val x = Math.toRadians(b.longitude - a.longitude) * cos(meanLatitude)
    val y = Math.toRadians(b.latitude - a.latitude)
    return EARTH_RADIUS_METERS * sqrt(x * x + y * y)
  }
}
