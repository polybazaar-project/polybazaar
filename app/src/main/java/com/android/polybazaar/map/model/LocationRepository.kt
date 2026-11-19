// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.model

/** Where the device is, to centre the map on the renter (#340). */
interface LocationRepository {

  /**
   * The device's approximate location, read now.
   *
   * Fails when the location permission is not granted, when location is turned off or no fix is
   * available (for example offline without a recent one), or when Play Services cannot answer. The
   * caller then falls back to a campus.
   *
   * The location is never sent to the backend. The listing form keeps it on the device, in its saved
   * state, so the map picker still opens there after the app is killed.
   */
  suspend fun currentLocation(): Result<Coordinates>
}
