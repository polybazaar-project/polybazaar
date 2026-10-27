// Co-authored-by: Claude Code (Claude Opus 5.5)
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
  doc,
  getDoc,
  getDocs,
  limit,
  query,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from "firebase/firestore";

const ALICE = "alice-uid";
const BOB = "bob-uid";

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

function itemListing(overrides = {}) {
  return {
    ownerUid: ALICE,
    category: "Tools",
    price: 12.5,
    type: "ITEM",
    title: "Drill",
    description: "Cordless drill",
    imageUrls: ["https://example.com/0.jpg"],
    refundValue: 100,
    ...overrides,
  };
}

function serviceListing(overrides = {}) {
  const { refundValue, ...service } = itemListing({ type: "SERVICE", category: "Tutoring" });
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

  test("a user can claim a username for themselves", async () => {
    await assertSucceeds(setDoc(doc(unverified(ALICE), "usernames/alice"), { uid: ALICE }));
  });

  test("a user cannot claim a username for someone else", async () => {
    await assertFails(setDoc(doc(verified(ALICE), "usernames/alice"), { uid: BOB }));
  });

  test("a signed-out user cannot claim a username", async () => {
    await assertFails(setDoc(doc(signedOut(), "usernames/alice"), { uid: ALICE }));
  });

  test("only an EPFL address can claim a username", async () => {
    await assertFails(setDoc(doc(outsider(ALICE), "usernames/alice"), { uid: ALICE }));
    await assertFails(
      setDoc(doc(signedIn(ALICE, "alice@notepfl.ch", true), "usernames/alice"), { uid: ALICE }),
    );
    await assertFails(
      setDoc(doc(signedIn(ALICE, "alice@epfl.ch.evil.com", true), "usernames/alice"), {
        uid: ALICE,
      }),
    );
    await assertSucceeds(
      setDoc(doc(signedIn(ALICE, "Alice@EPFL.CH", false), "usernames/alice"), { uid: ALICE }),
    );
  });

  test("a username entry holds only the uid", async () => {
    await assertFails(
      setDoc(doc(verified(ALICE), "usernames/alice"), { uid: ALICE, email: "a@epfl.ch" }),
    );
  });

  test("a username must be lowercase and trimmed", async () => {
    await assertFails(setDoc(doc(verified(ALICE), "usernames/Alice"), { uid: ALICE }));
    await assertFails(setDoc(doc(verified(ALICE), "usernames/ alice"), { uid: ALICE }));
  });

  test("a username is 3 to 30 characters", async () => {
    const db = verified(ALICE);
    await assertFails(setDoc(doc(db, "usernames/ab"), { uid: ALICE }));
    await assertSucceeds(setDoc(doc(db, "usernames/abc"), { uid: ALICE }));
    await assertSucceeds(setDoc(doc(db, `usernames/${"a".repeat(30)}`), { uid: ALICE }));
    await assertFails(setDoc(doc(db, `usernames/${"a".repeat(31)}`), { uid: ALICE }));
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
    const db = unverified(ALICE);
    const batch = writeBatch(db);
    batch.set(doc(db, "usernames/alice"), { uid: ALICE });
    batch.set(doc(db, `users/${ALICE}`), { username: "alice" }, { merge: true });
    await assertSucceeds(batch.commit());
  });

  test("a non-EPFL account cannot sign up", async () => {
    const db = outsider(ALICE);
    const batch = writeBatch(db);
    batch.set(doc(db, "usernames/alice"), { uid: ALICE });
    batch.set(doc(db, `users/${ALICE}`), { username: "alice" }, { merge: true });
    await assertFails(batch.commit());
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
    await assertFails(updateDoc(profile, { photoUrl: "https://example.com/p.jpg" }));
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
        { bio: "Hi", photoUrl: "https://example.com/p.jpg" },
        { merge: true },
      ),
    );
  });

  test("a bio is at most 500 characters", async () => {
    await seedUser(ALICE, "alice");
    const profile = doc(verified(ALICE), `users/${ALICE}`);
    await assertSucceeds(updateDoc(profile, { bio: "a".repeat(500) }));
    await assertFails(updateDoc(profile, { bio: "a".repeat(501) }));
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
  test("a verified user can create an item listing", async () => {
    await assertSucceeds(setDoc(doc(verified(ALICE), "listings/l1"), itemListing()));
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

  test("a title is 1 to 80 characters", async () => {
    const db = verified(ALICE);
    await assertFails(setDoc(doc(db, "listings/l1"), itemListing({ title: "" })));
    await assertSucceeds(setDoc(doc(db, "listings/l2"), itemListing({ title: "a".repeat(80) })));
    await assertFails(setDoc(doc(db, "listings/l3"), itemListing({ title: "a".repeat(81) })));
  });

  test("a description is at most 1000 characters", async () => {
    const db = verified(ALICE);
    await assertSucceeds(
      setDoc(doc(db, "listings/l1"), itemListing({ description: "a".repeat(1000) })),
    );
    await assertFails(
      setDoc(doc(db, "listings/l2"), itemListing({ description: "a".repeat(1001) })),
    );
  });

  test("a price is above 0 and at most 10,000", async () => {
    const db = verified(ALICE);
    await assertFails(setDoc(doc(db, "listings/l1"), itemListing({ price: 0 })));
    await assertSucceeds(setDoc(doc(db, "listings/l2"), itemListing({ price: 10000 })));
    await assertFails(setDoc(doc(db, "listings/l3"), itemListing({ price: 10000.01 })));
    await assertFails(setDoc(doc(db, "listings/l4"), itemListing({ price: "12" })));
  });

  test("an item needs a refund value above 0 and at most 100,000", async () => {
    const db = verified(ALICE);
    const { refundValue, ...withoutRefund } = itemListing();
    await assertFails(setDoc(doc(db, "listings/l1"), withoutRefund));
    await assertFails(setDoc(doc(db, "listings/l2"), itemListing({ refundValue: 0 })));
    await assertSucceeds(setDoc(doc(db, "listings/l3"), itemListing({ refundValue: 100000 })));
    await assertFails(setDoc(doc(db, "listings/l4"), itemListing({ refundValue: 100001 })));
  });

  test("a service cannot have a refund value", async () => {
    await assertFails(
      setDoc(doc(verified(ALICE), "listings/l1"), serviceListing({ refundValue: 10 })),
    );
  });

  test("a listing is an ITEM or a SERVICE", async () => {
    await assertFails(setDoc(doc(verified(ALICE), "listings/l1"), itemListing({ type: "CAR" })));
  });

  test("a listing has at most 5 images", async () => {
    const db = verified(ALICE);
    const urls = (n) => Array.from({ length: n }, (_, i) => `https://example.com/${i}.jpg`);
    await assertSucceeds(setDoc(doc(db, "listings/l1"), itemListing({ imageUrls: urls(5) })));
    await assertFails(setDoc(doc(db, "listings/l2"), itemListing({ imageUrls: urls(6) })));
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

  test("the owner cannot hand their listing to someone else", async () => {
    await seed("listings/l1", itemListing());
    await assertFails(updateDoc(doc(verified(ALICE), "listings/l1"), { ownerUid: BOB }));
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
});
