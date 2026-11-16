// Co-authored-by: Claude Code (Claude Opus 5.5)
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, setDoc } from "firebase/firestore";
import { deleteObject, getBytes, listAll, ref, uploadBytes } from "firebase/storage";

const ALICE = "alice-uid";
const BOB = "bob-uid";
const EVE = "eve-uid";

const JPEG = { contentType: "image/jpeg" };
const PHOTO = new Uint8Array([0xff, 0xd8, 0xff, 0xd9]);

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-polybazaar",
    storage: { rules: readFileSync(new URL("../storage.rules", import.meta.url), "utf8") },
    // storage.rules looks up chat participants in Firestore.
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") },
  });
});

after(() => env.cleanup());

beforeEach(() => Promise.all([env.clearStorage(), env.clearFirestore()]));

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

// Bob rents Alice's listing l1.
const CONVERSATION_ID = `l1_${BOB}`;
const CHAT_FOLDER = `conversations/${CONVERSATION_ID}`;

// The Storage emulator still treats a file removed by clearStorage as existing, so a later upload
// to it is checked against `resource != null`. Each test therefore uses message ids no other test
// has used.
let lastMessageNumber = 0;
/** A message id no other test has used. */
const newMessageId = () => `m${++lastMessageNumber}`;
/** Where storage.rules keeps the photo of a message in Bob's conversation. */
const chatPhoto = (messageId) => `${CHAT_FOLDER}/${messageId}.jpg`;
/** The photo path of a new message, unused by any other test. */
const newChatPhoto = () => chatPhoto(newMessageId());

/**
 * Writes a message by `senderUid` in Bob's conversation, with the rules turned off, and returns
 * its photo's path. The message names that path unless `fields` replaces it (a text message, or a
 * photo stored elsewhere).
 */
async function seedMessage(senderUid, { messageId = newMessageId(), fields } = {}) {
  const photo = chatPhoto(messageId);
  await env.withSecurityRulesDisabled((context) =>
    setDoc(doc(context.firestore(), `${CHAT_FOLDER}/messages/${messageId}`), {
      senderUid,
      sentAt: new Date(0),
      ...(fields ?? { imagePath: photo }),
    }),
  );
  return photo;
}

/** Writes the conversation storage.rules reads, with the rules turned off, to set up a test. */
async function seedConversation() {
  await env.withSecurityRulesDisabled((context) =>
    setDoc(doc(context.firestore(), CHAT_FOLDER), {
      listingId: "l1",
      renterUid: BOB,
      lenderUid: ALICE,
      participants: [BOB, ALICE],
    }),
  );
}

describe("chat photos", () => {
  test("both participants can upload the photo of their message", async () => {
    await assertSucceeds(uploadBytes(ref(verified(BOB), await seedMessage(BOB)), PHOTO, JPEG));
    await assertSucceeds(uploadBytes(ref(verified(ALICE), await seedMessage(ALICE)), PHOTO, JPEG));
  });

  test("both participants can see a photo", async () => {
    await seedConversation();
    const photo = newChatPhoto();
    await seed(photo);
    await assertSucceeds(getBytes(ref(verified(BOB), photo)));
    await assertSucceeds(getBytes(ref(verified(ALICE), photo)));
  });

  test("only the verified sender can upload the photo of a message", async () => {
    const photo = await seedMessage(BOB);
    const others = [verified(ALICE), verified(EVE), unverified(BOB), outsider(BOB), signedOut()];
    for (const storage of others) {
      await assertFails(uploadBytes(ref(storage, photo), PHOTO, JPEG));
    }
  });

  test("only a verified participant can see a photo", async () => {
    await seedConversation();
    const photo = newChatPhoto();
    await seed(photo);
    for (const storage of [verified(EVE), unverified(BOB), outsider(BOB), signedOut()]) {
      await assertFails(getBytes(ref(storage, photo)));
    }
  });

  test("a photo cannot be uploaded before its message exists", async () => {
    await seedConversation();
    await assertFails(uploadBytes(ref(verified(BOB), newChatPhoto()), PHOTO, JPEG));
  });

  test("a photo can only be uploaded at the path its message names", async () => {
    const storage = verified(BOB);
    const textMessage = await seedMessage(BOB, { fields: { text: "Hi" } });
    await assertFails(uploadBytes(ref(storage, textMessage), PHOTO, JPEG));
    const elsewhere = await seedMessage(BOB, { fields: { imagePath: chatPhoto("m0") } });
    await assertFails(uploadBytes(ref(storage, elsewhere), PHOTO, JPEG));
  });

  test("a sent photo cannot be replaced or deleted", async () => {
    const photo = await seedMessage(BOB);
    await seed(photo);
    await assertFails(uploadBytes(ref(verified(BOB), photo), PHOTO, JPEG));
    await assertFails(deleteObject(ref(verified(BOB), photo)));
    await assertFails(deleteObject(ref(verified(ALICE), photo)));
  });

  test("a chat photo must be an image under 10 MB", async () => {
    const storage = verified(BOB);
    const text = { contentType: "text/plain" };
    await assertFails(uploadBytes(ref(storage, await seedMessage(BOB)), PHOTO, text));
    const big = new Uint8Array(8 * MB);
    await assertSucceeds(uploadBytes(ref(storage, await seedMessage(BOB)), big, JPEG));
    const tooBig = new Uint8Array(10 * MB);
    await assertFails(uploadBytes(ref(storage, await seedMessage(BOB)), tooBig, JPEG));
  });

  // Message m0 names each path, so only the file name pattern can refuse it.
  test("a chat photo is named <messageId>.jpg", async () => {
    const storage = verified(BOB);
    for (const name of ["m0.png", "m0", "m0.jpg.png", "m0.v2.jpg", ".jpg", "m0/0.jpg"]) {
      const path = `${CHAT_FOLDER}/${name}`;
      await seedMessage(BOB, { messageId: "m0", fields: { imagePath: path } });
      await assertFails(uploadBytes(ref(storage, path), PHOTO, JPEG));
    }
  });

  test("a participant cannot list a conversation's photos", async () => {
    await seedConversation();
    await seed(newChatPhoto());
    await assertFails(listAll(ref(verified(BOB), CHAT_FOLDER)));
  });
});
