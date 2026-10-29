// Co-authored-by: Claude Code (Claude Opus 5.5)
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { deleteObject, getBytes, ref, uploadBytes } from "firebase/storage";

const ALICE = "alice-uid";
const BOB = "bob-uid";

const JPEG = { contentType: "image/jpeg" };
const PHOTO = new Uint8Array([0xff, 0xd8, 0xff, 0xd9]);

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-polybazaar",
    storage: { rules: readFileSync(new URL("../storage.rules", import.meta.url), "utf8") },
  });
});

after(() => env.cleanup());

beforeEach(() => env.clearStorage());

const signedIn = (uid, email, emailVerified) =>
  env.authenticatedContext(uid, { email, email_verified: emailVerified }).storage();
const verified = (uid) => signedIn(uid, `${uid}@epfl.ch`, true);
const unverified = (uid) => signedIn(uid, `${uid}@epfl.ch`, false);
/** A verified account whose address is not at EPFL, made through the Firebase Auth API. */
const outsider = (uid) => signedIn(uid, `${uid}@gmail.com`, true);
const signedOut = () => env.unauthenticatedContext().storage();

const MB = 1024 * 1024;

/** Uploads a file with the rules turned off, to set up a test. */
async function seed(path) {
  await env.withSecurityRulesDisabled((context) =>
    uploadBytes(ref(context.storage(), path), PHOTO, JPEG),
  );
}

const profilePhoto = (uid) => `users/${uid}/profile.jpg`;
const listingImage = (uid, index) => `listings/${uid}/l1/images/${index}`;

describe("profile photos", () => {
  test("a verified user can upload their profile photo", async () => {
    await assertSucceeds(uploadBytes(ref(verified(ALICE), profilePhoto(ALICE)), PHOTO, JPEG));
  });

  test("a user can replace their profile photo", async () => {
    await seed(profilePhoto(ALICE));
    await assertSucceeds(uploadBytes(ref(verified(ALICE), profilePhoto(ALICE)), PHOTO, JPEG));
  });

  test("an unverified user cannot upload a profile photo", async () => {
    await assertFails(uploadBytes(ref(unverified(ALICE), profilePhoto(ALICE)), PHOTO, JPEG));
  });

  test("a non-EPFL account cannot upload a profile photo", async () => {
    await assertFails(uploadBytes(ref(outsider(ALICE), profilePhoto(ALICE)), PHOTO, JPEG));
  });

  test("a user cannot upload another user's profile photo", async () => {
    await assertFails(uploadBytes(ref(verified(ALICE), profilePhoto(BOB)), PHOTO, JPEG));
  });

  test("a profile photo must be an image", async () => {
    await assertFails(
      uploadBytes(ref(verified(ALICE), profilePhoto(ALICE)), PHOTO, { contentType: "text/plain" }),
    );
  });

  test("a profile photo is under 10 MB", async () => {
    const photo = ref(verified(ALICE), profilePhoto(ALICE));
    await assertSucceeds(uploadBytes(photo, new Uint8Array(8 * MB), JPEG));
    await assertFails(uploadBytes(photo, new Uint8Array(10 * MB), JPEG));
  });

  test("a verified user can see any profile photo", async () => {
    await seed(profilePhoto(BOB));
    await assertSucceeds(getBytes(ref(verified(ALICE), profilePhoto(BOB))));
  });

  test("a signed-out user or non-EPFL account cannot see profile photos", async () => {
    await seed(profilePhoto(BOB));
    await assertFails(getBytes(ref(signedOut(), profilePhoto(BOB))));
    await assertFails(getBytes(ref(outsider(ALICE), profilePhoto(BOB))));
  });

  test("a user can delete only their own profile photo", async () => {
    await seed(profilePhoto(ALICE));
    await assertFails(deleteObject(ref(verified(BOB), profilePhoto(ALICE))));
    await assertSucceeds(deleteObject(ref(verified(ALICE), profilePhoto(ALICE))));
  });
});

describe("listing images", () => {
  test("the owner can upload images 0 to 4", async () => {
    const storage = verified(ALICE);
    for (const index of [0, 4]) {
      await assertSucceeds(uploadBytes(ref(storage, listingImage(ALICE, index)), PHOTO, JPEG));
    }
  });

  test("a listing has no image beyond index 4", async () => {
    await assertFails(uploadBytes(ref(verified(ALICE), listingImage(ALICE, 5)), PHOTO, JPEG));
  });

  test("a user cannot upload images under another user's folder", async () => {
    await assertFails(uploadBytes(ref(verified(ALICE), listingImage(BOB, 0)), PHOTO, JPEG));
  });

  test("an unverified user or non-EPFL account cannot upload listing images", async () => {
    await assertFails(uploadBytes(ref(unverified(ALICE), listingImage(ALICE, 0)), PHOTO, JPEG));
    await assertFails(uploadBytes(ref(outsider(ALICE), listingImage(ALICE, 0)), PHOTO, JPEG));
  });

  test("a listing image must be an image under 10 MB", async () => {
    const storage = verified(ALICE);
    await assertFails(
      uploadBytes(ref(storage, listingImage(ALICE, 0)), PHOTO, { contentType: "video/mp4" }),
    );
    const tooBig = new Uint8Array(10 * MB);
    await assertFails(uploadBytes(ref(storage, listingImage(ALICE, 1)), tooBig, JPEG));
  });

  test("the owner can replace an image when editing a listing", async () => {
    await seed(listingImage(ALICE, 0));
    await assertSucceeds(uploadBytes(ref(verified(ALICE), listingImage(ALICE, 0)), PHOTO, JPEG));
  });

  test("a verified user can see listing images", async () => {
    await seed(listingImage(ALICE, 0));
    await assertSucceeds(getBytes(ref(verified(BOB), listingImage(ALICE, 0))));
    await assertFails(getBytes(ref(signedOut(), listingImage(ALICE, 0))));
  });

  test("only the owner can delete listing images", async () => {
    await seed(listingImage(ALICE, 0));
    await assertFails(deleteObject(ref(verified(BOB), listingImage(ALICE, 0))));
    await assertSucceeds(deleteObject(ref(verified(ALICE), listingImage(ALICE, 0))));
  });
});

describe("other paths", () => {
  test("nothing else can be read or written", async () => {
    await assertFails(uploadBytes(ref(verified(ALICE), `other/${ALICE}/file.jpg`), PHOTO, JPEG));
    await assertFails(getBytes(ref(verified(ALICE), "listings/readme")));
  });
});
