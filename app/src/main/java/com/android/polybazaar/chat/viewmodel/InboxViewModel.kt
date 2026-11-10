// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.chat.model.ChatRepository
import com.android.polybazaar.chat.model.Conversation
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import com.android.polybazaar.profile.model.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One row of the inbox.
 *
 * @property conversation the conversation, as the repository emitted it.
 * @property otherUser profile of the other participant, null while unknown or when they have no
 *   profile.
 */
data class InboxConversation(val conversation: Conversation, val otherUser: User?)

/** State of the inbox screen. */
data class InboxUiState(
    /** The signed-in user's conversations, most recent message first. */
    val conversations: List<InboxConversation> = emptyList(),
    /** True until the first list of conversations arrives or loading fails. */
    val isLoading: Boolean = true,
    /** Why the conversations could not be loaded; the rows already shown are kept. */
    val error: AppError? = null,
)

/** Lists the signed-in user's conversations for the inbox. */
class InboxViewModel(
    private val chatRepository: ChatRepository,
    @Suppress("unused") private val profileRepository: ProfileRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(InboxUiState())
  val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  /**
   * Loads the conversations of [myUid], the signed-in user, and keeps them updated. Calling it
   * again drops the previous load and starts over, which is how the screen retries after an error.
   */
  fun loadInbox(myUid: String) {
    loadJob?.cancel()
    _uiState.value = InboxUiState()
    loadJob = viewModelScope.launch { collectConversations(myUid) }
  }

  @Suppress("UNUSED_PARAMETER")
  private suspend fun collectConversations(myUid: String) {
    try {
      chatRepository.observeMyConversations().collect { conversations ->
        val rows = conversations.map { InboxConversation(it, otherUser = null) }
        _uiState.update { it.copy(conversations = rows, isLoading = false) }
      }
    } catch (exception: Exception) {
      if (exception is CancellationException) throw exception
      _uiState.update { it.copy(isLoading = false, error = exception.toAppError()) }
    }
  }
}
