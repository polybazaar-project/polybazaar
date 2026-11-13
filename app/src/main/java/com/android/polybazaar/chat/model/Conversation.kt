// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

import java.time.Instant

/** ID of the conversation between the renter and the owner of a listing. */
fun conversationId(listingId: String, renterUid: String): String = "${listingId}_$renterUid"

/**
 * A conversation between a renter and the lender about one listing.
 *
 * @property listingId ID of the listing the conversation is about.
 * @property listingTitle title of the listing, shown in the inbox.
 * @property renterUid uid of the user who contacts the lender.
 * @property lenderUid uid of the listing owner.
 * @property lastMessageAt when the latest message was sent, null before the first message.
 * @property lastMessageText text of the latest message, shown as the inbox preview; null before the
 *   first message and on conversations created before previews existed.
 * @property lastMessageSenderUid uid of who sent the latest message; null when [lastMessageText]
 *   is.
 * @property lastReadAt when each participant last read the conversation, by uid. A participant who
 *   never opened it since read times exist has no entry.
 */
data class Conversation(
    val listingId: String,
    val listingTitle: String,
    val renterUid: String,
    val lenderUid: String,
    val lastMessageAt: Instant? = null,
    val lastMessageText: String? = null,
    val lastMessageSenderUid: String? = null,
    val lastReadAt: Map<String, Instant> = emptyMap(),
) {
  /**
   * Whether the latest message is one [uid] has not read: there is a message, it is not from [uid],
   * and [uid] never read the conversation or last read it before that message.
   */
  fun isUnreadFor(uid: String): Boolean {
    val lastMessage = lastMessageAt ?: return false
    if (lastMessageSenderUid == uid) return false
    val readAt = lastReadAt[uid] ?: return true
    return readAt.isBefore(lastMessage)
  }

  /** Firestore document ID of the conversation. */
  val id: String
    get() = conversationId(listingId, renterUid)

  /** uids of the renter and the lender, in that order. */
  val participants: List<String>
    get() = listOf(renterUid, lenderUid)
}
