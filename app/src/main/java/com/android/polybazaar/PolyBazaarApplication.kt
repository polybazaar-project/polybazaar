// Co-authored-by: Copilot (Auto)
package com.android.polybazaar

import android.app.Application
import com.android.polybazaar.booking.model.ConfirmationJournal
import com.android.polybazaar.booking.model.SharedPreferencesConfirmationJournal

class PolyBazaarApplication : Application() {
  val confirmationJournal: ConfirmationJournal by lazy {
    SharedPreferencesConfirmationJournal(this)
  }
}
