// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidConnectivityObserverTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
  private val shadow = shadowOf(connectivityManager)

  @Before
  fun setUp() {
    shadow.setDefaultNetworkActive(true)
  }

  private fun capabilities(vararg capabilities: Int): NetworkCapabilities =
      ShadowNetworkCapabilities.newInstance().also { caps ->
        capabilities.forEach { shadowOf(caps).addCapability(it) }
      }

  private val online
    get() =
        capabilities(
            NetworkCapabilities.NET_CAPABILITY_INTERNET,
            NetworkCapabilities.NET_CAPABILITY_VALIDATED,
        )

  /** Everything [AndroidConnectivityObserver.observeOnline] emits until [job] is cancelled. */
  private fun TestScope.observe(): Pair<MutableList<Boolean>, Job> {
    val values = mutableListOf<Boolean>()
    val job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          AndroidConnectivityObserver(context).observeOnline().toList(values)
        }
    return values to job
  }

  private fun assertStartsWith(expected: Boolean) = runTest {
    val (values, job) = observe()
    job.cancel()
    assertEquals(listOf(expected), values)
  }

  @Test
  fun startsOnlineWhenTheActiveNetworkReachesTheInternet() {
    shadow.setNetworkCapabilities(connectivityManager.activeNetwork, online)

    assertStartsWith(true)
  }

  @Test
  fun startsOfflineWithoutAnActiveNetwork() {
    shadow.setDefaultNetworkActive(false)

    assertStartsWith(false)
  }

  @Test
  fun startsOfflineWhenTheActiveNetworkHasNoCapabilities() {
    shadow.setNetworkCapabilities(connectivityManager.activeNetwork, null)

    assertStartsWith(false)
  }

  @Test
  fun startsOfflineWhenTheActiveNetworkHasNoInternet() {
    shadow.setNetworkCapabilities(
        connectivityManager.activeNetwork,
        capabilities(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
    )

    assertStartsWith(false)
  }

  @Test
  fun startsOfflineWhenTheInternetIsNotValidated() {
    shadow.setNetworkCapabilities(
        connectivityManager.activeNetwork,
        capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET),
    )

    assertStartsWith(false)
  }

  @Test
  fun followsTheDefaultNetworkAndStopsListeningWhenCancelled() = runTest {
    val network = connectivityManager.activeNetwork!!
    shadow.setDefaultNetworkActive(false)
    val (values, job) = observe()
    val callback = shadow.networkCallbacks.single()

    callback.onCapabilitiesChanged(network, online)
    callback.onCapabilitiesChanged(network, online)
    callback.onLost(network)
    job.cancel()

    assertEquals(listOf(false, true, false), values)
    assertTrue(shadow.networkCallbacks.isEmpty())
  }
}
