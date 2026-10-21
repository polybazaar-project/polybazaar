// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.profile.model

import com.android.polybazaar.auth.model.AuthRules

/** Constraints on profile fields, shared by the edit screen and the repository. */
object ProfileRules {
  const val MAX_BIO_LENGTH = 500

  const val BIO_TOO_LONG_MESSAGE = "Bio must be at most $MAX_BIO_LENGTH characters"

  fun limitBio(bio: String): String = AuthRules.limitTo(bio, MAX_BIO_LENGTH)
}
