// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.model

/**
 * The campuses a renter can centre the map on when they don't share their location (#21, #340).
 *
 * @property centre roughly the middle of the campus, close enough to centre the map on.
 */
enum class Campus(val centre: Coordinates) {
  /** EPFL, around the Rolex Learning Center. */
  EPFL(Coordinates(latitude = 46.5191, longitude = 6.5668)),

  /** UNIL-Dorigny, around the Amphimax. */
  UNIL(Coordinates(latitude = 46.5224, longitude = 6.5795)),
}
