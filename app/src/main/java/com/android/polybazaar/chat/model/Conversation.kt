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
 */
data class Conversation(
    val listingId: String,
    val listingTitle: String,
    val renterUid: String,
    val lenderUid: String,
    val lastMessageAt: Instant? = null,
) {
  /** Firestore document ID of the conversation. */
  val id: String
    get() = conversationId(listingId, renterUid)

  /** uids of the renter and the lender, in that order. */
  val participants: List<String>
    get() = listOf(renterUid, lenderUid)
}
