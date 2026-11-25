// Co-authored-by: Claude Code (Claude Opus 5.5, Claude Fable 5.2)
// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Copilot (Auto)
// Co-authored-by: Copilot Claude Sonnet 5.5
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  arrayRemove,
  collection,
  deleteDoc,
  deleteField,
  doc,
  FieldPath,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from "firebase/firestore";

const ALICE = "alice-uid";
const BOB = "bob-uid";
const EVE = "eve-uid";

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-polybazaar",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") },
  });
});

after(() => env.cleanup());

beforeEach(() => env.clearFirestore());

const signedIn = (uid, email, emailVerified) =>
  env.authenticatedContext(uid, { email, email_verified: emailVerified }).firestore();
const verified = (uid) => signedIn(uid, `${uid}@epfl.ch`, true);
const unverified = (uid) => signedIn(uid, `${uid}@epfl.ch`, false);
/** A verified account whose address is not at EPFL, made through the Firebase Auth API. */
const outsider = (uid) => signedIn(uid, `${uid}@gmail.com`, true);
const signedOut = () => env.unauthenticatedContext().firestore();

/** Writes documents with the rules turned off, to set up a test. */
async function seed(path, data) {
  await env.withSecurityRulesDisabled((context) => setDoc(doc(context.firestore(), path), data));
}

/** Seeds a user as sign-up leaves it: a username entry and a profile document. */
async function seedUser(uid, username) {
  await seed(`usernames/${username}`, { uid });
  await seed(`users/${uid}`, { username });
}

/** Commits what sign-up writes: the username entry and the profile, in one batch. */
function signUp(db, uid, username, usernameData = { uid }) {
  const batch = writeBatch(db);
  batch.set(doc(db, `usernames/${username}`), usernameData);
  batch.set(doc(db, `users/${uid}`), { username }, { merge: true });
  return batch.commit();
}

function itemListing(overrides = {}) {
  return {
    ownerUid: ALICE,
    category: "item_tools",
    price: 12.5,
    type: "ITEM",
    title: "Drill",
    description: "Cordless drill",
    imagePaths: [],
    paused: false,
    refundValue: 100,
    ...overrides,
  };
}

function serviceListing(overrides = {}) {
  const { refundValue, ...service } = itemListing({
    type: "SERVICE",
    category: "service_tutoring",
  });
  return { ...service, ...overrides };
}

describe("usernames", () => {
  test("anyone can check whether a username is taken", async () => {
    await seedUser(ALICE, "alice");
    await assertSucceeds(getDoc(doc(signedOut(), "usernames/alice")));
  });

  test("a user can look up their own username by uid", async () => {
    await seedUser(ALICE, "alice");
    const db = unverified(ALICE);
    await assertSucceeds(
      getDocs(query(collection(db, "usernames"), where("uid", "==", ALICE), limit(1))),
    );
  });

  test("a user cannot look up another user's username by uid", async () => {
    await seedUser(BOB, "bob");
    const db = verified(ALICE);
    await assertFails(getDocs(query(collection(db, "usernames"), where("uid", "==", BOB))));
  });

  test("a user can claim a username for themselves at sign-up", async () => {
    await assertSucceeds(signUp(unverified(ALICE), ALICE, "alice"));
  });

  test("a username cannot be claimed without the profile that names it", async () => {
    await assertFails(setDoc(doc(unverified(ALICE), "usernames/alice"), { uid: ALICE }));
  });

  test("a username cannot be claimed with a profile that names another one", async () => {
    const db = unverified(ALICE);
    const batch = writeBatch(db);
    batch.set(doc(db, "usernames/alice"), { uid: ALICE });
    batch.set(doc(db, "usernames/alice2"), { uid: ALICE });
    batch.set(doc(db, `users/${ALICE}`), { username: "alice" });
    await assertFails(batch.commit());
  });

  test("an account cannot claim a second username", async () => {
    const db = unverified(ALICE);
    await assertSucceeds(signUp(db, ALICE, "alice"));
    await assertFails(setDoc(doc(db, "usernames/alice2"), { uid: ALICE }));
    await assertFails(signUp(db, ALICE, "alice2"));
  });

  test("a user cannot claim a username for someone else", async () => {
    await assertFails(signUp(verified(ALICE), ALICE, "alice", { uid: BOB }));
  });

  test("a signed-out user cannot claim a username", async () => {
    await assertFails(setDoc(doc(signedOut(), "usernames/alice"), { uid: ALICE }));
  });

  test("only an EPFL address can claim a username", async () => {
    await assertFails(signUp(outsider(ALICE), ALICE, "alice"));
    await assertFails(signUp(signedIn(ALICE, "alice@notepfl.ch", true), ALICE, "alice"));
    await assertFails(signUp(signedIn(ALICE, "alice@epfl.ch.evil.com", true), ALICE, "alice"));
    await assertSucceeds(signUp(signedIn(ALICE, "Alice@EPFL.CH", false), ALICE, "alice"));
  });

  test("a username entry holds only the uid", async () => {
    await assertFails(signUp(verified(ALICE), ALICE, "alice", { uid: ALICE, email: "a@epfl.ch" }));
  });

  test("a username must be lowercase and trimmed", async () => {
    await assertFails(signUp(verified(ALICE), ALICE, "Alice"));
    await assertFails(signUp(verified(ALICE), ALICE, " alice"));
  });

  test("a username cannot be changed or released", async () => {
    await seedUser(ALICE, "alice");
    const db = verified(ALICE);
    await assertFails(setDoc(doc(db, "usernames/alice"), { uid: ALICE }));
    await assertFails(deleteDoc(doc(db, "usernames/alice")));
  });
});

describe("users", () => {
  test("sign-up writes the username and profile in one batch before verification", async () => {
    await assertSucceeds(signUp(unverified(ALICE), ALICE, "alice"));
  });

  test("a non-EPFL account cannot sign up", async () => {
    await assertFails(signUp(outsider(ALICE), ALICE, "alice"));
  });

  test("a profile needs a username owned by the same user", async () => {
    await seedUser(BOB, "bob");
    await assertFails(setDoc(doc(verified(ALICE), `users/${ALICE}`), { username: "bob" }));
    await assertFails(setDoc(doc(verified(ALICE), `users/${ALICE}`), { username: "nobody" }));
  });

  test("a user cannot create another user's profile", async () => {
    await seed("usernames/bob", { uid: BOB });
    await assertFails(setDoc(doc(verified(ALICE), `users/${BOB}`), { username: "bob" }));
  });

  test("sign-in can rewrite the username of an existing profile before verifying", async () => {
    await seedUser(ALICE, "alice");
    await assertSucceeds(
      setDoc(doc(unverified(ALICE), `users/${ALICE}`), { username: "alice" }, { merge: true }),
    );
  });

  test("an unverified user cannot edit their profile", async () => {
    await seedUser(ALICE, "alice");
    const profile = doc(unverified(ALICE), `users/${ALICE}`);
    await assertFails(updateDoc(profile, { bio: "Hi" }));
    await assertFails(updateDoc(profile, { photoPath: `users/${ALICE}/profile.jpg` }));
  });

  test("a non-EPFL account cannot edit a profile", async () => {
    await seedUser(ALICE, "alice");
    await assertFails(updateDoc(doc(outsider(ALICE), `users/${ALICE}`), { bio: "Hi" }));
  });

  test("a user can edit their bio and photo", async () => {
    await seedUser(ALICE, "alice");
    await assertSucceeds(
      setDoc(
        doc(verified(ALICE), `users/${ALICE}`),
        { bio: "Hi", photoPath: `users/${ALICE}/profile.jpg` },
        { merge: true },
      ),
    );
  });

  test("a profile photo is the user's own Storage path, not a URL", async () => {
    await seedUser(ALICE, "alice");
    const profile = doc(verified(ALICE), `users/${ALICE}`);
    await assertFails(updateDoc(profile, { photoPath: `users/${BOB}/profile.jpg` }));
    await assertFails(
      updateDoc(profile, { photoPath: "https://firebasestorage.googleapis.com/v0/b/x/o/p?token=t" }),
    );
    // The old field held a download URL, which works for anyone who has it.
    await assertFails(updateDoc(profile, { photoUrl: "https://example.com/p.jpg" }));
  });

  test("a bio is text", async () => {
    await seedUser(ALICE, "alice");
    const profile = doc(verified(ALICE), `users/${ALICE}`);
    await assertSucceeds(updateDoc(profile, { bio: "Weekend maker" }));
    await assertFails(updateDoc(profile, { bio: 42 }));
  });

  test("a bio can have at most 500 characters", async () => {
    await seedUser(ALICE, "alice");
    const profile = doc(verified(ALICE), `users/${ALICE}`);
    await assertSucceeds(updateDoc(profile, { bio: "a".repeat(500) }));
    await assertFails(updateDoc(profile, { bio: "a".repeat(501) }));
  });

  test("a username has 3 to 30 characters", async () => {
    await assertSucceeds(signUp(unverified(ALICE), ALICE, "abc"));
    await assertSucceeds(signUp(unverified(BOB), BOB, "b".repeat(30)));
    await assertFails(signUp(unverified("carol-uid"), "carol-uid", "ab"));
    await assertFails(signUp(unverified("dave-uid"), "dave-uid", "d".repeat(31)));
  });

  test("a user cannot change their username", async () => {
    await seedUser(ALICE, "alice");
    await seed("usernames/alicia", { uid: ALICE });
    await assertFails(updateDoc(doc(verified(ALICE), `users/${ALICE}`), { username: "alicia" }));
  });

  test("a profile cannot hold unknown fields", async () => {
    await seedUser(ALICE, "alice");
    await assertFails(updateDoc(doc(verified(ALICE), `users/${ALICE}`), { isAdmin: true }));
  });

  test("a user cannot edit another user's profile", async () => {
    await seedUser(BOB, "bob");
    await assertFails(updateDoc(doc(verified(ALICE), `users/${BOB}`), { bio: "hacked" }));
  });

  test("a profile cannot be deleted", async () => {
    await seedUser(ALICE, "alice");
    await assertFails(deleteDoc(doc(verified(ALICE), `users/${ALICE}`)));
  });

  test("a verified user can read any profile", async () => {
    await seedUser(BOB, "bob");
    await assertSucceeds(getDoc(doc(verified(ALICE), `users/${BOB}`)));
  });

  test("an unverified user can read only their own profile", async () => {
    await seedUser(ALICE, "alice");
    await seedUser(BOB, "bob");
    await assertSucceeds(getDoc(doc(unverified(ALICE), `users/${ALICE}`)));
    await assertFails(getDoc(doc(unverified(ALICE), `users/${BOB}`)));
  });

  test("a signed-out user or non-EPFL account cannot read others' profiles", async () => {
    await seedUser(ALICE, "alice");
    await assertFails(getDoc(doc(signedOut(), `users/${ALICE}`)));
    await assertFails(getDoc(doc(outsider(BOB), `users/${ALICE}`)));
  });
});

