// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.connectivity.model

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

/** [AndroidConnectivityRepository] on Robolectric's [ConnectivityManager]. */
@RunWith(AndroidJUnit4::class)
class AndroidConnectivityRepositoryTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

  private fun capabilities(vararg capabilities: Int): NetworkCapabilities =
      ShadowNetworkCapabilities.newInstance().also { network ->
        capabilities.forEach { shadowOf(network).addCapability(it) }
      }

  private val validated =
      capabilities(
          NetworkCapabilities.NET_CAPABILITY_INTERNET,
          NetworkCapabilities.NET_CAPABILITY_VALIDATED,
      )

  /** Gives the active network [capabilities], then creates the repository. */
  private fun repositoryOn(capabilities: NetworkCapabilities?): AndroidConnectivityRepository {
    shadowOf(connectivityManager)
        .setNetworkCapabilities(connectivityManager.activeNetwork, capabilities)
    return AndroidConnectivityRepository(context)
  }

  @Test
  fun startsOffline_withoutAValidatedInternetNetwork() {
    // Behind a login page, the network has the internet capability but is not validated.
    assertFalse(
        repositoryOn(capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET)).isOnline.value
    )
    assertFalse(repositoryOn(null).isOnline.value)
  }

  @Test
  fun followsTheDefaultNetwork() {
    val repository = repositoryOn(validated)
    val callback = shadowOf(connectivityManager).networkCallbacks.single()
    val network = connectivityManager.activeNetwork!!
    assertTrue(repository.isOnline.value)

    callback.onLost(network)
    assertFalse(repository.isOnline.value)

    callback.onCapabilitiesChanged(network, validated)
    assertTrue(repository.isOnline.value)
  }

  @Test
  fun staysOnline_whenThePreviousNetworkIsLostAfterAHandoff() {
    val repository = repositoryOn(validated)
    val callback = shadowOf(connectivityManager).networkCallbacks.single()
    val wifi = connectivityManager.activeNetwork!!
    val mobile = ShadowNetwork.newInstance(shadowOf(wifi).netId + 1)

    // Mobile becomes the default, validated, before Wi-Fi's loss arrives.
    callback.onCapabilitiesChanged(mobile, validated)
    callback.onLost(wifi)
    assertTrue(repository.isOnline.value)

    callback.onLost(mobile)
    assertFalse(repository.isOnline.value)
  }
}
