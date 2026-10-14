// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

/** Constraints a listing must respect, shared by every screen that creates or edits one. */
object ListingRules {
  /** Maximum number of photos a listing can have. */
  const val MAX_PHOTOS = 5

  // Temporary lists until the team agrees on the final categories.
  val ITEM_CATEGORIES = listOf("Tools", "Electronics", "Books", "Sports", "Kitchen", "Other")
  val SERVICE_CATEGORIES =
      listOf("Tutoring", "Repairs", "Moving help", "Gardening", "Cleaning", "Other")

  fun categoriesFor(type: ListingType): List<String> =
      when (type) {
        ListingType.ITEM -> ITEM_CATEGORIES
        ListingType.SERVICE -> SERVICE_CATEGORIES
      }
}
