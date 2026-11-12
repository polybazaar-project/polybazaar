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
  collection,
  deleteDoc,
  deleteField,
  doc,
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
      startDate: day0,
      endDate: day0 + 2,
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

    test("a booking cannot be longer than the listing's maximum rental", async () => {
      await seed("listings/l1", offering(days(day0, day0 + 3), { type: "ITEM", maxRentalDays: 3 }));
      await assertSucceeds(setDoc(doc(verified(BOB), "bookings/b1"), booking({ startDate: day0, endDate: day0 + 2 })));
      await assertFails(setDoc(doc(verified(BOB), "bookings/b2"), booking({ startDate: day0, endDate: day0 + 3 })));
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

    test("bookings have valid dates, requested status and no extra fields", async () => {
      const today = Math.floor(Date.now() / 86_400_000);
      // Every day used below is offered, so each refusal comes from the date checks.
      await seed(
        "listings/l1",
        offering([-365243219163, today - 1, today, ...days(day0 - 1, day0 + 2), 365241780472]),
      );
      const db = verified(BOB);
      await assertSucceeds(setDoc(doc(db, "bookings/same-day"), booking({ endDate: day0 })));
      await assertFails(setDoc(doc(db, "bookings/b1"), booking({ endDate: day0 - 1 })));
      await assertFails(setDoc(doc(db, "bookings/b2"), booking({ startDate: String(day0) })));
      await assertFails(setDoc(doc(db, "bookings/b3"), booking({ endDate: 365241780472 })));
      await assertFails(setDoc(doc(db, "bookings/b4"), booking({ startDate: -365243219163 })));
      await assertFails(setDoc(doc(db, "bookings/b5"), booking({ status: "CONFIRMED" })));
      await assertFails(setDoc(doc(db, "bookings/b6"), booking({ extra: true })));
      await assertFails(setDoc(doc(db, "bookings/past"), booking({ startDate: today - 1, endDate: today })));
      await assertSucceeds(setDoc(doc(db, "bookings/today"), booking({ startDate: today, endDate: today })));
    });

    test("a booking whose first and last days are offered is allowed", async () => {
      await seed("listings/l1", offering(days(day0, day0 + 2)));
      const db = verified(BOB);
      await assertSucceeds(setDoc(doc(db, "bookings/b1"), booking({ startDate: day0, endDate: day0 + 2 })));
      await assertSucceeds(setDoc(doc(db, "bookings/one-day"), booking({ startDate: day0 + 1, endDate: day0 + 1 })));
    });

    test("a booking whose first day is not offered is refused", async () => {
      await seed("listings/l1", offering(days(day0 + 1, day0 + 2)));
      await assertFails(setDoc(doc(verified(BOB), "bookings/b1"), booking({ startDate: day0, endDate: day0 + 2 })));
    });

    test("a booking whose last day is not offered is refused", async () => {
      await seed("listings/l1", offering(days(day0, day0 + 1)));
      await assertFails(setDoc(doc(verified(BOB), "bookings/b1"), booking({ startDate: day0, endDate: day0 + 2 })));
    });

    test("a listing without available days cannot be booked", async () => {
      await seed("listings/l1", itemListing());
      await assertFails(setDoc(doc(verified(BOB), "bookings/b1"), booking()));
    });

    test("a listing with an empty list of available days cannot be booked", async () => {
      await seed("listings/l1", offering([]));
      await assertFails(setDoc(doc(verified(BOB), "bookings/b1"), booking()));
    });

    test("only the first and last days are checked, so a gap in between is allowed", async () => {
      // Known limit: rules cannot loop over the range. The renter's screen only offers available days.
      await seed("listings/l1", offering([day0, day0 + 2]));
      await assertSucceeds(setDoc(doc(verified(BOB), "bookings/b1"), booking({ startDate: day0, endDate: day0 + 2 })));
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

    test("bookings cannot be updated or deleted", async () => {
      await seed("bookings/b1", booking());
      const ref = doc(verified(ALICE), "bookings/b1");
      await assertFails(updateDoc(ref, { status: "CONFIRMED" }));
      await assertFails(deleteDoc(ref));
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
 * updates it and names the new message, and that message, in one batch.
 */
function sendMessage(db, senderUid, { id = CONVERSATION_ID, conversation = {}, message = {} } = {}) {
  const messageRef = doc(collection(db, `conversations/${id}/messages`));
  const batch = writeBatch(db);
  batch.set(
    doc(db, `conversations/${id}`),
    newConversation({ lastMessageId: messageRef.id, ...conversation }),
    { merge: true },
  );
  batch.set(messageRef, newMessage(senderUid, message));
  return batch.commit();
}

/** Seeds Alice's listing and a conversation Bob started earlier, with its message m1. */
async function seedConversation(listing = itemListing()) {
  if (listing) await seed("listings/l1", listing);
  await seed(CONVERSATION, newConversation({ lastMessageAt: new Date(0) }));
  await seed(`${CONVERSATION}/messages/m1`, { senderUid: BOB, text: "Hi", sentAt: new Date(0) });
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
