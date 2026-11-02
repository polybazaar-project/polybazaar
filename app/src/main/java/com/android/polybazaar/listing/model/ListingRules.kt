// Co-authored-by: Claude Code (Claude Opus 5.5, Claude Sonnet 5.5)
package com.android.polybazaar.listing.model

import android.net.Uri

/** Constraints a listing must respect, shared by every screen that creates or edits one. */
object ListingRules {
  /** Maximum number of photos a listing can have. */
  const val MAX_PHOTOS = 5

  const val MAX_TITLE_LENGTH = 80
  const val MAX_DESCRIPTION_LENGTH = 1000

  /** Upper bound, in CHF, for a price. */
  const val MAX_PRICE = 10_000.0

  /**
   * Upper bound, in CHF, for a refund value. Higher than [MAX_PRICE] because the refund value can
   * be a deposit on valuable equipment, which is worth more than a rental's price.
   */
  const val MAX_REFUND_VALUE = 100_000.0

  /**
   * Upper bound, in days, for [ItemListing.maxRentalDays]: long enough to rent an item for a whole
   * semester, exams included. Change it here and in `firestore.rules` together.
   */
  const val MAX_RENTAL_DAYS = 180

  // Temporary lists until the team agrees on the final categories.
  val ITEM_CATEGORIES = listOf("Tools", "Electronics", "Books", "Sports", "Kitchen", "Other")
  val SERVICE_CATEGORIES =
      listOf("Tutoring", "Repairs", "Moving help", "Gardening", "Cleaning", "Other")

  fun categoriesFor(type: ListingType): List<String> =
      when (type) {
        ListingType.ITEM -> ITEM_CATEGORIES
        ListingType.SERVICE -> SERVICE_CATEGORIES
      }

  const val PRICE_RANGE_MESSAGE = "Enter a price between CHF 0 and CHF 10,000"
  const val REFUND_RANGE_MESSAGE = "Enter a refund value between CHF 0 and CHF 100,000"
  const val RENTAL_DAYS_RANGE_MESSAGE =
      "Enter a rental duration between 1 and $MAX_RENTAL_DAYS days"

  /** Whether [amount] is above zero and at most [max]. */
  fun isValidAmount(amount: Double, max: Double = MAX_PRICE): Boolean = amount > 0 && amount <= max

  /**
   * Returns why [listing] cannot be stored, or null if it is valid. Lengths are measured on the
   * trimmed text because that is what gets stored.
   */
  fun errorFor(listing: Listing): String? {
    val title = listing.title.trim()
    val description = listing.description.trim()
    return when {
      title.isEmpty() -> "Title is required"
      title.length > MAX_TITLE_LENGTH -> "Title must be at most $MAX_TITLE_LENGTH characters"
      description.isEmpty() -> "Description is required"
      description.length > MAX_DESCRIPTION_LENGTH ->
          "Description must be at most $MAX_DESCRIPTION_LENGTH characters"
      !isValidAmount(listing.price) -> PRICE_RANGE_MESSAGE
      listing is ItemListing && !isValidAmount(listing.refundValue, MAX_REFUND_VALUE) ->
          REFUND_RANGE_MESSAGE
      // Null means no limit, so only a set duration is checked.
      listing is ItemListing && listing.maxRentalDays?.let { it !in 1..MAX_RENTAL_DAYS } == true ->
          RENTAL_DAYS_RANGE_MESSAGE
      else -> null
    }
  }

  /**
   * Up to five integer digits (10000.00 is the largest valid amount), at most one decimal separator
   * and two decimals: what the price field accepts.
   */
  private val PRICE_INPUT = Regex("""\d{0,5}([.,]\d{0,2})?""")

  /** Same as [PRICE_INPUT] with a sixth integer digit, since 100000.00 is the largest refund. */
  private val REFUND_INPUT = Regex("""\d{0,6}([.,]\d{0,2})?""")

  fun isValidPriceInput(input: String): Boolean = PRICE_INPUT.matches(input)

  fun isValidRefundInput(input: String): Boolean = REFUND_INPUT.matches(input)

  /** Parses a user-typed amount, accepting either "." or "," as decimal separator. */
  fun parsePrice(input: String, max: Double = MAX_PRICE): Double? =
      input.trim().replace(',', '.').toDoubleOrNull()?.takeIf { isValidAmount(it, max) }

  /** Builds the listing described by a form, or returns null while the form is incomplete. */
  fun buildListing(
      type: ListingType,
      title: String,
      description: String,
      price: String,
      category: String?,
      photos: List<Uri>,
      refundValue: String = "",
  ): Listing? {
    val parsedPrice = parsePrice(price) ?: return null
    if (title.isBlank() || description.isBlank() || category == null) return null
    return when (type) {
      ListingType.ITEM ->
          ItemListing(
              category = category,
              price = parsedPrice,
              title = title.trim(),
              description = description.trim(),
              // Left blank, the refund value falls back to the price, like in ItemListing.
              refundValue =
                  if (refundValue.isBlank()) parsedPrice
                  else parsePrice(refundValue, MAX_REFUND_VALUE) ?: return null,
              images = photos,
          )
      ListingType.SERVICE ->
          ServiceListing(
              category = category,
              price = parsedPrice,
              title = title.trim(),
              description = description.trim(),
              images = photos,
          )
    }
  }
}
