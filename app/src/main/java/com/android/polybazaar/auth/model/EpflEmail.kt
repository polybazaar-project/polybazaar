// Co-authored-by: Claude Opus 5.5
package com.android.polybazaar.auth.model

/** Accounts can only be created with an address at this domain. */
const val EPFL_EMAIL_DOMAIN = "epfl.ch"

/** True when [email] is an address at [EPFL_EMAIL_DOMAIN], ignoring case and surrounding spaces. */
fun isEpflEmail(email: String): Boolean {
  val address = email.trim().lowercase()
  val suffix = "@$EPFL_EMAIL_DOMAIN"
  return address.length > suffix.length && address.endsWith(suffix)
}
