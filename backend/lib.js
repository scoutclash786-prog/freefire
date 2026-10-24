'use strict';
const admin = require('firebase-admin');
const crypto = require('crypto');
const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const { ApiError, sniff, mapTgError, parseServiceAccount, pickPrune } = require('./util');

function need(name) {
  const v = process.env[name];
  if (!v) { console.error(`Missing environment variable: ${name}`); process.exit(1); }
  return v;
}

const BOT_TOKEN = need('BOT_TOKEN');
const CHANNEL_ID = need('CHANNEL_ID');
const MAX_UPLOAD_MB = Number(process.env.MAX_UPLOAD_MB || 8);
const CACHE_DIR = process.env.CACHE_DIR || '/tmp/imgcache';
const CACHE_MAX_MB = Number(process.env.CACHE_MAX_MB || 300);
const TG_TIMEOUT_MS = 30000;
const TG = `https://api.telegram.org/bot${BOT_TOKEN}`;

let sa;
try { sa = parseServiceAccount(need('FIREBASE_CONFIG')); } catch (e) { console.error('FIREBASE_CONFIG is not valid JSON/base64:', e.message); process.exit(1); }
admin.initializeApp({
  credential: admin.credential.cert(sa),
  databaseURL: process.env.FIREBASE_DATABASE_URL || `https://${sa.project_id}-default-rtdb.firebaseio.com`,
});
const db = admin.database();
fs.mkdirSync(CACHE_DIR, { recursive: true });

// ---------------- Telegram ----------------
async function tgCall(method, body, form) {
  let res;
  try {
    res = await fetch(`${TG}/${method}`, {
      method: 'POST',
      body: form || JSON.stringify(body),
      headers: form ? undefined : { 'Content-Type': 'application/json' },
      signal: AbortSignal.timeout(TG_TIMEOUT_MS),
    });
  } catch (e) {
    if (e.name === 'TimeoutError' || e.name === 'AbortError') throw new ApiError(504, 'timeout', 'Telegram took too long. Try again');
    throw new ApiError(502, 'telegram_error', 'Could not reach Telegram');
  }
  let data;
  try { data = await res.json(); } catch { throw new ApiError(502, 'telegram_error', 'Telegram sent an invalid reply'); }
  if (!data.ok) throw mapTgError(data);
  return data.result;
}

async function deepCheck() {
  const me = await tgCall('getMe', {});
  const m = await tgCall('getChatMember', { chat_id: CHANNEL_ID, user_id: me.id });
  const isAdmin = m.status === 'administrator' || m.status === 'creator';
  return { ok: isAdmin && m.can_post_messages !== false, bot: me.username, channelAdmin: isAdmin, canPost: m.can_post_messages !== false };
}

// ---------------- Disk cache ----------------
let pruning = false;
async function prune() {
  if (pruning) return;
  pruning = true;
  try {
    const files = [];
    for (const n of await fsp.readdir(CACHE_DIR)) {
      if (n.endsWith('.tmp')) continue;
      try { const st = await fsp.stat(path.join(CACHE_DIR, n)); files.push({ name: n, size: st.size, mtimeMs: st.mtimeMs }); } catch { /* gone */ }
    }
    for (const n of pickPrune(files, CACHE_MAX_MB * 1024 * 1024)) await fsp.unlink(path.join(CACHE_DIR, n)).catch(() => {});
  } finally { pruning = false; }
}

async function cachePut(id, buffer) {
  const file = path.join(CACHE_DIR, id);
  const tmp = file + '.tmp';
  await fsp.writeFile(tmp, buffer);
  await fsp.rename(tmp, file);
  prune().catch(() => {});
}

// ---------------- Image metadata ----------------
const metaCache = new Map(); // id -> { meta, exp }
async function getMeta(id) {
  const hit = metaCache.get(id);
  if (hit && hit.exp > Date.now()) return hit.meta;
  let snap;
  try { snap = await db.ref(`images/${id}`).get(); } catch { throw new ApiError(503, 'firebase_error', 'Could not read from Firebase'); }
  const meta = snap.exists() ? snap.val() : null;
  metaCache.set(id, { meta, exp: Date.now() + (meta ? 10 * 60 * 1000 : 30 * 1000) });
  return meta;
}

