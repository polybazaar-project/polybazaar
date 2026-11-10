// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

/** Message limits shared by the conversation screen and the repository; match firestore.rules. */
object ChatRules {
  const val MAX_MESSAGE_LENGTH = 1000

  fun isSendable(text: String): Boolean = text.trim().length in 1..MAX_MESSAGE_LENGTH
}
