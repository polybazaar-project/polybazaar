// Co-authored-by: Claude Opus 5.5
package com.android.polybazaar.auth.model

/** Accounts can only be created with an address at this domain. */
const val EPFL_EMAIL_DOMAIN = "epfl.ch"

/**
 * A whole EPFL address: a local part made of dot-separated runs of letters, digits and `_ % + -`
 * (so no spaces, no second `@`, and no leading, trailing or doubled dot), then `@` and exactly
 * [EPFL_EMAIL_DOMAIN].
 */
private val EPFL_EMAIL_REGEX =
    Regex(
        "^[a-z0-9_%+-]+(\\.[a-z0-9_%+-]+)*@${Regex.escape(EPFL_EMAIL_DOMAIN)}$",
        RegexOption.IGNORE_CASE,
    )

/** True when [email] is a well-formed [EPFL_EMAIL_DOMAIN] address, ignoring surrounding spaces. */
fun isEpflEmail(email: String): Boolean = EPFL_EMAIL_REGEX.matches(email.trim())
