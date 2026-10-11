// Portions of this code were generated with the help of Claude Code.
package com.android.polybazaar.auth.model

/**
 * App-level failure of a password reset request.
 *
 * The repository translates Firebase errors into a [Reason] so the ViewModel can pick a message
 * from strings.xml without importing Firebase or showing a raw Firebase message.
 *
 * @property reason why the request failed.
 * @param cause the original exception, kept for logging only, never shown to the user.
 */
class PasswordResetException(val reason: Reason, cause: Throwable? = null) :
    Exception("Password reset failed: $reason", cause) {

  /** The failure cases the reset screen tells apart. */
  enum class Reason {
    /** The address is empty or badly formatted. */
    INVALID_EMAIL,

    /** The device could not reach Firebase. */
    NETWORK,

    /** Firebase blocked the request because too many were sent from this device. */
    TOO_MANY_REQUESTS,

    /** Any other error. */
    UNKNOWN,
  }
}
