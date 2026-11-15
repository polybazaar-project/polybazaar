// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.chat.model

/** Message limits shared by the conversation screen and the repository; match firestore.rules. */
object ChatRules {
  const val MAX_MESSAGE_LENGTH = 1000

  /**
   * The length [MAX_MESSAGE_LENGTH] applies to: the trimmed text, as sent, in UTF-16 units.
   *
   * UTF-16 units on purpose, as [String.length] counts them and as the security rules' `size()`
   * does, so an emoji counts as 2. Counting code points would let the app accept messages the rules
   * refuse.
   */
  fun countedLength(text: String): Int = text.trim().length

  fun isSendable(text: String): Boolean = countedLength(text) in 1..MAX_MESSAGE_LENGTH
}
