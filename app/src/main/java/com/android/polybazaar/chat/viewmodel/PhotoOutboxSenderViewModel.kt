// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.chat.model.ChatRepository
import com.android.polybazaar.chat.model.PhotoOutbox
import com.android.polybazaar.chat.model.QueuedPhoto
import com.android.polybazaar.connectivity.model.ConnectivityRepository
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import com.android.polybazaar.error.model.toAppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Sends the signed-in user's chat photos kept in [outbox] for want of a connection, from any
 * screen: when the app starts online, when the connection returns, when a user signs in, and when a
 * photo is queued while online.
 *
 * Only the signed-in user's photos are sent, oldest first and one at a time; other accounts' photos
 * stay on the device. A sent photo is removed from the outbox. One that fails for want of a
 * connection is tried again a few times, after [RETRY_DELAYS_MS]: just after the connection
 * returns, Firestore can still be reconnecting. If it still fails, or the user signed out
 * meanwhile, it stays and is tried again once the connection returns or a user signs in, never in a
 * loop while online. One that fails for another reason, such as the listing paused or deleted or
 * the photo unreadable, is marked failed and never tried again: the conversation shows it as not
 * sent, and the user can remove it.
 *
 * A photo is never sent twice: each send reuses its message ID, which [ChatRepository.sendPhoto]
 * never writes twice, [PhotoOutbox.claim] stops a second send of it at once, and passes never
 * overlap: a single collector runs them one after the other, and collectLatest waits for a
 * cancelled pass, by going offline or signing out, to end before the next starts.
 */
class PhotoOutboxSenderViewModel(
    private val outbox: PhotoOutbox,
    private val chatRepository: ChatRepository,
    connectivity: ConnectivityRepository,
) : ViewModel() {

  private val signedInUid = MutableStateFlow<String?>(null)

  init {
    viewModelScope.launch {
      combine(connectivity.isOnline, signedInUid, ::Pair)
          .distinctUntilChanged()
          // Going offline or signing out cancels the session's pass; the photo being sent stays.
          .collectLatest { (isOnline, uid) ->
            if (!isOnline || uid == null) return@collectLatest
            // Each photo is tried once per session, so releasing a photo that failed, or removing
            // a sent one, does not start another send of it.
            val tried = mutableSetOf<String>()
            outbox.queued.collect { photos ->
              photos
                  .filter { it.senderUid == uid && it.error == null && it.messageId !in tried }
                  .forEach { photo ->
                    tried += photo.messageId
                    sendWithRetries(photo)
                  }
            }
          }
    }
  }

  /** Sets the signed-in user whose photos are sent, or null when nobody is signed in. */
  fun setSignedInUser(uid: String?) {
    signedInUid.value = uid
  }

  /**
   * Sends [photo], trying again after each of [RETRY_DELAYS_MS] while it fails for want of a
   * connection. The next photos wait meanwhile, so the order is kept; going offline or signing out
   * cancels the wait.
   */
  private suspend fun sendWithRetries(photo: QueuedPhoto) {
    for (wait in RETRY_DELAYS_MS) {
      if (send(photo) != AppError.NETWORK) return
      delay(wait)
    }
    send(photo)
  }

  /**
   * Sends [photo] with its own message ID, unless it was removed or is being sent. A message or
   * photo already on the server from an earlier try is reused.
   *
   * Returns why the send failed, or null when it was sent or not tried.
   */
  private suspend fun send(photo: QueuedPhoto): AppError? {
    val messageId = photo.messageId
    if (!outbox.claim(messageId)) return null
    var sent = false
    var failure: AppError? = null
    try {
      val conversation =
          chatRepository.observeConversation(photo.conversationId).first()
              ?: throw AppException(AppError.NOT_FOUND)
      chatRepository.sendPhoto(conversation, messageId, photo.photo, photo.caption)
      sent = true
    } catch (exception: Exception) {
      if (exception is CancellationException) throw exception
      val error = exception.toAppError()
      failure = error
      Log.w(LOG_TAG, "Could not send queued photo $messageId", exception)
      if (error !in KEPT_FOR_LATER) outbox.markFailed(messageId, error)
    } finally {
      // Also when cancelled: the photo stays queued, to be sent with the same ID next time.
      outbox.release(messageId)
    }
    // Released first, as a photo being sent cannot be removed. Cancelled before this, it stays
    // queued, and its next send finds it already on the server.
    if (sent) outbox.remove(messageId)
    return failure
  }

  private companion object {
    const val LOG_TAG = "PhotoOutboxSender"

    /**
     * The failures a later try can fix: no connection, or the user signed out while the photo was
     * being sent, before the pass was cancelled.
     */
    val KEPT_FOR_LATER = setOf(AppError.NETWORK, AppError.UNAUTHENTICATED)

    /**
     * How long to wait before each new try of a photo that failed for want of a connection while
     * online, in milliseconds: about 20 seconds in all, then it waits for the next reconnect.
     */
    val RETRY_DELAYS_MS = listOf(2_000L, 5_000L, 15_000L)
  }
}
