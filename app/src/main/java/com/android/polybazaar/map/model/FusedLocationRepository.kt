// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.model

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A [LocationRepository] reading the device's location from Play Services' fused location provider.
 *
 * @param client the fused location client, injected so tests can mock it. Built in the app with
 *   `LocationServices.getFusedLocationProviderClient(context)`.
 */
class FusedLocationRepository(private val client: FusedLocationProviderClient) :
    LocationRepository {

  // await(CancellationTokenSource), which cancels Play Services' request with the caller, is
  // marked experimental in kotlinx-coroutines-play-services.
  @OptIn(ExperimentalCoroutinesApi::class)
  override suspend fun currentLocation(): Result<Coordinates> {
    // Cancelled with the caller (for example the map screen closing), so Play Services stops
    // looking for a fix nobody waits for.
    val cancellation = CancellationTokenSource()
    return try {
      // Bounded, so the map never waits forever for a fix (indoors, offline, no recent one): past
      // the limit the request is cancelled and the renter gets the campus choice instead.
      val location =
          withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
            // Balanced power: city-block accuracy, which is all coarse location gives and all the
            // map needs, without turning on GPS.
            client
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                .await(cancellation)
          }
              // Null when the time ran out, location is turned off, or no fix could be obtained.
              ?: return Result.failure(IllegalStateException("No location available"))
      Result.success(Coordinates(location.latitude, location.longitude))
    } catch (e: SecurityException) {
      // Same result as the catch below, but lint's MissingPermission check only accepts a call to
      // getCurrentLocation that handles SecurityException explicitly: removing this branch fails
      // the build. Here the permission was refused or revoked, so the caller offers a campus.
      Result.failure(e)
    } catch (e: Exception) {
      // Cancellation means the caller is gone and must propagate.
      if (e is CancellationException) throw e
      Result.failure(e)
    }
  }

  private companion object {
    /**
     * How long to wait for a fix: a coarse one from Wi-Fi or cell towers usually takes a second or
     * two, so after 10 s it is unlikely to come and the campus choice is the better answer.
     */
    const val LOCATION_TIMEOUT_MILLIS = 10_000L
  }
}
