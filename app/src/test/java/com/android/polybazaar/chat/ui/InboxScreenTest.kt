// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.ui

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.chat.model.Conversation
import com.android.polybazaar.chat.model.FakeChatRepository
import com.android.polybazaar.chat.viewmodel.InboxConversation
import com.android.polybazaar.chat.viewmodel.InboxUiState
import com.android.polybazaar.chat.viewmodel.InboxViewModel
import com.android.polybazaar.chat.viewmodel.ListingStatus
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.profile.model.FakeProfileRepository
import java.net.UnknownHostException
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InboxScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private val now = Instant.parse("2026-11-11T12:00:00Z")
  private val alice = User(uid = "alice", email = "", username = "Alice")

  /** A conversation with Alice about [listingId], whose last message is [minutesAgo] old. */
  private fun conversation(listingId: String, minutesAgo: Long = 5) =
      Conversation(
          listingId = listingId,
          listingTitle = "Stored $listingId",
          renterUid = "me",
          lenderUid = alice.uid,
          lastMessageAt = now.minus(Duration.ofMinutes(minutesAgo)),
      )

  /**
   * The row of [conversation] with Alice, its listing in [status] and titled "Current id"; read and
   * with a last message from Alice unless [isUnread] or [isLastMessageMine] say otherwise.
   */
  private fun row(
      conversation: Conversation,
      status: ListingStatus = ListingStatus.ACTIVE,
      otherUser: User? = alice,
      isUnread: Boolean = false,
      isLastMessageMine: Boolean = false,
  ) =
      InboxConversation(
          conversation = conversation,
          otherUser = otherUser,
          listingTitle = "Current ${conversation.listingId}",
          listingStatus = status,
          listingPhotoPath = null,
          isUnread = isUnread,
          isLastMessageMine = isLastMessageMine,
      )

  private fun loaded(vararg rows: InboxConversation, error: AppError? = null) =
      InboxUiState(conversations = rows.toList(), isLoading = false, error = error)

  private fun setContent(state: InboxUiState, actions: InboxActions = InboxActions()) {
    composeTestRule.setContent { InboxContent(state = state, actions = actions, now = now) }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  /** The row of [conversation], merged so it holds the texts of its children. */
  private fun rowOf(conversation: Conversation) =
      composeTestRule.onNodeWithTag(InboxScreenTags.row(conversation.id))

  /** The node tagged [tag] inside the row of [conversation]. */
  private fun nodeInRow(conversation: Conversation, tag: String) =
      composeTestRule.onNode(
          hasTestTag(tag) and hasAnyAncestor(hasTestTag(InboxScreenTags.row(conversation.id))),
          useUnmergedTree = true,
      )

  @Test
  fun loading_showsOnlyTheIndicator() {
    setContent(InboxUiState())

    node(InboxScreenTags.LOADING_INDICATOR).assertIsDisplayed()
    node(InboxScreenTags.LIST).assertDoesNotExist()
    node(InboxScreenTags.EMPTY_STATE).assertDoesNotExist()
    node(InboxScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun empty_showsBrowseListingsWhichCallsOnBrowse() {
    var browses = 0
    setContent(loaded(), InboxActions(onBrowse = { browses++ }))

    node(InboxScreenTags.EMPTY_STATE).assertIsDisplayed()
    node(InboxScreenTags.BROWSE_BUTTON).performClick()

    assertEquals(1, browses)
    node(InboxScreenTags.LIST).assertDoesNotExist()
    node(InboxScreenTags.LOADING_INDICATOR).assertDoesNotExist()
  }

  @Test
  fun errorWithoutRows_showsTheErrorWithRetryInsteadOfTheEmptyState() {
    var retries = 0
    setContent(loaded(error = AppError.NETWORK), InboxActions(onRetry = { retries++ }))

    node(InboxScreenTags.ERROR_MESSAGE).assertTextEquals(context.getString(R.string.error_network))
    node(InboxScreenTags.RETRY_BUTTON).performClick()

    assertEquals(1, retries)
    node(InboxScreenTags.EMPTY_STATE).assertDoesNotExist()
    node(InboxScreenTags.LIST).assertDoesNotExist()
  }

  @Test
  fun errorWithRows_keepsTheRowsAndShowsRetry() {
    var retries = 0
    val drill = conversation("drill")
    setContent(
        loaded(row(drill), error = AppError.NETWORK),
        InboxActions(onRetry = { retries++ }),
    )

    node(InboxScreenTags.row(drill.id)).assertIsDisplayed()
    node(InboxScreenTags.ERROR_MESSAGE).assertTextEquals(context.getString(R.string.error_network))
    node(InboxScreenTags.RETRY_BUTTON).performClick()

    assertEquals(1, retries)
  }

  @Test
  fun rowsLoadingAgain_stayShownWithoutTheIndicator() {
    val drill = conversation("drill")
    setContent(InboxUiState(conversations = listOf(row(drill)), isLoading = true))

    node(InboxScreenTags.row(drill.id)).assertIsDisplayed()
    node(InboxScreenTags.LOADING_INDICATOR).assertDoesNotExist()
  }

  @Test
  fun row_showsTheUsernameCurrentTitleAndTime() {
    val drill = conversation("drill", minutesAgo = 5)
    setContent(loaded(row(drill)))

    rowOf(drill)
        .assert(hasText(context.getString(R.string.inbox_username, alice.username)))
        .assert(hasText("Current drill"))
        .assert(hasText(context.getString(R.string.inbox_time_minutes, 5L)))
  }

  @Test
  fun rowTime_isSpokenInFullWords() {
    // TalkBack would read "5m" as five meters.
    val drill = conversation("drill", minutesAgo = 5)
    setContent(loaded(row(drill)))

    rowOf(drill).assert(hasContentDescription("5 minutes ago"))
  }

  @Test
  fun rowWithoutProfile_showsNoUsername() {
    val drill = conversation("drill")
    setContent(loaded(row(drill, otherUser = null)))

    rowOf(drill)
        .assert(hasText("Current drill"))
        .assert(hasText(context.getString(R.string.inbox_username, ""), substring = true).not())
  }

  @Test
  fun rows_showABadgeOnlyForPausedAndDeletedListings() {
    val active = conversation("active")
    val paused = conversation("paused")
    val unknown = conversation("unknown")
    val deleted = conversation("deleted")
    setContent(
        loaded(
            row(active),
            row(paused, ListingStatus.PAUSED),
            row(unknown, ListingStatus.UNKNOWN),
            row(deleted, ListingStatus.DELETED),
        )
    )

    nodeInRow(paused, InboxScreenTags.PAUSED_BADGE)
        .assertTextEquals(context.getString(R.string.inbox_listing_paused))
    nodeInRow(deleted, InboxScreenTags.DELETED_BADGE)
        .assertTextEquals(context.getString(R.string.inbox_listing_deleted))
    composeTestRule
        .onAllNodesWithTag(InboxScreenTags.PAUSED_BADGE, useUnmergedTree = true)
        .assertCountEquals(1)
    composeTestRule
        .onAllNodesWithTag(InboxScreenTags.DELETED_BADGE, useUnmergedTree = true)
        .assertCountEquals(1)
  }

  @Test
  fun rowPreview_showsTheLastMessage() {
    val drill = conversation("drill").copy(lastMessageText = "Does it come with the bits?")
    setContent(loaded(row(drill)))

    node(InboxScreenTags.preview(drill.id)).assertTextEquals("Does it come with the bits?")
  }

  @Test
  fun rowPreview_ofMyOwnMessage_startsWithYou() {
    val drill = conversation("drill").copy(lastMessageText = "See you at 6")
    setContent(loaded(row(drill, isLastMessageMine = true)))

    node(InboxScreenTags.preview(drill.id))
        .assertTextEquals(context.getString(R.string.inbox_last_message_mine, "See you at 6"))
  }

  @Test
  fun rowWithoutLastMessageText_showsNoPreview() {
    val drill = conversation("drill")
    setContent(loaded(row(drill, isUnread = true)))

    node(InboxScreenTags.preview(drill.id)).assertDoesNotExist()
    rowOf(drill).assert(hasText("Current drill"))
  }

  @Test
  fun unreadDot_isShownOnlyOnUnreadRows() {
    val unread = conversation("unread").copy(lastMessageText = "Hi")
    val read = conversation("read").copy(lastMessageText = "Hi")
    setContent(loaded(row(unread, isUnread = true), row(read)))

    nodeInRow(unread, InboxScreenTags.UNREAD_DOT).assertIsDisplayed()
    composeTestRule
        .onAllNodesWithTag(InboxScreenTags.UNREAD_DOT, useUnmergedTree = true)
        .assertCountEquals(1)
  }

  @Test
  fun unreadRow_isReadAsUnreadByTalkBack() {
    val unreadLabel = context.getString(R.string.inbox_unread)
    val unread = conversation("unread").copy(lastMessageText = "Hi")
    val read = conversation("read").copy(lastMessageText = "Hi")
    setContent(loaded(row(unread, isUnread = true), row(read)))

    rowOf(unread).assert(hasContentDescription(unreadLabel))
    rowOf(read).assert(hasContentDescription(unreadLabel).not())
  }

  @Test
  fun tappingARow_callsOnConversationClickWithItsConversation() {
    val clicked = mutableListOf<Conversation>()
    val drill = conversation("drill")
    val tent = conversation("tent", minutesAgo = 30)
    setContent(
        loaded(row(drill), row(tent)),
        InboxActions(onConversationClick = { clicked += it }),
    )

    node(InboxScreenTags.row(tent.id)).performClick()

    assertEquals(listOf(tent), clicked)
  }

  @Test
  fun rowsWithoutOnConversationClick_areNotClickable() {
    val drill = conversation("drill")
    setContent(loaded(row(drill)))

    rowOf(drill).assertHasNoClickAction().assert(hasText("Current drill"))
  }

  @Test
  fun screen_loadsTheInboxOfMyUid_andRetryLoadsItAgain() {
    val drill = conversation("drill")
    val chat = FakeChatRepository()
    chat.myConversations.value = Result.failure(UnknownHostException("offline"))
    val viewModel =
        InboxViewModel(
            chat,
            FakeProfileRepository(mapOf(alice.uid to alice)),
            FakeListingRepository(),
        )
    composeTestRule.setContent {
      InboxScreen(viewModel = viewModel, myUid = "me", onConversationClick = {}, onBrowse = {})
    }

    node(InboxScreenTags.ERROR_MESSAGE).assertTextEquals(context.getString(R.string.error_network))

    chat.myConversations.value = Result.success(listOf(drill))
    node(InboxScreenTags.RETRY_BUTTON).performClick()
    composeTestRule.waitForIdle()

    node(InboxScreenTags.row(drill.id)).assertIsDisplayed()
    node(InboxScreenTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun contentPreview_showsOneRowPerStatusWithTheirBadges() {
    composeTestRule.setContent { InboxContentPreview() }

    node(InboxScreenTags.LIST).assertIsDisplayed()
    composeTestRule
        .onAllNodesWithTag(InboxScreenTags.PAUSED_BADGE, useUnmergedTree = true)
        .assertCountEquals(1)
    composeTestRule
        .onAllNodesWithTag(InboxScreenTags.DELETED_BADGE, useUnmergedTree = true)
        .assertCountEquals(1)
  }

  @Test
  fun emptyPreview_showsTheEmptyState() {
    composeTestRule.setContent { InboxEmptyPreview() }

    node(InboxScreenTags.EMPTY_STATE).assertIsDisplayed()
  }
}
