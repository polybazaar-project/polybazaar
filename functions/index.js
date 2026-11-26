// Co-authored-by: Claude Code (Claude Opus 5.5)
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { setGlobalOptions } from "firebase-functions/v2";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { notifyNewMessage, notifyUploadedPhoto } from "./src/notify.js";

initializeApp();

// The Firestore database is in eur3, and its triggers must run in a region inside it.
// maxInstances caps the cost if a bug or a flood of writes fires the triggers in a loop.
setGlobalOptions({ region: "europe-west1", maxInstances: 10 });

// A message sent offline is created when it syncs, so its notification goes out then.
export const onMessageCreated = onDocumentCreated(
  "conversations/{conversationId}/messages/{messageId}",
  (event) => {
    if (event.data == null) return;
    return notifyNewMessage(getFirestore(), getMessaging(), event.params, event.data.data());
  },
);

export const onMessagePhotoUploaded = onDocumentUpdated(
  "conversations/{conversationId}/messages/{messageId}",
  (event) => {
    if (event.data == null) return;
    return notifyUploadedPhoto(getFirestore(), getMessaging(), event.params, {
      before: event.data.before.data(),
      after: event.data.after.data(),
    });
  },
);
