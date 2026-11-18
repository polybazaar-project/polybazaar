// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Tells whether the device can reach the internet, so work that needs it can wait for it. */
fun interface ConnectivityObserver {
  /** Emits whether the device is online now, then again each time that changes. */
  fun observeOnline(): Flow<Boolean>
}

/**
 * Online means the default network has internet access that Android has checked, so a Wi-Fi with no
 * internet behind it counts as offline.
 */
class AndroidConnectivityObserver(context: Context) : ConnectivityObserver {
  private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

  override fun observeOnline(): Flow<Boolean> = callbackFlow {
    val callback =
        object : ConnectivityManager.NetworkCallback() {
          override fun onCapabilitiesChanged(
              network: Network,
              capabilities: NetworkCapabilities,
          ) {
            trySend(capabilities.reachesInternet())
          }

          override fun onLost(network: Network) {
            trySend(false)
          }
        }
    // The callback only reports a network once there is one, so offline it says nothing.
    val active = connectivityManager.activeNetwork
    trySend(
        active != null &&
            connectivityManager.getNetworkCapabilities(active)?.reachesInternet() == true
    )
    connectivityManager.registerDefaultNetworkCallback(callback)
    awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
  }
      .distinctUntilChanged()

  private fun NetworkCapabilities.reachesInternet(): Boolean =
      hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
          hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
