// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.chat.model.ChatMessage
import com.android.polybazaar.chat.model.ChatRules
import com.android.polybazaar.chat.model.Conversation
import com.android.polybazaar.chat.model.FakeChatRepository
import com.android.polybazaar.chat.viewmodel.ConversationListingStatus
import com.android.polybazaar.chat.viewmodel.ConversationUiState
import com.android.polybazaar.chat.viewmodel.ConversationViewModel
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingLocation
import com.android.polybazaar.listing.model.ServiceListing
import com.android.polybazaar.profile.model.FakeProfileRepository
import java.net.UnknownHostException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class ConversationScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private val james = User(uid = "lender", email = "james@epfl.ch", username = "james")

  private val conversation =
      Conversation(
          listingId = "drill",
          listingTitle = "Bosch drill set",
          renterUid = "renter",
          lenderUid = james.uid,
          lastMessageAt = Instant.parse("2026-11-11T09:14:00Z"),
      )

  private val question =
      ChatMessage("m1", "renter", "Is it free on Saturday?", Instant.parse("2026-11-11T09:12:00Z"))
  private val answer =
      ChatMessage("m2", james.uid, "Yes, from ten.", Instant.parse("2026-11-11T09:14:00Z"))

  /** The renter's view of a loaded conversation about an active listing. */
  private val loaded =
      ConversationUiState(
          myUid = "renter",
          messages = listOf(question, answer),
          conversation = conversation,
          listingTitle = "Bosch drill set",
          listingStatus = ConversationListingStatus.ACTIVE,
          isRenter = true,
          otherUser = james,
          isLoading = false,
      )

  /** The time the tests look at the screen: the day of [question] and [answer]. */
  private val now = Instant.parse("2026-11-11T12:00:00Z")

  private fun setContent(
      uiState: ConversationUiState,
      onBack: () -> Unit = {},
      onDraftChange: (String) -> Unit = {},
      onSend: () -> Unit = {},
      onRetry: () -> Unit = {},
      onDismissSendError: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      ConversationContent(
          uiState = uiState,
          onBack = onBack,
          onDraftChange = onDraftChange,
          onSend = onSend,
          onRetry = onRetry,
          onDismissSendError = onDismissSendError,
          zone = ZoneOffset.UTC,
          now = now,
      )
    }
  }

  private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun text(text: String) = composeTestRule.onNodeWithText(text, useUnmergedTree = true)

  private fun hasDraft(text: String) =
      SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text))

  private fun tooLongSentence(): String =
      context.resources.getQuantityString(
          R.plurals.conversation_length_too_long,
          ChatRules.MAX_MESSAGE_LENGTH,
          ChatRules.MAX_MESSAGE_LENGTH,
      )

  private fun isPoliteLiveRegion() =
      SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

  private fun hasNoText() = SemanticsMatcher.keyNotDefined(SemanticsProperties.Text)

  /** Every semantics property of [node], by key, for comparing what TalkBack sees. */
  private fun semanticsOf(node: SemanticsNodeInteraction): Map<String, Any?> =
      node.fetchSemanticsNode().config.associate { (key, value) -> key.name to value }

  /**
   * Asserts [node] lies entirely inside the message list, from its unclipped bounds:
   * assertIsDisplayed and getBoundsInRoot, which clips to the list, would also pass for a message
   * that only peeks in at the list's edge.
   */
  private fun assertFullyInTheList(node: SemanticsNodeInteraction) {
    val list = node(ConversationScreenTags.MESSAGE_LIST).fetchSemanticsNode().boundsInRoot
    val unclipped = node.fetchSemanticsNode()
    val top = unclipped.positionInRoot.y
    val bottom = top + unclipped.size.height
    assertTrue(
        "Spans $top..$bottom px, outside the list at $list",
        top >= list.top && bottom <= list.bottom,
    )
  }

  /** Every text of the empty state. */
  private fun emptyStateTexts() =
      composeTestRule.onAllNodes(
          hasAnyAncestor(hasTestTag(ConversationScreenTags.EMPTY_STATE)) and
              SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
          useUnmergedTree = true,
      )

  /** Every text of the listing summary. */
  private fun summaryTexts() =
      composeTestRule.onAllNodes(
          hasAnyAncestor(hasTestTag(ConversationScreenTags.LISTING_SUMMARY)) and
              SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
          useUnmergedTree = true,
      )

  private fun pill() =
      composeTestRule.onNodeWithTag(
          ConversationScreenTags.LISTING_STATUS_PILL,
          useUnmergedTree = true,
      )

  private val drill =
      ItemListing(
          ListingCategory.ITEM_TOOLS,
          8.0,
          "Bosch drill set",
          "Cordless drill.",
          listingId = "drill",
          ownerUid = james.uid,
      )

  /**
   * Shows [ConversationScreen] with a real ViewModel: the renter's conversation about James's
   * drill, active unless [listings] says otherwise. It has not started unless [chat] holds it.
   */
  private fun showScreen(
      chat: FakeChatRepository,
      listings: FakeListingRepository = FakeListingRepository(allListings = listOf(drill)),
      lifecycleOwner: LifecycleOwner? = null,
  ): ConversationViewModel {
    val viewModel =
        ConversationViewModel(
            listingId = "drill",
            renterUid = "renter",
            myUid = "renter",
            chatRepository = chat,
            profileRepository = FakeProfileRepository(mapOf(james.uid to james)),
            listingRepository = listings,
        )
    composeTestRule.setContent {
      CompositionLocalProvider(
          LocalLifecycleOwner provides (lifecycleOwner ?: LocalLifecycleOwner.current)
      ) {
        ConversationScreen(viewModel = viewModel, onBack = {})
      }
    }
    return viewModel
  }

  /** A lifecycle a test moves by hand, to put the screen in the background and back. */
  private class ManualLifecycleOwner : LifecycleOwner {
    val registry =
        LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle
      get() = registry
  }

  @Test
  fun loading_showsTheIndicator_andLetsTheUserTypeButNotSend() {
    setContent(ConversationUiState(myUid = "renter"))

    node(ConversationScreenTags.LOADING_INDICATOR).assertIsDisplayed()
    node(ConversationScreenTags.MESSAGE_FIELD).assertIsEnabled()
    node(ConversationScreenTags.SEND_BUTTON).assertIsNotEnabled()
    node(ConversationScreenTags.EMPTY_STATE).assertDoesNotExist()
  }

  @Test
  fun loadFailedWithoutMessages_showsWhy_andRetryReloads() {
    var retries = 0
    // The rest is still loading: the error shows anyway.
    setContent(
        ConversationUiState(myUid = "renter", loadError = AppError.NETWORK),
        onRetry = { retries++ },
    )

    text(string(R.string.conversation_load_error_title)).assertIsDisplayed()
    node(ConversationScreenTags.ERROR_MESSAGE).assertTextEquals(string(R.string.error_network))
    node(ConversationScreenTags.LOADING_INDICATOR).assertDoesNotExist()
    node(ConversationScreenTags.RETRY_BUTTON).performClick()
    assertEquals(1, retries)
    node(ConversationScreenTags.MESSAGE_FIELD).assertIsEnabled()
    node(ConversationScreenTags.SEND_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun loadFailedWithoutMessages_sendsWhenTheViewModelAllowsIt() {
    var sends = 0
    setContent(
        loaded.copy(
            messages = emptyList(),
            loadError = AppError.NETWORK,
            draft = "Hi",
            canSend = true,
        ),
        onSend = { sends++ },
    )

    node(ConversationScreenTags.ERROR_MESSAGE).assertIsDisplayed()
    node(ConversationScreenTags.SEND_BUTTON).assertIsEnabled()
    node(ConversationScreenTags.SEND_BUTTON).performClick()
    assertEquals(1, sends)
  }

  @Test
  fun reloadFailed_keepsTheMessages_withTheErrorAndRetryAboveThem() {
    var retries = 0
    setContent(loaded.copy(loadError = AppError.NETWORK), onRetry = { retries++ })

    node(ConversationScreenTags.myMessage("m1")).assertIsDisplayed()
    node(ConversationScreenTags.theirMessage("m2")).assertIsDisplayed()
    node(ConversationScreenTags.ERROR_MESSAGE).assertTextEquals(string(R.string.error_network))
    text(string(R.string.conversation_load_error_title)).assertDoesNotExist()
    val error = node(ConversationScreenTags.ERROR_MESSAGE).getBoundsInRoot()
    val list = node(ConversationScreenTags.MESSAGE_LIST).getBoundsInRoot()
    assertTrue(error.bottom <= list.top)
    node(ConversationScreenTags.RETRY_BUTTON).performClick()
    assertEquals(1, retries)
    node(ConversationScreenTags.MESSAGE_FIELD).assertIsEnabled()
  }

  @Test
  fun emptyActiveListing_invitesAFirstMessageToTheOtherUser() {
    setContent(loaded.copy(messages = emptyList(), conversation = null))

    text(string(R.string.conversation_empty_title, "james")).assertIsDisplayed()
    text("No messages yet. Ask about “Bosch drill set”, its availability or a good time to meet.")
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ConversationScreenTags.EMPTY_TAGLINE, useUnmergedTree = true)
        .assertTextEquals("Good tools. Great neighbours.")
    node(ConversationScreenTags.MESSAGE_FIELD).assertIsEnabled()
  }

  @Test
  fun emptyPausedListing_onlySaysThereAreNoMessages() {
    setContent(
        loaded.copy(
            messages = emptyList(),
            conversation = null,
            listingStatus = ConversationListingStatus.PAUSED,
        )
    )

    emptyStateTexts().assertCountEquals(1)
    emptyStateTexts()[0].assertTextEquals(string(R.string.conversation_empty_body_no_title))
  }

  @Test
  fun emptyDeletedListing_onlySaysThereAreNoMessages() {
    setContent(
        loaded.copy(
            messages = emptyList(),
            conversation = null,
            listingStatus = ConversationListingStatus.DELETED,
        )
    )

    emptyStateTexts().assertCountEquals(1)
    emptyStateTexts()[0].assertTextEquals(string(R.string.conversation_empty_body_no_title))
  }

  @Test
  fun emptyWithoutTheProfileOrTheTitle_saysHelloWithoutThem() {
    setContent(ConversationUiState(myUid = "renter", isLoading = false))

    node(ConversationScreenTags.EMPTY_STATE).assertIsDisplayed()
    text(string(R.string.conversation_empty_title_unknown)).assertIsDisplayed()
    text(string(R.string.conversation_empty_body_no_title)).assertIsDisplayed()
    node(ConversationScreenTags.OTHER_USERNAME).assertDoesNotExist()
  }

  @Test
  fun messages_showOldestFirst() {
    setContent(loaded)

    val older = node(ConversationScreenTags.myMessage("m1")).getBoundsInRoot()
    val newer = node(ConversationScreenTags.theirMessage("m2")).getBoundsInRoot()
    assertTrue(older.bottom <= newer.top)
  }

  @Test
  fun myMessagesSitAtTheEnd_andTheirsAtTheStart() {
    setContent(loaded)

    node(ConversationScreenTags.theirMessage("m1")).assertDoesNotExist()
    node(ConversationScreenTags.myMessage("m2")).assertDoesNotExist()
    val mine = node(ConversationScreenTags.myMessage("m1")).getBoundsInRoot()
    val theirs = node(ConversationScreenTags.theirMessage("m2")).getBoundsInRoot()
    // Mine is further towards the end on both edges, whatever the widths of the two bubbles.
    assertTrue(mine.left > theirs.left)
    assertTrue(mine.right > theirs.right)
  }

  // Real text widths, so the message really is long, and a list over 500dp wide, so only the cap
  // can stop the bubble at 300dp.
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  @Config(qualifiers = "w600dp-h720dp")
  fun longMessage_wrapsInsideA300dpBubble() {
    val long = question.copy(text = "Could I pick it up early? ".repeat(10))
    setContent(loaded.copy(messages = listOf(long)))

    node(ConversationScreenTags.myMessage("m1")).assertWidthIsEqualTo(300.dp)
  }

  // Real text heights, and a window shorter than the long message, so either its top or its
  // bottom is out of view.
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  @Config(qualifiers = "w360dp-h480dp")
  fun longNewestMessage_showsItsFooter() {
    val long =
        ChatMessage(
            "m3",
            "renter",
            "I can pick it up after my lab, around six. ".repeat(23),
            Instant.parse("2026-11-11T09:20:00Z"),
        )
    setContent(loaded.copy(messages = listOf(question, answer, long)))

    assertFullyInTheList(text("You · 09:20"))
  }

  @Test
  fun footers_sayWhoSentEachMessageAndWhen() {
    setContent(loaded)

    node(ConversationScreenTags.myMessage("m1"))
        .assertTextEquals("Is it free on Saturday?", "You · 09:12")
    node(ConversationScreenTags.theirMessage("m2"))
        .assertTextEquals("Yes, from ten.", "james · 09:14")
  }

  @Test
  fun unknownProfile_showsOnlyTheTimeUnderTheirMessages() {
    setContent(loaded.copy(otherUser = null))

    node(ConversationScreenTags.theirMessage("m2")).assertTextEquals("Yes, from ten.", "09:14")
    node(ConversationScreenTags.OTHER_USERNAME).assertDoesNotExist()
  }

  @Test
  fun manyMessages_openOnTheLatest_andFollowANewOne() {
    val start = Instant.parse("2026-11-11T08:00:00Z")
    val many =
        List(50) {
          ChatMessage("m$it", if (it % 2 == 0) "renter" else james.uid, "Message $it", start)
        }
    var uiState by mutableStateOf(loaded.copy(messages = many))
    composeTestRule.setContent { ConversationContent(uiState = uiState, zone = ZoneOffset.UTC) }

    assertFullyInTheList(node(ConversationScreenTags.theirMessage("m49")))
    node(ConversationScreenTags.myMessage("m0")).assertIsNotDisplayed()

    uiState = uiState.copy(messages = many + ChatMessage("m50", "renter", "On my way", start))

    assertFullyInTheList(node(ConversationScreenTags.myMessage("m50")))
    // The oldest message was only out of view, at the top of the list.
    node(ConversationScreenTags.MESSAGE_LIST)
        .performScrollToNode(hasTestTag(ConversationScreenTags.myMessage("m0")))
    node(ConversationScreenTags.myMessage("m0")).assertIsDisplayed()
  }

  @Test
  fun pausedNotice_showsWhileTheMessagesLoad() {
    setContent(
        ConversationUiState(myUid = "renter", listingStatus = ConversationListingStatus.PAUSED)
    )

    node(ConversationScreenTags.LOADING_INDICATOR).assertIsDisplayed()
    node(ConversationScreenTags.PAUSED_NOTICE).assertIsDisplayed()
  }

  @Test
  fun header_showsTheListingAndTheOtherUser_andBackLeaves() {
    var backs = 0
    setContent(loaded, onBack = { backs++ })

    node(ConversationScreenTags.LISTING_TITLE).assertTextEquals("Bosch drill set")
    node(ConversationScreenTags.OTHER_USERNAME).assertTextEquals("@james · Lender")
    node(ConversationScreenTags.OTHER_AVATAR).assertIsDisplayed()
    node(ConversationScreenTags.PAUSED_NOTICE).assertDoesNotExist()
    node(ConversationScreenTags.DELETED_NOTICE).assertDoesNotExist()
    node(ConversationScreenTags.BACK_BUTTON).performClick()
    assertEquals(1, backs)
  }

  @Test
  fun pausedListingWithAConversation_showsOnlyTheNoticeTitle_andSendsWhenAllowed() {
    var sends = 0
    setContent(
        loaded.copy(
            listingStatus = ConversationListingStatus.PAUSED,
            draft = "See you at six",
            canSend = true,
        ),
        onSend = { sends++ },
    )

    node(ConversationScreenTags.PAUSED_NOTICE)
        .assertTextEquals(string(R.string.conversation_paused_title))
    node(ConversationScreenTags.MESSAGE_FIELD).assertIsEnabled()
    node(ConversationScreenTags.SEND_BUTTON).performClick()
    assertEquals(1, sends)
  }

  @Test
  fun pausedListingWithoutAConversation_saysAFirstMessageWaitsUntilItIsActiveAgain() {
    setContent(
        loaded.copy(
            messages = emptyList(),
            conversation = null,
            listingStatus = ConversationListingStatus.PAUSED,
            draft = "Hello",
            canSend = false,
            firstMessageBlockedByPause = true,
        )
    )

    node(ConversationScreenTags.PAUSED_NOTICE)
        .assertTextEquals(
            string(R.string.conversation_paused_title),
            string(R.string.conversation_paused_first_message_body),
        )
    node(ConversationScreenTags.MESSAGE_FIELD).assertIsEnabled()
    node(ConversationScreenTags.SEND_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun cannotSend_keepsSendDisabled_evenWithADraft() {
    setContent(
        loaded.copy(
            listingStatus = ConversationListingStatus.PAUSED,
            draft = "Hello",
            canSend = false,
        )
    )

    node(ConversationScreenTags.MESSAGE_FIELD).assertIsEnabled()
    node(ConversationScreenTags.SEND_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun deletedListing_showsTheNotice_andClosesTheComposer() {
    // canSend is true only to show that the closed composer disables Send by itself.
    setContent(loaded.copy(listingStatus = ConversationListingStatus.DELETED, canSend = true))

    node(ConversationScreenTags.DELETED_NOTICE)
        .assertTextEquals(
            string(R.string.conversation_deleted_title),
            string(R.string.conversation_deleted_body),
        )
    node(ConversationScreenTags.myMessage("m1")).assertIsDisplayed()
    node(ConversationScreenTags.MESSAGE_FIELD).assertIsNotEnabled()
    node(ConversationScreenTags.SEND_BUTTON).assertIsNotEnabled()
    text(string(R.string.conversation_placeholder_closed)).assertIsDisplayed()
  }

  @Test
  fun typing_changesTheDraft() {
    val drafts = mutableListOf<String>()
    var dismissals = 0
    setContent(loaded, onDraftChange = { drafts += it }, onDismissSendError = { dismissals++ })

    node(ConversationScreenTags.MESSAGE_FIELD).performTextInput("Hi")

    assertEquals(listOf("Hi"), drafts)
    assertEquals(0, dismissals)
  }

  @Test
  fun typingAfterASendFailed_dismissesTheErrorFirst() {
    val calls = mutableListOf<String>()
    setContent(
        loaded.copy(sendError = AppError.NETWORK),
        onDraftChange = { calls += "draft:$it" },
        onDismissSendError = { calls += "dismiss" },
    )

    node(ConversationScreenTags.SEND_ERROR).assertTextEquals(string(R.string.error_network))
    node(ConversationScreenTags.MESSAGE_FIELD).performTextInput("Hi")

    assertEquals(listOf("dismiss", "draft:Hi"), calls)
  }

  @Test
  fun draftOverTheLimit_showsTheCounter_andKeepsTheText() {
    val long = "a".repeat(1012)
    setContent(loaded.copy(draft = long, canSend = false))

    node(ConversationScreenTags.LENGTH_COUNTER)
        .assertIsDisplayed()
        .assertContentDescriptionEquals(tooLongSentence())
        .assert(isPoliteLiveRegion())
        .assert(hasNoText())
    node(ConversationScreenTags.MESSAGE_FIELD).assert(hasDraft(long))
    // Alone, it still sits at the end, under Send.
    val counter = node(ConversationScreenTags.LENGTH_COUNTER).getBoundsInRoot()
    val send = node(ConversationScreenTags.SEND_BUTTON).getBoundsInRoot()
    assertEquals(send.right.value, counter.right.value, 0.5f)
  }

  @Test
  fun typingPastTheLimit_keepsTheSpokenSentence() {
    var uiState by mutableStateOf(loaded.copy(draft = "a".repeat(1012), canSend = false))
    composeTestRule.setContent { ConversationContent(uiState = uiState, zone = ZoneOffset.UTC) }

    val before = semanticsOf(node(ConversationScreenTags.LENGTH_COUNTER))

    uiState = uiState.copy(draft = "a".repeat(1013))

    assertEquals(before, semanticsOf(node(ConversationScreenTags.LENGTH_COUNTER)))
    assertEquals(listOf(tooLongSentence()), before[SemanticsProperties.ContentDescription.name])
  }

  @Test
  fun leadingWhitespacePastTheLimit_countsTheTrimmedText() {
    var uiState by
        mutableStateOf(
            loaded.copy(draft = "\n\n" + "a".repeat(ChatRules.MAX_MESSAGE_LENGTH), canSend = true)
        )
    composeTestRule.setContent { ConversationContent(uiState = uiState, zone = ZoneOffset.UTC) }

    node(ConversationScreenTags.LENGTH_COUNTER).assertDoesNotExist()

    uiState =
        uiState.copy(
            draft = "\n\n" + "a".repeat(ChatRules.MAX_MESSAGE_LENGTH + 1),
            canSend = false,
        )

    node(ConversationScreenTags.LENGTH_COUNTER).assertIsDisplayed()
  }

  @Test
  fun draftAtTheLimit_showsNoCounter() {
    setContent(loaded.copy(draft = "a".repeat(ChatRules.MAX_MESSAGE_LENGTH), canSend = true))

    node(ConversationScreenTags.LENGTH_COUNTER).assertDoesNotExist()
  }

  @Test
  fun trailingSpacesPastTheLimit_showNoCounter() {
    setContent(
        loaded.copy(draft = "a".repeat(ChatRules.MAX_MESSAGE_LENGTH) + "   ", canSend = true)
    )

    node(ConversationScreenTags.LENGTH_COUNTER).assertDoesNotExist()
  }

  @Test
  fun counterAndSendError_showTogether() {
    setContent(
        loaded.copy(
            draft = "a".repeat(ChatRules.MAX_MESSAGE_LENGTH + 1),
            canSend = false,
            sendError = AppError.NETWORK,
        )
    )

    node(ConversationScreenTags.SEND_ERROR)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.error_network))
    node(ConversationScreenTags.LENGTH_COUNTER).assertIsDisplayed()
    val error = node(ConversationScreenTags.SEND_ERROR).getBoundsInRoot()
    val counter = node(ConversationScreenTags.LENGTH_COUNTER).getBoundsInRoot()
    assertTrue(error.right <= counter.left)
  }

  @Test
  fun deletedListing_showsNoCounter() {
    setContent(
        loaded.copy(
            listingStatus = ConversationListingStatus.DELETED,
            draft = "a".repeat(ChatRules.MAX_MESSAGE_LENGTH + 1),
        )
    )

    node(ConversationScreenTags.LENGTH_COUNTER).assertDoesNotExist()
  }

  @Test
  fun screen_typingPastTheLimitShowsTheCounter_andDeletingBackReenablesSend() {
    val chat = FakeChatRepository()
    showScreen(chat)
    text(string(R.string.conversation_empty_title, "james")).assertIsDisplayed()

    node(ConversationScreenTags.MESSAGE_FIELD)
        .performTextInput("a".repeat(ChatRules.MAX_MESSAGE_LENGTH + 1))

    node(ConversationScreenTags.LENGTH_COUNTER).assertIsDisplayed()
    node(ConversationScreenTags.SEND_BUTTON).assertIsNotEnabled()

    node(ConversationScreenTags.MESSAGE_FIELD)
        .performTextReplacement("a".repeat(ChatRules.MAX_MESSAGE_LENGTH))

    node(ConversationScreenTags.LENGTH_COUNTER).assertDoesNotExist()
    node(ConversationScreenTags.SEND_BUTTON).assertIsEnabled()
  }

  @Test
  fun screen_trailingSpacesPastTheLimit_keepSendEnabled() {
    val chat = FakeChatRepository()
    showScreen(chat)
    text(string(R.string.conversation_empty_title, "james")).assertIsDisplayed()

    node(ConversationScreenTags.MESSAGE_FIELD)
        .performTextInput("a".repeat(ChatRules.MAX_MESSAGE_LENGTH) + "   ")

    node(ConversationScreenTags.LENGTH_COUNTER).assertDoesNotExist()
    node(ConversationScreenTags.SEND_BUTTON).assertIsEnabled()
  }

  @Test
  fun preview_showsAPausedListingWithItsMessages() {
    composeTestRule.setContent { ConversationContentPreview() }

    node(ConversationScreenTags.LISTING_TITLE).assertTextEquals("Bosch drill set")
    node(ConversationScreenTags.PAUSED_NOTICE).assertIsDisplayed()
    node(ConversationScreenTags.myMessage("1")).assertIsDisplayed()
    node(ConversationScreenTags.theirMessage("2")).assertIsDisplayed()
  }

  @Test
  fun conversationBody_prefersTheMessages_thenTheError_thenLoading_elseEmpty() {
    fun body(messages: Boolean, error: Boolean, loading: Boolean) =
        conversationBody(
            ConversationUiState(
                myUid = "renter",
                messages = if (messages) listOf(question) else emptyList(),
                loadError = if (error) AppError.NETWORK else null,
                isLoading = loading,
            )
        )

    assertEquals(ConversationBody.MESSAGES, body(messages = true, error = true, loading = true))
    assertEquals(ConversationBody.MESSAGES, body(messages = true, error = true, loading = false))
    assertEquals(ConversationBody.MESSAGES, body(messages = true, error = false, loading = true))
    assertEquals(ConversationBody.MESSAGES, body(messages = true, error = false, loading = false))
    assertEquals(ConversationBody.ERROR, body(messages = false, error = true, loading = true))
    assertEquals(ConversationBody.ERROR, body(messages = false, error = true, loading = false))
    assertEquals(ConversationBody.LOADING, body(messages = false, error = false, loading = true))
    assertEquals(ConversationBody.EMPTY, body(messages = false, error = false, loading = false))
  }

  @Test
  fun screen_sendsWhatTheUserTypes_andClearsTheField() {
    val chat = FakeChatRepository()
    showScreen(chat)
    text(string(R.string.conversation_empty_title, "james")).assertIsDisplayed()

    node(ConversationScreenTags.MESSAGE_FIELD).performTextInput("Is it free on Saturday?")
    node(ConversationScreenTags.SEND_BUTTON).performClick()

    assertEquals(listOf("Is it free on Saturday?"), chat.sentMessages.map { it.second })
    node(ConversationScreenTags.MESSAGE_FIELD).assert(hasDraft(""))
  }

  @Test
  fun screen_marksReadOnlyWhileStarted() {
    val unread = conversation.copy(lastMessageText = answer.text, lastMessageSenderUid = james.uid)
    val chat =
        FakeChatRepository(listOf(unread), myUid = "renter").apply {
          messages.value = mapOf(conversation.id to listOf(question, answer))
        }
    val lifecycleOwner = ManualLifecycleOwner()
    showScreen(chat, lifecycleOwner = lifecycleOwner)
    composeTestRule.waitForIdle()
    assertEquals(listOf(conversation.id), chat.markedRead)

    composeTestRule.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.CREATED }
    val read = chat.myConversations.value.getOrThrow().single()
    chat.myConversations.value =
        Result.success(listOf(read.copy(lastMessageAt = Instant.parse("2026-11-11T09:20:00Z"))))
    composeTestRule.waitForIdle()
    assertEquals(listOf(conversation.id), chat.markedRead)

    composeTestRule.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.STARTED }
    composeTestRule.waitForIdle()
    assertEquals(listOf(conversation.id, conversation.id), chat.markedRead)
  }

  @Test
  fun screen_retryReloadsTheMessagesThatFailed() {
    val chat =
        FakeChatRepository().apply { messagesFailure.value = UnknownHostException("offline") }
    showScreen(chat)
    text(string(R.string.conversation_load_error_title)).assertIsDisplayed()
    node(ConversationScreenTags.ERROR_MESSAGE).assertTextEquals(string(R.string.error_network))

    val reload = CompletableDeferred<Unit>()
    chat.messagesGate = reload
    chat.messagesFailure.value = null
    node(ConversationScreenTags.RETRY_BUTTON).performClick()

    // Nothing to show while the messages reload: the spinner, never an empty conversation that
    // invites a message.
    node(ConversationScreenTags.LOADING_INDICATOR).assertIsDisplayed()
    node(ConversationScreenTags.EMPTY_STATE).assertDoesNotExist()
    node(ConversationScreenTags.MESSAGE_FIELD).assertIsEnabled()
    node(ConversationScreenTags.SEND_BUTTON).assertIsNotEnabled()
    node(ConversationScreenTags.ERROR_MESSAGE).assertDoesNotExist()

    reload.complete(Unit)

    node(ConversationScreenTags.EMPTY_STATE).assertIsDisplayed()
    node(ConversationScreenTags.LOADING_INDICATOR).assertDoesNotExist()
    assertEquals(2, chat.observeMessagesCalls)
  }

  @Test
  fun screen_retryWithMessagesShown_keepsThemAndTheComposerWhileTheListingReloads() {
    val chat =
        FakeChatRepository(listOf(conversation)).apply {
          messages.value = mapOf(conversation.id to listOf(question, answer))
        }
    val listings =
        FakeListingRepository(allListings = listOf(drill)).apply {
          failure = UnknownHostException("offline")
        }
    val viewModel = showScreen(chat, listings)
    node(ConversationScreenTags.ERROR_MESSAGE).assertTextEquals(string(R.string.error_network))
    node(ConversationScreenTags.myMessage("m1")).assertIsDisplayed()

    val reload = CompletableDeferred<Unit>()
    listings.gate = reload
    listings.failure = null
    node(ConversationScreenTags.RETRY_BUTTON).performClick()

    // The ViewModel is loading again, yet what is shown stays up and usable.
    assertTrue(viewModel.uiState.value.isLoading)
    node(ConversationScreenTags.myMessage("m1")).assertIsDisplayed()
    node(ConversationScreenTags.theirMessage("m2")).assertIsDisplayed()
    node(ConversationScreenTags.LOADING_INDICATOR).assertDoesNotExist()
    node(ConversationScreenTags.MESSAGE_FIELD).assertIsEnabled()

    reload.complete(Unit)

    node(ConversationScreenTags.ERROR_MESSAGE).assertDoesNotExist()
    node(ConversationScreenTags.RETRY_BUTTON).assertDoesNotExist()
    assertEquals(2, listings.observeListingCalls)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun screen_pausedListingWithoutAConversation_explainsTheDisabledSend_untilItIsResumed() {
    val listings = FakeListingRepository(allListings = listOf(drill.copy(isPaused = true)))
    showScreen(FakeChatRepository(), listings)
    node(ConversationScreenTags.MESSAGE_FIELD).performTextInput("Hello")

    text(string(R.string.conversation_paused_first_message_body)).assertIsDisplayed()
    node(ConversationScreenTags.SEND_BUTTON).assertIsNotEnabled()

    composeTestRule.runOnIdle { listings.allListings = listOf(drill) }

    node(ConversationScreenTags.PAUSED_NOTICE).assertDoesNotExist()
    text(string(R.string.conversation_paused_first_message_body)).assertDoesNotExist()
    node(ConversationScreenTags.SEND_BUTTON).assertIsEnabled()
  }

  @Test
  fun screen_failedSendKeepsTheText_andTypingDismissesTheError() {
    val chat = FakeChatRepository().apply { sendFailure = UnknownHostException("offline") }
    showScreen(chat)

    node(ConversationScreenTags.MESSAGE_FIELD).performTextInput("Is it free on Saturday?")
    node(ConversationScreenTags.SEND_BUTTON).performClick()

    node(ConversationScreenTags.SEND_ERROR).assertTextEquals(string(R.string.error_network))
    node(ConversationScreenTags.MESSAGE_FIELD).assert(hasDraft("Is it free on Saturday?"))

    node(ConversationScreenTags.MESSAGE_FIELD).performTextInput("!")

    node(ConversationScreenTags.SEND_ERROR).assertDoesNotExist()
    node(ConversationScreenTags.MESSAGE_FIELD).assert(hasDraft("Is it free on Saturday?!"))
  }

  @Test
  fun summary_forAnItem_showsTheLenderThePricePerDayAndThePlace_asARental() {
    setContent(
        loaded.copy(listing = drill.copy(location = ListingLocation(46.54, 6.58, "Renens VD")))
    )

    node(ConversationScreenTags.LISTING_SUMMARY)
        .assertIsDisplayed()
        .assertContentDescriptionEquals(
            string(R.string.conversation_summary_spoken_lender, "james")
        )
    text("CHF 8 / day · Renens VD").assertIsDisplayed()
    pill().assertTextEquals("Rental")
  }

  @Test
  fun summary_forAService_showsThePriceAlone_asAService() {
    val tutoring =
        ServiceListing(
            ListingCategory.SERVICE_TUTORING,
            25.0,
            "Calculus tutoring",
            "Analysis I and II.",
            listingId = "tutoring",
            ownerUid = james.uid,
        )

    setContent(loaded.copy(listingTitle = "Calculus tutoring", listing = tutoring))

    text("CHF 25").assertIsDisplayed()
    pill().assertTextEquals("Service")
  }

  @Test
  fun summary_followsTheListingStatus() {
    var uiState by mutableStateOf(loaded.copy(listing = drill))
    composeTestRule.setContent {
      ConversationContent(uiState = uiState, zone = ZoneOffset.UTC, now = now)
    }
    pill().assertTextEquals("Rental")

    uiState =
        uiState.copy(
            listing = drill.copy(isPaused = true),
            listingStatus = ConversationListingStatus.PAUSED,
        )
    pill().assertTextEquals("Paused")

    uiState = uiState.copy(listingStatus = ConversationListingStatus.DELETED)
    pill().assertTextEquals("Deleted")
  }

  @Test
  fun summary_ofAListingDeletedBeforeTheScreenOpened_showsOnlyTheLenderAndTheStatus() {
    setContent(loaded.copy(listing = null, listingStatus = ConversationListingStatus.DELETED))

    // The lender is spoken through its content description, so the status is the only text.
    summaryTexts().assertCountEquals(1)
    node(ConversationScreenTags.LISTING_SUMMARY)
        .assertContentDescriptionEquals(
            string(R.string.conversation_summary_spoken_lender, "james")
        )
    pill().assertTextEquals("Deleted")
  }

  @Test
  fun lender_seesThemselvesAsTheLender_andTheOtherPersonAsTheRenter() {
    val remi = User(uid = "renter", email = "remi@epfl.ch", username = "remi")

    setContent(loaded.copy(myUid = james.uid, isRenter = false, otherUser = remi, listing = drill))

    node(ConversationScreenTags.LISTING_SUMMARY)
        .assertContentDescriptionEquals(string(R.string.conversation_summary_spoken_mine))
    node(ConversationScreenTags.OTHER_USERNAME).assertTextEquals("@remi · Renter")
  }

  @Test
  fun summary_isHiddenWhileTheListingIsUnknown() {
    setContent(ConversationUiState(myUid = "renter"))

    node(ConversationScreenTags.LISTING_SUMMARY).assertDoesNotExist()
    node(ConversationScreenTags.OTHER_AVATAR).assertIsDisplayed()
  }

  @Test
  fun notices_showTheirIcons() {
    var uiState by mutableStateOf(loaded.copy(listingStatus = ConversationListingStatus.PAUSED))
    composeTestRule.setContent {
      ConversationContent(uiState = uiState, zone = ZoneOffset.UTC, now = now)
    }
    composeTestRule
        .onNodeWithTag(ConversationScreenTags.NOTICE_ICON, useUnmergedTree = true)
        .assertIsDisplayed()

    uiState = uiState.copy(listingStatus = ConversationListingStatus.DELETED)
    composeTestRule
        .onNodeWithTag(ConversationScreenTags.NOTICE_ICON, useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun loadError_showsItsIcon() {
    setContent(ConversationUiState(myUid = "renter", loadError = AppError.NETWORK))

    node(ConversationScreenTags.LOAD_ERROR_ICON).assertIsDisplayed()
  }

  @Test
  // Tall enough for every message and divider to be laid out.
  @Config(qualifiers = "w412dp-h1400dp")
  fun dayDividers_nameEachDay_aboveItsFirstMessage() {
    fun message(id: String, at: String) =
        ChatMessage(id, "renter", "Message $id", Instant.parse(at))
    val lastYear = message("m1", "2025-12-31T18:00:00Z")
    val earlier = message("m2", "2026-11-09T10:00:00Z")
    val yesterdayFirst = message("m3", "2026-11-10T08:00:00Z")
    val yesterdayLast = message("m4", "2026-11-10T20:00:00Z")
    val today = message("m5", "2026-11-11T09:00:00Z")

    setContent(
        loaded.copy(messages = listOf(lastYear, earlier, yesterdayFirst, yesterdayLast, today))
    )

    fun divider(date: String) = node(ConversationScreenTags.dayDivider(LocalDate.parse(date)))
    divider("2025-12-31").assertTextEquals("31 Dec 2025")
    divider("2026-11-09").assertTextEquals("9 Nov")
    divider("2026-11-10").assertTextEquals("Yesterday")
    divider("2026-11-11").assertTextEquals("Today")
    val above = node(ConversationScreenTags.myMessage("m2")).getBoundsInRoot()
    val yesterday = divider("2026-11-10").getBoundsInRoot()
    val first = node(ConversationScreenTags.myMessage("m3")).getBoundsInRoot()
    val last = node(ConversationScreenTags.myMessage("m4")).getBoundsInRoot()
    assertTrue(above.bottom <= yesterday.top)
    assertTrue(yesterday.bottom <= first.top)
    assertTrue(first.bottom <= last.top)
  }

  @Test
  fun messageWithTheIdOfADayKey_showsBesideThatDaysDivider() {
    // A message id can look like a day key with another separator; the "/" in the divider's key
    // keeps them apart.
    val odd =
        ChatMessage("day:2026-11-11", "renter", "Odd id", Instant.parse("2026-11-11T10:00:00Z"))

    setContent(loaded.copy(messages = listOf(question, odd)))

    node(ConversationScreenTags.myMessage("day:2026-11-11")).assertIsDisplayed()
    node(ConversationScreenTags.dayDivider(LocalDate.parse("2026-11-11"))).assertTextEquals("Today")
  }

  @Test
  fun listingSummary_tellsTalkBackWhoseListingItIs() {
    var uiState by mutableStateOf(loaded.copy(listing = drill))
    composeTestRule.setContent {
      ConversationContent(uiState = uiState, zone = ZoneOffset.UTC, now = now)
    }
    // The merged card: who it belongs to, then the details and the status, without the bare name.
    node(ConversationScreenTags.LISTING_SUMMARY)
        .assertContentDescriptionEquals(
            string(R.string.conversation_summary_spoken_lender, "james")
        )
        .assertTextEquals("CHF 8 / day", "Rental")

    uiState = uiState.copy(myUid = james.uid, isRenter = false)
    node(ConversationScreenTags.LISTING_SUMMARY)
        .assertContentDescriptionEquals(string(R.string.conversation_summary_spoken_mine))
        .assertTextEquals("CHF 8 / day", "Rental")
  }
}
