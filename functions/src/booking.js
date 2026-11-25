// Co-authored-by: Claude Code (Claude Opus 5.5)
// Decides which booking change notifies whom, without Firebase, so its tests need no emulator.
import { recipientOf } from "./payload.js";

const REQUESTED = "REQUESTED";
const CONFIRMED = "CONFIRMED";
const CANCELLED = "CANCELLED";

// `before` is null for a new booking, which the rules only allow as a request. A booking names its
// parties as a conversation does, so a cancel goes to the one who did not write it, and to no one
// (`recipientUid` null) when `writerUid` is neither. Handover and return steps keep the status, so
// they send nothing.
export function bookingNotice(before, after, writerUid) {
  if (before == null) return { type: "booking_requested", recipientUid: after.lenderUid };
  if (before.status === after.status) return null;
  if (before.status === REQUESTED && after.status === CONFIRMED) {
    return { type: "booking_confirmed", recipientUid: after.renterUid };
  }
  if (after.status === CANCELLED) {
    return {
      type: "booking_cancelled",
      recipientUid: recipientOf(after, writerUid),
      wasRequest: before.status === REQUESTED,
    };
  }
  return null;
}

// FCM data values must all be strings. Either party can receive a cancellation, so it carries the
// renter: the app picks its words and the bookings list to open by whether that is the recipient.
export function bookingPayload({ bookingId, booking, notice, listingTitle, renterName }) {
  const data = {
    type: notice.type,
    recipientUid: notice.recipientUid,
    bookingId,
    listingId: booking.listingId ?? "",
    listingTitle: listingTitle ?? "",
  };
  if (notice.type === "booking_requested") data.renterName = renterName ?? "";
  if (notice.type === "booking_cancelled") {
    data.wasRequest = String(notice.wasRequest);
    data.renterUid = booking.renterUid ?? "";
  }
  return data;
}
