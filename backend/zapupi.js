'use strict';

const crypto = require('crypto');
const express = require('express');
const rateLimit = require('express-rate-limit');
const lib = require('./lib');
const { ApiError } = require('./util');
const { parsePaise, formatRupees, makeGatewayOrderId, validCustomDepositAmount, isLocalOrderId, isZapupiPaymentUrl, amountsMatch } = require('./zapupi-core');

const router = express.Router();
const db = lib.db;
const API_ROOT = 'https://pay.zapupi.com/api';
const limited = (windowMs, limit) => rateLimit({
  windowMs, limit, standardHeaders: true, legacyHeaders: false,
  handler: (_req, res) => res.status(429).json({ success: false, error: 'rate_limited', message: 'Too many payment requests. Please wait and try again.' }),
});
const createLimiter = limited(10 * 60 * 1000, 8);
const statusLimiter = limited(60 * 1000, 30);
const webhookLimiter = limited(60 * 1000, 120);

function apiKey() {
  let key = String(process.env.ZAPUPI_API_KEY || '').trim();
  if (key.length >= 2 && ((key[0] === '"' && key[key.length - 1] === '"') || (key[0] === "'" && key[key.length - 1] === "'")))
    key = key.slice(1, -1).trim();
  if (!key) throw new ApiError(503, 'payments_not_configured', 'Online payments are not configured yet');
  return key;
}

