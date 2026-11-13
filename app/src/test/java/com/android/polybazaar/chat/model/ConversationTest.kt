// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTest {
  private val lastMessageAt = Instant.ofEpochSecond(1_700_000_060)
  private val fromLender =
      Conversation(
          listingId = "listing",
          listingTitle = "Drill",
          renterUid = "renter",
          lenderUid = "lender",
          lastMessageAt = lastMessageAt,
          lastMessageText = "Yes, it is",
          lastMessageSenderUid = "lender",
      )

  @Test
  fun conversationWithoutMessageIsNotUnread() {
    val empty =
        fromLender.copy(lastMessageAt = null, lastMessageText = null, lastMessageSenderUid = null)

    assertFalse(empty.isUnreadFor("renter"))
  }

  @Test
  fun myOwnLastMessageIsNotUnreadForMe() {
    assertFalse(fromLender.isUnreadFor("lender"))
  }

  @Test
  fun lastMessageINeverReadIsUnread() {
    assertTrue(fromLender.isUnreadFor("renter"))
  }

  @Test
  fun otherParticipantsReadTimeDoesNotMarkItReadForMe() {
    val readByLender = fromLender.copy(lastReadAt = mapOf("lender" to lastMessageAt))

    assertTrue(readByLender.isUnreadFor("renter"))
  }

  @Test
  fun lastMessageReadBeforeItWasSentIsUnread() {
    val readBefore = fromLender.copy(lastReadAt = mapOf("renter" to lastMessageAt.minusSeconds(1)))

    assertTrue(readBefore.isUnreadFor("renter"))
  }

  @Test
  fun lastMessageReadAtItsSendTimeIsRead() {
    val readAt = fromLender.copy(lastReadAt = mapOf("renter" to lastMessageAt))

    assertFalse(readAt.isUnreadFor("renter"))
  }

  @Test
  fun lastMessageReadAfterItWasSentIsRead() {
    val readAfter = fromLender.copy(lastReadAt = mapOf("renter" to lastMessageAt.plusSeconds(1)))

    assertFalse(readAfter.isUnreadFor("renter"))
  }

  @Test
  fun conversationStoredBeforePreviewsIsUnreadForBothUntilRead() {
    val old = fromLender.copy(lastMessageText = null, lastMessageSenderUid = null)

    assertTrue(old.isUnreadFor("renter"))
    assertTrue(old.isUnreadFor("lender"))
  }
}
