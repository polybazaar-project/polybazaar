// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.model

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * A [LocationRepository] reading the device's location from Play Services' fused location provider.
 *
 * @param client the fused location client, injected so tests can mock it. Built in the app with
 *   `LocationServices.getFusedLocationProviderClient(context)`.
 */
class FusedLocationRepository(private val client: FusedLocationProviderClient) :
    LocationRepository {

  override suspend fun currentLocation(): Result<Coordinates> {
    // Cancelled with the caller (for example the map screen closing), so Play Services stops
    // looking for a fix nobody waits for.
    val cancellation = CancellationTokenSource()
    return try {
      // Balanced power: city-block accuracy, which is all coarse location gives and all the map
      // needs, without turning on GPS.
      val location =
          client
              .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
              .await(cancellation)
              // Null when location is turned off or no fix could be obtained.
              ?: throw IllegalStateException("No location available")
      Result.success(Coordinates(location.latitude, location.longitude))
    } catch (e: SecurityException) {
      // The permission was refused or revoked: the caller offers the campus choice instead.
      Result.failure(e)
    } catch (e: Exception) {
      // Cancellation means the caller is gone and must propagate.
      if (e is CancellationException) throw e
      Result.failure(e)
    }
  }
}
