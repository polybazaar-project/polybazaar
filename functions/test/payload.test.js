// Co-authored-by: Claude Code (Claude Opus 5.5)
import assert from "node:assert/strict";
import { describe, test } from "node:test";
import {
  failureSummary,
  messagePayload,
  previewOf,
  recipientOf,
  unusableDevices,
} from "../src/payload.js";

const RENTER = "renter-uid";
const LENDER = "lender-uid";

const conversation = {
  listingId: "listing-1",
  listingTitle: "Bike",
  renterUid: RENTER,
  lenderUid: LENDER,
  participants: [RENTER, LENDER],
};

describe("recipientOf", () => {
  test("a renter's message goes to the lender", () => {
    assert.equal(recipientOf(conversation, RENTER), LENDER);
  });

  test("a lender's message goes to the renter", () => {
    assert.equal(recipientOf(conversation, LENDER), RENTER);
  });

  test("a sender who is not a participant has no recipient", () => {
    assert.equal(recipientOf(conversation, "eve-uid"), null);
  });
});

describe("previewOf", () => {
  test("a short text is kept whole", () => {
    assert.equal(previewOf("Still available?"), "Still available?");
  });

  test("a long text is cut at 100 characters", () => {
    const text = "a".repeat(150);
    assert.equal(previewOf(text), "a".repeat(100));
  });

  test("an emoji at the cut is kept whole", () => {
    const text = "a".repeat(99) + "😀" + "b";
    assert.equal(previewOf(text), "a".repeat(99) + "😀");
  });

  test("a flag or a family at the cut is kept whole", () => {
    assert.equal(previewOf("a".repeat(99) + "🇨🇭b"), "a".repeat(99) + "🇨🇭");
    assert.equal(previewOf("a".repeat(99) + "👨‍👩‍👧b"), "a".repeat(99) + "👨‍👩‍👧");
  });

  test("a text of stacked accents is cut to 1000 bytes at a character boundary", () => {
    const character = "a" + "\u0301".repeat(19);
    const preview = previewOf(character.repeat(100));
    assert.ok(Buffer.byteLength(preview) <= 1000);
    assert.equal(preview, character.repeat(Math.floor(1000 / Buffer.byteLength(character))));
  });

  test("a first character over 1000 bytes is cut inside it, so the preview stays non-empty", () => {
    const preview = previewOf("a" + "\u0301".repeat(1999));
    assert.ok(preview.startsWith("a\u0301"));
    assert.ok(Buffer.byteLength(preview) <= 1000);
    assert.ok(Buffer.byteLength(preview) > 990);
  });

  test("a photo-only message has an empty preview", () => {
    assert.equal(previewOf(undefined), "");
  });
});

describe("messagePayload", () => {
  test("renter to lender", () => {
    const data = messagePayload({
      conversation,
      message: { senderUid: RENTER, text: "Hi" },
      senderName: "renter",
      recipientUid: LENDER,
    });
    assert.deepEqual(data, {
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
    const data = messagePayload({
      conversation,
      message: { senderUid: LENDER, text: "Yes" },
      senderName: "lender",
      recipientUid: RENTER,
    });
    assert.equal(data.recipientUid, RENTER);
    assert.equal(data.renterUid, RENTER);
    assert.equal(data.senderName, "lender");
    assert.equal(data.preview, "Yes");
  });

  test("a photo-only message", () => {
    const data = messagePayload({
      conversation,
      message: {
        senderUid: RENTER,
        imagePath: "conversations/c/m.jpg",
        photoPending: true,
      },
      senderName: "renter",
      recipientUid: LENDER,
    });
    assert.equal(data.preview, "");
  });

  test("a photo with a caption previews the caption", () => {
    const data = messagePayload({
      conversation,
      message: {
        senderUid: RENTER,
        text: "The scratch",
        imagePath: "conversations/c/m.jpg",
        photoPending: true,
      },
      senderName: "renter",
      recipientUid: LENDER,
    });
    assert.equal(data.preview, "The scratch");
  });

  test("a long text is cut at 100 characters", () => {
    const data = messagePayload({
      conversation,
      message: { senderUid: RENTER, text: "x".repeat(300) },
      senderName: "renter",
      recipientUid: LENDER,
    });
    assert.equal(data.preview, "x".repeat(100));
  });

  test("the largest message the rules allow fits FCM's 4096-byte payload", () => {
    const data = messagePayload({
      conversation: {
        ...conversation,
        listingId: "l".repeat(40),
        listingTitle: "😀".repeat(80),
      },
      message: { senderUid: RENTER, text: "a" + "\u0301".repeat(1999) },
      senderName: "😀".repeat(30),
      recipientUid: "u".repeat(128),
    });
    const bytes = Object.entries(data).reduce(
      (sum, [key, value]) => sum + Buffer.byteLength(key) + Buffer.byteLength(value),
      0,
    );
    assert.ok(bytes < 4096, `${bytes} bytes`);
  });

  test("every value is a string, as FCM data requires", () => {
    const data = messagePayload({
      conversation: { ...conversation, listingTitle: undefined },
      message: { senderUid: RENTER },
      senderName: undefined,
      recipientUid: LENDER,
    });
    for (const value of Object.values(data)) {
      assert.equal(typeof value, "string");
    }
  });
});

const ok = { success: true };
const failure = (code) => ({ success: false, error: { code } });

describe("failureSummary", () => {
  test("splits the failures into devices to delete and failures to report", () => {
    const fids = ["a", "b", "c", "d", "e"];
    const response = {
      successCount: 1,
      failureCount: 4,
      responses: [
        ok,
        failure("messaging/installation-id-not-registered"),
        failure("messaging/internal-error"),
        failure("messaging/third-party-auth-error"),
        failure("messaging/internal-error"),
      ],
    };
    assert.deepEqual(failureSummary(fids, response), {
      deleted: ["b"],
      failed: 3,
      codes: {
        "messaging/internal-error": 2,
        "messaging/third-party-auth-error": 1,
      },
    });
  });

  test("reports nothing when every failure is a device to delete", () => {
    const response = {
      successCount: 1,
      failureCount: 1,
      responses: [ok, failure("messaging/installation-id-not-registered")],
    };
    assert.deepEqual(failureSummary(["a", "b"], response), {
      deleted: ["b"],
      failed: 0,
      codes: {},
    });
  });
});

describe("unusableDevices", () => {
  test("drops unregistered devices, and invalid ones when another device was reached", () => {
    const fids = ["a", "b", "c", "d", "e"];
    const response = {
      successCount: 1,
      responses: [
        ok,
        failure("messaging/installation-id-not-registered"),
        failure("messaging/registration-token-not-registered"),
        failure("messaging/invalid-argument"),
        failure("messaging/internal-error"),
      ],
    };
    assert.deepEqual(unusableDevices(fids, response), ["b", "c", "d"]);
  });

  test("keeps invalid-argument devices when none was reached, since the payload may be at fault", () => {
    const fids = ["a", "b"];
    const response = {
      successCount: 0,
      responses: [
        failure("messaging/invalid-argument"),
        failure("messaging/installation-id-not-registered"),
      ],
    };
    assert.deepEqual(unusableDevices(fids, response), ["b"]);
  });
});
