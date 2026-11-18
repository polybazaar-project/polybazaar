// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A connection a test turns on and off by setting [online]. Online unless told otherwise. */
class FakeConnectivityObserver(online: Boolean = true) : ConnectivityObserver {
  val online = MutableStateFlow(online)

  override fun observeOnline(): Flow<Boolean> = online
}