describe("devices", () => {
  const device = (db, uid = ALICE) => doc(db, `users/${uid}/devices/fid-1`);
  const stamp = () => ({ updatedAt: serverTimestamp() });

  test("a verified user can store and refresh their device", async () => {
    await assertSucceeds(setDoc(device(verified(ALICE)), stamp()));
    await assertSucceeds(setDoc(device(verified(ALICE)), stamp()));
  });

  test("an unverified or non-EPFL account cannot store a device", async () => {
    await assertFails(setDoc(device(unverified(ALICE)), stamp()));
    await assertFails(setDoc(device(outsider(ALICE)), stamp()));
  });

  test("a user cannot store a device for someone else", async () => {
    await assertFails(setDoc(device(verified(BOB)), stamp()));
  });

  test("a device holds only the server time it was stored at", async () => {
    await assertFails(setDoc(device(verified(ALICE)), { ...stamp(), platform: "android" }));
    await assertFails(setDoc(device(verified(ALICE)), { updatedAt: new Date() }));
    await assertFails(setDoc(device(verified(ALICE)), {}));
  });

  test("a user can remove their own device, verified or not", async () => {
    await seed(`users/${ALICE}/devices/fid-1`, { updatedAt: new Date() });
    await assertSucceeds(deleteDoc(device(verified(ALICE))));
    await seed(`users/${ALICE}/devices/fid-1`, { updatedAt: new Date() });
    await assertSucceeds(deleteDoc(device(unverified(ALICE))));
  });

  test("a user cannot remove someone else's device", async () => {
    await seed(`users/${ALICE}/devices/fid-1`, { updatedAt: new Date() });
    await assertFails(deleteDoc(device(verified(BOB))));
  });

  test("nobody can read the devices, their owner included", async () => {
    await seed(`users/${ALICE}/devices/fid-1`, { updatedAt: new Date() });
    await assertFails(getDoc(device(verified(ALICE))));
    await assertFails(getDocs(collection(verified(ALICE), `users/${ALICE}/devices`)));
    await assertFails(getDoc(device(verified(BOB))));
  });
});

