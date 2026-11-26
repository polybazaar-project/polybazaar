// Co-authored-by: Claude Code (Claude Opus 5.5)
import { logger } from "firebase-functions";
import { bookingNotice, bookingPayload } from "./booking.js";
import { failureSummary, messagePayload, recipientOf } from "./payload.js";

// A client can create any number of device documents, so this bounds the reads and sends one
// message can cost. A user has a phone or two; ten leaves room for old devices not yet removed.
const MAX_DEVICES = 10;

// Sends `data` to the user's most recently updated devices as a data-only, high-priority message:
// the app builds each notification itself, so it controls the tap target.
export async function sendToUser(db, messaging, uid, data) {
  const devices = await db
    .collection(`users/${uid}/devices`)
    .orderBy("updatedAt", "desc")
    .limit(MAX_DEVICES)
    .get();
  if (devices.empty) return;

  const fids = devices.docs.map((doc) => doc.id);
  const response = await messaging.sendEachForMulticast({
    fids,
    data,
    android: { priority: "high" },
  });

  const { deleted, failed, codes } = failureSummary(fids, response);
  if (failed > 0) {
    logger.warn("Some notifications failed", { type: data.type, failed, codes });
  }
  if (deleted.length === 0) return;

  const unusable = new Set(deleted);
  const batch = db.batch();
  for (const doc of devices.docs) {
    if (unusable.has(doc.id)) batch.delete(doc.ref);
  }
  // A device left behind fails again on the next send and is deleted then.
  try {
    await batch.commit();
    logger.info("Deleted unusable devices", { type: data.type, count: deleted.length });
  } catch (error) {
    logger.error("Could not delete unusable devices", {
      count: deleted.length,
      // The code alone: a message can quote a document path, which holds the fid.
      code: error.code,
    });
  }
}

// A photo message is created before its photo is uploaded, so it is announced by
// notifyUploadedPhoto once the photo can be downloaded. A photo removed after a failed upload is
// never announced.
export async function notifyNewMessage(db, messaging, { conversationId }, message) {
  if (message.photoPending === true) return;
  const [conversationSnapshot, sender] = await Promise.all([
    db.doc(`conversations/${conversationId}`).get(),
    db.doc(`users/${message.senderUid}`).get(),
  ]);
  if (!conversationSnapshot.exists) {
    logger.warn("Message in a missing conversation", { conversationId });
    return;
  }
  const conversation = conversationSnapshot.data();

  const recipientUid = recipientOf(conversation, message.senderUid);
  if (recipientUid == null) {
    logger.warn("Message sender is not a participant", { conversationId });
    return;
  }

  const data = messagePayload({
    conversation,
    message,
    senderName: sender.get("username"),
    recipientUid,
  });
  await sendToUser(db, messaging, recipientUid, data);
}

// The rules let a message's sender change it once: photoPending from true to false, when the
// photo's upload ends.
export async function notifyUploadedPhoto(db, messaging, params, { before, after }) {
  if (before.photoPending !== true || after.photoPending !== false) return;
  await notifyNewMessage(db, messaging, params, after);
}

// `writer` is the trigger's auth context; a new booking has none. The listing and the renter are
// read only once a notification is due, so a handover or return step costs no reads.
export async function notifyBookingChange(
  db,
  messaging,
  { bookingId },
  { before, after },
  writer = {},
) {
  const notice = bookingNotice(before, after, writer.authId);
  if (notice == null) return;
  if (notice.recipientUid == null) {
    // authId is the writer's uid by a community report, not by Firebase's docs: if it is not,
    // every cancel ends here.
    logger.warn("Booking cancelled by neither party", { bookingId, authType: writer.authType });
    return;
  }

  const [listing, renter] = await Promise.all([
    db.doc(`listings/${after.listingId}`).get(),
    notice.type === "booking_requested" ? db.doc(`users/${after.renterUid}`).get() : null,
  ]);
  // A deleted listing leaves its bookings, and anyone can create a listing under its id, so the
  // title counts only while the booking's lender owns the listing.
  const listingTitle =
    listing.get("ownerUid") === after.lenderUid ? listing.get("title") : undefined;
  const data = bookingPayload({
    bookingId,
    booking: after,
    notice,
    listingTitle,
    renterName: renter?.get("username"),
  });
  await sendToUser(db, messaging, notice.recipientUid, data);
}
