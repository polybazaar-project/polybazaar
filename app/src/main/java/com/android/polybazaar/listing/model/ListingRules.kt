// Co-authored-by: Claude Code (Claude Opus 5.5, Claude Sonnet 5.5, Claude Fable 5.2)
// Co-authored-by: Copilot GPT-5.6 Terra
package com.android.polybazaar.listing.model

import android.net.Uri
import com.android.polybazaar.auth.model.User
import java.math.BigDecimal
import java.time.LocalDate

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

  /**
   * Maximum number of available days a listing can store: a year of them, leap day included. Change
   * it here and in `firestore.rules` together.
   */
  const val MAX_AVAILABLE_DAYS = 366

  fun categoriesFor(type: ListingType): List<ListingCategory> =
      ListingCategory.entries.filter { it.type == type }

  const val PRICE_RANGE_MESSAGE = "Enter a price between CHF 0 and CHF 10,000"
  const val REFUND_RANGE_MESSAGE = "Enter a refund value between CHF 0 and CHF 100,000"
  const val PHOTOS_LIMIT_MESSAGE = "A listing can have at most $MAX_PHOTOS photos"
  const val RENTAL_DAYS_RANGE_MESSAGE =
      "Enter a rental duration between 1 and $MAX_RENTAL_DAYS days"
  const val AVAILABLE_DAYS_LIMIT_MESSAGE =
      "A listing can have at most $MAX_AVAILABLE_DAYS available days"

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
      // Stored photos kept plus new ones: storage.rules only allow images/0 to images/4.
      listing.imagePaths.size + listing.images.size > MAX_PHOTOS -> PHOTOS_LIMIT_MESSAGE
      description.isEmpty() -> "Description is required"
      description.length > MAX_DESCRIPTION_LENGTH ->
          "Description must be at most $MAX_DESCRIPTION_LENGTH characters"
      !isValidAmount(listing.price) -> PRICE_RANGE_MESSAGE
      listing is ItemListing && !isValidAmount(listing.refundValue, MAX_REFUND_VALUE) ->
          REFUND_RANGE_MESSAGE
      // Null means no limit, so only a set duration is checked.
      listing is ItemListing && listing.maxRentalDays?.let { it !in 1..MAX_RENTAL_DAYS } == true ->
          RENTAL_DAYS_RANGE_MESSAGE
      listing.availableDates.size > MAX_AVAILABLE_DAYS -> AVAILABLE_DAYS_LIMIT_MESSAGE
      else -> null
    }
  }

  /**
   * Whether [viewer] may contact the lender of [listing]: true only when the viewer's email is
   * verified, the listing belongs to someone else and its owner has not paused it. A listing
   * without an owner (not saved yet) has nobody to contact.
   *
   * One rule for both the "Message" and "Request booking" actions of the listing detail (#22). The
   * ViewModel exposes the result in its UI state, and the screen never recomputes it.
   */
  fun canContactLender(viewer: User, listing: Listing): Boolean =
      viewer.isEmailVerified &&
          listing.ownerUid != null &&
          listing.ownerUid != viewer.uid &&
          !listing.isPaused

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

  /**
   * An amount as the price and refund fields show it: "25" rather than "25.0", and "12.5" rather
   * than "12.50", so a saved listing's amounts fill the edit form in a form [parsePrice] accepts.
   */
  fun formatAmountInput(amount: Double): String =
      BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString()

  /**
   * Builds the listing described by a form, or returns null while the form is incomplete.
   *
   * @param photos photos on this device, to upload.
   * @param imagePaths when editing, the stored photos to keep.
   * @param listingId when editing, the listing being edited.
   * @param maxRentalDays kept from the edited item, since the form has no input for it. Ignored for
   *   services.
   * @param availableDates the days the owner offers the listing on.
   */
  fun buildListing(
      type: ListingType,
      title: String,
      description: String,
      price: String,
      category: ListingCategory?,
      photos: List<Uri>,
      refundValue: String = "",
      imagePaths: List<String> = emptyList(),
      listingId: String? = null,
      maxRentalDays: Int? = null,
      availableDates: Set<LocalDate> = emptySet(),
  ): Listing? {
    val parsedPrice = parsePrice(price) ?: return null
    if (title.isBlank() || description.isBlank() || category?.type != type) return null
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
              maxRentalDays = maxRentalDays,
              images = photos,
              imagePaths = imagePaths,
              listingId = listingId,
              availableDates = availableDates,
          )
      ListingType.SERVICE ->
          ServiceListing(
              category = category,
              price = parsedPrice,
              title = title.trim(),
              description = description.trim(),
              images = photos,
              imagePaths = imagePaths,
              listingId = listingId,
              availableDates = availableDates,
          )
    }
  }
}