describe("listings", () => {
  test("title, description, price and refund value stay within the app's limits", async () => {
    const create = (id, data) => setDoc(doc(verified(ALICE), `listings/${id}`), data);
    await assertSucceeds(
      create(
        "max",
        itemListing({
          title: "t".repeat(80),
          description: "d".repeat(1000),
          price: 10000,
          refundValue: 100000,
        }),
      ),
    );
    await assertFails(create("title", itemListing({ title: "t".repeat(81) })));
    await assertFails(create("desc", itemListing({ description: "d".repeat(1001) })));
    await assertFails(create("price", itemListing({ price: 10000.01 })));
    await assertFails(create("refund", itemListing({ refundValue: 100000.01 })));
    await assertFails(create("service", serviceListing({ price: 10001 })));
  });

  test("an update cannot exceed the limits either", async () => {
    await seed("listings/l1", itemListing());
    const listing = doc(verified(ALICE), "listings/l1");
    await assertFails(setDoc(listing, itemListing({ title: "t".repeat(81) })));
    await assertSucceeds(setDoc(listing, itemListing({ title: "t".repeat(80) })));
  });

  test("a verified user can create an item listing", async () => {
    await assertSucceeds(setDoc(doc(verified(ALICE), "listings/l1"), itemListing()));
  });

  describe("bookings", () => {
    // First booked day, 30 days from now, so these tests never book a day in the past.
    const day0 = Math.floor(Date.now() / 86_400_000) + 30;
    const booking = (overrides = {}) => ({
      listingId: "l1",
      renterUid: BOB,
      lenderUid: ALICE,
      days: [day0, day0 + 1, day0 + 2],
      status: "REQUESTED",
      ...overrides,
    });
    // Inclusive range of epoch days, stored like a listing's availableDates.
    const days = (from, to) => Array.from({ length: to - from + 1 }, (_, i) => from + i);
    const offering = (availableDates, overrides = {}) => itemListing({ availableDates, ...overrides });

    test("a verified renter can request a booking for an existing listing", async () => {
      await seed("listings/l1", offering(days(day0, day0 + 2)));
      await assertSucceeds(setDoc(doc(verified(BOB), "bookings/b1"), booking()));
      await assertFails(setDoc(doc(unverified(BOB), "bookings/b2"), booking()));
      await assertFails(setDoc(doc(outsider(BOB), "bookings/b3"), booking()));
      await assertFails(setDoc(doc(signedOut(), "bookings/b4"), booking()));
    });

    test("the renter and lender must match the authenticated user and listing owner", async () => {
      await seed("listings/l1", offering(days(day0, day0 + 2)));
      const db = verified(BOB);
      await assertFails(setDoc(doc(db, "bookings/b1"), booking({ renterUid: ALICE })));
      await assertFails(setDoc(doc(db, "bookings/b2"), booking({ lenderUid: BOB })));
      await assertFails(setDoc(doc(db, "bookings/b3"), booking({ listingId: "missing" })));
    });

    test("separate days can be booked together", async () => {
      await seed("listings/l1", offering([day0, day0 + 3, ...days(day0 + 7, day0 + 9)]));
      await assertSucceeds(
        setDoc(doc(verified(BOB), "bookings/b1"), booking({ days: [day0, day0 + 3, day0 + 7, day0 + 8, day0 + 9] })),
      );
    });

    test("every booked day counts towards the listing's maximum rental", async () => {
      await seed("listings/l1", offering(days(day0, day0 + 9), { type: "ITEM", maxRentalDays: 3 }));
      const db = verified(BOB);
      await assertSucceeds(setDoc(doc(db, "bookings/b1"), booking({ days: [day0, day0 + 4, day0 + 9] })));
      // Four separate days, none of them next to another, are still four days.
      await assertFails(setDoc(doc(db, "bookings/b2"), booking({ days: [day0, day0 + 3, day0 + 6, day0 + 9] })));
    });

    test("a listing without maxRentalDays has no limit", async () => {
      await seed("listings/l1", offering(days(day0, day0 + 9)));
      await assertSucceeds(setDoc(doc(verified(BOB), "bookings/b1"), booking({ days: days(day0, day0 + 9) })));
    });

    test("a paused listing cannot be booked", async () => {
      await seed("listings/l1", offering(days(day0, day0 + 2), { paused: true }));
      await assertFails(setDoc(doc(verified(BOB), "bookings/b1"), booking()));
    });

    test("an owner cannot book their own listing", async () => {
      await seed("listings/l1", offering(days(day0, day0 + 2)));
      await assertFails(
        setDoc(doc(verified(ALICE), "bookings/b1"), booking({ renterUid: ALICE, lenderUid: ALICE })),
      );
    });

    test("bookings have valid days, requested status and no extra fields", async () => {
      const today = Math.floor(Date.now() / 86_400_000);
      // Every day used below is offered, so each refusal comes from the checks on the booking.
      await seed("listings/l1", offering([today - 1, today, ...days(day0, day0 + 2)]));
      const db = verified(BOB);
      await assertSucceeds(setDoc(doc(db, "bookings/one-day"), booking({ days: [day0] })));
      await assertSucceeds(setDoc(doc(db, "bookings/today"), booking({ days: [today, day0] })));
      await assertFails(setDoc(doc(db, "bookings/empty"), booking({ days: [] })));
      await assertFails(setDoc(doc(db, "bookings/not-a-list"), booking({ days: day0 })));
      await assertFails(setDoc(doc(db, "bookings/twice"), booking({ days: [day0, day0] })));
      await assertFails(setDoc(doc(db, "bookings/string"), booking({ days: [String(day0)] })));
      await assertFails(setDoc(doc(db, "bookings/past"), booking({ days: [today - 1, today] })));
      const { days: _, ...withoutDays } = booking();
      await assertFails(setDoc(doc(db, "bookings/missing"), withoutDays));
      // The first and last day of earlier versions are no longer accepted.
      await assertFails(setDoc(doc(db, "bookings/old-shape"), { ...withoutDays, startDate: day0, endDate: day0 }));
      await assertFails(setDoc(doc(db, "bookings/status"), booking({ status: "CONFIRMED" })));
      await assertFails(setDoc(doc(db, "bookings/extra"), booking({ extra: true })));
    });

    test("a booking with any day the listing does not offer is refused", async () => {
      await seed("listings/l1", offering([day0, day0 + 2, day0 + 4]));
      const db = verified(BOB);
      await assertSucceeds(setDoc(doc(db, "bookings/b1"), booking({ days: [day0, day0 + 2, day0 + 4] })));
      // A day in the middle, not only the first or last, is checked.
      await assertFails(setDoc(doc(db, "bookings/b2"), booking({ days: [day0, day0 + 1, day0 + 2] })));
      await assertFails(setDoc(doc(db, "bookings/b3"), booking({ days: [day0 + 1] })));
    });

    test("a listing without available days cannot be booked", async () => {
      await seed("listings/l1", itemListing());
      await assertFails(setDoc(doc(verified(BOB), "bookings/b1"), booking()));
    });

    test("a listing with an empty list of available days cannot be booked", async () => {
      await seed("listings/l1", offering([]));
      await assertFails(setDoc(doc(verified(BOB), "bookings/b1"), booking()));
    });

    test("only the first day is checked against today, so a later past day the listing offers is allowed", async () => {
      // Known limit (#346): rules cannot loop over the days. The app sends them earliest first and
      // never offers a past day.
      const today = Math.floor(Date.now() / 86_400_000);
      await seed("listings/l1", offering([today - 1, day0]));
      await assertSucceeds(setDoc(doc(verified(BOB), "bookings/b1"), booking({ days: [day0, today - 1] })));
    });

    test("verified lenders and renters can read their own bookings", async () => {
      await seed("bookings/b1", booking());
      const bookingsForLender = (db, lender) =>
        getDocs(query(collection(db, "bookings"), where("listingId", "==", "l1"), where("lenderUid", "==", lender)));
      const bookingsForRenter = (db, renter) =>
        getDocs(query(collection(db, "bookings"), where("listingId", "==", "l1"), where("renterUid", "==", renter)));

      await assertSucceeds(bookingsForLender(verified(ALICE), ALICE));
      await assertSucceeds(bookingsForRenter(verified(BOB), BOB));
      await assertFails(bookingsForLender(unverified(ALICE), ALICE));
      await assertSucceeds(getDoc(doc(verified(BOB), "bookings/b1")));
      await assertFails(getDoc(doc(verified("eve"), "bookings/b1")));
      await assertFails(getDocs(query(collection(verified(ALICE), "bookings"), where("listingId", "==", "l1"))));
    });

    // The Toolbox reads every booking of the lender's listings in one query (#196).
    test("a verified lender can list the bookings of all their listings, and only theirs", async () => {
      await seed("bookings/b1", booking());
      await seed("bookings/b2", booking({ listingId: "l2" }));
      const allForLender = (db, lender) =>
        getDocs(query(collection(db, "bookings"), where("lenderUid", "==", lender)));

      const listed = await assertSucceeds(allForLender(verified(ALICE), ALICE));
      assert.deepEqual(listed.docs.map((d) => d.id).sort(), ["b1", "b2"]);
      await assertFails(allForLender(verified(BOB), ALICE));
      await assertFails(allForLender(unverified(ALICE), ALICE));
    });

    test("renters and lenders can cancel requested or confirmed bookings", async () => {
      await seed("bookings/renter-requested", booking());
      await seed("bookings/renter-confirmed", booking({ status: "CONFIRMED" }));
      await seed("bookings/lender-requested", booking());
      await seed("bookings/lender-confirmed", booking({ status: "CONFIRMED" }));

      await assertSucceeds(updateDoc(doc(verified(BOB), "bookings/renter-requested"), { status: "CANCELLED" }));
      await assertSucceeds(updateDoc(doc(verified(BOB), "bookings/renter-confirmed"), { status: "CANCELLED" }));
      await assertSucceeds(updateDoc(doc(verified(ALICE), "bookings/lender-requested"), { status: "CANCELLED" }));
      await assertSucceeds(updateDoc(doc(verified(ALICE), "bookings/lender-confirmed"), { status: "CANCELLED" }));
    });

    test("handover and return advance in order, by the responsible party, on legacy bookings", async () => {
      await seed("bookings/b1", booking({ status: "CONFIRMED" }));
      const lender = doc(verified(ALICE), "bookings/b1");
      const renter = doc(verified(BOB), "bookings/b1");
      const outsiderBooking = doc(verified(EVE), "bookings/b1");

      await assertFails(updateDoc(renter, { renterHandoverConfirmed: true }));
      await assertFails(updateDoc(renter, { renterReturnConfirmed: true }));
      await assertFails(updateDoc(lender, { lenderReturnConfirmed: true }));
      await assertFails(updateDoc(renter, { lenderHandoverConfirmed: true }));
      await assertFails(updateDoc(outsiderBooking, { lenderHandoverConfirmed: true }));
      await assertFails(updateDoc(doc(unverified(ALICE), "bookings/b1"), { lenderHandoverConfirmed: true }));
      await assertFails(updateDoc(lender, { lenderHandoverConfirmed: true, renterHandoverConfirmed: true }));

      await assertSucceeds(updateDoc(lender, { lenderHandoverConfirmed: true }));
      await assertSucceeds(updateDoc(lender, { lenderHandoverConfirmed: true }));
      await assertFails(updateDoc(lender, { status: "CANCELLED" }));
      await assertFails(updateDoc(renter, { status: "CANCELLED" }));
      await assertFails(updateDoc(lender, { lenderHandoverConfirmed: false }));
      await assertFails(updateDoc(renter, { renterReturnConfirmed: true }));
      await assertFails(updateDoc(lender, { renterHandoverConfirmed: true }));
      await assertSucceeds(updateDoc(renter, { renterHandoverConfirmed: true }));
      await assertFails(updateDoc(lender, { lenderReturnConfirmed: true }));
      await assertSucceeds(updateDoc(renter, { renterReturnConfirmed: true }));
      await assertFails(updateDoc(renter, { lenderReturnConfirmed: true }));
      await assertSucceeds(updateDoc(lender, { lenderReturnConfirmed: true }));
      await assertFails(updateDoc(renter, { status: "CANCELLED" }));

      const saved = (await getDoc(lender)).data();
      assert.equal(saved.lenderHandoverConfirmed, true);
      assert.equal(saved.renterHandoverConfirmed, true);
      assert.equal(saved.renterReturnConfirmed, true);
      assert.equal(saved.lenderReturnConfirmed, true);
    });

    test("confirmations cannot be set on requests or cancelled bookings, or on creation", async () => {
      await seed("listings/l1", offering(days(day0, day0 + 2)));
      await seed("bookings/requested", booking());
      await seed("bookings/cancelled", booking({ status: "CANCELLED" }));
      await assertFails(updateDoc(doc(verified(ALICE), "bookings/requested"), { lenderHandoverConfirmed: true }));
      await assertFails(updateDoc(doc(verified(ALICE), "bookings/cancelled"), { lenderHandoverConfirmed: true }));
      await assertFails(setDoc(doc(verified(BOB), "bookings/new"), booking({ lenderHandoverConfirmed: true })));
    });

    test("cancellation requires a verified renter or lender", async () => {
      await seed("bookings/requested", booking());

      await assertFails(updateDoc(doc(verified(EVE), "bookings/requested"), { status: "CANCELLED" }));
      await assertFails(updateDoc(doc(unverified(BOB), "bookings/requested"), { status: "CANCELLED" }));
      await assertFails(updateDoc(doc(unverified(ALICE), "bookings/requested"), { status: "CANCELLED" }));
      await assertFails(updateDoc(doc(signedOut(), "bookings/requested"), { status: "CANCELLED" }));
    });

    test("cancellation rejects stale or invalid status transitions", async () => {
      await seed("bookings/requested", booking());
      await seed("bookings/confirmed", booking({ status: "CONFIRMED" }));
      await seed("bookings/cancelled", booking({ status: "CANCELLED" }));

      await assertFails(updateDoc(doc(verified(BOB), "bookings/cancelled"), { status: "REQUESTED" }));
      await assertFails(updateDoc(doc(verified(BOB), "bookings/requested"), { status: "CONFIRMED" }));
      await assertFails(updateDoc(doc(verified(BOB), "bookings/requested"), { status: "INVALID" }));
      await assertFails(updateDoc(doc(verified(ALICE), "bookings/confirmed"), { status: "REQUESTED" }));
      await assertFails(deleteDoc(doc(verified(BOB), "bookings/requested")));
    });

    test("cancelling a cancelled booking changes nothing and is allowed to its renter and lender", async () => {
      const today = Math.floor(Date.now() / 86_400_000);
      await seed("bookings/cancelled", booking({ status: "CANCELLED" }));
      await seed("bookings/finished", booking({ status: "CANCELLED", days: [today - 5, today - 3] }));

      await assertSucceeds(updateDoc(doc(verified(BOB), "bookings/cancelled"), { status: "CANCELLED" }));
      await assertSucceeds(updateDoc(doc(verified(ALICE), "bookings/cancelled"), { status: "CANCELLED" }));
      await assertSucceeds(updateDoc(doc(verified(ALICE), "bookings/finished"), { status: "CANCELLED" }));
      await assertFails(updateDoc(doc(verified(EVE), "bookings/cancelled"), { status: "CANCELLED" }));
      await assertFails(updateDoc(doc(unverified(BOB), "bookings/cancelled"), { status: "CANCELLED" }));
      await assertFails(updateDoc(doc(verified(BOB), "bookings/cancelled"), { status: "CANCELLED", extra: true }));
    });

    test("a finished booking cannot be cancelled", async () => {
      const today = Math.floor(Date.now() / 86_400_000);
      await seed("bookings/finished", booking({ days: [today - 5, today - 3] }));
      await seed("bookings/ends-today", booking({ days: [today - 2, today] }));

      await assertFails(updateDoc(doc(verified(BOB), "bookings/finished"), { status: "CANCELLED" }));
      await assertFails(updateDoc(doc(verified(ALICE), "bookings/finished"), { status: "CANCELLED" }));
      await assertSucceeds(updateDoc(doc(verified(BOB), "bookings/ends-today"), { status: "CANCELLED" }));
    });

    test("cancellation cannot change fields other than status", async () => {
      await seed("bookings/b1", booking());
      const ref = doc(verified(BOB), "bookings/b1");

      await assertFails(updateDoc(ref, { status: "CANCELLED", days: [day0] }));
      await assertFails(updateDoc(ref, { status: "CANCELLED", extra: true }));
    });

    /** Accepting as the app does: confirm, take the days off the listing, cancel the overlapping. */
    function accept(db, bookingId, bookedDays, cancelledIds = []) {
      const batch = writeBatch(db);
      batch.update(doc(db, `bookings/${bookingId}`), { status: "CONFIRMED" });
      cancelledIds.forEach((id) => batch.update(doc(db, `bookings/${id}`), { status: "CANCELLED" }));
      batch.update(doc(db, "listings/l1"), { availableDates: arrayRemove(...bookedDays) });
      return batch.commit();
    }

    test("the lender accepts a request and cancels the requests that share its days", async () => {
      await seed("listings/l1", offering(days(day0, day0 + 5)));
      await seed("bookings/b1", booking({ days: [day0, day0 + 1] }));
      await seed("bookings/b2", booking({ renterUid: EVE, days: [day0 + 1, day0 + 2] }));
      await seed("bookings/b3", booking({ renterUid: EVE, days: [day0 + 4] }));
      const db = verified(ALICE);

      await assertSucceeds(accept(db, "b1", [day0, day0 + 1], ["b2"]));

      const status = async (id) => (await getDoc(doc(db, `bookings/${id}`))).data().status;
      assert.equal(await status("b1"), "CONFIRMED");
      assert.equal(await status("b2"), "CANCELLED");
      assert.equal(await status("b3"), "REQUESTED");
      const listing = (await getDoc(doc(db, "listings/l1"))).data();
      assert.deepEqual(listing.availableDates, days(day0 + 2, day0 + 5));
      // The accepted days can no longer be requested; the others still can.
      await assertFails(setDoc(doc(verified(BOB), "bookings/b4"), booking({ days: [day0 + 1] })));
      await assertSucceeds(setDoc(doc(verified(BOB), "bookings/b5"), booking({ days: [day0 + 2] })));
    });

    test("the accept still succeeds when a renter cancels an overlapping request just before it", async () => {
      await seed("listings/l1", offering(days(day0, day0 + 5)));
      await seed("bookings/b1", booking({ days: [day0, day0 + 1] }));
      await seed("bookings/b2", booking({ renterUid: EVE, days: [day0 + 1, day0 + 2] }));
      const db = verified(ALICE);
      // The lender's app read b2 as requested; its renter cancels it before the accept is written.
      await assertSucceeds(updateDoc(doc(verified(EVE), "bookings/b2"), { status: "CANCELLED" }));

      await assertSucceeds(accept(db, "b1", [day0, day0 + 1], ["b2"]));

      const status = async (id) => (await getDoc(doc(db, `bookings/${id}`))).data().status;
      assert.equal(await status("b1"), "CONFIRMED");
      assert.equal(await status("b2"), "CANCELLED");
    });

    test("a request is only confirmed when the same write takes all its days off the listing", async () => {
      await seed("listings/l1", offering(days(day0, day0 + 5)));
      await seed("bookings/b1", booking({ days: [day0, day0 + 1] }));
      const db = verified(ALICE);
      await assertFails(updateDoc(doc(db, "bookings/b1"), { status: "CONFIRMED" }));
      await assertFails(accept(db, "b1", [day0]));
      await assertSucceeds(accept(db, "b1", [day0, day0 + 1]));
    });

    test("two requests sharing a day cannot both be accepted", async () => {
      await seed("listings/l1", offering(days(day0, day0 + 5)));
      await seed("bookings/b1", booking({ days: [day0, day0 + 1] }));
      await seed("bookings/b2", booking({ renterUid: EVE, days: [day0 + 1, day0 + 2] }));
      const db = verified(ALICE);
      await assertSucceeds(accept(db, "b1", [day0, day0 + 1]));
      // day0 + 1 is no longer offered, even though day0 + 2 still is.
      await assertFails(accept(db, "b2", [day0 + 1, day0 + 2]));
    });

    test("only the verified lender accepts, and only a request still waiting", async () => {
      await seed("listings/l1", offering(days(day0, day0 + 5)));
      await seed("bookings/b1", booking({ days: [day0] }));
      // Their days are still offered: only their status stops them being accepted.
      await seed("bookings/confirmed", booking({ status: "CONFIRMED", days: [day0 + 3] }));
      await seed("bookings/cancelled", booking({ status: "CANCELLED", days: [day0 + 4] }));

      await assertFails(accept(verified(BOB), "b1", [day0]));
      await assertFails(accept(verified(EVE), "b1", [day0]));
      await assertFails(accept(unverified(ALICE), "b1", [day0]));
      await assertFails(accept(verified(ALICE), "confirmed", [day0 + 3]));
      await assertFails(accept(verified(ALICE), "cancelled", [day0 + 4]));
      await assertSucceeds(accept(verified(ALICE), "b1", [day0]));
    });
  });

  test("a verified user can create a service listing", async () => {
    await assertSucceeds(setDoc(doc(verified(ALICE), "listings/l1"), serviceListing()));
  });

  test("an unverified user or non-EPFL account cannot create a listing", async () => {
    await assertFails(setDoc(doc(unverified(ALICE), "listings/l1"), itemListing()));
    await assertFails(setDoc(doc(outsider(ALICE), "listings/l2"), itemListing()));
  });

  test("a user cannot create a listing owned by someone else", async () => {
    await assertFails(
      setDoc(doc(verified(ALICE), "listings/l1"), itemListing({ ownerUid: BOB })),
    );
  });

  test("a title is non-empty text", async () => {
    const db = verified(ALICE);
    await assertFails(setDoc(doc(db, "listings/l1"), itemListing({ title: "" })));
    await assertFails(setDoc(doc(db, "listings/l2"), itemListing({ title: 42 })));
  });

  test("a title or description cannot be blank when creating", async () => {
    const db = verified(ALICE);
    await assertFails(setDoc(doc(db, "listings/l1"), itemListing({ title: "   " })));
    await assertFails(setDoc(doc(db, "listings/l2"), itemListing({ description: "" })));
    await assertFails(setDoc(doc(db, "listings/l3"), itemListing({ description: " \n\t " })));
  });

  test("a description is text", async () => {
    await assertFails(
      setDoc(doc(verified(ALICE), "listings/l1"), itemListing({ description: 42 })),
    );
  });

  test("a price is a number above 0", async () => {
    const db = verified(ALICE);
    await assertFails(setDoc(doc(db, "listings/l1"), itemListing({ price: 0 })));
    await assertFails(setDoc(doc(db, "listings/l2"), itemListing({ price: "12" })));
  });

  test("an item needs a refund value above 0", async () => {
    const db = verified(ALICE);
    const { refundValue, ...withoutRefund } = itemListing();
    await assertFails(setDoc(doc(db, "listings/l1"), withoutRefund));
    await assertFails(setDoc(doc(db, "listings/l2"), itemListing({ refundValue: 0 })));
  });

  test("a service cannot have a refund value", async () => {
    await assertFails(
      setDoc(doc(verified(ALICE), "listings/l1"), serviceListing({ refundValue: 10 })),
    );
  });

  // Same bounds as ListingRules.MAX_RENTAL_DAYS in the app. Without it (no limit), an item is
  // still valid: see "a verified user can create an item listing".
  test("an item can have a rental duration of 1 to 180 days", async () => {
    const db = verified(ALICE);
    await assertSucceeds(setDoc(doc(db, "listings/l1"), itemListing({ maxRentalDays: 1 })));
    await assertSucceeds(setDoc(doc(db, "listings/l2"), itemListing({ maxRentalDays: 180 })));
  });

  test("a rental duration is a whole number of days from 1 to 180", async () => {
    const db = verified(ALICE);
    await assertFails(setDoc(doc(db, "listings/l1"), itemListing({ maxRentalDays: 0 })));
    await assertFails(setDoc(doc(db, "listings/l2"), itemListing({ maxRentalDays: 181 })));
    await assertFails(setDoc(doc(db, "listings/l3"), itemListing({ maxRentalDays: 1.5 })));
    await assertFails(setDoc(doc(db, "listings/l4"), itemListing({ maxRentalDays: "7" })));
  });

  test("a service cannot have a rental duration", async () => {
    await assertFails(
      setDoc(doc(verified(ALICE), "listings/l1"), serviceListing({ maxRentalDays: 7 })),
    );
  });

  test("an edit keeps the rental duration checks", async () => {
    await seed("listings/l1", itemListing());
    await seed("listings/s1", serviceListing());
    const db = verified(ALICE);
    await assertSucceeds(updateDoc(doc(db, "listings/l1"), { maxRentalDays: 14 }));
    await assertFails(updateDoc(doc(db, "listings/l1"), { maxRentalDays: 181 }));
    await assertFails(updateDoc(doc(db, "listings/l1"), { maxRentalDays: 1.5 }));
    await assertFails(updateDoc(doc(db, "listings/s1"), { maxRentalDays: 7 }));
  });

  test("a listing can store up to 366 available days", async () => {
    const db = verified(ALICE);
    const year = Array.from({ length: 366 }, (_, day) => 20770 + day);
    await assertSucceeds(setDoc(doc(db, "listings/l1"), itemListing({ availableDates: [20770, 20771] })));
    await assertSucceeds(setDoc(doc(db, "listings/l2"), itemListing({ availableDates: year })));
    await assertSucceeds(setDoc(doc(db, "listings/l3"), itemListing({ availableDates: [] })));
    await assertSucceeds(setDoc(doc(db, "listings/s1"), serviceListing({ availableDates: [20770] })));
  });

  test("available days are a list of at most 366 different days", async () => {
    const db = verified(ALICE);
    const tooMany = Array.from({ length: 367 }, (_, day) => 20770 + day);
    await assertFails(setDoc(doc(db, "listings/l1"), itemListing({ availableDates: tooMany })));
    await assertFails(setDoc(doc(db, "listings/l2"), itemListing({ availableDates: [20770, 20770] })));
    await assertFails(setDoc(doc(db, "listings/l3"), itemListing({ availableDates: "tomorrow" })));
    await assertFails(setDoc(doc(db, "listings/l4"), itemListing({ availableDates: 20770 })));
  });

  test("an edit keeps the available days checks, and can remove them", async () => {
    await seed("listings/l1", itemListing({ availableDates: [20770] }));
    const listing = doc(verified(ALICE), "listings/l1");
    await assertSucceeds(updateDoc(listing, { availableDates: [20771, 20772] }));
    await assertFails(updateDoc(listing, { availableDates: [20771, 20771] }));
    await assertSucceeds(updateDoc(listing, { availableDates: deleteField() }));
  });

  const epfl = { latitude: 46.5191, longitude: 6.5668, name: "Rolex Learning Center" };

  // Every location a listing must not store, on create and on update alike.
  const invalidLocations = {
    "latitude above 90": { ...epfl, latitude: 90.0001 },
    "latitude below -90": { ...epfl, latitude: -90.0001 },
    "longitude above 180": { ...epfl, longitude: 180.0001 },
    "longitude below -180": { ...epfl, longitude: -180.0001 },
    "NaN latitude": { ...epfl, latitude: NaN },
    "NaN longitude": { ...epfl, longitude: NaN },
    "text latitude": { ...epfl, latitude: "46.5" },
    "text longitude": { ...epfl, longitude: "6.5" },
    "missing latitude": { longitude: 6.5668, name: "EPFL" },
    "missing longitude": { latitude: 46.5191, name: "EPFL" },
    "missing name": { latitude: 46.5191, longitude: 6.5668 },
    "empty name": { ...epfl, name: "" },
    "blank name": { ...epfl, name: "   " },
    "name of 81 characters": { ...epfl, name: "a".repeat(81) },
    "name that is not text": { ...epfl, name: 42 },
    "extra key": { ...epfl, address: "Route Cantonale" },
    "text instead of a map": "Rolex Learning Center",
    "list instead of a map": [46.5191, 6.5668],
  };

  /** assertFails, naming the [label] case when the write was accepted. */
  const refused = (label, write) =>
    assertFails(write).catch((error) => {
      throw new Error(`${label}: ${error.message}`);
    });

  test("a listing can have no location or a valid one, up to the bounds", async () => {
    const db = verified(ALICE);
    await assertSucceeds(setDoc(doc(db, "listings/none"), itemListing()));
    await assertSucceeds(setDoc(doc(db, "listings/epfl"), itemListing({ location: epfl })));
    await assertSucceeds(
      setDoc(doc(db, "listings/max"), serviceListing({ location: { latitude: 90, longitude: 180, name: "a".repeat(80) } })),
    );
    await assertSucceeds(
      setDoc(doc(db, "listings/min"), itemListing({ location: { latitude: -90, longitude: -180, name: "South" } })),
    );
  });

  test("a listing cannot be created with an invalid location", async () => {
    const db = verified(ALICE);
    for (const [label, location] of Object.entries(invalidLocations)) {
      await refused(label, setDoc(doc(db, "listings/l1"), itemListing({ location })));
      await refused(label, setDoc(doc(db, "listings/s1"), serviceListing({ location })));
    }
  });

  test("an edit keeps the location checks, and can add, change or remove it", async () => {
    await seed("listings/l1", itemListing());
    const listing = doc(verified(ALICE), "listings/l1");
    await assertSucceeds(updateDoc(listing, { location: epfl }));
    await assertSucceeds(updateDoc(listing, { location: { latitude: -90, longitude: 180, name: "Edge" } }));
    for (const [label, location] of Object.entries(invalidLocations)) {
      await refused(label, updateDoc(listing, { location }));
    }
    await assertSucceeds(updateDoc(listing, { location: deleteField() }));
  });

  test("a listing is an ITEM or a SERVICE", async () => {
    await assertFails(setDoc(doc(verified(ALICE), "listings/l1"), itemListing({ type: "CAR" })));
  });

  test("every supported category can be used for its listing type", async () => {
    const db = verified(ALICE);
    const validCategories = [
      ["item_tools", itemListing],
      ["item_electronics", itemListing],
      ["item_books", itemListing],
      ["item_sports", itemListing],
      ["item_kitchen", itemListing],
      ["item_other", itemListing],
      ["service_tutoring", serviceListing],
      ["service_repairs", serviceListing],
      ["service_moving_help", serviceListing],
      ["service_gardening", serviceListing],
      ["service_cleaning", serviceListing],
      ["service_other", serviceListing],
    ];

    for (const [index, [category, listing]] of validCategories.entries()) {
      await assertSucceeds(
        setDoc(doc(db, `listings/category-${index}`), listing({ category })),
      );
    }
  });

  test("a listing category must belong to its type", async () => {
    const db = verified(ALICE);
    await assertFails(
      setDoc(doc(db, "listings/l1"), itemListing({ category: "service_tutoring" })),
    );
    await assertFails(
      setDoc(doc(db, "listings/l2"), serviceListing({ category: "item_tools" })),
    );
    await assertFails(setDoc(doc(db, "listings/l3"), itemListing({ category: "unknown" })));
  });

  test("a listing has at most 5 images", async () => {
    const db = verified(ALICE);
    const paths = (listingId, n) =>
      Array.from({ length: n }, (_, i) => `listings/${ALICE}/${listingId}/images/${i}`);
    await assertSucceeds(setDoc(doc(db, "listings/l1"), itemListing({ imagePaths: paths("l1", 5) })));
    // A sixth image has nowhere to go: there is no images/5.
    await assertFails(setDoc(doc(db, "listings/l2"), itemListing({ imagePaths: paths("l2", 6) })));
    // Nor can the five allowed paths be padded with a repeat.
    const repeated = [...paths("l3", 5), `listings/${ALICE}/l3/images/0`];
    await assertFails(setDoc(doc(db, "listings/l3"), itemListing({ imagePaths: repeated })));
  });

  test("a listing names each image once", async () => {
    const image = `listings/${ALICE}/l1/images/0`;
    await assertFails(
      setDoc(doc(verified(ALICE), "listings/l1"), itemListing({ imagePaths: [image, image] })),
    );
  });

  test("a listing's images are its own Storage paths, not URLs", async () => {
    const db = verified(ALICE);
    await assertFails(
      setDoc(doc(db, "listings/l1"), itemListing({ imagePaths: [`listings/${ALICE}/l2/images/0`] })),
    );
    await assertFails(
      setDoc(doc(db, "listings/l1"), itemListing({ imagePaths: [`listings/${BOB}/l1/images/0`] })),
    );
    await assertFails(
      setDoc(doc(db, "listings/l1"), itemListing({ imagePaths: ["https://example.com/0.jpg"] })),
    );
    // The old field held download URLs, which work for anyone who has them.
    const { imagePaths, ...withoutPaths } = itemListing();
    await assertFails(
      setDoc(doc(db, "listings/l1"), { ...withoutPaths, imageUrls: ["https://example.com/0.jpg"] }),
    );
  });

  test("a listing cannot hold unknown fields", async () => {
    await assertFails(
      setDoc(doc(verified(ALICE), "listings/l1"), itemListing({ featured: true })),
    );
  });

  test("a verified user can read listings", async () => {
    await seed("listings/l1", itemListing());
    await assertSucceeds(getDoc(doc(verified(BOB), "listings/l1")));
    await assertSucceeds(getDocs(collection(verified(BOB), "listings")));
  });

  test("an unverified, non-EPFL or signed-out user cannot read listings", async () => {
    await seed("listings/l1", itemListing());
    await assertFails(getDoc(doc(unverified(BOB), "listings/l1")));
    await assertFails(getDoc(doc(outsider(BOB), "listings/l1")));
    await assertFails(getDoc(doc(signedOut(), "listings/l1")));
  });

  test("the owner can edit their listing", async () => {
    await seed("listings/l1", itemListing());
    await assertSucceeds(
      updateDoc(doc(verified(ALICE), "listings/l1"), { title: "Hammer drill", price: 15 }),
    );
  });

  test("an edit must keep the listing valid", async () => {
    await seed("listings/l1", itemListing());
    await assertFails(updateDoc(doc(verified(ALICE), "listings/l1"), { price: -1 }));
  });

  test("an edit cannot blank the title or description", async () => {
    await seed("listings/l1", itemListing());
    const ref = doc(verified(ALICE), "listings/l1");
    await assertFails(updateDoc(ref, { title: "" }));
    await assertFails(updateDoc(ref, { title: "   " }));
    await assertFails(updateDoc(ref, { description: "" }));
    await assertFails(updateDoc(ref, { description: "   " }));
  });

  test("an edit with a trimmed, non-empty description succeeds", async () => {
    await seed("listings/l1", itemListing());
    await assertSucceeds(
      updateDoc(doc(verified(ALICE), "listings/l1"), { description: "Cordless drill, 18 V" }),
    );
  });

  test("the owner can save their whole listing again with changed photos", async () => {
    // What updateListing writes: images/1 removed, a new photo in the unused images/2.
    const image = (i) => `listings/${ALICE}/l1/images/${i}`;
    await seed("listings/l1", itemListing({ imagePaths: [image(0), image(1)] }));
    await assertSucceeds(
      setDoc(
        doc(verified(ALICE), "listings/l1"),
        itemListing({ title: "Hammer drill", imagePaths: [image(0), image(2)] }),
      ),
    );
  });

  test("an edit cannot point to an image outside images/0 to images/4", async () => {
    await seed("listings/l1", itemListing());
    await assertFails(
      setDoc(
        doc(verified(ALICE), "listings/l1"),
        itemListing({ imagePaths: [`listings/${ALICE}/l1/images/5`] }),
      ),
    );
  });

  test("the owner cannot hand their listing to someone else", async () => {
    await seed("listings/l1", itemListing());
    await assertFails(updateDoc(doc(verified(ALICE), "listings/l1"), { ownerUid: BOB }));
  });

  test("the owner cannot turn an item into a service", async () => {
    await seed("listings/l1", itemListing());
    await assertFails(setDoc(doc(verified(ALICE), "listings/l1"), serviceListing()));
  });

  test("an unverified owner cannot edit or delete their listing", async () => {
    await seed("listings/l1", itemListing());
    const db = unverified(ALICE);
    await assertFails(updateDoc(doc(db, "listings/l1"), { title: "Hammer drill" }));
    await assertFails(deleteDoc(doc(db, "listings/l1")));
  });

  test("another user cannot edit a listing", async () => {
    await seed("listings/l1", itemListing());
    await assertFails(updateDoc(doc(verified(BOB), "listings/l1"), { title: "Mine now" }));
  });

  test("the owner can delete their listing", async () => {
    await seed("listings/l1", itemListing());
    await assertSucceeds(deleteDoc(doc(verified(ALICE), "listings/l1")));
  });

  test("another user cannot delete a listing", async () => {
    await seed("listings/l1", itemListing());
    await assertFails(deleteDoc(doc(verified(BOB), "listings/l1")));
  });

  // Browse queries paused == false, so a listing without the field would never show.
  test("a listing must say whether it is paused", async () => {
    const db = verified(ALICE);
    const { paused, ...withoutPaused } = itemListing();
    await assertFails(setDoc(doc(db, "listings/l1"), withoutPaused));
    await assertFails(setDoc(doc(db, "listings/l2"), itemListing({ paused: "false" })));
    await assertSucceeds(setDoc(doc(db, "listings/l3"), itemListing({ paused: true })));
  });

  test("the owner can pause and resume their listing", async () => {
    await seed("listings/l1", itemListing());
    const listing = doc(verified(ALICE), "listings/l1");
    await assertSucceeds(updateDoc(listing, { paused: true }));
    await assertSucceeds(updateDoc(listing, { paused: false }));
    await assertFails(updateDoc(listing, { paused: null }));
  });

  test("an edit cannot remove the pause state", async () => {
    await seed("listings/l1", itemListing());
    const { paused, ...withoutPaused } = itemListing({ title: "Hammer drill" });
    await assertFails(setDoc(doc(verified(ALICE), "listings/l1"), withoutPaused));
  });

  // The app edits with update() and leaves "paused" out, so a pause made meanwhile on another
  // device stays. A rental limit the owner removed is deleted with deleteField().
  test("an edit without the pause state keeps it", async () => {
    await seed("listings/l1", itemListing({ paused: true, maxRentalDays: 7 }));
    const listing = doc(verified(ALICE), "listings/l1");
    await assertSucceeds(
      updateDoc(listing, { title: "Hammer drill", maxRentalDays: deleteField() }),
    );
    const stored = (await getDoc(listing)).data();
    assert.equal(stored.paused, true);
    assert.equal("maxRentalDays" in stored, false);
  });

  test("only a verified owner can pause a listing", async () => {
    await seed("listings/l1", itemListing());
    await assertFails(updateDoc(doc(verified(BOB), "listings/l1"), { paused: true }));
    await assertFails(updateDoc(doc(unverified(ALICE), "listings/l1"), { paused: true }));
  });
});

