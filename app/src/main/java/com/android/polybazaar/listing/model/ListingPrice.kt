// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

import java.util.Locale

/**
 * An amount in Swiss francs as the app shows it: `8.0` → `"CHF 8"`, `8.5` → `"CHF 8.50"`. Whole
 * amounts stay short because the screens add a suffix such as "/ day" next to them.
 */
fun formatChf(amount: Double): String {
  // Locale.ROOT keeps the decimal point, whatever the device language.
  val digits = String.format(Locale.ROOT, "%.2f", amount).removeSuffix(".00")
  return "CHF $digits"
}