const inflight = new Map();
/** Returns the cached file path, downloading it from Telegram once if needed. */
async function ensureCached(id, meta) {
  const file = path.join(CACHE_DIR, id);
  try { await fsp.access(file); return file; } catch { /* not cached yet */ }
  if (inflight.has(id)) return inflight.get(id);
  const p = (async () => {
    const info = await tgCall('getFile', { file_id: meta.telegramFileId });
    if (!info.file_path) throw new ApiError(502, 'telegram_error', 'Telegram did not return the file');
    let r;
    try { r = await fetch(`https://api.telegram.org/file/bot${BOT_TOKEN}/${info.file_path}`, { signal: AbortSignal.timeout(TG_TIMEOUT_MS) }); }
    catch (e) { throw new ApiError(504, 'timeout', 'Telegram took too long. Try again'); }
    if (!r.ok) throw new ApiError(502, 'telegram_error', 'Could not download the image from Telegram');
    await cachePut(id, Buffer.from(await r.arrayBuffer()));
    return file;
  })().finally(() => inflight.delete(id));
  inflight.set(id, p);
  return p;
}

// ---------------- Store / delete ----------------
async function storeImage({ buffer, type, refId, uid }) {
  const kind = sniff(buffer);
  if (!kind) throw new ApiError(415, 'invalid_image', 'This file is not a valid image (JPG, PNG, WEBP or GIF)');
  const imageId = crypto.randomBytes(12).toString('hex');
  const form = new FormData();
  form.append('chat_id', CHANNEL_ID);
  form.append('disable_notification', 'true');
  form.append('caption', `#${type} ${imageId}`);
  form.append('document', new Blob([buffer], { type: kind.mime }), `${imageId}.${kind.ext}`);
  const msg = await tgCall('sendDocument', null, form);
  const doc = msg.document || msg.animation;
  if (!doc) throw new ApiError(502, 'telegram_error', 'Telegram did not return a file id');
  try {
    await db.ref(`images/${imageId}`).set({
      telegramMessageId: msg.message_id,
      telegramFileId: doc.file_id,
      telegramFileUniqueId: doc.file_unique_id || null,
      type,
      tournamentId: refId || null,
      uploadedBy: uid,
      createdAt: Date.now(),
      mime: kind.mime,
      size: buffer.length,
    });
  } catch {
    tgCall('deleteMessage', { chat_id: CHANNEL_ID, message_id: msg.message_id }).catch(() => {});
    throw new ApiError(503, 'firebase_error', 'Could not save the image details in Firebase');
  }
  await cachePut(imageId, buffer).catch(() => {});
  return { imageId, messageId: String(msg.message_id) };
}

async function removeImage(id) {
  let snap;
  try { snap = await db.ref(`images/${id}`).get(); } catch { throw new ApiError(503, 'firebase_error', 'Could not read from Firebase'); }
  if (!snap.exists()) return false;
  await tgCall('deleteMessage', { chat_id: CHANNEL_ID, message_id: snap.val().telegramMessageId }).catch(() => {});
  try { await db.ref(`images/${id}`).remove(); } catch { throw new ApiError(503, 'firebase_error', 'Could not update Firebase'); }
  metaCache.delete(id);
  await fsp.unlink(path.join(CACHE_DIR, id)).catch(() => {});
  return true;
}

// ---------------- Admin auth ----------------
/** Verifies the Firebase ID token and that the account is a logged-in admin/staff (12-digit key session). */
async function verifyAdmin(authHeader, requiredPermission) {
  const m = /^Bearer (.+)$/.exec(authHeader || '');
  if (!m) throw new ApiError(401, 'unauthorized', 'Please log in again');
  let decoded;
  try { decoded = await admin.auth().verifyIdToken(m[1]); } catch { throw new ApiError(401, 'unauthorized', 'Session expired. Log in again'); }
  let role, session, permissions = {};
  try {
    const [r, s, p] = await Promise.all([
      db.ref(`users/${decoded.uid}/role`).get(),
      db.ref(`panelLogin/${decoded.uid}`).get(),
      db.ref(`users/${decoded.uid}/staffPermissions`).get(),
    ]);
    role = r.val(); session = s.exists(); permissions = p.val() || {};
  } catch { throw new ApiError(503, 'firebase_error', 'Could not check your account in Firebase'); }
  if ((role !== 'admin' && role !== 'staff') || !session) throw new ApiError(403, 'forbidden', 'This account is not allowed to upload images');
  if (role === 'staff' && requiredPermission && permissions[requiredPermission] !== true)
    throw new ApiError(403, 'forbidden', 'Your staff account does not have permission for this image action');
  return { uid: decoded.uid, role };
}

/** Verifies a Firebase ID token for a signed-in player request. */
async function verifyPlayer(authHeader) {
  const m = /^Bearer (.+)$/.exec(authHeader || '');
  if (!m) throw new ApiError(401, 'unauthorized', 'Please sign in again');
  try {
    const decoded = await admin.auth().verifyIdToken(m[1]);
    return { uid: decoded.uid };
  } catch {
    throw new ApiError(401, 'unauthorized', 'Session expired. Please sign in again');
  }
}

module.exports = { admin, db, MAX_UPLOAD_MB, tgCall, deepCheck, getMeta, ensureCached, storeImage, removeImage, verifyAdmin, verifyPlayer };
