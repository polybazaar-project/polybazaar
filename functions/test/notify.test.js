// Co-authored-by: Claude Code (Claude Opus 5.5)
import assert from "node:assert/strict";
import { after, beforeEach, describe, test } from "node:test";
import { deleteApp, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { notifyBookingChange, notifyNewMessage, notifyUploadedPhoto } from "../src/notify.js";

const PROJECT_ID = "demo-polybazaar";
const RENTER = "renter-uid";
const LENDER = "lender-uid";
const CONVERSATION_ID = `listing-1_${RENTER}`;

const EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST;
assert.ok(
  EMULATOR_HOST,
  "FIRESTORE_EMULATOR_HOST is unset: run these tests with `npm test`, which starts the emulator",
);

const app = initializeApp({ projectId: PROJECT_ID }, "notify-test");
const db = getFirestore(app);

after(() => deleteApp(app));

beforeEach(async () => {
  const cleared = await fetch(
    `http://${EMULATOR_HOST}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`,
    { method: "DELETE" },
  );
  assert.ok(cleared.ok, `Clearing the emulator failed: ${cleared.status}`);
  await db.doc(`conversations/${CONVERSATION_ID}`).set({
    listingId: "listing-1",
    listingTitle: "Bike",
    renterUid: RENTER,
    lenderUid: LENDER,
    participants: [RENTER, LENDER],
  });
  await db.doc(`users/${RENTER}`).set({ username: "renter" });
  await db.doc(`users/${LENDER}`).set({ username: "lender" });
});

// Records each send; devices listed in `errors` fail with that error code.
function fakeMessaging(errors = {}) {
  const calls = [];
  return {
    calls,
    async sendEachForMulticast(message) {
      calls.push(message);
      const responses = message.fids.map((fid) =>
        fid in errors ? { success: false, error: { code: errors[fid] } } : { success: true },
      );
      const successCount = responses.filter((r) => r.success).length;
      return { responses, successCount, failureCount: responses.length - successCount };
    },
  };
}

// The first fid is the most recently updated, a minute apart.
async function addDevices(uid, fids) {
  const batch = db.batch();
  fids.forEach((fid, i) => {
    batch.set(db.doc(`users/${uid}/devices/${fid}`), {
      updatedAt: new Date(Date.now() - i * 60_000),
    });
  });
  await batch.commit();
}

async function deviceIds(uid) {
  const snapshot = await db.collection(`users/${uid}/devices`).get();
  return snapshot.docs.map((doc) => doc.id).sort();
}

const hi = { senderUid: RENTER, text: "Hi" };

async function send(
  message = hi,
  { errors, database = db, conversationId = CONVERSATION_ID } = {},
) {
  const messaging = fakeMessaging(errors);
  await notifyNewMessage(database, messaging, { conversationId, messageId: "m1" }, message);
  return messaging.calls;
}

describe("notifyNewMessage", () => {
  test("sends the payload to each of the recipient's devices by fid, at high priority", async () => {
    await addDevices(LENDER, ["lender-phone", "lender-tablet"]);
    await addDevices(RENTER, ["renter-phone"]);

    const [call, ...rest] = await send();

    assert.deepEqual(rest, []);
    assert.deepEqual(call.fids, ["lender-phone", "lender-tablet"]);
    assert.equal(call.tokens, undefined);
    assert.equal(call.notification, undefined);
    assert.equal(call.android.priority, "high");
    assert.deepEqual(call.data, {
      type: "message",
      recipientUid: LENDER,
      listingId: "listing-1",
      renterUid: RENTER,
      senderName: "renter",
      listingTitle: "Bike",
      preview: "Hi",
    });
  });

  test("a lender's message goes to the renter's devices", async () => {
    await addDevices(RENTER, ["renter-phone"]);
    await addDevices(LENDER, ["lender-phone"]);

    const [call] = await send({ senderUid: LENDER, text: "Yes" });

    assert.deepEqual(call.fids, ["renter-phone"]);
    assert.equal(call.data.senderName, "lender");
  });

  test("sends to the 10 most recently updated devices only", async () => {
    const fids = Array.from({ length: 12 }, (_, i) => `d${i}`);
    await addDevices(LENDER, fids);

    const calls = await send();

    assert.deepEqual(
      calls.map((call) => call.fids),
      [fids.slice(0, 10)],
    );
  });

  test("deletes unregistered and invalid devices and keeps the others", async () => {
    await addDevices(LENDER, ["gone", "garbled", "good", "flaky"]);

    await send(hi, {
      errors: {
        gone: "messaging/installation-id-not-registered",
        garbled: "messaging/invalid-argument",
        flaky: "messaging/internal-error",
      },
    });

    assert.deepEqual(await deviceIds(LENDER), ["flaky", "good"]);
  });

  test("a failed delete does not fail the function", async () => {
    await addDevices(LENDER, ["gone", "good"]);
    const deleted = [];
    const failingCommits = new Proxy(db, {
      get(target, property) {
        if (property === "batch") {
          return () => ({
            delete: (ref) => deleted.push(ref.id),
            commit: () => Promise.reject(new Error("Firestore unavailable")),
          });
        }
        const value = target[property];
        return typeof value === "function" ? value.bind(target) : value;
      },
    });

    await send(hi, {
      errors: { gone: "messaging/installation-id-not-registered" },
      database: failingCommits,
    });

    assert.deepEqual(deleted, ["gone"]);
  });

  test("a sender with no profile is sent with an empty name", async () => {
    await db.doc(`users/${RENTER}`).delete();
    await addDevices(LENDER, ["lender-phone"]);

    const [call] = await send();

    assert.equal(call.data.senderName, "");
  });

  test("sends nothing to a recipient with no devices, from a non-participant, or in a missing conversation", async () => {
    assert.deepEqual(await send(), []);

    await addDevices(RENTER, ["renter-phone"]);
    await addDevices(LENDER, ["lender-phone"]);
    assert.deepEqual(await send({ senderUid: "eve-uid", text: "Hi" }), []);
    assert.deepEqual(await send(hi, { conversationId: "missing_x" }), []);
  });

  test("a photo is announced once its upload ends, not when its message is created", async () => {
    await addDevices(LENDER, ["lender-phone"]);
    const photo = { senderUid: RENTER, text: "Look", imagePath: "p.jpg", photoPending: true };
    const uploaded = { ...photo, photoPending: false };
    const params = { conversationId: CONVERSATION_ID, messageId: "m1" };
    const messaging = fakeMessaging();

    await notifyNewMessage(db, messaging, params, photo);
    assert.deepEqual(messaging.calls, []);

    await notifyUploadedPhoto(db, messaging, params, { before: uploaded, after: uploaded });
    assert.deepEqual(messaging.calls, []);

    await notifyUploadedPhoto(db, messaging, params, { before: photo, after: uploaded });
    assert.deepEqual(
      messaging.calls.map((call) => [call.fids, call.data.preview]),
      [[["lender-phone"], "Look"]],
    );
  });
});

describe("notifyBookingChange", () => {
  const requested = {
    listingId: "listing-1",
    renterUid: RENTER,
    lenderUid: LENDER,
    days: [20500],
    status: "REQUESTED",
  };
  const cancelled = { ...requested, status: "CANCELLED" };

  // booking.test.js covers the decision and the payload; these cover what the handler reads.
  async function change(before, after, authId) {
    const messaging = fakeMessaging();
    const writer = { authType: "api_key", authId };
    await notifyBookingChange(db, messaging, { bookingId: "booking-1" }, { before, after }, writer);
    return messaging.calls;
  }

  beforeEach(async () => {
    await db.doc("listings/listing-1").set({ ownerUid: LENDER, title: "Bike" });
    await addDevices(RENTER, ["renter-phone"]);
    await addDevices(LENDER, ["lender-phone"]);
  });

  test("a request reaches the lender with the listing's title and the renter's name", async () => {
    const [call, ...rest] = await change(null, requested);

    assert.deepEqual(rest, []);
    assert.deepEqual(call.fids, ["lender-phone"]);
    assert.equal(call.data.type, "booking_requested");
    assert.equal(call.data.listingTitle, "Bike");
    assert.equal(call.data.renterName, "renter");
  });

  test("a lender's decline reaches the renter only", async () => {
    const [call, ...rest] = await change(requested, cancelled, LENDER);

    assert.deepEqual(rest, []);
    assert.deepEqual(call.fids, ["renter-phone"]);
    assert.equal(call.data.type, "booking_cancelled");
    assert.equal(call.data.listingTitle, "Bike");
  });

  test("sends nothing for a handover step or a cancel written by neither party", async () => {
    const confirmed = { ...requested, status: "CONFIRMED" };
    const handover = { ...confirmed, lenderHandoverConfirmed: true };

    assert.deepEqual(await change(confirmed, handover, LENDER), []);
    assert.deepEqual(await change(requested, cancelled, "eve-uid"), []);
  });

  test("a listing someone else re-created under the booking's listing id, or a deleted one, gives an empty title", async () => {
    await db.doc("listings/listing-1").set({ ownerUid: "eve-uid", title: "Not the lender's" });
    const [recreated] = await change(requested, cancelled, RENTER);
    assert.equal(recreated.data.listingTitle, "");

    await db.doc("listings/listing-1").delete();
    const [deleted] = await change(requested, cancelled, RENTER);
    assert.equal(deleted.data.listingTitle, "");
  });
});
