'use strict';

const crypto = require('crypto');
const express = require('express');
const rateLimit = require('express-rate-limit');
const lib = require('./lib');
const { ApiError } = require('./util');
const { POLICIES, isSafeRecordKey, canReceiveAdminAlert, formatAdminAlert } = require('./admin-event-core');

const router = express.Router();
const db = lib.db;
const limiter = rateLimit({ windowMs: 60 * 1000, limit: 20, standardHeaders: true, legacyHeaders: false });

async function verifyEventRecord(type, uid, entityId, relatedId) {
  if (type === 'withdrawal_request') {
    const snap = await db.ref(`withdrawals/${entityId}`).get();
    const record = snap.val();
    if (!record || String(record.uid || '') !== uid || String(record.status || '') !== 'pending')
      throw new ApiError(404, 'event_not_found', 'The request could not be verified');
    return record;
  }

  const ticketSnap = await db.ref(`supportTickets/${uid}/${entityId}`).get();
  const ticket = ticketSnap.val();
  if (!ticket || String(ticket.uid || '') !== uid) throw new ApiError(404, 'event_not_found', 'The support ticket could not be verified');
  if (type === 'support_ticket') {
    const messages = ticket.messages && typeof ticket.messages === 'object' ? Object.values(ticket.messages) : [];
    if (!messages.some((m) => m && String(m.senderUid || '') === uid && String(m.senderRole || '') === 'user'))
      throw new ApiError(404, 'event_not_found', 'The support ticket could not be verified');
    return { username: ticket.username, category: ticket.category };
  }
  if (!isSafeRecordKey(relatedId)) throw new ApiError(400, 'invalid_event', 'The support reply could not be verified');
  const messageSnap = await db.ref(`supportTickets/${uid}/${entityId}/messages/${relatedId}`).get();
  const message = messageSnap.val();
  if (!message || String(message.senderUid || '') !== uid || String(message.senderRole || '') !== 'user')
    throw new ApiError(404, 'event_not_found', 'The support reply could not be verified');
  return { username: ticket.username, category: ticket.category };
}

async function getAdminDeviceTokens(type) {
  const [adminsSnap, staffSnap, sessionsSnap] = await Promise.all([
    db.ref('users').orderByChild('role').equalTo('admin').get(),
    db.ref('users').orderByChild('role').equalTo('staff').get(),
    db.ref('panelLogin').get(),
  ]);
  const people = [];
  for (const [snap, role] of [[adminsSnap, 'admin'], [staffSnap, 'staff']]) {
    snap.forEach((child) => {
      const uid = child.key;
      const profile = child.val() || {};
      if (uid && sessionsSnap.child(uid).exists() && canReceiveAdminAlert(role, profile.staffPermissions, type))
        people.push(uid);
    });
  }
  const records = await Promise.all(people.map(async (uid) => ({ uid, snap: await db.ref(`adminNotificationTokens/${uid}`).get() })));
  const tokens = [];
  const seen = new Set();
  for (const { uid, snap } of records) {
    snap.forEach((child) => {
      const record = child.val() || {};
      const token = typeof record.token === 'string' ? record.token.trim() : '';
      if (token && !seen.has(token)) {
        seen.add(token);
        tokens.push({ uid, token, ref: db.ref(`adminNotificationTokens/${uid}/${child.key}`) });
      }
    });
  }
  return tokens;
}

async function sendAdminAlert(alert, eventId, targets) {
  if (targets.length === 0) return 0;
  const adminSdk = lib.admin;
  if (!adminSdk || !adminSdk.apps.length) throw new ApiError(503, 'messaging_unavailable', 'Admin alerts are not configured');
  let sent = 0;
  for (let offset = 0; offset < targets.length; offset += 500) {
    const batch = targets.slice(offset, offset + 500);
    const result = await adminSdk.messaging().sendEachForMulticast({
      tokens: batch.map((target) => target.token),
      data: { title: alert.title, body: alert.body, type: alert.type, route: alert.route, eventId },
      android: { priority: 'high', ttl: 60 * 60 * 1000 },
    });
    sent += result.successCount;
    const stale = [];
    result.responses.forEach((response, index) => {
      const code = response.error && response.error.code;
      if (code === 'messaging/registration-token-not-registered' || code === 'messaging/invalid-registration-token')
        stale.push(batch[index].ref.remove().catch(() => {}));
    });
    await Promise.all(stale);
  }
  return sent;
}

router.post('/admin-event', limiter, async (req, res, next) => {
  try {
    const identity = await lib.verifyPlayer(req.headers.authorization);
    const profileSnap = await db.ref(`users/${identity.uid}`).get();
    const profile = profileSnap.val() || {};
    if (!profileSnap.exists() || profile.role !== 'user' || profile.banned === true)
      throw new ApiError(403, 'forbidden', 'This account cannot create Admin alerts');

    const type = String(req.body && req.body.type || '');
    const entityId = String(req.body && req.body.entityId || '');
    const relatedId = String(req.body && req.body.relatedId || '');
    if (!POLICIES[type] || !isSafeRecordKey(entityId)) throw new ApiError(400, 'invalid_event', 'Invalid Admin alert request');
    if (type === 'support_reply' && !isSafeRecordKey(relatedId)) throw new ApiError(400, 'invalid_event', 'Invalid support reply ID');

    const record = await verifyEventRecord(type, identity.uid, entityId, relatedId);
    const alert = formatAdminAlert(type, record);
    const eventId = crypto.createHash('sha256').update(`${type}:${identity.uid}:${entityId}:${relatedId}`).digest('hex');
    const claimRef = db.ref(`adminNotificationDedup/${eventId}`);
    const claim = await claimRef.transaction((current) => current === null ? { createdAt: Date.now(), type, uid: identity.uid, state: 'sending' } : undefined, undefined, false);
    if (!claim.committed) return res.json({ success: true, duplicate: true, sent: 0 });

    try {
      const targets = await getAdminDeviceTokens(type);
      const sent = await sendAdminAlert(alert, eventId, targets);
      await claimRef.update({ state: targets.length ? 'sent' : 'no_registered_devices', sent, updatedAt: Date.now() });
      return res.json({ success: true, sent });
    } catch (e) {
      await claimRef.remove().catch(() => {});
      throw e;
    }
  } catch (e) { next(e); }
});

module.exports = { router };
