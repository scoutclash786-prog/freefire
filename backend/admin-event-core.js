'use strict';

const POLICIES = Object.freeze({
  withdrawal_request: { title: 'New withdrawal request', route: 'withdrawals', permissions: ['withdrawals_review', 'withdrawals_export'] },
  support_ticket: { title: 'New support ticket', route: 'supporttickets', permissions: ['support_manage'] },
  support_reply: { title: 'Player replied to a support ticket', route: 'supporttickets', permissions: ['support_manage'] },
});

function isSafeRecordKey(value) {
  return typeof value === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(value);
}

function canReceiveAdminAlert(role, permissions, type) {
  const policy = POLICIES[type];
  if (!policy) return false;
  if (role === 'admin') return true;
  if (role !== 'staff') return false;
  const grants = permissions && typeof permissions === 'object' ? permissions : {};
  return policy.permissions.some((permission) => grants[permission] === true);
}

function formatAdminAlert(type, record) {
  const policy = POLICIES[type];
  if (!policy) throw new TypeError('Unknown Admin alert type');
  const username = String(record && record.username || 'Player').replace(/[\r\n\t]+/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 32) || 'Player';
  const amount = Number(record && record.amount);
  const money = Number.isFinite(amount) && amount > 0 ? ` ₹${amount.toFixed(2)}` : '';
  const category = String(record && record.category || 'Other').replace(/[^\p{L}\p{N} _-]/gu, '').slice(0, 24) || 'Other';
  let body;
  switch (type) {
    case 'withdrawal_request': body = `${username} requested a withdrawal${money}.`; break;
    case 'support_ticket': body = `${username} opened a support ticket (${category}).`; break;
    case 'support_reply': body = `${username} sent a reply to a support ticket.`; break;
    default: throw new TypeError('Unknown Admin alert type');
  }
  return { title: policy.title, body, route: policy.route, type };
}

module.exports = { POLICIES, isSafeRecordKey, canReceiveAdminAlert, formatAdminAlert };
