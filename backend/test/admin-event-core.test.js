'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { isSafeRecordKey, canReceiveAdminAlert, formatAdminAlert } = require('../admin-event-core');

test('Admin alerts accept only Firebase push-key-safe entity IDs', () => {
  assert.ok(isSafeRecordKey('-Oabc_1234567890123456'));
  assert.ok(!isSafeRecordKey('../users'));
  assert.ok(!isSafeRecordKey(''));
});

test('Admin receives all configured event types while staff needs matching grants', () => {
  for (const type of ['withdrawal_request', 'support_ticket', 'support_reply']) {
    assert.ok(canReceiveAdminAlert('admin', {}, type));
  }
  assert.ok(!canReceiveAdminAlert('admin', {}, 'player_registered'));
  assert.ok(!canReceiveAdminAlert('admin', {}, 'deposit_request'));
  assert.ok(canReceiveAdminAlert('staff', { withdrawals_review: true }, 'withdrawal_request'));
  assert.ok(!canReceiveAdminAlert('staff', { support_manage: true }, 'withdrawal_request'));
  assert.ok(canReceiveAdminAlert('staff', { support_manage: true }, 'support_reply'));
  assert.ok(!canReceiveAdminAlert('user', { support_manage: true }, 'support_reply'));
});

test('Admin push copy omits UPI details, ticket body, and other private fields', () => {
  const withdrawal = formatAdminAlert('withdrawal_request', { username: 'player1', amount: 250, upi: 'private@upi' });
  assert.equal(withdrawal.title, 'New withdrawal request');
  assert.match(withdrawal.body, /player1.*250\.00/);
  assert.ok(!withdrawal.body.includes('private@upi'));
  const ticket = formatAdminAlert('support_ticket', { username: 'player2', category: 'Payments', subject: 'private subject', lastMessage: 'private body' });
  assert.match(ticket.body, /Payments/);
  assert.ok(!ticket.body.includes('private subject'));
  assert.ok(!ticket.body.includes('private body'));
});
