// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.model

/**
 * A [LocationRepository] for tests: [currentLocation] returns [result], a failure by default as
 * when the permission is refused, and counts its calls in [calls].
 */
class FakeLocationRepository(
    var result: Result<Coordinates> = Result.failure(IllegalStateException("No location"))
) : LocationRepository {

  var calls = 0
    private set

  override suspend fun currentLocation(): Result<Coordinates> {
    calls++
    return result
  }
}