// Bob rents Alice's listing l1.
const CONVERSATION_ID = `l1_${BOB}`;
const CONVERSATION = `conversations/${CONVERSATION_ID}`;

function newConversation(overrides = {}) {
  return {
    listingId: "l1",
    listingTitle: "Drill",
    renterUid: BOB,
    lenderUid: ALICE,
    participants: [BOB, ALICE],
    lastMessageAt: serverTimestamp(),
    lastMessageId: "m1",
    // What Bob's newMessage writes: its text, its sender, and only the sender's read time.
    lastMessageText: "Is it available on Saturday?",
    lastMessageSenderUid: BOB,
    lastReadAt: { [BOB]: serverTimestamp() },
    ...overrides,
  };
}

function newMessage(senderUid, overrides = {}) {
  return {
    senderUid,
    text: "Is it available on Saturday?",
    sentAt: serverTimestamp(),
    ...overrides,
  };
}

/**
 * Commits what sending a message writes: a merge on the conversation, which creates it or only
 * updates it and names the new message, copies its text and sender and marks it read for the
 * sender, and that message, in one batch. `message` overrides fields of newMessage, or is a
 * function of the new message's id that returns the whole message (see photoMessage).
 */
function sendMessage(db, senderUid, { id = CONVERSATION_ID, conversation = {}, message = {} } = {}) {
  const messageRef = doc(collection(db, `conversations/${id}/messages`));
  const sent =
    typeof message === "function" ? message(messageRef.id) : newMessage(senderUid, message);
  const batch = writeBatch(db);
  batch.set(
    doc(db, `conversations/${id}`),
    newConversation({
      lastMessageId: messageRef.id,
      // A photo-only message has no text, and its preview is ''.
      lastMessageText: sent.text ?? "",
      lastMessageSenderUid: senderUid,
      lastReadAt: { [senderUid]: serverTimestamp() },
      ...conversation,
    }),
    { merge: true },
  );
  batch.set(messageRef, sent);
  return batch.commit();
}

