// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.model

import androidx.annotation.VisibleForTesting
import com.android.polybazaar.listing.model.ListingRules.MAX_LOCATION_NAME_LENGTH
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

/**
 * A [PlaceNameRepository] asking the Google Geocoding web API for the neighbourhood and town a
 * point is in.
 *
 * @param apiKey a key for the Geocoding web API. With the build's placeholder every call fails,
 *   without a request.
 * @param fetch reads the body of a successful GET to the URL, injected so tests avoid the network.
 */
class GoogleGeocodingRepository(
    private val apiKey: String,
    private val fetch: suspend (URL) -> String = ::httpGet,
) : PlaceNameRepository {

  override suspend fun townName(point: Coordinates): Result<String> =
      try {
        if (apiKey == PLACEHOLDER_KEY) throw IOException("No Geocoding API key")
        val response = JSONObject(fetch(urlFor(point)))
        val status = response.getString("status")
        // ZERO_RESULTS, REQUEST_DENIED, OVER_QUERY_LIMIT...: nothing to suggest.
        if (status != "OK") throw IOException("Geocoding failed: $status")
        val name = placeIn(response).trim().take(MAX_LOCATION_NAME_LENGTH).trim()
        if (name.isEmpty()) throw IOException("No place found")
        Result.success(name)
      } catch (e: Exception) {
        // Cancellation means the caller is gone and must propagate.
        if (e is CancellationException) throw e
        Result.failure(e)
      }

  /**
   * Only neighbourhoods and towns are asked for, named in the device's language when Google has
   * that name. The point is rounded to about 100 m: enough for a neighbourhood, and Google never
   * learns exactly where the lender is.
   */
  private fun urlFor(point: Coordinates): URL {
    val latLng = String.format(Locale.ROOT, "%.3f,%.3f", point.latitude, point.longitude)
    val params =
        mapOf(
            "latlng" to latLng,
            "result_type" to "neighborhood|sublocality|locality",
            "language" to Locale.getDefault().toLanguageTag(),
            "key" to apiKey,
        )
    val query = params.entries.joinToString("&") { (name, value) -> "$name=${encode(value)}" }
    return URL("$GEOCODE_URL?$query")
  }

  /**
   * "<neighbourhood>, <town>", or the town or neighbourhood alone when Google knows only one, or ""
   * when it knows neither. Results go from the most precise to the least, so the first name of each
   * kind wins.
   */
  private fun placeIn(response: JSONObject): String {
    val results = response.getJSONArray("results")
    val components =
        (0 until results.length()).flatMap { index ->
          val array = results.getJSONObject(index).getJSONArray("address_components")
          (0 until array.length()).map(array::getJSONObject)
        }
    fun named(vararg types: String): String? = types.firstNotNullOfOrNull { type ->
      components.firstNotNullOfOrNull { component ->
        val componentTypes = component.getJSONArray("types")
        val hasType = (0 until componentTypes.length()).any { componentTypes.getString(it) == type }
        component.optString("long_name").trim().takeIf { hasType && it.isNotEmpty() }
      }
    }
    val neighbourhood = named("neighborhood", "sublocality_level_1", "sublocality")
    val town = named("locality", "postal_town")
    return when {
      neighbourhood != null && town != null && neighbourhood != town -> "$neighbourhood, $town"
      else -> town ?: neighbourhood ?: ""
    }
  }

  private companion object {
    const val GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json"

    /** The key in secrets.defaults.properties, used when local.properties has none. */
    const val PLACEHOLDER_KEY = "DEFAULT_API_KEY"

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
  }
}

/** How long [httpGet] waits to connect, then for the response. */
private const val TIMEOUT_MILLIS = 10_000

/**
 * The body of a GET to [url], read on [dispatcher]; throws [IOException] on a response other than
 * 2xx. Cancelling the caller disconnects, which unblocks a read in progress and frees its thread.
 */
@VisibleForTesting
internal suspend fun httpGet(url: URL, dispatcher: CoroutineDispatcher = Dispatchers.IO): String =
    coroutineScope {
      val connection = url.openConnection() as HttpURLConnection
      val body =
          async(dispatcher) {
            try {
              connection.connectTimeout = TIMEOUT_MILLIS
              connection.readTimeout = TIMEOUT_MILLIS
              if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}")
              }
              connection.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
              // Disconnecting on cancellation fails the read: report the cancellation instead.
              ensureActive()
              throw e
            }
          }
      try {
        body.await()
      } finally {
        connection.disconnect()
      }
    }
