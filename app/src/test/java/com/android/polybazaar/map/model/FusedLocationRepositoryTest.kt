// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.map.model

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class FusedLocationRepositoryTest {

  private val client = mock<FusedLocationProviderClient>()
  private val repository = FusedLocationRepository(client)

  private fun location(latitude: Double, longitude: Double) =
      Location("fused").apply {
        this.latitude = latitude
        this.longitude = longitude
      }

  @Test
  fun currentLocation_returnsTheFix_askingForBalancedAccuracy() = runTest {
    whenever(client.getCurrentLocation(any<Int>(), any<CancellationToken>()))
        .thenReturn(Tasks.forResult(location(46.52, 6.57)))

    val result = repository.currentLocation()

    assertEquals(Coordinates(46.52, 6.57), result.getOrNull())
    // Coarse is all the app asks for, so it never turns on GPS.
    verify(client)
        .getCurrentLocation(eq(Priority.PRIORITY_BALANCED_POWER_ACCURACY), any<CancellationToken>())
  }

  @Test
  fun noFix_fails() = runTest {
    // What Play Services answers when location is turned off or no fix can be obtained.
    whenever(client.getCurrentLocation(any<Int>(), any<CancellationToken>()))
        .thenReturn(Tasks.forResult(null))

    assertTrue(repository.currentLocation().isFailure)
  }

  @Test
  fun permissionNotGranted_fails() = runTest {
    whenever(client.getCurrentLocation(any<Int>(), any<CancellationToken>()))
        .thenThrow(SecurityException("Location permission not granted"))

    val result = repository.currentLocation()

    assertTrue(result.exceptionOrNull() is SecurityException)
  }

  @Test
  fun failedTask_fails() = runTest {
    whenever(client.getCurrentLocation(any<Int>(), any<CancellationToken>()))
        .thenReturn(Tasks.forException(IllegalStateException("Play Services unavailable")))

    assertTrue(repository.currentLocation().isFailure)
  }

  @Test
  fun cancelledCaller_cancelsTheLocationRequest() = runTest {
    val tokens = argumentCaptor<CancellationToken>()
    // A request that never answers, like a fix still being searched for.
    whenever(client.getCurrentLocation(any<Int>(), tokens.capture()))
        .thenReturn(TaskCompletionSource<Location?>().task)

    var result: Result<Coordinates>? = null
    val call = launch { result = repository.currentLocation() }
    runCurrent()
    call.cancel()
    runCurrent()

    assertTrue(tokens.firstValue.isCancellationRequested)
    // The cancellation propagates instead of coming back as a failure to show.
    assertNull(result)
  }

  @Test
  fun noFixWithinTenSeconds_fails_andCancelsTheRequest() = runTest {
    val tokens = argumentCaptor<CancellationToken>()
    // Play Services still searching (indoors, offline): the task never completes.
    whenever(client.getCurrentLocation(any<Int>(), tokens.capture()))
        .thenReturn(TaskCompletionSource<Location?>().task)

    var result: Result<Coordinates>? = null
    launch { result = repository.currentLocation() }
    advanceTimeBy(9_999)
    assertNull(result)
    advanceTimeBy(2)

    assertTrue(result!!.isFailure)
    assertTrue(tokens.firstValue.isCancellationRequested)
  }
}
