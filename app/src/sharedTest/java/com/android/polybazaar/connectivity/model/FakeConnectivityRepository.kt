// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.connectivity.model

import kotlinx.coroutines.flow.MutableStateFlow

/** A [ConnectivityRepository] for tests: set [isOnline] to go offline or back online. */
class FakeConnectivityRepository(online: Boolean = true) : ConnectivityRepository {
  override val isOnline = MutableStateFlow(online)
}
