// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.error.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.polybazaar.R
import com.android.polybazaar.error.model.AppError

/** The string resource shown to the user for this error. */
@get:StringRes
val AppError.messageRes: Int
  get() =
      when (this) {
        AppError.NETWORK -> R.string.error_network
        AppError.PERMISSION_DENIED -> R.string.error_permission_denied
        AppError.NOT_FOUND -> R.string.error_not_found
        AppError.UNAUTHENTICATED -> R.string.error_unauthenticated
        AppError.INVALID_CREDENTIALS -> R.string.error_invalid_credentials
        AppError.INVALID_EMAIL -> R.string.error_invalid_email
        AppError.WEAK_PASSWORD -> R.string.error_weak_password
        AppError.EMAIL_ALREADY_IN_USE -> R.string.error_email_already_in_use
        AppError.TOO_MANY_REQUESTS -> R.string.error_too_many_requests
        AppError.UNKNOWN -> R.string.error_unknown
      }

/** The localized message shown to the user for this error. */
@Composable fun AppError.message(): String = stringResource(messageRes)
