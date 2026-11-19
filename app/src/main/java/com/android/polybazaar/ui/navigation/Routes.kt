// Co-authored-by: Claude Code (Claude Opus 5.5)
// Co-authored-by: Claude Code (Claude Sonnet 5.5)
// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Fable 5.2)
// Co-authored-by: Copilot (Auto)
package com.android.polybazaar.ui.navigation

import android.net.Uri

object Routes {
  const val LOADING = "loading"
  const val SIGN_IN = "sign_in"
  const val CREATE_ACCOUNT = "create_account"
  const val FORGOT_PASSWORD = "forgot_password"
  const val VERIFY_EMAIL = "verify_email"
  const val BROWSE = "browse"
  const val MAP = "map"
  const val CREATE_LISTING = "create_listing"
  const val LISTING_ID = "listingId"
  const val EDIT_LISTING = "edit_listing/{$LISTING_ID}"
  const val LISTING_DETAIL = "listing_detail/{$LISTING_ID}"
  const val LENDER_UID = "lenderUid"
  const val LENDER_PROFILE = "lender_profile/{$LENDER_UID}"
  const val PROFILE = "profile"
  const val EDIT_PROFILE = "edit_profile"
  const val SETTINGS = "settings"
  const val INBOX = "inbox"
  const val RENTER_UID = "renterUid"
  const val CONVERSATION = "conversation/{$LISTING_ID}/{$RENTER_UID}"
  const val BOOKING_REQUEST = "booking_request/{$LISTING_ID}"
  const val RENTER_BOOKINGS = "renter_bookings"
  const val LENDER_BOOKINGS = "lender_bookings/{$LISTING_ID}"

  /** The route of the "Edit listing" screen for the listing [listingId]. */
  fun editListing(listingId: String) = "edit_listing/${Uri.encode(listingId)}"

  /** The route of the listing detail screen for the listing [listingId]. */
  fun listingDetail(listingId: String) = "listing_detail/${Uri.encode(listingId)}"

  /** The route of the profile of the lender with uid [lenderUid]. */
  fun lenderProfile(lenderUid: String) = "lender_profile/${Uri.encode(lenderUid)}"

  /**
   * The route of the conversation about the listing [listingId] between the renter [renterUid] and
   * the listing's owner.
   */
  fun conversation(listingId: String, renterUid: String) =
      "conversation/${Uri.encode(listingId)}/${Uri.encode(renterUid)}"

  /** The route of the screen where a renter requests the listing [listingId] for some days. */
  fun bookingRequest(listingId: String) = "booking_request/${Uri.encode(listingId)}"

  /** The route of the lender's bookings of the listing [listingId]. */
  fun lenderBookings(listingId: String) = "lender_bookings/${Uri.encode(listingId)}"
}
