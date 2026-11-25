// Co-authored-by: Claude Code (Claude Opus 5.5)
import assert from "node:assert/strict";
import { describe, test } from "node:test";
import { bookingNotice, bookingPayload } from "../src/booking.js";

const RENTER = "renter-uid";
const LENDER = "lender-uid";

const requested = {
  listingId: "listing-1",
  renterUid: RENTER,
  lenderUid: LENDER,
  days: [20500, 20501],
  status: "REQUESTED",
};
const confirmed = { ...requested, status: "CONFIRMED" };
const cancelled = { ...requested, status: "CANCELLED" };

describe("bookingNotice", () => {
  test("a request notifies the lender", () => {
    assert.deepEqual(bookingNotice(null, requested), {
      type: "booking_requested",
      recipientUid: LENDER,
    });
  });

  test("an accept notifies the renter", () => {
    assert.deepEqual(bookingNotice(requested, confirmed, LENDER), {
      type: "booking_confirmed",
      recipientUid: RENTER,
    });
  });

  test("a renter's cancel notifies the lender of a cancelled request", () => {
    assert.deepEqual(bookingNotice(requested, cancelled, RENTER), {
      type: "booking_cancelled",
      recipientUid: LENDER,
      wasRequest: true,
    });
  });

  test("a lender's decline notifies the renter of a cancelled request", () => {
    assert.deepEqual(bookingNotice(requested, cancelled, LENDER), {
      type: "booking_cancelled",
      recipientUid: RENTER,
      wasRequest: true,
    });
  });

  test("a lender cancelling a confirmed booking notifies the renter", () => {
    assert.deepEqual(bookingNotice(confirmed, cancelled, LENDER), {
      type: "booking_cancelled",
      recipientUid: RENTER,
      wasRequest: false,
    });
  });

  test("a renter cancelling a confirmed booking notifies the lender", () => {
    assert.deepEqual(bookingNotice(confirmed, cancelled, RENTER), {
      type: "booking_cancelled",
      recipientUid: LENDER,
      wasRequest: false,
    });
  });

  test("a request cancelled by accepting another notifies its renter", () => {
    // The lender's accept cancels the overlapping requests in the same batch, as the lender.
    const overlapping = { ...requested, renterUid: "other-renter-uid", days: [20501] };
    const after = { ...overlapping, status: "CANCELLED" };
    assert.deepEqual(bookingNotice(overlapping, after, LENDER), {
      type: "booking_cancelled",
      recipientUid: "other-renter-uid",
      wasRequest: true,
    });
  });

  test("a handover or return step sends nothing", () => {
    const steps = {
      lenderHandoverConfirmed: LENDER,
      renterHandoverConfirmed: RENTER,
      renterReturnConfirmed: RENTER,
      lenderReturnConfirmed: LENDER,
    };
    for (const [step, writer] of Object.entries(steps)) {
      assert.equal(bookingNotice(confirmed, { ...confirmed, [step]: true }, writer), null, step);
    }
  });

  test("cancelling a booking already cancelled sends nothing", () => {
    assert.equal(bookingNotice(cancelled, cancelled, RENTER), null);
  });

  test("a cancel written by neither party has no recipient", () => {
    for (const writer of ["eve-uid", undefined]) {
      assert.deepEqual(bookingNotice(requested, cancelled, writer), {
        type: "booking_cancelled",
        recipientUid: null,
        wasRequest: true,
      });
    }
  });
});

describe("bookingPayload", () => {
  const payload = (booking, notice, facts = {}) =>
    bookingPayload({
      bookingId: "booking-1",
      booking,
      notice,
      listingTitle: "Bike",
      renterName: "renter",
      ...facts,
    });

  test("a request names the renter", () => {
    const data = payload(requested, bookingNotice(null, requested));
    assert.deepEqual(data, {
      type: "booking_requested",
      recipientUid: LENDER,
      bookingId: "booking-1",
      listingId: "listing-1",
      listingTitle: "Bike",
      renterName: "renter",
    });
  });

  test("an accept carries the booking and its listing", () => {
    const data = payload(confirmed, bookingNotice(requested, confirmed, LENDER));
    assert.deepEqual(data, {
      type: "booking_confirmed",
      recipientUid: RENTER,
      bookingId: "booking-1",
      listingId: "listing-1",
      listingTitle: "Bike",
    });
  });

  test("a cancel says whether it was a request, and who the renter is", () => {
    const data = payload(cancelled, bookingNotice(requested, cancelled, RENTER));
    assert.deepEqual(data, {
      type: "booking_cancelled",
      recipientUid: LENDER,
      bookingId: "booking-1",
      listingId: "listing-1",
      listingTitle: "Bike",
      wasRequest: "true",
      renterUid: RENTER,
    });
    const booking = payload(cancelled, bookingNotice(confirmed, cancelled, LENDER));
    assert.equal(booking.wasRequest, "false");
    assert.equal(booking.renterUid, RENTER);
  });

  test("every value is a string, as FCM data requires, even with no title or name", () => {
    const notices = [
      bookingNotice(null, requested),
      bookingNotice(requested, confirmed, LENDER),
      bookingNotice(requested, cancelled, LENDER),
    ];
    for (const notice of notices) {
      const data = payload(cancelled, notice, { listingTitle: undefined, renterName: undefined });
      for (const [key, value] of Object.entries(data)) {
        assert.equal(typeof value, "string", `${notice.type}.${key}`);
      }
    }
  });

  test("the longest ids, title and username still fit FCM's 4096 bytes", () => {
    // Clients choose the booking and listing ids, which only Firestore's 1500-byte limit on
    // document ids bounds. A Firebase uid has at most 128 characters.
    const booking = {
      ...cancelled,
      listingId: "l".repeat(1500),
      renterUid: "r".repeat(128),
      lenderUid: "u".repeat(128),
    };
    const notices = [
      bookingNotice(null, booking),
      bookingNotice(requested, booking, booking.lenderUid),
    ];
    for (const notice of notices) {
      const data = payload(booking, notice, {
        bookingId: "b".repeat(1500),
        listingTitle: "😀".repeat(80),
        renterName: "😀".repeat(30),
      });
      const bytes = Object.entries(data).reduce(
        (sum, [key, value]) => sum + Buffer.byteLength(key) + Buffer.byteLength(value),
        0,
      );
      assert.ok(bytes < 4096, `${notice.type}: ${bytes} bytes`);
    }
  });
});
