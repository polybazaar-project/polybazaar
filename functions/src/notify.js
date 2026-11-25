// Co-authored-by: Claude Code (Claude Opus 5.5)
import { logger } from "firebase-functions";
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

export async function notifyNewMessage(db, messaging, { conversationId }, message) {
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
