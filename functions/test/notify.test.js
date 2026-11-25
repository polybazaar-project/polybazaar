// Co-authored-by: Claude Code (Claude Opus 5.5)
import assert from "node:assert/strict";
import { after, beforeEach, describe, test } from "node:test";
import { deleteApp, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { notifyNewMessage } from "../src/notify.js";

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


// Records what was sent; devices listed in `errors` fail with that error code.
function fakeMessaging(errors = {}) {
  const calls = [];
  return {
    calls,
    async sendEachForMulticast(message) {
      calls.push(message);
      const responses = message.fids.map((fid) =>
        fid in errors
          ? { success: false, error: { code: errors[fid] } }
          : { success: true, messageId: `sent-${fid}` },
      );
      const successCount = responses.filter((r) => r.success).length;
      return {
        responses,
        successCount,
        failureCount: responses.length - successCount,
      };
    },
  };
}

// The first fid is the most recently updated, a minute apart.
async function addDevices(uid, fids) {
  const batch = db.batch();
  const now = Date.now();
  fids.forEach((fid, i) => {
    batch.set(db.doc(`users/${uid}/devices/${fid}`), {
      updatedAt: new Date(now - i * 60_000),
    });
  });
  await batch.commit();
}

async function deviceIds(uid) {
  const snapshot = await db.collection(`users/${uid}/devices`).get();
  return snapshot.docs.map((doc) => doc.id).sort();
}

const params = { conversationId: CONVERSATION_ID, messageId: "m1" };

describe("notifyNewMessage", () => {
  test("sends the payload to each of the recipient's devices by fid, at high priority", async () => {
    await addDevices(LENDER, ["lender-phone", "lender-tablet"]);
    const messaging = fakeMessaging();

    await notifyNewMessage(db, messaging, params, {
      senderUid: RENTER,
      text: "Hi",
    });

    assert.equal(messaging.calls.length, 1);
    const [call] = messaging.calls;
    assert.deepEqual(call.fids.sort(), ["lender-phone", "lender-tablet"]);
    assert.equal(call.tokens, undefined);
    assert.equal(call.android.priority, "high");
    assert.equal(call.notification, undefined);
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

  test("never sends to the sender's own devices", async () => {
    await addDevices(RENTER, ["renter-phone"]);
    await addDevices(LENDER, ["lender-phone"]);
    const messaging = fakeMessaging();

    await notifyNewMessage(db, messaging, params, {
      senderUid: LENDER,
      text: "Yes",
    });

    assert.deepEqual(messaging.calls[0].fids, ["renter-phone"]);
    assert.equal(messaging.calls[0].data.senderName, "lender");
  });

  test("sends to the 10 most recently updated devices only", async () => {
    const fids = Array.from({ length: 12 }, (_, i) => `d${i}`);
    await addDevices(LENDER, fids);
    const messaging = fakeMessaging();

    await notifyNewMessage(db, messaging, params, {
      senderUid: RENTER,
      text: "Hi",
    });

    assert.equal(messaging.calls.length, 1);
    assert.deepEqual(messaging.calls[0].fids, fids.slice(0, 10));
  });

  test("a recipient with no devices gets nothing", async () => {
    const messaging = fakeMessaging();

    await notifyNewMessage(db, messaging, params, {
      senderUid: RENTER,
      text: "Hi",
    });

    assert.equal(messaging.calls.length, 0);
  });

  test("deletes unregistered and invalid devices and keeps the others", async () => {
    await addDevices(LENDER, ["gone", "garbled", "good", "flaky"]);
    const messaging = fakeMessaging({
      gone: "messaging/installation-id-not-registered",
      garbled: "messaging/invalid-argument",
      flaky: "messaging/internal-error",
    });

    await notifyNewMessage(db, messaging, params, {
      senderUid: RENTER,
      text: "Hi",
    });

    assert.deepEqual(await deviceIds(LENDER), ["flaky", "good"]);
  });

  test("invalid-argument on every device deletes nothing, since the payload may be at fault", async () => {
    await addDevices(LENDER, ["a", "b"]);
    const messaging = fakeMessaging({
      a: "messaging/invalid-argument",
      b: "messaging/invalid-argument",
    });

    await notifyNewMessage(db, messaging, params, {
      senderUid: RENTER,
      text: "Hi",
    });

    assert.deepEqual(await deviceIds(LENDER), ["a", "b"]);
  });

  test("a failed delete is logged instead of failing the function", async () => {
    await addDevices(LENDER, ["gone", "good"]);
    const messaging = fakeMessaging({
      gone: "messaging/installation-id-not-registered",
    });
    const deleted = [];
    const failingCommits = new Proxy(db, {
      get(target, property) {
        if (property === "batch") {
          return () => ({
            delete(ref) {
              deleted.push(ref.id);
            },
            commit: () => Promise.reject(new Error("Firestore unavailable")),
          });
        }
        const value = target[property];
        return typeof value === "function" ? value.bind(target) : value;
      },
    });

    await notifyNewMessage(failingCommits, messaging, params, {
      senderUid: RENTER,
      text: "Hi",
    });

    assert.deepEqual(deleted, ["gone"]);
  });

  test("a sender with no profile is sent with an empty name", async () => {
    await db.doc(`users/${RENTER}`).delete();
    await addDevices(LENDER, ["lender-phone"]);
    const messaging = fakeMessaging();

    await notifyNewMessage(db, messaging, params, {
      senderUid: RENTER,
      text: "Hi",
    });

    assert.equal(messaging.calls[0].data.senderName, "");
  });

  test("a sender who is not a participant sends nothing", async () => {
    await addDevices(RENTER, ["renter-phone"]);
    await addDevices(LENDER, ["lender-phone"]);
    const messaging = fakeMessaging();

    await notifyNewMessage(db, messaging, params, {
      senderUid: "eve-uid",
      text: "Hi",
    });

    assert.equal(messaging.calls.length, 0);
  });

  test("a message in a missing conversation sends nothing", async () => {
    await addDevices(LENDER, ["lender-phone"]);
    const messaging = fakeMessaging();

    await notifyNewMessage(
      db,
      messaging,
      { conversationId: "missing_x", messageId: "m1" },
      { senderUid: RENTER, text: "Hi" },
    );

    assert.equal(messaging.calls.length, 0);
  });
});
