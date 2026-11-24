// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

import android.net.Uri
import com.android.polybazaar.error.model.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A [PhotoOutbox] in memory, for tests. [enqueue] keeps the photo as it is, sent by [senderUid], or
 * fails with [failure] when it is set. [claim], [release], [markFailed] and [remove] behave as in
 * [FilePhotoOutbox].
 */
class FakePhotoOutbox(var senderUid: String = "me") : PhotoOutbox {
  private val _queued = MutableStateFlow<List<QueuedPhoto>>(emptyList())
  override val queued: StateFlow<List<QueuedPhoto>> = _queued.asStateFlow()

  var failure: Exception? = null

  /** Adds [photo] as if it had been queued earlier, as after a restart. */
  fun add(photo: QueuedPhoto) {
    _queued.update { it + photo }
  }

  override suspend fun enqueue(
      conversationId: String,
      messageId: String,
      photo: Uri,
      caption: String?,
  ) {
    failure?.let { throw it }
    _queued.update { photos ->
      photos.filterNot { it.messageId == messageId } +
          QueuedPhoto(messageId, conversationId, senderUid, photo, caption)
    }
  }

  override fun claim(messageId: String): Boolean {
    var claimed = false
    _queued.update { photos ->
      claimed = photos.any { it.messageId == messageId && !it.sending }
      if (claimed) photos.map { if (it.messageId == messageId) it.copy(sending = true) else it }
      else photos
    }
    return claimed
  }

  override fun release(messageId: String) {
    _queued.update { photos ->
      photos.map { if (it.messageId == messageId) it.copy(sending = false) else it }
    }
  }

  override suspend fun markFailed(messageId: String, error: AppError) {
    _queued.update { photos ->
      photos.map { if (it.messageId == messageId) it.copy(error = error) else it }
    }
  }

  override suspend fun remove(messageId: String): Boolean {
    var removed = false
    _queued.update { photos ->
      removed = photos.any { it.messageId == messageId && !it.sending }
      if (removed) photos.filterNot { it.messageId == messageId } else photos
    }
    return removed
  }
}
