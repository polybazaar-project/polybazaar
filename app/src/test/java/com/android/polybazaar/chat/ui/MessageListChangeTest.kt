// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.ui

import com.android.polybazaar.chat.model.ChatMessage
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageListChangeTest {

  private val sentAt = Instant.parse("2026-11-11T09:00:00Z")

  private fun mine(id: String, sending: Boolean = false) =
      ChatMessage(id, "renter", "Mine", sentAt, sending = sending)

  private fun theirs(id: String) = ChatMessage(id, "lender", "Theirs", sentAt)

  private val shown = listOf(mine("m1"), theirs("m2"))

  /** The change to [messages] and [pendingIds] from [shown] with nothing pending, by default. */
  private fun change(
      messages: List<ChatMessage>,
      pendingIds: List<String> = emptyList(),
      previousNewestId: String? = "m2",
      previousPendingIds: List<String> = emptyList(),
      firstLoad: Boolean = false,
  ) =
      messageListChange(
          messages = messages,
          pendingIds = pendingIds,
          myUid = "renter",
          previousNewestId = previousNewestId,
          previousPendingIds = previousPendingIds,
          firstLoad = firstLoad,
      )

  @Test
  fun firstLoad_follows() {
    assertEquals(
        MessageListChange.FOLLOW,
        change(shown, previousNewestId = null, firstLoad = true),
    )
  }

  @Test
  fun nothingNew_isNone() {
    assertEquals(MessageListChange.NONE, change(shown))
  }

  @Test
  fun theirMessageAfterTheNewest_arrives() {
    assertEquals(MessageListChange.THEIRS_ARRIVED, change(shown + theirs("m3")))
  }

  @Test
  fun myMessageAfterTheNewest_follows() {
    assertEquals(MessageListChange.FOLLOW, change(shown + mine("m3")))
  }

  @Test
  fun myMessageStillSendingAfterTheNewest_follows() {
    assertEquals(MessageListChange.FOLLOW, change(shown + mine("m3", sending = true)))
  }

  @Test
  fun myNewestMessageReachingTheServer_isNone() {
    // Was shown as sending; the next list has the same messages, now all sent.
    assertEquals(MessageListChange.NONE, change(shown + mine("m3"), previousNewestId = "m3"))
  }

  @Test
  fun theirMessageWhenOnlyAPhotoWasShown_arrives() {
    assertEquals(
        MessageListChange.THEIRS_ARRIVED,
        change(
            listOf(theirs("m2")),
            pendingIds = listOf("m1"),
            previousNewestId = null,
            previousPendingIds = listOf("m1"),
        ),
    )
  }

  @Test
  fun photoBeingSent_follows() {
    assertEquals(MessageListChange.FOLLOW, change(shown, pendingIds = listOf("m3")))
  }

  @Test
  fun anotherPhotoBeingSentAfterAFailedOne_follows() {
    assertEquals(
        MessageListChange.FOLLOW,
        change(shown, pendingIds = listOf("m3", "m4"), previousPendingIds = listOf("m3")),
    )
  }

  @Test
  fun uploadFinished_revealsThePhotoWithoutAnArrival() {
    assertEquals(
        MessageListChange.NONE,
        change(shown + mine("m3"), previousPendingIds = listOf("m3")),
    )
  }

  @Test
  fun newerPhotoFinishedAboveAFailedOne_isNone() {
    assertEquals(
        MessageListChange.NONE,
        change(
            shown + mine("m4"),
            pendingIds = listOf("m3"),
            previousPendingIds = listOf("m3", "m4"),
        ),
    )
  }

  @Test
  fun lastPhotoDiscarded_isNone() {
    assertEquals(
        MessageListChange.NONE,
        change(shown, pendingIds = listOf("m3"), previousPendingIds = listOf("m3", "m4")),
    )
  }

  @Test
  fun newestDeleted_withTheirsNowNewest_isNone() {
    assertEquals(MessageListChange.NONE, change(shown, previousNewestId = "m3"))
  }

  @Test
  fun newestDeleted_withMineNowNewest_isNone() {
    assertEquals(MessageListChange.NONE, change(listOf(mine("m1"))))
  }
}
