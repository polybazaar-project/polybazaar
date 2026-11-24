// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.Uri
import android.view.View
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
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
import com.android.polybazaar.chat.viewmodel.PendingPhoto
import com.android.polybazaar.chat.viewmodel.PendingPhotoStatus
import com.android.polybazaar.connectivity.model.FakeConnectivityRepository
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.FilePhotoStorage
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingLocation
import com.android.polybazaar.listing.model.PhotoStorage
import com.android.polybazaar.listing.model.ServiceListing
import com.android.polybazaar.listing.model.TakenPhoto
import com.android.polybazaar.listing.ui.CreateListingScreenTags
import com.android.polybazaar.profile.model.FakeProfileRepository
import com.android.polybazaar.profile.ui.redPhoto
import java.io.File
import java.net.UnknownHostException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
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
      onTakePhoto: () -> Unit = {},
      onChoosePhoto: () -> Unit = {},
      onRemoveAttachedPhoto: () -> Unit = {},
      onLoadPhoto: (String) -> Unit = {},
      onRetryPhoto: (String) -> Unit = {},
      onDiscardPhoto: (String) -> Unit = {},
      onComposeView: (View) -> Unit = {},
  ) {
    composeTestRule.setContent {
      onComposeView(LocalView.current)
      ConversationContent(
          uiState = uiState,
          onBack = onBack,
          onDraftChange = onDraftChange,
          onSend = onSend,
          onRetry = onRetry,
          onDismissSendError = onDismissSendError,
          onTakePhoto = onTakePhoto,
          onChoosePhoto = onChoosePhoto,
          onRemoveAttachedPhoto = onRemoveAttachedPhoto,
          onLoadPhoto = onLoadPhoto,
          onRetryPhoto = onRetryPhoto,
          onDiscardPhoto = onDiscardPhoto,
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

  /** When [manyMessages] were sent. */
  private val manyStart = Instant.parse("2026-11-11T08:00:00Z")

  /** More messages than fit on the screen, oldest first: the renter's are the even ones. */
  private val manyMessages =
      List(50) {
        ChatMessage("m$it", if (it % 2 == 0) "renter" else james.uid, "Message $it", manyStart)
      }

  /**
   * Shows [messages], through [restoration] when given, and returns how to make a new one arrive
   * after them.
   */
  private fun showManyMessages(
      messages: List<ChatMessage> = manyMessages,
      restoration: StateRestorationTester? = null,
  ): (ChatMessage) -> Unit {
    var uiState by mutableStateOf(loaded.copy(messages = messages))
    val content: @Composable () -> Unit = {
      ConversationContent(uiState = uiState, zone = ZoneOffset.UTC)
    }
    if (restoration != null) restoration.setContent(content)
    else composeTestRule.setContent(content)
    return { message -> uiState = uiState.copy(messages = uiState.messages + message) }
  }

  /** Scrolls up to the oldest of [manyMessages], at the top of the list. */
  private fun scrollToTheOldest() {
    node(ConversationScreenTags.MESSAGE_LIST)
        .performScrollToNode(hasTestTag(ConversationScreenTags.myMessage("m0")))
    node(ConversationScreenTags.myMessage("m0")).assertIsDisplayed()
  }

  /** Shows [state], and returns how to change it. */
  private fun showChanging(
      state: ConversationUiState
  ): ((ConversationUiState) -> ConversationUiState) -> Unit {
    var uiState by mutableStateOf(state)
    composeTestRule.setContent { ConversationContent(uiState = uiState, zone = ZoneOffset.UTC) }
    return { change -> uiState = change(uiState) }
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
      photoStorage: PhotoStorage = NoPhotoStorage,
      launchers: FakeLaunchers = FakeLaunchers(),
  ): ConversationViewModel {
    val viewModel =
        ConversationViewModel(
            listingId = "drill",
            renterUid = "renter",
            myUid = "renter",
            chatRepository = chat,
            profileRepository = FakeProfileRepository(mapOf(james.uid to james)),
            listingRepository = listings,
            photoStorage = photoStorage,
            savedStateHandle = SavedStateHandle(),
            uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            connectivity = FakeConnectivityRepository(),
        )
    val launcherOwner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry = launchers
        }
    composeTestRule.setContent {
      CompositionLocalProvider(
          LocalLifecycleOwner provides (lifecycleOwner ?: LocalLifecycleOwner.current),
          // rememberLauncherForActivityResult reads its registry from this composition local.
          LocalActivityResultRegistryOwner provides launcherOwner,
      ) {
        ConversationScreen(viewModel = viewModel, onBack = {})
      }
    }
    return viewModel
  }

  /**
   * Stands in for the gallery, the camera app and the permission request: answers each launch at
   * once with the next of [results] (a picked URI or null, or a Boolean for the camera and the
   * permission), and records each contract launched in [launched].
   */
  private class FakeLaunchers : ActivityResultRegistry() {
    val results = ArrayDeque<Any?>()
    val launched = mutableListOf<ActivityResultContract<*, *>>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
      launched += contract
      dispatchResult(requestCode, results.removeFirst())
    }
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
  fun keyboardOpen_keepsTheHeaderAndPutsTheComposerAboveIt() {
    lateinit var composeView: View
    setContent(loaded, onComposeView = { composeView = it })
    val keyboard = 300.dp
    val keyboardPx = with(composeTestRule.density) { keyboard.roundToPx() }

    // Straight to the Compose view: the test activity's window fits system windows, so an inset
    // sent to it would never reach Compose.
    composeTestRule.runOnUiThread {
      ViewCompat.dispatchApplyWindowInsets(
          composeView,
          WindowInsetsCompat.Builder()
              .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboardPx))
              .setVisible(WindowInsetsCompat.Type.ime(), true)
              .build(),
      )
    }

    node(ConversationScreenTags.BACK_BUTTON).assertIsDisplayed()
    node(ConversationScreenTags.LISTING_TITLE).assertIsDisplayed()
    val field = node(ConversationScreenTags.MESSAGE_FIELD).getBoundsInRoot()
    val screen = composeTestRule.onRoot().getBoundsInRoot()
    assertTrue(field.bottom <= screen.bottom - keyboard)
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
  fun myMessageStillSending_saysSendingInsteadOfTheTime_toTalkBackToo() {
    setContent(loaded.copy(messages = listOf(question.copy(sending = true), answer)))

    // The bubble's merged texts, which TalkBack reads: the footer is replaced, not added to.
    node(ConversationScreenTags.myMessage("m1"))
        .assertTextEquals("Is it free on Saturday?", string(R.string.conversation_message_sending))
  }

  @Test
  fun myMessageReachingTheServer_showsWhoSentItAndWhenAgain() {
    val change = showChanging(loaded.copy(messages = listOf(question.copy(sending = true))))
    text(string(R.string.conversation_message_sending)).assertIsDisplayed()

    change { it.copy(messages = listOf(question)) }

    node(ConversationScreenTags.myMessage("m1"))
        .assertTextEquals(
            "Is it free on Saturday?",
            string(R.string.conversation_my_message_footer, "09:12"),
        )
  }

  @Test
  fun theirMessageMarkedSending_stillSaysWhoSentItAndWhen() {
    setContent(loaded.copy(messages = listOf(question, answer.copy(sending = true))))

    node(ConversationScreenTags.theirMessage("m2"))
        .assertTextEquals(
            "Yes, from ten.",
            string(R.string.conversation_their_message_footer, "james", "09:14"),
        )
  }

  @Test
  fun manyMessages_openOnTheLatest_andFollowANewOne() {
    val arrive = showManyMessages()

    assertFullyInTheList(node(ConversationScreenTags.theirMessage("m49")))
    node(ConversationScreenTags.myMessage("m0")).assertIsNotDisplayed()

    arrive(ChatMessage("m50", "renter", "On my way", manyStart))

    assertFullyInTheList(node(ConversationScreenTags.myMessage("m50")))
    // The oldest message was only out of view, at the top of the list.
    node(ConversationScreenTags.MESSAGE_LIST)
        .performScrollToNode(hasTestTag(ConversationScreenTags.myMessage("m0")))
    node(ConversationScreenTags.myMessage("m0")).assertIsDisplayed()
  }

  @Test
  fun theirNewMessageWhileTheLatestIsInView_isFollowed_withoutTheHint() {
    val arrive = showManyMessages()

    arrive(ChatMessage("m50", james.uid, "See you", manyStart))

    assertFullyInTheList(node(ConversationScreenTags.theirMessage("m50")))
    node(ConversationScreenTags.NEW_MESSAGE_HINT).assertDoesNotExist()
  }

  @Test
  fun theirNewMessagesWhileReadingOlderOnes_keepThePlace_andShowTheHint() {
    val arrive = showManyMessages()
    scrollToTheOldest()

    arrive(ChatMessage("m50", james.uid, "See you", manyStart))
    composeTestRule.waitForIdle()
    node(ConversationScreenTags.NEW_MESSAGE_HINT).assertIsDisplayed()
    arrive(ChatMessage("m51", james.uid, "At ten", manyStart))

    node(ConversationScreenTags.myMessage("m0")).assertIsDisplayed()
    node(ConversationScreenTags.theirMessage("m50")).assertIsNotDisplayed()
    node(ConversationScreenTags.theirMessage("m51")).assertIsNotDisplayed()
    node(ConversationScreenTags.NEW_MESSAGE_HINT)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.conversation_new_message))
        .assertHasClickAction()
        .assert(isPoliteLiveRegion())
  }

  @Test
  fun theirNewMessageDuringADragAtTheLatest_isFollowedOnceTheFingerLifts() {
    val arrive = showManyMessages()
    // Past the touch slop, so the finger holds the list's scroll while it stays down.
    node(ConversationScreenTags.MESSAGE_LIST).performTouchInput {
      down(center)
      moveBy(Offset(0f, viewConfiguration.touchSlop + 5f))
      moveBy(Offset(0f, 5f))
    }
    node(ConversationScreenTags.theirMessage("m49")).assertIsDisplayed()

    arrive(ChatMessage("m50", james.uid, "See you", manyStart))
    composeTestRule.waitForIdle()
    // Held still before lifting, so the list does not fling.
    composeTestRule.mainClock.advanceTimeBy(500)
    node(ConversationScreenTags.MESSAGE_LIST).performTouchInput { up() }

    assertFullyInTheList(node(ConversationScreenTags.theirMessage("m50")))
    node(ConversationScreenTags.NEW_MESSAGE_HINT).assertDoesNotExist()
  }

  // Real text heights, and a window shorter than the long message, so its start and its end are
  // not in view together.
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  @Config(qualifiers = "w360dp-h480dp")
  fun theirNewMessageWhileReadingTheStartOfALongNewestOne_keepsThePlace_andShowsTheHint() {
    // The longest message the rules allow.
    val long =
        ChatMessage(
            "m49",
            james.uid,
            "word ".repeat(ChatRules.MAX_MESSAGE_LENGTH / "word ".length),
            manyStart,
        )
    val arrive = showManyMessages(manyMessages.dropLast(1) + long)
    node(ConversationScreenTags.MESSAGE_LIST).performTouchInput {
      swipe(Offset(centerX, top + 20f), Offset(centerX, top + 220f), durationMillis = 2000)
    }
    val list = node(ConversationScreenTags.MESSAGE_LIST).fetchSemanticsNode().boundsInRoot
    val reading = node(ConversationScreenTags.theirMessage("m49")).fetchSemanticsNode()
    val readingTop = reading.positionInRoot.y
    assertTrue("Its end is in view", readingTop + reading.size.height > list.bottom)

    arrive(ChatMessage("m50", james.uid, "See you", manyStart))

    val after = node(ConversationScreenTags.theirMessage("m49")).fetchSemanticsNode()
    assertEquals(readingTop, after.positionInRoot.y, 0.5f)
    node(ConversationScreenTags.theirMessage("m50")).assertIsNotDisplayed()
    node(ConversationScreenTags.NEW_MESSAGE_HINT).assertIsDisplayed()
  }

  @Test
  fun recreatedWhileTheHintShows_keepsTheHintAndThePlace() {
    val restoration = StateRestorationTester(composeTestRule)
    val arrive = showManyMessages(restoration = restoration)
    scrollToTheOldest()
    arrive(ChatMessage("m50", james.uid, "See you", manyStart))
    node(ConversationScreenTags.NEW_MESSAGE_HINT).assertIsDisplayed()

    restoration.emulateSavedInstanceStateRestore()

    node(ConversationScreenTags.NEW_MESSAGE_HINT).assertIsDisplayed()
    node(ConversationScreenTags.myMessage("m0")).assertIsDisplayed()
  }

  @Test
  fun tappingTheHint_showsTheNewMessage_andHidesTheHint() {
    val arrive = showManyMessages()
    scrollToTheOldest()
    arrive(ChatMessage("m50", james.uid, "See you", manyStart))

    node(ConversationScreenTags.NEW_MESSAGE_HINT).performClick()

    assertFullyInTheList(node(ConversationScreenTags.theirMessage("m50")))
    node(ConversationScreenTags.NEW_MESSAGE_HINT).assertDoesNotExist()
  }

  @Test
  fun scrollingDownToTheNewMessage_hidesTheHint() {
    val arrive = showManyMessages()
    scrollToTheOldest()
    arrive(ChatMessage("m50", james.uid, "See you", manyStart))
    node(ConversationScreenTags.NEW_MESSAGE_HINT).assertIsDisplayed()

    node(ConversationScreenTags.MESSAGE_LIST).performScrollToIndex(0)

    node(ConversationScreenTags.theirMessage("m50")).assertIsDisplayed()
    node(ConversationScreenTags.NEW_MESSAGE_HINT).assertDoesNotExist()
  }

  @Test
  fun myNewMessageWhileReadingOlderOnes_isFollowed_withoutTheHint() {
    val arrive = showManyMessages()
    scrollToTheOldest()

    arrive(ChatMessage("m50", "renter", "On my way", manyStart))

    assertFullyInTheList(node(ConversationScreenTags.myMessage("m50")))
    node(ConversationScreenTags.NEW_MESSAGE_HINT).assertDoesNotExist()
  }

  @Test
  fun photoSentWhileReadingOlderOnes_isFollowed() {
    val change = showChanging(loaded.copy(messages = manyMessages))
    scrollToTheOldest()

    change { it.copy(pendingPhotos = listOf(pending(PendingPhotoStatus.UPLOADING, id = "m50"))) }

    assertFullyInTheList(node(ConversationScreenTags.pendingPhoto("m50")))
  }

  @Test
  fun theirNewMessageWhileAPhotoSendsAtTheEnd_staysAboveThePhoto_withoutTheHint() {
    val change =
        showChanging(
            loaded.copy(
                messages = manyMessages,
                pendingPhotos = listOf(pending(PendingPhotoStatus.UPLOADING, id = "m50")),
            )
        )
    assertFullyInTheList(node(ConversationScreenTags.pendingPhoto("m50")))

    change { it.copy(messages = manyMessages + ChatMessage("m51", james.uid, "Nice", manyStart)) }

    assertFullyInTheList(node(ConversationScreenTags.pendingPhoto("m50")))
    node(ConversationScreenTags.theirMessage("m51")).assertIsDisplayed()
    node(ConversationScreenTags.NEW_MESSAGE_HINT).assertDoesNotExist()
  }

  @Test
  fun uploadFinishingWhileReadingOlderOnes_keepsThePlace_withoutTheHint() {
    val change =
        showChanging(
            loaded.copy(
                messages = manyMessages + photoMessage("m50", "renter"),
                pendingPhotos = listOf(pending(PendingPhotoStatus.UPLOADING, id = "m50")),
            )
        )
    scrollToTheOldest()

    change { it.copy(pendingPhotos = emptyList()) }

    node(ConversationScreenTags.myMessage("m0")).assertIsDisplayed()
    node(ConversationScreenTags.myMessage("m50")).assertIsNotDisplayed()
    node(ConversationScreenTags.NEW_MESSAGE_HINT).assertDoesNotExist()
  }

  @Test
  fun newerPhotoFinishingAboveAFailedOneWhileReadingOlderOnes_keepsThePlace() {
    val change =
        showChanging(
            loaded.copy(
                messages =
                    manyMessages + photoMessage("m50", "renter") + photoMessage("m51", "renter"),
                pendingPhotos =
                    listOf(
                        pending(PendingPhotoStatus.FAILED, AppError.NETWORK, id = "m50"),
                        pending(PendingPhotoStatus.UPLOADING, id = "m51"),
                    ),
            )
        )
    scrollToTheOldest()

    change { it.copy(pendingPhotos = it.pendingPhotos.dropLast(1)) }

    node(ConversationScreenTags.myMessage("m0")).assertIsDisplayed()
    node(ConversationScreenTags.myMessage("m51")).assertIsNotDisplayed()
    node(ConversationScreenTags.NEW_MESSAGE_HINT).assertDoesNotExist()
  }

  // A window taller than a photo bubble, so the photo and the messages around it fit together.
  @Test
  @Config(qualifiers = "w360dp-h800dp")
  fun uploadFinishingAtTheEnd_staysAtTheEnd() {
    val change =
        showChanging(
            loaded.copy(
                messages = manyMessages + photoMessage("m50", "renter"),
                pendingPhotos = listOf(pending(PendingPhotoStatus.UPLOADING, id = "m50")),
            )
        )
    assertFullyInTheList(node(ConversationScreenTags.pendingPhoto("m50")))

    change { it.copy(pendingPhotos = emptyList()) }

    assertFullyInTheList(node(ConversationScreenTags.myMessage("m50")))
  }

  @Test
  fun newestMessageDeletedWhileReadingOlderOnes_withTheirsNowNewest_keepsThePlace_withoutTheHint() {
    val change =
        showChanging(
            loaded.copy(messages = manyMessages + ChatMessage("m50", "renter", "Photo", manyStart))
        )
    scrollToTheOldest()

    change { it.copy(messages = manyMessages) }

    node(ConversationScreenTags.myMessage("m0")).assertIsDisplayed()
    node(ConversationScreenTags.NEW_MESSAGE_HINT).assertDoesNotExist()
  }

  @Test
  fun newestMessageDeletedWhileReadingOlderOnes_withMineNowNewest_keepsThePlace() {
    val change = showChanging(loaded.copy(messages = manyMessages))
    scrollToTheOldest()

    change { it.copy(messages = manyMessages.dropLast(1)) }

    node(ConversationScreenTags.myMessage("m0")).assertIsDisplayed()
    node(ConversationScreenTags.NEW_MESSAGE_HINT).assertDoesNotExist()
  }

  @Test
  @Config(qualifiers = "w360dp-h800dp")
  fun theirNewMessageFullyInViewAboveAFailedPhoto_hidesTheHint() {
    val change =
        showChanging(
            loaded.copy(
                messages = manyMessages,
                pendingPhotos =
                    listOf(pending(PendingPhotoStatus.FAILED, AppError.NETWORK, id = "m50")),
            )
        )
    scrollToTheOldest()
    change { it.copy(messages = manyMessages + ChatMessage("m51", james.uid, "Nice", manyStart)) }
    node(ConversationScreenTags.NEW_MESSAGE_HINT).assertIsDisplayed()

    // The new message first from the bottom, the failed photo under it scrolled away.
    node(ConversationScreenTags.MESSAGE_LIST).performScrollToIndex(1)

    assertFullyInTheList(node(ConversationScreenTags.theirMessage("m51")))
    node(ConversationScreenTags.NEW_MESSAGE_HINT).assertDoesNotExist()
  }

  @Test
  @Config(qualifiers = "w360dp-h800dp")
  fun theirNewMessageLandingInViewAboveTwoFailedPhotos_leavesNoHint() {
    val change =
        showChanging(
            loaded.copy(
                messages = manyMessages,
                pendingPhotos =
                    listOf(
                        pending(PendingPhotoStatus.FAILED, AppError.NETWORK, id = "m50"),
                        pending(PendingPhotoStatus.FAILED, AppError.NETWORK, id = "m51"),
                    ),
            )
        )
    // The upper failed photo first from the bottom, the lower one scrolled away.
    node(ConversationScreenTags.MESSAGE_LIST).performScrollToIndex(1)

    change { it.copy(messages = manyMessages + ChatMessage("m52", james.uid, "Nice", manyStart)) }

    node(ConversationScreenTags.theirMessage("m52")).assertIsDisplayed()
    node(ConversationScreenTags.NEW_MESSAGE_HINT).assertDoesNotExist()
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
  fun photosPreview_showsTheirPhotoLoadingAndMyFailedPhoto() {
    composeTestRule.setContent { ConversationPhotosPreview() }

    composeTestRule
        .onNodeWithTag(ConversationScreenTags.messagePhoto("2"), useUnmergedTree = true)
        .assertExists()
    node(ConversationScreenTags.pendingPhoto("3")).assertExists()
    node(ConversationScreenTags.retryPhoto("3")).assertExists()
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

  // Photo button and attached photo

  // A photo that cannot be read, which shows the placeholder: Robolectric throws for an unknown
  // content:// URI, where a device answers FileNotFoundException.
  private val galleryPhoto = Uri.parse("file:///missing/photo_1.jpg")

  /** The loaded conversation, where a photo can be attached. */
  private val canAttach = loaded.copy(canAttachPhoto = true)

  @Test
  fun photoButton_isEnabledWhenAPhotoCanBeAttached_andSaysWhatItDoes() {
    setContent(canAttach)

    node(ConversationScreenTags.PHOTO_BUTTON)
        .assertIsEnabled()
        .assertContentDescriptionEquals(string(R.string.conversation_photo_button))
  }

  @Test
  fun photoButton_isDisabledWhenNoPhotoCanBeAttached() {
    setContent(loaded.copy(canAttachPhoto = false))

    node(ConversationScreenTags.PHOTO_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun photoMenu_takePhoto_opensTheCamera_andClosesTheMenu() {
    var takes = 0
    var chooses = 0
    setContent(canAttach, onTakePhoto = { takes++ }, onChoosePhoto = { chooses++ })
    node(ConversationScreenTags.TAKE_PHOTO).assertDoesNotExist()

    node(ConversationScreenTags.PHOTO_BUTTON).performClick()
    node(ConversationScreenTags.TAKE_PHOTO)
        .assertTextEquals(string(R.string.conversation_take_photo))
        .performClick()

    assertEquals(1, takes)
    assertEquals(0, chooses)
    node(ConversationScreenTags.PHOTO_MENU).assertDoesNotExist()
  }

  @Test
  fun photoMenu_choosePhoto_opensTheGallery_andClosesTheMenu() {
    var takes = 0
    var chooses = 0
    setContent(canAttach, onTakePhoto = { takes++ }, onChoosePhoto = { chooses++ })

    node(ConversationScreenTags.PHOTO_BUTTON).performClick()
    node(ConversationScreenTags.CHOOSE_PHOTO)
        .assertTextEquals(string(R.string.conversation_choose_photo))
        .performClick()

    assertEquals(0, takes)
    assertEquals(1, chooses)
    node(ConversationScreenTags.PHOTO_MENU).assertDoesNotExist()
  }

  @Test
  fun noAttachedPhoto_showsNoPreview_andTheMessagePlaceholder() {
    setContent(canAttach)

    node(ConversationScreenTags.ATTACHED_PHOTO).assertDoesNotExist()
    node(ConversationScreenTags.REMOVE_ATTACHED_PHOTO).assertDoesNotExist()
    text(string(R.string.conversation_placeholder)).assertIsDisplayed()
  }

  @Test
  fun attachedPhoto_showsAboveTheField_andAsksForACaption() {
    setContent(canAttach.copy(attachedPhoto = galleryPhoto))

    node(ConversationScreenTags.ATTACHED_PHOTO)
        .assertIsDisplayed()
        .assertContentDescriptionEquals(string(R.string.conversation_attached_photo))
    text(string(R.string.conversation_placeholder_caption)).assertIsDisplayed()
    text(string(R.string.conversation_placeholder)).assertDoesNotExist()
    val photo = node(ConversationScreenTags.ATTACHED_PHOTO).getBoundsInRoot()
    val field = node(ConversationScreenTags.MESSAGE_FIELD).getBoundsInRoot()
    assertTrue(photo.bottom <= field.top)
  }

  @Test
  fun removeAttachedPhoto_callsTheCallback() {
    var removals = 0
    setContent(
        canAttach.copy(attachedPhoto = galleryPhoto),
        onRemoveAttachedPhoto = { removals++ },
    )

    node(ConversationScreenTags.REMOVE_ATTACHED_PHOTO)
        .assertContentDescriptionEquals(string(R.string.conversation_remove_attached_photo))
        .performClick()

    assertEquals(1, removals)
  }

  @Test
  fun attachedPhotoWithoutACaption_canBeSent() {
    var sends = 0
    setContent(canAttach.copy(attachedPhoto = galleryPhoto, canSend = true), onSend = { sends++ })

    node(ConversationScreenTags.MESSAGE_FIELD).assert(hasDraft(""))
    node(ConversationScreenTags.SEND_BUTTON).assertIsEnabled().performClick()

    assertEquals(1, sends)
  }

  // Photo bubbles

  private val photoTime = Instant.parse("2026-11-11T09:16:00Z")

  /** A photo message [id] from [senderUid], with [caption] as its text ("" for none). */
  private fun photoMessage(id: String, senderUid: String, caption: String = "") =
      ChatMessage(id, senderUid, caption, photoTime, imagePath = "conversations/drill_renter/$id")

  private val myPhoto = photoMessage("m3", "renter", caption = "Here it is")
  private val theirPhoto = photoMessage("m4", james.uid)

  private fun pending(status: PendingPhotoStatus, error: AppError? = null, id: String = "m5") =
      PendingPhoto(id, galleryPhoto, "And the shelf", status, error)

  /** Waits until the photo of message [id] is drawn: [redPhoto] decoded, not the placeholder. */
  private fun waitForRedPhoto(id: String) {
    composeTestRule.waitUntil(5_000) {
      val image =
          composeTestRule
              .onNodeWithTag(ConversationScreenTags.messagePhoto(id), useUnmergedTree = true)
              .captureToImage()
      image.toPixelMap()[image.width / 2, image.height / 2] == Color.Red
    }
  }

  private fun photoNode(id: String) =
      composeTestRule.onNodeWithTag(ConversationScreenTags.messagePhoto(id), useUnmergedTree = true)

  @Test
  fun myPhotoWithACaption_isOneTalkBackItem_photoThenCaptionThenFooter() {
    setContent(loaded.copy(messages = listOf(myPhoto)))

    node(ConversationScreenTags.myMessage("m3"))
        .assertContentDescriptionEquals(string(R.string.conversation_photo_loading))
        .assertTextEquals("Here it is", "You · 09:16")
    val photo = photoNode("m3").getBoundsInRoot()
    val caption = text("Here it is").getBoundsInRoot()
    assertTrue(photo.bottom <= caption.top)
  }

  @Test
  fun theirPhotoWithoutACaption_showsOnlyThePhotoAndTheFooter() {
    setContent(loaded.copy(messages = listOf(theirPhoto)))

    node(ConversationScreenTags.theirMessage("m4"))
        .assertContentDescriptionEquals(string(R.string.conversation_photo_loading))
        .assertTextEquals("james · 09:16")
    photoNode("m4").assertIsDisplayed()
  }

  @Test
  fun photoNotDownloaded_isAskedForOnce_withAFixedSizePlaceholder() {
    val loads = mutableListOf<String>()
    setContent(loaded.copy(messages = listOf(myPhoto, theirPhoto)), onLoadPhoto = { loads += it })

    composeTestRule.waitForIdle()
    assertEquals(listOf(theirPhoto.imagePath, myPhoto.imagePath), loads)
    photoNode("m3").assertHeightIsEqualTo(200.dp)
    node(ConversationScreenTags.photoLoadError("m3")).assertDoesNotExist()
  }

  @Test
  fun pendingPhoto_showsTheLoadingPlaceholder_withoutBeingAskedFor() {
    val loads = mutableListOf<String>()
    val uploading = theirPhoto.copy(photoPending = true)
    setContent(loaded.copy(messages = listOf(uploading)), onLoadPhoto = { loads += it })

    composeTestRule.waitForIdle()
    assertEquals(emptyList<String>(), loads)
    photoNode("m4").assertHeightIsEqualTo(200.dp)
    node(ConversationScreenTags.photoLoadError("m4")).assertDoesNotExist()
  }

  @Test
  fun pendingPhoto_isAskedForOnceMarkedUploaded() {
    val loads = mutableListOf<String>()
    var state by
        mutableStateOf(loaded.copy(messages = listOf(theirPhoto.copy(photoPending = true))))
    composeTestRule.setContent {
      ConversationContent(uiState = state, onLoadPhoto = { loads += it }, zone = ZoneOffset.UTC)
    }
    composeTestRule.waitForIdle()

    state = state.copy(messages = listOf(theirPhoto))
    composeTestRule.waitForIdle()

    assertEquals(listOf(theirPhoto.imagePath), loads)
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE) // Real decoding and drawing.
  fun downloadedPhoto_isShownAtTheSameSize_withoutAskingAgain() {
    val loads = mutableListOf<String>()
    setContent(
        loaded.copy(
            messages = listOf(theirPhoto),
            messagePhotos = mapOf(theirPhoto.imagePath!! to redPhoto()),
        ),
        onLoadPhoto = { loads += it },
    )

    waitForRedPhoto("m4")
    photoNode("m4").assertHeightIsEqualTo(200.dp)
    // Loaded: no longer "loading" for TalkBack.
    node(ConversationScreenTags.theirMessage("m4"))
        .assertContentDescriptionEquals(string(R.string.conversation_photo))
    assertEquals(emptyList<String>(), loads)
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE) // Real decoding and drawing.
  fun decodedPhoto_scrolledOutOfViewAndBack_isShownAtOnce() {
    val later =
        List(30) { ChatMessage("t$it", "renter", "Message $it", photoTime.plusSeconds(60L + it)) }
    setContent(
        loaded.copy(
            messages = listOf(theirPhoto) + later,
            messagePhotos = mapOf(theirPhoto.imagePath!! to redPhoto()),
        )
    )
    val list = node(ConversationScreenTags.MESSAGE_LIST)
    // Newest first: the photo is last.
    list.performScrollToIndex(later.size)
    waitForRedPhoto("m4")

    list.performScrollToIndex(0)
    photoNode("m4").assertDoesNotExist()
    // Frozen clock: a photo decoded again would still show the placeholder.
    composeTestRule.mainClock.autoAdvance = false
    list.performScrollToIndex(later.size)

    // Not decoded again: drawn in the first frame, without the placeholder.
    val image = photoNode("m4").captureToImage()
    assertEquals(Color.Red, image.toPixelMap()[image.width / 2, image.height / 2])
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE) // The legacy BitmapFactory "decodes" any bytes.
  fun undecodablePhoto_saysItCouldNotLoad_withoutOfferingARetry() {
    setContent(
        loaded.copy(
            messages = listOf(theirPhoto),
            messagePhotos = mapOf(theirPhoto.imagePath!! to byteArrayOf(1, 2, 3)),
        )
    )

    composeTestRule.waitUntil(5_000) {
      composeTestRule
          .onAllNodes(
              hasText(string(R.string.conversation_photo_load_failed)),
              useUnmergedTree = true,
          )
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    node(ConversationScreenTags.photoLoadError("m4")).assertDoesNotExist()
  }

  @Test
  fun photoThatFailedToDownload_offersARetry_andTappingAsksForItAgain() {
    val loads = mutableListOf<String>()
    setContent(
        loaded.copy(
            messages = listOf(theirPhoto),
            photoLoadErrors = mapOf(theirPhoto.imagePath!! to AppError.NETWORK),
        ),
        onLoadPhoto = { loads += it },
    )
    composeTestRule.waitForIdle()
    assertEquals(emptyList<String>(), loads)

    composeTestRule
        .onNodeWithTag(ConversationScreenTags.photoLoadError("m4"), useUnmergedTree = true)
        .assertHeightIsEqualTo(200.dp)
        // Announced by TalkBack as a button that retries.
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        .assert(
            SemanticsMatcher("retries on click") {
              it.config[SemanticsActions.OnClick].label == string(R.string.conversation_retry)
            }
        )
        .performClick()

    text(string(R.string.conversation_photo_load_failed_retry)).assertIsDisplayed()
    assertEquals(listOf(theirPhoto.imagePath), loads)
  }

  @Test
  fun photoMissingOrRefused_saysItCouldNotLoad_withoutOfferingARetry() {
    val loads = mutableListOf<String>()
    var error by mutableStateOf(AppError.NOT_FOUND)
    composeTestRule.setContent {
      ConversationContent(
          uiState =
              loaded.copy(
                  messages = listOf(theirPhoto),
                  photoLoadErrors = mapOf(theirPhoto.imagePath!! to error),
              ),
          zone = ZoneOffset.UTC,
          onLoadPhoto = { loads += it },
      )
    }

    for (permanent in listOf(AppError.NOT_FOUND, AppError.PERMISSION_DENIED)) {
      error = permanent
      composeTestRule
          .onNodeWithTag(ConversationScreenTags.photoLoadError("m4"), useUnmergedTree = true)
          .assertHasNoClickAction()
      text(string(R.string.conversation_photo_load_failed)).assertIsDisplayed()
      text(string(R.string.conversation_photo_load_failed_retry)).assertDoesNotExist()
    }
    assertEquals(emptyList<String>(), loads)
  }

  @Test
  fun pendingPhoto_whoseMessageArrived_showsOnce() {
    setContent(
        loaded.copy(
            messages = loaded.messages + photoMessage("m5", "renter", caption = "And the shelf"),
            pendingPhotos = listOf(pending(PendingPhotoStatus.UPLOADING)),
        )
    )

    node(ConversationScreenTags.pendingPhoto("m5")).assertIsDisplayed()
    node(ConversationScreenTags.myMessage("m5")).assertDoesNotExist()
  }

  @Test
  fun uploadingPhoto_saysSending_withoutRetryOrRemove() {
    setContent(loaded.copy(pendingPhotos = listOf(pending(PendingPhotoStatus.UPLOADING))))

    node(ConversationScreenTags.pendingPhoto("m5"))
        .assertContentDescriptionEquals(string(R.string.conversation_photo))
        .assertTextEquals("And the shelf", string(R.string.conversation_photo_sending))
    node(ConversationScreenTags.retryPhoto("m5")).assertDoesNotExist()
    node(ConversationScreenTags.discardPhoto("m5")).assertDoesNotExist()
  }

  @Test
  fun failedPhoto_saysWhy_andOffersRetryAndRemove() {
    val retried = mutableListOf<String>()
    val discarded = mutableListOf<String>()
    setContent(
        loaded.copy(pendingPhotos = listOf(pending(PendingPhotoStatus.FAILED, AppError.NETWORK))),
        onRetryPhoto = { retried += it },
        onDiscardPhoto = { discarded += it },
    )

    text(string(R.string.conversation_photo_not_sent, string(R.string.error_network)))
        .assertIsDisplayed()
        .assert(isPoliteLiveRegion())
    text(string(R.string.conversation_photo_sending)).assertDoesNotExist()
    node(ConversationScreenTags.retryPhoto("m5")).performClick()
    node(ConversationScreenTags.discardPhoto("m5")).performClick()

    assertEquals(listOf("m5"), retried)
    assertEquals(listOf("m5"), discarded)
  }

  @Test
  fun pendingPhotos_showBelowTheMessages_asMyOwnBubbles() {
    setContent(loaded.copy(pendingPhotos = listOf(pending(PendingPhotoStatus.UPLOADING))))

    val pending = node(ConversationScreenTags.pendingPhoto("m5")).getBoundsInRoot()
    val answer = node(ConversationScreenTags.theirMessage("m2")).getBoundsInRoot()
    val mine = node(ConversationScreenTags.myMessage("m1")).getBoundsInRoot()
    assertTrue(answer.bottom <= pending.top)
    assertEquals(mine.right.value, pending.right.value, 0.5f)
  }

  // The screen wires the photo menu to the gallery, the camera and the ViewModel.

  /** A chat whose conversation exists, so a photo can be attached. */
  private fun existingConversation() = FakeChatRepository(listOf(conversation))

  private fun openPhotoMenuAndPick(tag: String) {
    node(ConversationScreenTags.PHOTO_BUTTON).assertIsEnabled().performClick()
    node(tag).performClick()
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE) // Real decoding and drawing of the thumbnail.
  fun screen_choosingFromTheGallery_attachesThePickedPhoto_andShowsIt() {
    val picked = File(context.cacheDir, "picked.png").apply { writeBytes(redPhoto()) }
    val launchers = FakeLaunchers().apply { results.add(Uri.fromFile(picked)) }
    val viewModel = showScreen(existingConversation(), launchers = launchers)

    openPhotoMenuAndPick(ConversationScreenTags.CHOOSE_PHOTO)

    assertTrue(launchers.launched.single() is ActivityResultContracts.PickVisualMedia)
    assertEquals(Uri.fromFile(picked), viewModel.uiState.value.attachedPhoto)
    composeTestRule.waitUntil(5_000) {
      val image =
          composeTestRule
              .onNodeWithTag(ConversationScreenTags.ATTACHED_PHOTO, useUnmergedTree = true)
              .captureToImage()
      image.toPixelMap()[image.width / 2, image.height / 2] == Color.Red
    }
  }

  @Test
  fun screen_closingTheGalleryWithoutAPhoto_attachesNothing() {
    val launchers = FakeLaunchers().apply { results.add(null) }
    val viewModel = showScreen(existingConversation(), launchers = launchers)

    openPhotoMenuAndPick(ConversationScreenTags.CHOOSE_PHOTO)

    assertEquals(null, viewModel.uiState.value.attachedPhoto)
    node(ConversationScreenTags.ATTACHED_PHOTO).assertDoesNotExist()
  }

  // The camera app leaves its file empty; native graphics fail to decode it cleanly.
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun screen_takingAPhoto_withPermission_attachesTheTakenFile() {
    shadowOf(context as Application).grantPermissions(Manifest.permission.CAMERA)
    val launchers = FakeLaunchers().apply { results.add(true) } // The camera app saved it.
    val storage = FilePhotoStorage(context, FilePhotoStorage.CHAT_FORM)
    val viewModel =
        showScreen(existingConversation(), photoStorage = storage, launchers = launchers)

    openPhotoMenuAndPick(ConversationScreenTags.TAKE_PHOTO)

    assertTrue(launchers.launched.single() is ActivityResultContracts.TakePicture)
    val attached = viewModel.uiState.value.attachedPhoto
    assertTrue(attached != null && File(attached.path!!).exists())
    node(ConversationScreenTags.ATTACHED_PHOTO).assertIsDisplayed()
  }

  @Test
  fun screen_refusingTheCamera_explainsWhy_andCancelClosesIt() {
    val launchers = FakeLaunchers().apply { results.add(false) } // The user refuses.
    val viewModel = showScreen(existingConversation(), launchers = launchers)

    openPhotoMenuAndPick(ConversationScreenTags.TAKE_PHOTO)

    assertTrue(launchers.launched.single() is ActivityResultContracts.RequestPermission)
    node(CreateListingScreenTags.CAMERA_PERMISSION_DIALOG).assertIsDisplayed()
    node(CreateListingScreenTags.CAMERA_PERMISSION_CANCEL).performClick()
    node(CreateListingScreenTags.CAMERA_PERMISSION_DIALOG).assertDoesNotExist()
    assertEquals(null, viewModel.uiState.value.attachedPhoto)
  }

  /** No test of the screen takes a photo yet. */
  private object NoPhotoStorage : PhotoStorage {
    override fun newPhoto(): TakenPhoto? = null

    override fun delete(photo: Uri) = Unit

    override fun deleteAllExcept(keep: List<Uri>) = Unit
  }
}
