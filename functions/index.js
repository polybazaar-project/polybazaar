// Co-authored-by: Claude Code (Claude Opus 5.5)
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { setGlobalOptions } from "firebase-functions/v2";
import {
  onDocumentCreated,
  onDocumentUpdatedWithAuthContext,
} from "firebase-functions/v2/firestore";
import { notifyBookingChange, notifyNewMessage } from "./src/notify.js";

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

export const onBookingCreated = onDocumentCreated("bookings/{bookingId}", (event) => {
  if (event.data == null) return;
  return notifyBookingChange(getFirestore(), getMessaging(), event.params, {
    before: null,
    after: event.data.data(),
  });
});

// A booking has no field that says who cancelled it, so the auth context names the writer.
export const onBookingUpdated = onDocumentUpdatedWithAuthContext(
  "bookings/{bookingId}",
  (event) => {
    if (event.data == null) return;
    return notifyBookingChange(
      getFirestore(),
      getMessaging(),
      event.params,
      { before: event.data.before.data(), after: event.data.after.data() },
      { authType: event.authType, authId: event.authId },
    );
  },
);
