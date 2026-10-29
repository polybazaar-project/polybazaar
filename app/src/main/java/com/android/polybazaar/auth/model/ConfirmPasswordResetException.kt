// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.auth.model

/**
 * App-level failure of setting a new password from a reset link.
 *
 * The repository translates Firebase errors into a [Reason] so the ViewModel can pick a message
 * from strings.xml without importing Firebase or showing a raw Firebase message.
 *
 * @property reason why the new password was not set.
 * @param cause the original exception, kept for logging only, never shown to the user.
 */
class ConfirmPasswordResetException(val reason: Reason, cause: Throwable? = null) :
    Exception("Confirm password reset failed: $reason", cause) {

  /** The failure cases the reset password screen tells apart. */
  enum class Reason {
    /** The reset link is blank, malformed, expired or was already used. */
    INVALID_LINK,

    /** Firebase rejected the new password as too weak. */
    WEAK_PASSWORD,

    /** The device could not reach Firebase. */
    NETWORK,

    /** Firebase blocked the request because too many were sent from this device. */
    TOO_MANY_REQUESTS,

    /** Any other error. */
    UNKNOWN,
  }
}