function safeProviderText(value, limit = 160) {
  if (typeof value !== 'string' && typeof value !== 'number') return '';
  let text = String(value).replace(/[\r\n\t]+/g, ' ').replace(/\s+/g, ' ').trim();
  const secret = String(process.env.ZAPUPI_API_KEY || '').trim().replace(/^(["'])(.*)\1$/, '$2');
  if (secret) text = text.split(secret).join('[redacted]');
  return text.replace(/\b(zap[_ -]?key|api[_ -]?key|secret|token|authorization)\b\s*[:=]\s*[^,;\s]+/gi, '$1=[redacted]').slice(0, limit);
}

function webhookUrl() {
  const raw = process.env.ZAPUPI_WEBHOOK_URL || '';
  try {
    const url = new URL(raw);
    if (url.protocol !== 'https:' || !url.hostname || url.hostname === 'localhost' || url.username || url.password)
      throw new Error('invalid');
    return url.toString();
  } catch { throw new ApiError(503, 'payments_not_configured', 'Online payments are not configured yet'); }
}

async function zapupiPost(endpoint, body) {
  let response;
  try {
    response = await fetch(`${API_ROOT}/${endpoint}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(15000),
    });
  } catch (e) {
    console.error('[ZapUPI] provider request failed', JSON.stringify({ endpoint, errorType: safeProviderText(e && e.name, 40) }));
    throw new ApiError(e.name === 'TimeoutError' || e.name === 'AbortError' ? 504 : 502,
      'gateway_unavailable', 'Could not reach the payment provider. Please try again.');
  }
  let data;
  try { data = await response.json(); } catch {
    console.error('[ZapUPI] provider returned non-JSON response', JSON.stringify({ endpoint, httpStatus: response.status }));
    throw new ApiError(502, 'gateway_reply_invalid', 'The payment provider returned an invalid response');
  }
  if (!response.ok || !data || data.status !== 'success') {
    const diagnostic = {
      endpoint,
      httpStatus: response.status,
      providerStatus: safeProviderText(data && data.status, 40),
      providerCode: safeProviderText(data && (data.code || data.error_code || data.error), 80),
      providerMessage: safeProviderText(data && data.message, 160),
    };
    console.error('[ZapUPI] provider rejected request', JSON.stringify(diagnostic));
    throw new ApiError(502, 'gateway_rejected', `ZapUPI rejected the request (HTTP ${response.status}). Check Railway logs for the sanitized provider reason.`);
  }
  return data;
}

async function verifiedPlayer(req) {
  const identity = await lib.verifyPlayer(req.headers.authorization);
  let snap;
  try { snap = await db.ref(`users/${identity.uid}`).get(); }
  catch { throw new ApiError(503, 'firebase_error', 'Could not verify your account'); }
  if (!snap.exists()) throw new ApiError(403, 'forbidden', 'Player account not found');
  const user = snap.val() || {};
  if (user.banned === true || (user.role && user.role !== 'user')) throw new ApiError(403, 'forbidden', 'This account cannot make wallet payments');
  return { uid: identity.uid, user };
}

async function findLocalOrder(orderId) {
  if (!isLocalOrderId(orderId)) return null;
  const snap = await db.ref(`zapupiPayments/${orderId}`).get();
  return snap.exists() ? snap.val() : null;
}

async function setOrderFailure(orderId, status, detail, remote = {}) {
  const now = Date.now();
  await db.ref().update({
    [`zapupiPayments/${orderId}/status`]: status,
    [`zapupiPayments/${orderId}/gatewayStatus`]: String(remote.status || '').slice(0, 32),
    [`zapupiPayments/${orderId}/gatewayTxnId`]: String(remote.txn_id || '').slice(0, 100),
    [`zapupiPayments/${orderId}/updatedAt`]: now,
    [`zapupiPayments/${orderId}/reviewNote`]: detail || null,
    [`deposits/${orderId}/status`]: status === 'failed' || status === 'test' ? 'rejected' : 'pending',
    [`deposits/${orderId}/utr`]: String(remote.utr || '').slice(0, 100),
    [`deposits/${orderId}/gatewayStatus`]: String(remote.status || '').slice(0, 32),
    [`deposits/${orderId}/gatewayReview`]: detail || null,
  });
  return status;
}

async function creditOnce(orderId, order, remote) {
  const uid = String(order.uid || '');
  const userRef = db.ref(`users/${uid}`);
  const amountPaise = Number(order.amountPaise);
  const amount = amountPaise / 100;
  if (!uid || !Number.isSafeInteger(amountPaise) || amountPaise < 1)
    throw new ApiError(503, 'payment_record_invalid', 'The payment order could not be verified');

  // A transaction on the user record combines the wallet increment and its idempotency marker.
  // If delivery is retried after a crash, the marker prevents a duplicate credit.
  const result = await userRef.transaction((current) => {
    if (!current) return;
    const credited = current.zapupiCreditedOrders || {};
    if (credited[orderId] === true) return;
    const oldBalance = Number(current.deposited || 0);
    if (!Number.isFinite(oldBalance) || oldBalance < 0) return;
    current.deposited = Math.round((oldBalance + amount) * 100) / 100;
    credited[orderId] = true;
    current.zapupiCreditedOrders = credited;
    return current;
  }, undefined, false);
  if (!result.committed) {
    const marker = await userRef.child(`zapupiCreditedOrders/${orderId}`).get();
    if (marker.val() !== true) throw new ApiError(503, 'wallet_credit_failed', 'Payment confirmed, but wallet update needs retry');
  }

  const now = Date.now();
  const txnId = `zapupi_${orderId}`;
  const gatewayTxnId = String(remote.txn_id || '').slice(0, 100);
  const utr = String(remote.utr || '').slice(0, 100);
  await db.ref().update({
    [`zapupiPayments/${orderId}/status`]: 'success',
    [`zapupiPayments/${orderId}/gatewayStatus`]: 'Success',
    [`zapupiPayments/${orderId}/gatewayTxnId`]: gatewayTxnId,
    [`zapupiPayments/${orderId}/utr`]: utr,
    [`zapupiPayments/${orderId}/environment`]: String(remote.environment || '').slice(0, 32),
    [`zapupiPayments/${orderId}/verifiedAt`]: now,
    [`zapupiPayments/${orderId}/updatedAt`]: now,
    [`deposits/${orderId}/status`]: 'approved',
    [`deposits/${orderId}/utr`]: utr,
    [`deposits/${orderId}/gatewayTxnId`]: gatewayTxnId,
    [`deposits/${orderId}/gatewayStatus`]: 'Success',
    [`transactions/${uid}/${txnId}`]: {
      id: txnId, type: 'deposit', amount, note: String(order.description || 'ZapUPI wallet top-up').slice(0, 100), ts: now, status: 'success',
    },
  });
  return 'success';
}

async function reconcileOrder(orderId, order) {
  if (order.status === 'success' || order.status === 'failed' || order.status === 'test') return order.status;
  const statusRequestId = String(order.gatewayOrderId || orderId);
  const result = await zapupiPost('order-status', { zap_key: apiKey(), order_id: statusRequestId });
  const remote = result.data;
  if (!remote || !['Pending', 'Success', 'Failed'].includes(String(remote.status)))
    throw new ApiError(502, 'gateway_status_invalid', 'Could not verify the payment status');
  if (remote.order_id !== undefined && String(remote.order_id) !== statusRequestId)
    throw new ApiError(502, 'gateway_order_mismatch', 'Could not verify the payment order');

  if (remote.status === 'Pending') {
    await db.ref(`zapupiPayments/${orderId}`).update({ gatewayStatus: 'Pending', updatedAt: Date.now() });
    return 'pending';
  }
  if (remote.status === 'Failed') return setOrderFailure(orderId, 'failed', null, remote);

  if (!amountsMatch(remote, Number(order.amountPaise)))
    return setOrderFailure(orderId, 'amount_mismatch', 'Gateway amount requires manual review', remote);
  const environment = String(remote.environment || '').toLowerCase();
  if (environment === 'test' && process.env.ZAPUPI_ALLOW_TEST_PAYMENTS !== 'true')
    return setOrderFailure(orderId, 'test', 'Test-environment payments are not credited', remote);
  if (!['cashier', 'zappay', 'test'].includes(environment))
    return setOrderFailure(orderId, 'amount_mismatch', 'Gateway environment requires manual review', remote);

  return creditOnce(orderId, order, remote);
}

router.post('/create-order', createLimiter, async (req, res, next) => {
  try {
    const { uid, user } = await verifiedPlayer(req);
    const settingsSnapshot = await db.ref('settings').get();
    const settings = settingsSnapshot.val() || {};
    if (String(settings.zapupiEnabled || '').toLowerCase() === 'false')
      throw new ApiError(503, 'payments_disabled', 'Online payments are temporarily disabled');
    const amountPaise = parsePaise(req.body && req.body.amount);
    const configuredMin = parsePaise(settings.minDeposit || '10') || 1000;
    const maxPaise = parsePaise(process.env.ZAPUPI_MAX_AMOUNT_INR || '100000') || 10000000;
    if (!validCustomDepositAmount(amountPaise, configuredMin, maxPaise))
      throw new ApiError(400, 'invalid_amount', `Amount must be between ₹${formatRupees(Math.max(configuredMin, 100))} and ₹${formatRupees(maxPaise)}`);

    const key = apiKey();
    const callback = webhookUrl();
    const orderId = `M4ZAP-${crypto.randomBytes(12).toString('hex').toUpperCase()}`;
    const providerOrderId = makeGatewayOrderId(Date.now(), crypto.randomInt(0, 1000));
    const amount = amountPaise / 100;
    const username = String(user.username || 'Player').slice(0, 64);
    const description = String(settings.zapupiDescription || 'M4 ESPORTS wallet top-up').replace(/[\r\n\t]+/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 80) || 'M4 ESPORTS wallet top-up';
    const now = Date.now();
    const order = {
      orderId, uid, username, amount, amountPaise, description, status: 'creating', gateway: 'zapupi',
      createdAt: now, updatedAt: now,
    };
    await db.ref().update({
      [`zapupiPayments/${orderId}`]: order,
      [`deposits/${orderId}`]: { uid, username, amount, description, utr: '', status: 'pending', ts: now, paymentMethod: 'ZapUPI', gateway: 'zapupi', orderId },
    });

    let gatewayOrder;
    try {
      const payload = {
        zap_key: key,
        order_id: providerOrderId,
        amount: formatRupees(amountPaise),
        remark: description,
        webhook_url: callback,
      };
      const mobile = String(user.phone || '').replace(/\D/g, '');
      if (/^\d{10}$/.test(mobile)) payload.customer_mobile = mobile;
      gatewayOrder = await zapupiPost('create-order', payload);
      if (!isZapupiPaymentUrl(gatewayOrder.payment_url)) throw new ApiError(502, 'gateway_reply_invalid', 'Payment provider returned an invalid checkout address');
    } catch (e) {
      // Preserve the local record so a late webhook can still be independently reconciled.
      await db.ref(`zapupiPayments/${orderId}`).update({ status: 'create_error', updatedAt: Date.now() }).catch(() => {});
      if (e instanceof ApiError) throw e;
      throw new ApiError(502, 'gateway_rejected', 'Could not create a payment order');
    }

    const gatewayOrderId = String(gatewayOrder.order_id || providerOrderId).slice(0, 128);
    const orderMapUpdates = {};
    for (const externalId of new Set([providerOrderId, gatewayOrderId])) {
      const mappingKey = crypto.createHash('sha256').update(externalId).digest('hex');
      orderMapUpdates[`zapupiOrderMap/${mappingKey}`] = orderId;
    }
    await db.ref().update({
      [`zapupiPayments/${orderId}/status`]: 'pending',
      [`zapupiPayments/${orderId}/providerOrderId`]: providerOrderId,
      [`zapupiPayments/${orderId}/gatewayOrderId`]: gatewayOrderId,
      [`zapupiPayments/${orderId}/gatewayCreateTxnId`]: String(gatewayOrder.txn_id || '').slice(0, 100),
      [`zapupiPayments/${orderId}/updatedAt`]: Date.now(),
      ...orderMapUpdates,
    });
    res.json({ success: true, orderId, paymentUrl: gatewayOrder.payment_url });
  } catch (e) { next(e); }
});

router.post('/status', statusLimiter, async (req, res, next) => {
  try {
    const { uid } = await verifiedPlayer(req);
    const orderId = String((req.body && req.body.orderId) || '');
    if (!isLocalOrderId(orderId)) throw new ApiError(400, 'invalid_order', 'Invalid payment order');
    const order = await findLocalOrder(orderId);
    if (!order || order.uid !== uid) throw new ApiError(404, 'not_found', 'Payment order not found');
    const status = await reconcileOrder(orderId, order);
    res.json({ success: true, orderId, status });
  } catch (e) { next(e); }
});

router.post('/webhook', webhookLimiter, async (req, res) => {
  const body = req.body || {};
  const externalOrderId = String(body.order_id || '').slice(0, 128);
  let orderId = externalOrderId;
  try {
    if (!isLocalOrderId(orderId)) {
      if (!externalOrderId) return res.status(200).json({ status: 'ok' });
      const mappingKey = crypto.createHash('sha256').update(externalOrderId).digest('hex');
      const mapped = await db.ref(`zapupiOrderMap/${mappingKey}`).get();
      orderId = String(mapped.val() || '');
    }
    if (!isLocalOrderId(orderId)) return res.status(200).json({ status: 'ok' });
    const order = await findLocalOrder(orderId);
    if (!order) return res.status(200).json({ status: 'ok' });
    await reconcileOrder(orderId, order);
    return res.status(200).json({ status: 'ok' });
  } catch (_e) {
    // No payment is credited from webhook fields. A failed server-side status check is retriable.
    return res.status(503).json({ status: 'retry' });
  }
});

module.exports = { router };