/** Where storage.rules keeps the photo of a message. */
const photoPath = (conversationId, messageId) => `conversations/${conversationId}/${messageId}.jpg`;

/**
 * A photo message for sendMessage: no text unless it has a caption, and by default the photo at
 * the message's own path in Bob's conversation, still being uploaded. `imagePath` maps the message
 * id to the stored path.
 */
function photoMessage(
  senderUid,
  { caption, imagePath = (messageId) => photoPath(CONVERSATION_ID, messageId), extra = {} } = {},
) {
  return (messageId) => ({
    senderUid,
    sentAt: serverTimestamp(),
    imagePath: imagePath(messageId),
    photoPending: true,
    ...(caption === undefined ? {} : { text: caption }),
    ...extra,
  });
}

/** Seeds Alice's listing and a conversation Bob started earlier, with its message m1. */
async function seedConversation(listing = itemListing()) {
  if (listing) await seed("listings/l1", listing);
  await seed(
    CONVERSATION,
    newConversation({
      lastMessageAt: new Date(0),
      lastMessageText: "Hi",
      lastReadAt: { [BOB]: new Date(0) },
    }),
  );
  await seed(`${CONVERSATION}/messages/m1`, { senderUid: BOB, text: "Hi", sentAt: new Date(0) });
}

/** Commits what opening a conversation writes: only the reader's own read time, at server time. */
function markRead(db, uid, { at = serverTimestamp(), extra = {} } = {}) {
  const others = Object.entries(extra).flat();
  return updateDoc(doc(db, CONVERSATION), new FieldPath("lastReadAt", uid), at, ...others);
}

