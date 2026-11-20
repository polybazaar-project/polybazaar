// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.listing.model.ListingRules.MAX_LOCATION_NAME_LENGTH
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URL
import java.util.Locale
import java.util.concurrent.CountDownLatch
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

// Robolectric, for org.json: the Android stub jar's JSONObject throws in plain JUnit.
@RunWith(AndroidJUnit4::class)
class GoogleGeocodingRepositoryTest {

  private val morges = Coordinates(46.5113, 6.4985)
  private val requested = mutableListOf<URL>()

  /** A repository whose requests are recorded in [requested] and answered with [body]. */
  private fun repository(body: String, key: String = "test-key") =
      GoogleGeocodingRepository(key) { url ->
        requested += url
        body
      }

  private fun component(name: String, vararg types: String) =
      """{"long_name": "$name", "types": [${types.joinToString { "\"$it\"" }}]}"""

  private fun response(vararg components: String, status: String = "OK") =
      """{"status": "$status", "results": [{"address_components": [${components.joinToString()}]}]}"""

  /** A response with each of [components] in a result of its own, as Google lists them. */
  private fun eachInAResult(vararg components: String) =
      """{"status": "OK", "results": [${components.joinToString { "{\"address_components\": [$it]}" }}]}"""

  private suspend fun townFor(body: String) = repository(body).townName(morges)

  @Test
  fun townName_isTheFirstResultsLocality() = runTest {
    val body =
        response(
            component("1110", "postal_code"),
            component("Morges", "locality", "political"),
            component("Vaud", "administrative_area_level_1", "political"),
        )

    assertEquals(Result.success("Morges"), townFor(body))
    assertEquals(1, requested.size)
  }

  @Test
  fun townName_skipsABlankLocalityForThePostalTown() = runTest {
    val body = response(component(" ", "locality"), component("London", "postal_town"))

    assertEquals(Result.success("London"), townFor(body))
  }

  @Test
  fun townName_namesTheNeighbourhoodBeforeTheTown() = runTest {
    val ouchy = component("Ouchy", "neighborhood", "political")
    val lausanne = component("Lausanne", "locality", "political")
    assertEquals("Ouchy, Lausanne", townFor(eachInAResult(ouchy, lausanne)).getOrThrow())

    val sublocality = component("Sous-Gare", "sublocality", "sublocality_level_1", "political")
    assertEquals("Sous-Gare, Lausanne", townFor(eachInAResult(sublocality, lausanne)).getOrThrow())
  }

  @Test
  fun townName_prefersTheMostSpecificTypeOfName() = runTest {
    val sublocality = component("Sous-Gare", "sublocality", "sublocality_level_1")
    val ouchy = component("Ouchy", "neighborhood")
    val lausanne = component("Lausanne", "locality")
    assertEquals(
        "Ouchy, Lausanne",
        townFor(eachInAResult(sublocality, ouchy, lausanne)).getOrThrow(),
    )

    val london = component("London", "postal_town")
    assertEquals(
        "Morges",
        townFor(eachInAResult(london, component("Morges", "locality"))).getOrThrow(),
    )
  }

  @Test
  fun townName_withOnlyOneName_isThatName() = runTest {
    // A neighbourhood named like its town is not repeated.
    val sameName = component("Lausanne", "sublocality", "sublocality_level_1")
    val lausanne = component("Lausanne", "locality")
    assertEquals("Lausanne", townFor(eachInAResult(sameName, lausanne)).getOrThrow())

    assertEquals("Ouchy", townFor(eachInAResult(component("Ouchy", "neighborhood"))).getOrThrow())
  }

  @Test
  fun townName_isTrimmedAndCappedToTheLocationNameLength() = runTest {
    assertEquals("Morges", townFor(response(component("  Morges ", "locality"))).getOrThrow())

    val long = "a".repeat(MAX_LOCATION_NAME_LENGTH + 10)
    assertEquals(
        "a".repeat(MAX_LOCATION_NAME_LENGTH),
        townFor(response(component(long, "locality"))).getOrThrow(),
    )
  }

