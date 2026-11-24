// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.model

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred

/**
 * A [PlaceNameRepository] for tests: [townName] answers [town], or fails like offline when it is
 * null, after waiting for [gate] when it is set, and counts its calls in [calls].
 */
class FakePlaceNameRepository(var town: String? = null) : PlaceNameRepository {
  var gate: CompletableDeferred<Unit>? = null
  var calls = 0
    private set

  override suspend fun townName(point: Coordinates): Result<String> {
    calls++
    gate?.await()
    return town?.let { Result.success(it) } ?: Result.failure(IOException("No town"))
  }
}
