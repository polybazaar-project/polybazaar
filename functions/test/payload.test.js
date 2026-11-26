// Co-authored-by: Claude Code (Claude Opus 5.5)
import assert from "node:assert/strict";
import { describe, test } from "node:test";
import { failureSummary, messagePayload, previewOf, recipientOf } from "../src/payload.js";

const RENTER = "renter-uid";
const LENDER = "lender-uid";
const conversation = {
  listingId: "listing-1",
  listingTitle: "Bike",
  renterUid: RENTER,
  lenderUid: LENDER,
};

const payload = (message, overrides = {}) =>
  messagePayload({
    conversation,
    message,
    senderName: "renter",
    recipientUid: LENDER,
    ...overrides,
  });

test("the recipient is the participant who did not send", () => {
  assert.equal(recipientOf(conversation, RENTER), LENDER);
  assert.equal(recipientOf(conversation, LENDER), RENTER);
  assert.equal(recipientOf(conversation, "eve-uid"), null);
});

describe("previewOf", () => {
  test("a text is cut at 100 characters", () => {
    assert.equal(previewOf("Still available?"), "Still available?");
    assert.equal(previewOf("a".repeat(150)), "a".repeat(100));
  });

  test("an emoji, a flag or a family at the cut is kept whole", () => {
    for (const character of ["😀", "🇨🇭", "👨‍👩‍👧"]) {
      const kept = "a".repeat(99) + character;
      assert.equal(previewOf(kept + "b"), kept);
    }
  });

  test("a text of stacked accents is cut to 1000 bytes at a character boundary", () => {
    const character = "a" + "́".repeat(19);
    const expected = character.repeat(Math.floor(1000 / Buffer.byteLength(character)));
    assert.equal(previewOf(character.repeat(100)), expected);
  });

  test("a first character over 1000 bytes is cut inside it, so the preview stays non-empty", () => {
    const bytes = Buffer.byteLength(previewOf("a" + "́".repeat(1999)));
    assert.ok(bytes > 990 && bytes <= 1000, `${bytes} bytes`);
  });
});

describe("messagePayload", () => {
  test("renter to lender", () => {
    assert.deepEqual(payload({ senderUid: RENTER, text: "Hi" }), {
      type: "message",
      recipientUid: LENDER,
      listingId: "listing-1",
      renterUid: RENTER,
      senderName: "renter",
      listingTitle: "Bike",
      preview: "Hi",
    });
  });

  test("lender to renter", () => {
    const data = payload(
      { senderUid: LENDER, text: "Yes" },
      { senderName: "lender", recipientUid: RENTER },
    );
    assert.equal(data.recipientUid, RENTER);
    assert.equal(data.renterUid, RENTER);
    assert.equal(data.senderName, "lender");
  });

  test("a photo-only message has an empty preview, and a photo's caption is its preview", () => {
    const photo = { senderUid: RENTER, imagePath: "conversations/c/m.jpg", photoPending: false };
    assert.equal(payload(photo).preview, "");
    assert.equal(payload({ ...photo, text: "The scratch" }).preview, "The scratch");
  });

  test("the longest listing id, title, username and preview still fit FCM's 4096 bytes", () => {
    const data = payload(
      { senderUid: RENTER, text: "a" + "́".repeat(1999) },
      {
        conversation: {
          ...conversation,
          listingId: "l".repeat(1500 - 1 - 28),
          listingTitle: "😀".repeat(80),
          renterUid: "r".repeat(28),
        },
        senderName: "😀".repeat(30),
        recipientUid: "u".repeat(28),
      },
    );
    const bytes = Object.entries(data).reduce(
      (sum, [key, value]) => sum + Buffer.byteLength(key) + Buffer.byteLength(value),
      0,
    );
    assert.ok(bytes < 4096, `${bytes} bytes`);
  });

  test("every value is a string, as FCM data requires", () => {
    const data = payload(
      { senderUid: RENTER },
      { conversation: { ...conversation, listingTitle: undefined }, senderName: undefined },
    );
    for (const value of Object.values(data)) assert.equal(typeof value, "string");
  });
});

describe("failureSummary", () => {
  const ok = { success: true };
  const failure = (code) => ({ success: false, error: { code } });
  const summarize = (...responses) =>
    failureSummary(
      responses.map((_, i) => `d${i}`),
      { successCount: responses.filter((r) => r.success).length, responses },
    );

  test("deletes unregistered devices, and invalid ones when another device was reached", () => {
    assert.deepEqual(
      summarize(
        ok,
        failure("messaging/installation-id-not-registered"),
        failure("messaging/registration-token-not-registered"),
        failure("messaging/invalid-argument"),
        failure("messaging/internal-error"),
        failure("messaging/internal-error"),
      ),
      { deleted: ["d1", "d2", "d3"], failed: 2, codes: { "messaging/internal-error": 2 } },
    );
  });

  test("keeps invalid devices when none was reached, since the payload may be at fault", () => {
    assert.deepEqual(
      summarize(
        failure("messaging/invalid-argument"),
        failure("messaging/installation-id-not-registered"),
      ),
      { deleted: ["d1"], failed: 1, codes: { "messaging/invalid-argument": 1 } },
    );
  });
});
