// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.viewmodel

import com.android.polybazaar.auth.model.User
import com.android.polybazaar.chat.model.Conversation
import com.android.polybazaar.chat.model.FakeChatRepository
import com.android.polybazaar.profile.model.FakeProfileRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var chat: FakeChatRepository
  private lateinit var profiles: FakeProfileRepository
  private lateinit var viewModel: InboxViewModel

  private val me = User(uid = "me", email = "", username = "Me")
  private val alice = User(uid = "alice", email = "", username = "Alice")

  /** I rent Alice's drill. */
  private val drillWithAlice =
      Conversation(
          listingId = "drill",
          listingTitle = "Bosch drill",
          renterUid = me.uid,
          lenderUid = alice.uid,
          lastMessageAt = Instant.ofEpochSecond(200),
      )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    chat = FakeChatRepository()
    profiles = FakeProfileRepository(mapOf(me.uid to me, alice.uid to alice))
    viewModel = InboxViewModel(chat, profiles)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** Loads the inbox of [me] and lets the first emission through. */
  private fun loadInbox() {
    viewModel.loadInbox(me.uid)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @Test
  fun loadInbox_showsConversationWithTheOtherPersonsProfile() {
    chat.myConversations.value = Result.success(listOf(drillWithAlice))

    loadInbox()

    assertEquals(
        InboxUiState(
            conversations = listOf(InboxConversation(drillWithAlice, alice)),
            isLoading = false,
            error = null,
        ),
        viewModel.uiState.value,
    )
  }
}
