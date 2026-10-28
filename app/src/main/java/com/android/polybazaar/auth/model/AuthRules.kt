// Co-authored-by: Claude Code (Claude Sonnet 5.5)
package com.android.polybazaar.auth.model

/** Validation rules for the sign-up fields, shared by the UI and the repository. */
object AuthRules {
  const val MIN_USERNAME_LENGTH = 3

  /** Must stay within the usernames size limit enforced by the Firestore security rules. */
  const val MAX_USERNAME_LENGTH = 30

  /** Maximum email address length defined by RFC 5321. */
  const val MAX_EMAIL_LENGTH = 254

  /** Firebase Auth rejects passwords shorter than this. */
  const val MIN_PASSWORD_LENGTH = 6

  /** Product cap on password length. */
  const val MAX_PASSWORD_LENGTH = 128

  /**
   * Hard cap on password input, matching Firebase Auth's own 4096-character limit and keeping
   * rememberSaveable state small.
   */
  const val MAX_PASSWORD_INPUT_LENGTH = 4096

  /**
   * Hard cap on username and email input, far above their limits, so a huge paste cannot bloat
   * rememberSaveable state. Input between the limit and this cap is kept and reported as an error.
   */
  const val MAX_TEXT_INPUT_LENGTH = 1024

  private val RESERVED_FIRESTORE_ID = Regex("__.*__")

  /**
   * Returns why the raw [username] cannot be used, or null if it is valid. Validation runs on the
   * [normalizeUsername] form, which becomes a Firestore document ID and so must follow Firestore's
   * ID rules. A blank username has no error here; callers treat emptiness separately.
   */
  fun usernameError(username: String): String? {
    val id = normalizeUsername(username)
    return when {
      id.contains('/') -> "Username cannot contain /"
      id == "." || id == ".." || RESERVED_FIRESTORE_ID.matches(id) -> "This username is not allowed"
      id.isNotEmpty() && id.length < MIN_USERNAME_LENGTH ->
          "Username must be at least $MIN_USERNAME_LENGTH characters"
      id.length > MAX_USERNAME_LENGTH -> "Username must be at most $MAX_USERNAME_LENGTH characters"
      else -> null
    }
  }

  fun emailLengthError(email: String): String? =
      if (email.trim().length > MAX_EMAIL_LENGTH)
          "Email must be at most $MAX_EMAIL_LENGTH characters"
      else null

  fun passwordLengthError(password: String): String? =
      if (password.length > MAX_PASSWORD_LENGTH)
          "Password must be at most $MAX_PASSWORD_LENGTH characters"
      else null

  /** Takes the first [max] chars of [text]; a cut must not leave half of a surrogate pair. */
  fun limitTo(text: String, max: Int): String {
    if (text.length <= max) return text
    val cut = if (max > 0 && text[max - 1].isHighSurrogate()) max - 1 else max
    return text.substring(0, cut)
  }

  /**
   * Caps free-text input to [max] chars after dropping leading whitespace, so padding cannot push
   * real characters past the cap. Trailing whitespace is kept while the user is still typing.
   */
  fun limitInput(text: String, max: Int): String = limitTo(text.trimStart(), max)

  /**
   * The form a username is stored and validated in. Lowercasing can lengthen a string (for example
   * "İ" becomes two chars), so validation must run on this form.
   */
  fun normalizeUsername(raw: String): String = raw.trim().lowercase()
}
