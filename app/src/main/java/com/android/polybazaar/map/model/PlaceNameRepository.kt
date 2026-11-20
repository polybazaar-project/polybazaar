// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.model

/** Names the place a point is in, to suggest a name for a listing's location off campus (#395). */
interface PlaceNameRepository {

  /**
   * The name of the town or city [point] is in, for example "Morges", led by its neighbourhood when
   * one is known, for example "Ouchy, Lausanne".
   *
   * Fails offline, when no place is found there (for example in the middle of a lake), or when the
   * geocoding service refuses the request. The caller then leaves the name for the lender to type.
   */
  suspend fun townName(point: Coordinates): Result<String>
}