describe("conversations", () => {
  test("a verified renter starts a conversation with their first message", async () => {
    await seed("listings/l1", itemListing());
    await assertSucceeds(sendMessage(verified(BOB), BOB));
  });

  test("an unverified, non-EPFL or signed-out user cannot start a conversation", async () => {
    await seed("listings/l1", itemListing());
    await assertFails(sendMessage(unverified(BOB), BOB));
    await assertFails(sendMessage(outsider(BOB), BOB));
    await assertFails(sendMessage(signedOut(), BOB));
  });

  test("only the renter can start a conversation", async () => {
    await seed("listings/l1", itemListing());
    await assertFails(sendMessage(verified(ALICE), ALICE));
    await assertFails(sendMessage(verified(EVE), EVE));
  });

  test("an owner cannot message their own listing", async () => {
    await seed("listings/l1", itemListing());
    await assertFails(
      sendMessage(verified(ALICE), ALICE, {
        id: `l1_${ALICE}`,
        conversation: { renterUid: ALICE, participants: [ALICE, ALICE] },
      }),
    );
  });

  test("the id is the listing and the renter", async () => {
    await seed("listings/l1", itemListing());
    await seed("listings/l2", itemListing());
    const db = verified(BOB);
    await assertFails(sendMessage(db, BOB, { id: `l2_${BOB}` }));
    await assertFails(sendMessage(db, BOB, { id: `l1_${EVE}` }));
    await assertFails(sendMessage(db, BOB, { id: "l1" }));
    await assertFails(sendMessage(db, BOB, { id: `${BOB}_l1` }));
  });

  // The renter is read from the id, so a listing id with '_' would make it ambiguous.
  test("a conversation id holds exactly one underscore", async () => {
    const listingId = `x_${BOB}`;
    await seed(`listings/${listingId}`, itemListing());
    await assertFails(
      sendMessage(verified(BOB), BOB, { id: `${listingId}_${BOB}`, conversation: { listingId } }),
    );
  });

  test("the lender is the listing's owner and the participants are renter and lender", async () => {
    await seed("listings/l1", itemListing());
    const db = verified(BOB);
    await assertFails(
      sendMessage(db, BOB, { conversation: { lenderUid: EVE, participants: [BOB, EVE] } }),
    );
    await assertFails(sendMessage(db, BOB, { conversation: { participants: [ALICE, BOB] } }));
    await assertFails(sendMessage(db, BOB, { conversation: { participants: [BOB, ALICE, EVE] } }));
    await assertFails(sendMessage(db, BOB, { conversation: { renterUid: EVE } }));
  });

  test("a conversation holds only its fields", async () => {
    await seed("listings/l1", itemListing());
    const db = verified(BOB);
    await assertFails(sendMessage(db, BOB, { conversation: { createdAt: serverTimestamp() } }));
    const { listingTitle, ...withoutTitle } = newConversation();
    const batch = writeBatch(db);
    batch.set(doc(db, CONVERSATION), withoutTitle);
    batch.set(doc(db, `${CONVERSATION}/messages/m1`), newMessage(BOB));
    await assertFails(batch.commit());
  });

  test("a conversation needs a listing that exists and is not paused", async () => {
    await assertFails(sendMessage(verified(BOB), BOB));
    await seed("listings/l1", itemListing({ paused: true }));
    await assertFails(sendMessage(verified(BOB), BOB));
  });

  test("the conversation shows the listing's own title", async () => {
    await seed("listings/l1", itemListing());
    await assertFails(
      sendMessage(verified(BOB), BOB, { conversation: { listingTitle: "Free drill" } }),
    );
  });

  test("both participants can reply in an existing conversation", async () => {
    await seedConversation();
    await assertSucceeds(sendMessage(verified(BOB), BOB));
    await assertSucceeds(sendMessage(verified(ALICE), ALICE));
  });

  test("an unverified participant cannot reply", async () => {
    await seedConversation();
    await assertFails(sendMessage(unverified(BOB), BOB));
    await assertFails(sendMessage(unverified(ALICE), ALICE));
  });

  test("a paused listing still accepts messages in an existing conversation", async () => {
    await seedConversation(itemListing({ paused: true }));
    await assertSucceeds(sendMessage(verified(BOB), BOB));
    await assertSucceeds(sendMessage(verified(ALICE), ALICE));
  });

  test("a deleted listing accepts no more messages", async () => {
    await seedConversation(null);
    await assertFails(sendMessage(verified(BOB), BOB));
    await assertFails(sendMessage(verified(ALICE), ALICE));
  });

  // Clients choose listing ids, so someone else can re-create a deleted listing under its id.
  test("a listing re-created by another user does not revive the conversation", async () => {
    for (const owner of [BOB, EVE]) {
      await env.clearFirestore();
      await seedConversation(null);
      await seed("listings/l1", itemListing({ ownerUid: owner, title: "Re-listed drill" }));
      const title = { listingTitle: "Re-listed drill" };
      await assertFails(sendMessage(verified(BOB), BOB));
      await assertFails(sendMessage(verified(BOB), BOB, { conversation: title }));
      await assertFails(sendMessage(verified(ALICE), ALICE));
    }
  });

  test("a conversation cannot be created without its first message", async () => {
    await seed("listings/l1", itemListing());
    await assertFails(setDoc(doc(verified(BOB), CONVERSATION), newConversation()));
  });

  test("a renter cannot create a conversation for another renter", async () => {
    await seed("listings/l1", itemListing());
    await assertFails(
      sendMessage(verified(BOB), BOB, {
        conversation: { renterUid: EVE, participants: [EVE, ALICE] },
      }),
    );
  });

  test("an owner cannot open a conversation in another renter's name", async () => {
    await seed("listings/l1", itemListing({ ownerUid: BOB }));
    await assertFails(
      sendMessage(verified(BOB), BOB, {
        conversation: { renterUid: EVE, lenderUid: BOB, participants: [EVE, BOB] },
      }),
    );
  });

  test("a conversation cannot be created with a client time", async () => {
    await seed("listings/l1", itemListing());
    await assertFails(
      sendMessage(verified(BOB), BOB, {
        conversation: { lastMessageAt: new Date(Date.now() + 86_400_000) },
      }),
    );
  });

  test("a conversation cannot be bumped without a message", async () => {
    await seedConversation();
    const ref = doc(verified(BOB), CONVERSATION);
    const at = serverTimestamp();
    await assertFails(updateDoc(ref, { lastMessageAt: at }));
    // m1 exists but was sent earlier; m9 is not written by this request.
    await assertFails(updateDoc(ref, { lastMessageAt: at, lastMessageId: "m1" }));
    await assertFails(updateDoc(ref, { lastMessageAt: at, lastMessageId: "m9" }));
  });

  test("a reply names its own message", async () => {
    await seedConversation();
    await assertFails(sendMessage(verified(BOB), BOB, { conversation: { lastMessageId: "m1" } }));
    await assertFails(sendMessage(verified(BOB), BOB, { conversation: { lastMessageId: "m9" } }));
  });

  test("a batch writes only the message the conversation names", async () => {
    await seedConversation();
    const db = verified(BOB);
    const batch = writeBatch(db);
    batch.set(doc(db, CONVERSATION), newConversation({ lastMessageId: "m2" }), { merge: true });
    batch.set(doc(db, `${CONVERSATION}/messages/m2`), newMessage(BOB));
    batch.set(doc(db, `${CONVERSATION}/messages/m3`), newMessage(BOB));
    await assertFails(batch.commit());
  });

  test("a reply can refresh the title to the listing's current one", async () => {
    await seedConversation(itemListing({ title: "Hammer drill" }));
    await assertSucceeds(
      sendMessage(verified(ALICE), ALICE, { conversation: { listingTitle: "Hammer drill" } }),
    );
  });

  test("a reply can keep the title it had", async () => {
    await seedConversation(itemListing({ title: "Hammer drill" }));
    await assertSucceeds(sendMessage(verified(BOB), BOB));
  });

  test("a reply that leaves out the title keeps the stored one", async () => {
    await seedConversation(itemListing({ title: "Hammer drill" }));
    const db = verified(BOB);
    const messageRef = doc(collection(db, `${CONVERSATION}/messages`));
    const { listingTitle, ...withoutTitle } = newConversation({ lastMessageId: messageRef.id });
    const batch = writeBatch(db);
    batch.set(doc(db, CONVERSATION), withoutTitle, { merge: true });
    batch.set(messageRef, newMessage(BOB));
    await assertSucceeds(batch.commit());
  });

  test("a reply cannot set a title the listing does not have", async () => {
    await seedConversation();
    await assertFails(
      sendMessage(verified(BOB), BOB, { conversation: { listingTitle: "Free drill" } }),
    );
  });

  test("a reply can change only lastMessageAt, lastMessageId and the title", async () => {
    await seedConversation();
    await seed("listings/l2", itemListing());
    const db = verified(BOB);
    const reply = (conversation) => sendMessage(db, BOB, { conversation });
    await assertSucceeds(reply({}));
    await assertFails(reply({ lenderUid: EVE }));
    await assertFails(reply({ renterUid: EVE }));
    await assertFails(reply({ participants: [BOB, EVE] }));
    await assertFails(reply({ listingId: "l2" }));
    await assertFails(reply({ createdAt: serverTimestamp() }));
  });

  test("a reply moves lastMessageAt to the server time", async () => {
    await seedConversation();
    await assertFails(
      sendMessage(verified(BOB), BOB, { conversation: { lastMessageAt: new Date() } }),
    );
  });

  test("a non-participant cannot update a conversation", async () => {
    await seedConversation();
    await assertFails(sendMessage(verified(EVE), EVE));
  });

  test("only verified participants can read a conversation", async () => {
    await seedConversation();
    await assertSucceeds(getDoc(doc(verified(BOB), CONVERSATION)));
    await assertSucceeds(getDoc(doc(verified(ALICE), CONVERSATION)));
    await assertFails(getDoc(doc(verified(EVE), CONVERSATION)));
    await assertFails(getDoc(doc(unverified(BOB), CONVERSATION)));
    await assertFails(getDoc(doc(outsider(BOB), CONVERSATION)));
    await assertFails(getDoc(doc(signedOut(), CONVERSATION)));
  });

  test("the renter can open their conversation before it exists", async () => {
    await seed("listings/l1", itemListing());
    await assertSucceeds(getDoc(doc(verified(BOB), CONVERSATION)));
    await assertSucceeds(getDocs(collection(verified(BOB), `${CONVERSATION}/messages`)));
    await assertFails(getDoc(doc(verified(EVE), CONVERSATION)));
    await assertFails(getDocs(collection(verified(EVE), `${CONVERSATION}/messages`)));
    await assertFails(getDoc(doc(unverified(BOB), CONVERSATION)));
  });

  test("a participant can list only their own conversations", async () => {
    await seedConversation();
    const mine = (db, uid) =>
      getDocs(
        query(
          collection(db, "conversations"),
          where("participants", "array-contains", uid),
          orderBy("lastMessageAt", "desc"),
        ),
      );
    await assertSucceeds(mine(verified(BOB), BOB));
    await assertSucceeds(mine(verified(ALICE), ALICE));
    await assertFails(mine(verified(EVE), BOB));
    await assertFails(mine(unverified(BOB), BOB));
    await assertFails(getDocs(collection(verified(BOB), "conversations")));
  });

  test("a conversation cannot be deleted", async () => {
    await seedConversation();
    await assertFails(deleteDoc(doc(verified(BOB), CONVERSATION)));
    await assertFails(deleteDoc(doc(verified(ALICE), CONVERSATION)));
  });

  test("a first message stores its text and sender and marks it read for the sender", async () => {
    await seed("listings/l1", itemListing());
    await assertSucceeds(sendMessage(verified(BOB), BOB, { message: { text: "Still free?" } }));
    const stored = (await getDoc(doc(verified(BOB), CONVERSATION))).data();
    assert.equal(stored.lastMessageText, "Still free?");
    assert.equal(stored.lastMessageSenderUid, BOB);
    assert.deepEqual(Object.keys(stored.lastReadAt), [BOB]);
  });

  test("a reply stores its text and sender and keeps the other participant's read time", async () => {
    await seedConversation();
    await assertSucceeds(sendMessage(verified(ALICE), ALICE, { message: { text: "Yes, it is" } }));
    const stored = (await getDoc(doc(verified(ALICE), CONVERSATION))).data();
    assert.equal(stored.lastMessageText, "Yes, it is");
    assert.equal(stored.lastMessageSenderUid, ALICE);
    assert.deepEqual(Object.keys(stored.lastReadAt).sort(), [ALICE, BOB].sort());
    assert.equal(stored.lastReadAt[BOB].toMillis(), 0);
  });

  test("a reply in a conversation stored before previews adds the fields", async () => {
    await seed("listings/l1", itemListing());
    const { lastMessageText, lastMessageSenderUid, lastReadAt, ...old } = newConversation({
      lastMessageAt: new Date(0),
    });
    await seed(CONVERSATION, old);
    await assertSucceeds(sendMessage(verified(ALICE), ALICE));
  });

  test("a message must store its own text as the preview", async () => {
    const wrongText = { conversation: { lastMessageText: "Something else" } };
    await seed("listings/l1", itemListing());
    await assertFails(sendMessage(verified(BOB), BOB, wrongText));
    await seedConversation();
    await assertFails(sendMessage(verified(ALICE), ALICE, wrongText));
  });

  test("a photo without text stores an empty preview", async () => {
    await seedConversation();
    await assertSucceeds(sendMessage(verified(BOB), BOB, { message: photoMessage(BOB) }));
    await assertSucceeds(sendMessage(verified(ALICE), ALICE, { message: photoMessage(ALICE) }));
    const stored = (await getDoc(doc(verified(BOB), CONVERSATION))).data();
    assert.equal(stored.lastMessageText, "");
    assert.equal(stored.lastMessageSenderUid, ALICE);
  });

  test("a photo without text cannot store a preview", async () => {
    await seedConversation();
    const db = verified(BOB);
    for (const lastMessageText of ["Photo", " ", "Is it available on Saturday?"]) {
      await assertFails(
        sendMessage(db, BOB, { message: photoMessage(BOB), conversation: { lastMessageText } }),
      );
    }
  });

  test("a photo with a caption stores the caption as the preview", async () => {
    await seedConversation();
    const db = verified(BOB);
    const message = photoMessage(BOB, { caption: "The drill bits" });
    await assertFails(sendMessage(db, BOB, { message, conversation: { lastMessageText: "" } }));
    await assertSucceeds(sendMessage(db, BOB, { message }));
    const stored = (await getDoc(doc(db, CONVERSATION))).data();
    assert.equal(stored.lastMessageText, "The drill bits");
  });

  test("a text message cannot store the empty preview of a photo", async () => {
    await seedConversation();
    await assertFails(sendMessage(verified(BOB), BOB, { conversation: { lastMessageText: "" } }));
  });

  test("a message must store its sender as the last sender", async () => {
    await seed("listings/l1", itemListing());
    await assertFails(
      sendMessage(verified(BOB), BOB, { conversation: { lastMessageSenderUid: ALICE } }),
    );
    await seedConversation();
    await assertFails(
      sendMessage(verified(ALICE), ALICE, { conversation: { lastMessageSenderUid: BOB } }),
    );
  });

  test("a message cannot set the other participant's read time", async () => {
    const both = { lastReadAt: { [BOB]: serverTimestamp(), [ALICE]: serverTimestamp() } };
    await seed("listings/l1", itemListing());
    await assertFails(sendMessage(verified(BOB), BOB, { conversation: both }));
    const otherOnly = { lastReadAt: { [ALICE]: serverTimestamp() } };
    await assertFails(sendMessage(verified(BOB), BOB, { conversation: otherOnly }));
    await seedConversation();
    await assertFails(sendMessage(verified(ALICE), ALICE, { conversation: both }));
  });

  test("a message marks it read for the sender at the server time", async () => {
    const clientTime = { conversation: { lastReadAt: { [BOB]: new Date() } } };
    await seed("listings/l1", itemListing());
    await assertFails(sendMessage(verified(BOB), BOB, clientTime));
    await seedConversation();
    await assertFails(sendMessage(verified(BOB), BOB, clientTime));
  });

  test("the renter and the lender can mark a conversation read", async () => {
    await seedConversation();
    await assertSucceeds(markRead(verified(BOB), BOB));
    await assertSucceeds(markRead(verified(ALICE), ALICE));
    const stored = (await getDoc(doc(verified(BOB), CONVERSATION))).data();
    assert.deepEqual(Object.keys(stored.lastReadAt).sort(), [ALICE, BOB].sort());
  });

  test("a conversation stored before read times can be marked read", async () => {
    await seed("listings/l1", itemListing());
    const { lastMessageText, lastMessageSenderUid, lastReadAt, ...old } = newConversation({
      lastMessageAt: new Date(0),
    });
    await seed(CONVERSATION, old);
    await assertSucceeds(markRead(verified(ALICE), ALICE));
    const stored = (await getDoc(doc(verified(ALICE), CONVERSATION))).data();
    assert.deepEqual(Object.keys(stored.lastReadAt), [ALICE]);
  });

  test("a conversation can be marked read on a paused or deleted listing", async () => {
    await seedConversation(itemListing({ paused: true }));
    await assertSucceeds(markRead(verified(ALICE), ALICE));
    await env.clearFirestore();
    await seedConversation(null);
    await assertSucceeds(markRead(verified(BOB), BOB));
  });

  test("only a verified participant can mark a conversation read", async () => {
    await seedConversation();
    await assertFails(markRead(verified(EVE), EVE));
    await assertFails(markRead(unverified(BOB), BOB));
    await assertFails(markRead(unverified(ALICE), ALICE));
  });

  test("a participant cannot mark a conversation read for the other one", async () => {
    await seedConversation();
    await assertFails(markRead(verified(BOB), ALICE));
    await assertFails(markRead(verified(ALICE), BOB));
  });

  test("a conversation is marked read at the server time", async () => {
    await seedConversation();
    await assertFails(markRead(verified(ALICE), ALICE, { at: new Date() }));
    await assertFails(markRead(verified(ALICE), ALICE, { at: new Date(0) }));
  });

  test("marking a conversation read changes nothing else", async () => {
    await seedConversation();
    const db = verified(BOB);
    await assertFails(markRead(db, BOB, { extra: { lastMessageText: "Edited" } }));
    await assertFails(markRead(db, BOB, { extra: { lastMessageSenderUid: ALICE } }));
    await assertFails(markRead(db, BOB, { extra: { lastMessageAt: serverTimestamp() } }));
    await assertFails(markRead(db, BOB, { extra: { listingTitle: "Free drill" } }));
  });
});

