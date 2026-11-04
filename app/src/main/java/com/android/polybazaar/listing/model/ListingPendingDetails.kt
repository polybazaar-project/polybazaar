// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.model

/**
 * The fields of the listing detail screen (Figma frame "Rental listing detail", #22) that no
 * listing stores yet. They are always empty for now, and the screen shows its "to be confirmed"
 * text for them.
 *
 * Why only a placeholder: storing a field needs three changes the team hasn't agreed on yet: an
 * input in the Create Listing form, a field in `toFirestoreData`/`toListing` in
 * [FirebaseListingRepository], and a rule in `firestore.rules`. Location also depends on the map
 * task. Keeping them out of [Listing] means the create flow doesn't carry fields nobody can fill.
 *
 * How to replace it: for each field, move it to [Listing] (or [ItemListing] if only items have it),
 * add the form input, the Firestore mapping and the rule, then delete it here. Delete the class
 * when it is empty.
 *
 * @property condition the item's state, shown as a badge (for example "Like new").
 * @property location where the item or service is offered, as shown under the title.
 * @property specs the technical details listed under "Specs", one entry per line.
 * @property includedItems the accessories listed under "Included", one entry per item.
 * @property pickupTerms how and when the renter picks the item up.
 * @property returnTerms how and when the renter gives the item back.
 * @property cancellationPolicy what happens when a booking is cancelled.
 */
data class ListingPendingDetails(
    val condition: String? = null,
    val location: String? = null,
    val specs: List<String> = emptyList(),
    val includedItems: List<String> = emptyList(),
    val pickupTerms: String? = null,
    val returnTerms: String? = null,
    val cancellationPolicy: String? = null,
)
