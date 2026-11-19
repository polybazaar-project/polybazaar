// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Runs the photo uploads of [ConversationViewModel] for as long as the app's process lives, so that
 * leaving a conversation does not cancel an upload after its message is written, which would leave
 * the photo pending for both participants. On the main thread, like viewModelScope.
 */
val photoUploadScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
