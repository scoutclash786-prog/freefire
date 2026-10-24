'use strict';

class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

/** Detects the real image type from the first bytes (the file name / client type is never trusted). */
function sniff(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < 12) return null;
  if (buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) return { mime: 'image/jpeg', ext: 'jpg' };
  if (buf.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return { mime: 'image/png', ext: 'png' };
  if (buf.subarray(0, 4).toString('latin1') === 'RIFF' && buf.subarray(8, 12).toString('latin1') === 'WEBP') return { mime: 'image/webp', ext: 'webp' };
  if (buf.subarray(0, 3).toString('latin1') === 'GIF') return { mime: 'image/gif', ext: 'gif' };
  return null;
}

const cleanType = (t) => (/^[a-z0-9_-]{1,32}$/i.test(t || '') ? String(t).toLowerCase() : 'image');
const cleanRef = (r) => (/^[A-Za-z0-9_-]{1,64}$/.test(r || '') ? r : '');
const isImageId = (id) => /^[a-f0-9]{24}$/.test(id || '');

/** Turns a Telegram Bot API error into a clear error for the app. */
function mapTgError(d) {
  const desc = String((d && d.description) || '').toLowerCase();
  const code = d && d.error_code;
  if (code === 429) return new ApiError(503, 'telegram_busy', 'Telegram is busy. Try again in a moment');
  if (code === 403 || desc.includes('chat not found') || desc.includes('not enough rights') || desc.includes('not a member') ||
      desc.includes('need administrator rights') || desc.includes('have no rights')) {
    return new ApiError(502, 'telegram_channel', 'The bot is not an admin of the Telegram channel (or CHANNEL_ID is wrong)');
  }
  if (code === 401) return new ApiError(502, 'telegram_token', 'Telegram rejected the BOT_TOKEN');
  return new ApiError(502, 'telegram_error', 'Telegram error: ' + ((d && d.description) || 'unknown'));
}

/** FIREBASE_CONFIG may be the raw service-account JSON or its base64. */
function parseServiceAccount(raw) {
  let txt = String(raw || '').trim();
  if (!txt.startsWith('{')) txt = Buffer.from(txt, 'base64').toString('utf8');
  const sa = JSON.parse(txt);
  if (typeof sa.private_key === 'string') sa.private_key = sa.private_key.replace(/\\n/g, '\n');
  return sa;
}

/** Given cached files, returns the names to delete (oldest first) so the folder drops to 80% of the limit. */
function pickPrune(files, maxBytes) {
  let total = files.reduce((s, f) => s + f.size, 0);
  if (total <= maxBytes) return [];
  const out = [];
  for (const f of [...files].sort((a, b) => a.mtimeMs - b.mtimeMs)) {
    if (total <= maxBytes * 0.8) break;
    out.push(f.name);
    total -= f.size;
  }
  return out;
}

module.exports = { ApiError, sniff, cleanType, cleanRef, isImageId, mapTgError, parseServiceAccount, pickPrune };
