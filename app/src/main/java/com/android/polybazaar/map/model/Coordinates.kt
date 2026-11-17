// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.model

/**
 * A point on the map, in degrees. The model's own type rather than the Maps SDK's `LatLng`, so the
 * model and the ViewModels don't depend on the map library; the map screen converts it.
 *
 * @property latitude from -90 to 90.
 * @property longitude from -180 to 180.
 */
data class Coordinates(val latitude: Double, val longitude: Double)
