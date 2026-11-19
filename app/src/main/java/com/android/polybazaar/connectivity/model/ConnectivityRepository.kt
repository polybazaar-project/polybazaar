// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.connectivity.model

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the device can reach the internet, for features that need it (#339). */
interface ConnectivityRepository {
  /** True while the device has a network that reaches the internet. */
  val isOnline: StateFlow<Boolean>
}

/**
 * A [ConnectivityRepository] that follows Android's default network.
 *
 * Its callback is never unregistered: create one per process, with the application context, and
 * share it, so it lives as long as the app does.
 */
class AndroidConnectivityRepository(context: Context) : ConnectivityRepository {

  private val connectivityManager =
      context.applicationContext.getSystemService(ConnectivityManager::class.java)

  private val _isOnline =
      MutableStateFlow(
          reachesInternet(
              connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
          )
      )
  override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

  /** The default network the callback last reported, or null once it is lost. */
  @Volatile private var defaultNetwork: Network? = connectivityManager.activeNetwork

  init {
    connectivityManager.registerDefaultNetworkCallback(
        object : ConnectivityManager.NetworkCallback() {
          // Also called right after a new default network becomes available.
          override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            defaultNetwork = network
            _isOnline.value = reachesInternet(capabilities)
          }

          // During a Wi-Fi/mobile handoff the old network can be lost after the new one became
          // the default: only losing the current default means the device is offline.
          override fun onLost(network: Network) {
            if (network != defaultNetwork) return
            defaultNetwork = null
            _isOnline.value = false
          }
        }
    )
  }

  /** Whether [capabilities] reach the internet: validated, so not stuck behind a login page. */
  private fun reachesInternet(capabilities: NetworkCapabilities?): Boolean =
      capabilities != null &&
          capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
          capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
