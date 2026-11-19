// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.model

import kotlinx.coroutines.CompletableDeferred

/**
 * A [LocationRepository] for tests: [currentLocation] answers [location], or fails with [failure]
 * when it is set, after waiting for [gate] when it is set, and counts its calls in [calls].
 */
class FakeLocationRepository(var location: Coordinates = Campus.EPFL.centre) : LocationRepository {
  var failure: Exception? = null
  var gate: CompletableDeferred<Unit>? = null
  var calls = 0
    private set

  override suspend fun currentLocation(): Result<Coordinates> {
    calls++
    gate?.await()
    return failure?.let { Result.failure(it) } ?: Result.success(location)
  }
}
