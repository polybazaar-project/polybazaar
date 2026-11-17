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

  init {
    connectivityManager.registerDefaultNetworkCallback(
        object : ConnectivityManager.NetworkCallback() {
          // Also called right after a new default network becomes available.
          override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _isOnline.value = reachesInternet(capabilities)
          }

          override fun onLost(network: Network) {
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