  @Test
  fun townName_failsWhenTheStatusIsNotOk() = runTest {
    listOf("ZERO_RESULTS", "REQUEST_DENIED", "OVER_QUERY_LIMIT").forEach { status ->
      val result = townFor(response(component("Morges", "locality"), status = status))
      assertTrue(status, result.exceptionOrNull() is IOException)
    }
    // ZERO_RESULTS comes without results at all.
    assertTrue(townFor("""{"status": "ZERO_RESULTS", "results": []}""").isFailure)
  }

  @Test
  fun townName_failsWithoutATownInTheResult() = runTest {
    assertTrue(townFor(response(component("Vaud", "administrative_area_level_1"))).isFailure)
    assertTrue(townFor(response(component("   ", "locality"))).isFailure)
    assertTrue(townFor("""{"status": "OK", "results": []}""").isFailure)
    assertTrue(townFor("not json").isFailure)
  }

  @Test
  fun townName_failsWhenTheRequestFails() = runTest {
    val offline = IOException("offline")
    val repository = GoogleGeocodingRepository("test-key") { throw offline }

    assertEquals(offline, repository.townName(morges).exceptionOrNull())
  }

  @Test
  fun townName_letsCancellationPropagate() = runTest {
    val repository = GoogleGeocodingRepository("test-key") { throw CancellationException("gone") }

    try {
      repository.townName(morges)
      fail("Cancellation must not be turned into a failed result")
    } catch (e: CancellationException) {
      assertEquals("gone", e.message)
    }
  }

  @Test
  fun townName_withThePlaceholderKey_failsWithoutARequest() = runTest {
    val result =
        repository(response(component("Morges", "locality")), "DEFAULT_API_KEY").townName(morges)

    assertTrue(result.isFailure)
    assertTrue(requested.isEmpty())
  }

  @Test
  fun request_asksForTheTownOfThePointWithTheKey() = runTest {
    val default = Locale.getDefault()
    // French writes decimals with a comma, which the API would not read.
    Locale.setDefault(Locale.FRANCE)
    try {
      repository(response(component("Morges", "locality")), "key&with=signs")
          .townName(Coordinates(46.5, 6.123456789))
    } finally {
      Locale.setDefault(default)
    }

    val url = requested.single()
    assertEquals(
        "https://maps.googleapis.com/maps/api/geocode/json",
        url.toString().substringBefore('?'),
    )
    val params =
        url.query.split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
    assertEquals("46.500%2C6.123", params["latlng"])
    assertEquals("neighborhood%7Csublocality%7Clocality", params["result_type"])
    assertEquals("fr-FR", params["language"])
    assertEquals("key%26with%3Dsigns", params["key"])
    assertFalse(url.query.contains("46,5"))
  }

  /** Runs [block] on the URL of a local server answering every GET with [code] and [body]. */
  private fun withServer(code: Int, body: String, block: suspend TestScope.(URL) -> Unit) =
      withServer(
          { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
          },
          block,
      )

  /** Runs [block] on the URL of a local server handling every request with [handler]. */
  private fun withServer(handler: HttpHandler, block: suspend TestScope.(URL) -> Unit) = runTest {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/", handler)
    server.start()
    try {
      block(URL("http://127.0.0.1:${server.address.port}/geocode"))
    } finally {
      server.stop(0)
    }
  }

  @Test
  fun httpGet_returnsTheBodyOfASuccessfulResponse() =
      withServer(200, """{"status": "OK"}""") { url ->
        assertEquals("""{"status": "OK"}""", httpGet(url))
      }

  @Test
  fun httpGet_failsOnAnErrorResponse() =
      withServer(403, "denied") { url ->
        val error = runCatching { httpGet(url) }.exceptionOrNull()
        assertTrue(error is IOException)
        assertEquals("HTTP 403", error?.message)
      }

  @Test
  fun httpGet_whenCancelled_stopsWithoutWaitingForTheResponse() {
    val received = CountDownLatch(1)
    val respond = CountDownLatch(1)
    withServer({
      received.countDown()
      respond.await()
    }) { url ->
      // The server stops only once its handler returns.
      try {
        val call = async { httpGet(url) }
        withContext(Dispatchers.IO) { received.await() }
        val millis = measureTimeMillis { call.cancelAndJoin() }
        assertTrue(runCatching { call.await() }.exceptionOrNull() is CancellationException)
        assertTrue("took $millis ms", millis < 5_000)
      } finally {
        respond.countDown()
      }
    }
  }
}