describe("messages", () => {
  test("a message needs the conversation update in the same batch", async () => {
    await seedConversation();
    const db = verified(BOB);
    await assertFails(
      setDoc(doc(db, `${CONVERSATION}/messages/m2`), {
        senderUid: BOB,
        text: "Hi",
        sentAt: serverTimestamp(),
      }),
    );
  });

  test("a first message needs the conversation it creates", async () => {
    await seed("listings/l1", itemListing());
    await assertFails(
      setDoc(doc(verified(BOB), `${CONVERSATION}/messages/m1`), {
        senderUid: BOB,
        text: "Hi",
        sentAt: serverTimestamp(),
      }),
    );
  });

  test("a non-participant cannot write a message", async () => {
    await seedConversation();
    await assertFails(
      setDoc(doc(verified(EVE), `${CONVERSATION}/messages/m2`), {
        senderUid: EVE,
        text: "Hi",
        sentAt: serverTimestamp(),
      }),
    );
  });

  test("a message is sent by the caller at the server time", async () => {
    await seedConversation();
    const db = verified(BOB);
    await assertFails(sendMessage(db, BOB, { message: { senderUid: ALICE } }));
    await assertFails(sendMessage(db, BOB, { message: { sentAt: new Date() } }));
  });

  test("a message holds only its fields", async () => {
    await seedConversation();
    const db = verified(BOB);
    await assertFails(sendMessage(db, BOB, { message: { imageUrl: "https://example.com/0.jpg" } }));
    const batch = writeBatch(db);
    batch.set(doc(db, CONVERSATION), newConversation({ lastMessageId: "m2" }), { merge: true });
    batch.set(doc(db, `${CONVERSATION}/messages/m2`), { senderUid: BOB, text: "Hi" });
    await assertFails(batch.commit());
  });

  test("a message can have surrounding whitespace", async () => {
    await seedConversation();
    await assertSucceeds(sendMessage(verified(BOB), BOB, { message: { text: "  Hi there \n" } }));
  });

  test("a message cannot be empty or blank", async () => {
    await seedConversation();
    const db = verified(BOB);
    await assertFails(sendMessage(db, BOB, { message: { text: "" } }));
    await assertFails(sendMessage(db, BOB, { message: { text: "   \n\t " } }));
  });

  test("a message is text of 1 to 1000 characters once trimmed", async () => {
    await seedConversation();
    const db = verified(BOB);
    const long = "x".repeat(1000);
    await assertSucceeds(sendMessage(db, BOB, { message: { text: "x" } }));
    await assertSucceeds(sendMessage(db, BOB, { message: { text: long } }));
    await assertSucceeds(sendMessage(db, BOB, { message: { text: `  ${long}\n` } }));
    await assertFails(sendMessage(db, BOB, { message: { text: `${long}x` } }));
    await assertFails(sendMessage(db, BOB, { message: { text: 42 } }));
  });

  test("a message's length counts UTF-16 units, so an emoji counts as 2", async () => {
    await seedConversation();
    const db = verified(BOB);
    await assertSucceeds(sendMessage(db, BOB, { message: { text: "\u{1F600}".repeat(500) } }));
    await assertFails(sendMessage(db, BOB, { message: { text: "\u{1F600}".repeat(501) } }));
  });

  test("a message's untrimmed text is at most 2000 characters", async () => {
    await seedConversation();
    const db = verified(BOB);
    const long = "x".repeat(1000);
    const padding = " ".repeat(600);
    await assertSucceeds(sendMessage(db, BOB, { message: { text: ` ${long} ` } }));
    await assertFails(sendMessage(db, BOB, { message: { text: `${padding}${long}${padding}` } }));
  });

  test("only verified participants can read messages", async () => {
    await seedConversation();
    const messages = (db) => getDocs(query(collection(db, `${CONVERSATION}/messages`), orderBy("sentAt")));
    await assertSucceeds(messages(verified(BOB)));
    await assertSucceeds(messages(verified(ALICE)));
    await assertSucceeds(getDoc(doc(verified(ALICE), `${CONVERSATION}/messages/m1`)));
    await assertFails(messages(verified(EVE)));
    await assertFails(getDoc(doc(verified(EVE), `${CONVERSATION}/messages/m1`)));
    await assertFails(messages(unverified(BOB)));
    await assertFails(messages(signedOut()));
  });

  test("a message cannot be edited or deleted", async () => {
    await seedConversation();
    const ref = doc(verified(BOB), `${CONVERSATION}/messages/m1`);
    await assertFails(updateDoc(ref, { text: "Edited" }));
    await assertFails(deleteDoc(ref));
  });
});

