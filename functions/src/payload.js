// Co-authored-by: Claude Code (Claude Opus 5.5)
// Builds notification payloads without Firebase, so their tests need no emulator. The app writes
// the words from these facts, which keeps the copy in strings.xml.

const PREVIEW_LENGTH = 100;

// FCM's answers when the app was uninstalled or its data cleared. A device is addressed by its fid,
// which yields the first; the second is what the SDK reports if it cannot tell a fid was sent.
const UNREGISTERED = new Set([
  "messaging/installation-id-not-registered",
  "messaging/registration-token-not-registered",
]);

// FCM's answer for a malformed fid, but also for a malformed payload.
const INVALID_ARGUMENT = "messaging/invalid-argument";

export function recipientOf(conversation, senderUid) {
  if (senderUid === conversation.renterUid) return conversation.lenderUid;
  if (senderUid === conversation.lenderUid) return conversation.renterUid;
  return null;
}

const graphemes = new Intl.Segmenter(undefined, { granularity: "grapheme" });

// FCM refuses a payload over 4096 bytes. The rest of the payload stays under 700, and a character
// can carry any number of accents, so the preview is also capped in bytes.
const PREVIEW_MAX_BYTES = 1000;

// Counts characters as the reader sees them, so a flag or a family emoji at the cut stays whole.
// A first character over the byte cap is cut inside it, because an empty preview means a photo.
export function previewOf(text) {
  if (typeof text !== "string") return "";
  let preview = "";
  let bytes = 0;
  let count = 0;
  for (const { segment } of graphemes.segment(text)) {
    const size = Buffer.byteLength(segment);
    if (count === PREVIEW_LENGTH || bytes + size > PREVIEW_MAX_BYTES) {
      if (count === 0) return cutToBytes(segment, PREVIEW_MAX_BYTES);
      break;
    }
    preview += segment;
    bytes += size;
    count++;
  }
  return preview;
}

function cutToBytes(text, maxBytes) {
  let cut = "";
  let bytes = 0;
  for (const codePoint of text) {
    bytes += Buffer.byteLength(codePoint);
    if (bytes > maxBytes) break;
    cut += codePoint;
  }
  return cut;
}

// FCM data values must all be strings.
export function messagePayload({ conversation, message, senderName, recipientUid }) {
  return {
    type: "message",
    recipientUid,
    listingId: conversation.listingId ?? "",
    renterUid: conversation.renterUid ?? "",
    senderName: senderName ?? "",
    listingTitle: conversation.listingTitle ?? "",
    preview: previewOf(message.text),
  };
}

// sendEachForMulticast reports network, auth and quota failures per device instead of throwing, so
// the logged codes are the only trace of a misconfigured project. Device ids stay out of the logs.
// `failed` and `codes` both count the failures left after the deletions, so they always agree.
export function failureSummary(fids, batchResponse) {
  const deleted = unusableDevices(fids, batchResponse);
  const deletedSet = new Set(deleted);
  const codes = {};
  let failed = 0;
  batchResponse.responses.forEach((response, i) => {
    if (response.success || deletedSet.has(fids[i])) return;
    const code = response.error?.code ?? "unknown";
    codes[code] = (codes[code] ?? 0) + 1;
    failed++;
  });
  return { deleted, failed, codes };
}

// `responses` is in the same order as `fids`. Every device in a send gets the same payload, so an
// invalid argument blames the device only when another device was reached; a bad payload fails
// them all and deletes nothing.
export function unusableDevices(fids, batchResponse) {
  const payloadIsValid = batchResponse.successCount > 0;
  return fids.filter((_, i) => {
    const response = batchResponse.responses[i];
    if (response.success) return false;
    const code = response.error?.code;
    return UNREGISTERED.has(code) || (code === INVALID_ARGUMENT && payloadIsValid);
  });
}