describe("photo messages", () => {
  test("the renter and the lender can send a photo without text", async () => {
    await seedConversation();
    await assertSucceeds(sendMessage(verified(BOB), BOB, { message: photoMessage(BOB) }));
    await assertSucceeds(sendMessage(verified(ALICE), ALICE, { message: photoMessage(ALICE) }));
  });

  // The app sends a text first; a photo, even with a caption, comes after.
  test("a photo cannot open a conversation", async () => {
    await seed("listings/l1", itemListing());
    const db = verified(BOB);
    await assertFails(sendMessage(db, BOB, { message: photoMessage(BOB) }));
    const captioned = photoMessage(BOB, { caption: "Is it available on Saturday?" });
    await assertFails(sendMessage(db, BOB, { message: captioned }));
    await assertSucceeds(sendMessage(db, BOB));
  });

  test("a photo can have a caption", async () => {
    await seedConversation();
    const message = photoMessage(BOB, { caption: "  The drill bits \n" });
    await assertSucceeds(sendMessage(verified(BOB), BOB, { message }));
  });

  test("a caption follows the text rules", async () => {
    await seedConversation();
    const db = verified(BOB);
    const long = "x".repeat(1000);
    await assertSucceeds(sendMessage(db, BOB, { message: photoMessage(BOB, { caption: long }) }));
    for (const caption of ["", "   \n\t ", `${long}x`, 42]) {
      await assertFails(sendMessage(db, BOB, { message: photoMessage(BOB, { caption }) }));
    }
  });

  test("a message needs a text or a photo", async () => {
    await seedConversation();
    const nothing = () => ({ senderUid: BOB, sentAt: serverTimestamp() });
    await assertFails(sendMessage(verified(BOB), BOB, { message: nothing }));
  });

  test("a photo is stored at its own conversation and message id", async () => {
    await seedConversation();
    const db = verified(BOB);
    const wrongPaths = [
      (messageId) => photoPath(`l2_${BOB}`, messageId),
      () => photoPath(CONVERSATION_ID, "m1"),
      (messageId) => `conversations/${CONVERSATION_ID}/${messageId}.png`,
      (messageId) => `conversations/${CONVERSATION_ID}/${messageId}`,
      (messageId) => `/${photoPath(CONVERSATION_ID, messageId)}`,
    ];
    for (const imagePath of wrongPaths) {
      await assertFails(sendMessage(db, BOB, { message: photoMessage(BOB, { imagePath }) }));
    }
  });

  test("a photo path is a string", async () => {
    await seedConversation();
    const db = verified(BOB);
    for (const path of [42, null, [CONVERSATION_ID]]) {
      const message = photoMessage(BOB, { imagePath: () => path });
      await assertFails(sendMessage(db, BOB, { message }));
    }
  });

  test("a photo message holds only its fields", async () => {
    await seedConversation();
    const message = photoMessage(BOB, { extra: { imageUrl: "https://example.com/0.jpg" } });
    await assertFails(sendMessage(verified(BOB), BOB, { message }));
  });

  test("a photo message cannot be edited or deleted", async () => {
    await seedConversation();
    const path = `${CONVERSATION}/messages/m2`;
    const imagePath = photoPath(CONVERSATION_ID, "m2");
    await seed(path, { senderUid: BOB, imagePath, sentAt: new Date(0) });
    const ref = doc(verified(BOB), path);
    await assertFails(updateDoc(ref, { imagePath: photoPath(CONVERSATION_ID, "m3") }));
    await assertFails(updateDoc(ref, { text: "Caption" }));
    await assertFails(deleteDoc(ref));
  });

  test("a photo message is sent with its photo pending", async () => {
    await seedConversation();
    const db = verified(BOB);
    for (const photoPending of [false, "true", null]) {
      const message = photoMessage(BOB, { extra: { photoPending } });
      await assertFails(sendMessage(db, BOB, { message }));
    }
    const withoutPending = (messageId) => {
      const { photoPending, ...message } = photoMessage(BOB)(messageId);
      return message;
    };
    await assertFails(sendMessage(db, BOB, { message: withoutPending }));
  });

  test("a text message has no pending photo", async () => {
    await seedConversation();
    for (const photoPending of [true, false]) {
      await assertFails(sendMessage(verified(BOB), BOB, { message: { photoPending } }));
    }
  });
});

describe("pending photos", () => {
  const PATH = `${CONVERSATION}/messages/m2`;

  /** Seeds Bob's photo message m2, sent earlier, with its photo still pending unless told so. */
  async function seedPhotoMessage(fields = { photoPending: true }) {
    await seedConversation();
    await seed(PATH, {
      senderUid: BOB,
      imagePath: photoPath(CONVERSATION_ID, "m2"),
      sentAt: new Date(0),
      ...fields,
    });
  }

  test("the sender can mark their photo uploaded", async () => {
    await seedPhotoMessage();
    await assertSucceeds(updateDoc(doc(verified(BOB), PATH), { photoPending: false }));
  });

  test("a photo with a caption can be marked uploaded", async () => {
    await seedPhotoMessage({ photoPending: true, text: "The drill bits" });
    await assertSucceeds(updateDoc(doc(verified(BOB), PATH), { photoPending: false }));
  });

  test("only the sender can mark their photo uploaded", async () => {
    await seedPhotoMessage();
    await assertFails(updateDoc(doc(verified(ALICE), PATH), { photoPending: false }));
    await assertFails(updateDoc(doc(verified(EVE), PATH), { photoPending: false }));
    await assertFails(updateDoc(doc(unverified(BOB), PATH), { photoPending: false }));
  });

  test("a photo is marked uploaded only once", async () => {
    await seedPhotoMessage({ photoPending: false });
    const ref = doc(verified(BOB), PATH);
    await assertFails(updateDoc(ref, { photoPending: true }));
    await assertFails(updateDoc(ref, { photoPending: false, text: "Caption" }));
  });

  test("a photo sent before photoPending existed cannot be marked", async () => {
    await seedPhotoMessage({});
    await assertFails(updateDoc(doc(verified(BOB), PATH), { photoPending: false }));
  });

  test("marking a photo uploaded changes nothing else", async () => {
    await seedPhotoMessage();
    const ref = doc(verified(BOB), PATH);
    await assertFails(updateDoc(ref, { photoPending: false, text: "Caption" }));
    await assertFails(
      updateDoc(ref, { photoPending: false, imagePath: photoPath(CONVERSATION_ID, "m3") }),
    );
    await assertFails(updateDoc(ref, { photoPending: false, sentAt: serverTimestamp() }));
    await assertFails(updateDoc(ref, { photoPending: "false" }));
    await assertFails(updateDoc(ref, { photoPending: deleteField() }));
  });

  test("the sender can delete their message while its photo is pending", async () => {
    await seedPhotoMessage();
    await assertSucceeds(deleteDoc(doc(verified(BOB), PATH)));
  });

  test("a pending photo with a caption can be deleted", async () => {
    await seedPhotoMessage({ photoPending: true, text: "The drill bits" });
    await assertSucceeds(deleteDoc(doc(verified(BOB), PATH)));
  });

  test("only the sender can delete their pending photo message", async () => {
    await seedPhotoMessage();
    await assertFails(deleteDoc(doc(verified(ALICE), PATH)));
    await assertFails(deleteDoc(doc(verified(EVE), PATH)));
    await assertFails(deleteDoc(doc(unverified(BOB), PATH)));
    await assertFails(deleteDoc(doc(signedOut(), PATH)));
  });

  test("a photo message cannot be deleted once its photo is uploaded", async () => {
    await seedPhotoMessage({ photoPending: false });
    await assertFails(deleteDoc(doc(verified(BOB), PATH)));
  });

  test("a photo sent before photoPending existed cannot be deleted", async () => {
    await seedPhotoMessage({});
    await assertFails(deleteDoc(doc(verified(BOB), PATH)));
  });

  // The app's Remove also deletes a message that was never written; the rules refuse it.
  test("a missing message cannot be deleted", async () => {
    await seedConversation();
    await assertFails(deleteDoc(doc(verified(BOB), `${CONVERSATION}/messages/m9`)));
  });
});
