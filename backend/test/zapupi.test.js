'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { parsePaise, parseAmountOptions, parseZapupiPackages, formatRupees, makeGatewayOrderId, validCustomDepositAmount, isLocalOrderId, isZapupiPaymentUrl, amountsMatch } = require('../zapupi-core');

test('INR conversion uses exact paise and rejects invalid precision/input', () => {
  assert.equal(parsePaise('1'), 100);
  assert.equal(parsePaise('100.50'), 10050);
  assert.equal(parsePaise(10.5), 1050);
  assert.equal(formatRupees(10050), '100.50');
  for (const bad of ['', '0', '-1', '1.001', '₹10', '1e2', 'Infinity', '100000000']) assert.equal(parsePaise(bad), null);
});

test('Admin deposit choices parse safely to unique paise values', () => {
  assert.deepEqual(parseAmountOptions('50, 100; 100.50\n50, invalid, 0'), [5000, 10000, 10050]);
  assert.deepEqual(parseAmountOptions(''), []);
  assert.deepEqual(parseAmountOptions('invalid, ₹50'), []);
});

test('Admin ZapUPI packages parse amount and description per line', () => {
  assert.deepEqual(parseZapupiPackages('50|Starter coins\n100.50|Special | top-up\n50|Duplicate\nbad|Ignored'), [
    { amountPaise: 5000, description: 'Starter coins' },
    { amountPaise: 10050, description: 'Special | top-up' },
  ]);
  assert.deepEqual(parseZapupiPackages('bad|No amount'), []);
});

test('only generated M4 ZapUPI IDs are accepted', () => {
  assert.ok(isLocalOrderId('M4ZAP-0123456789ABCDEF01234567'));
  assert.ok(!isLocalOrderId('../users'));
  assert.ok(!isLocalOrderId('M4ZAP-0123456789abcdef01234567'));
});

test('ZapUPI receives numeric provider order IDs while local IDs stay separate', () => {
  const first = makeGatewayOrderId(1760000000000, 7);
  const second = makeGatewayOrderId(1760000000000, 8);
  assert.match(first, /^\d{16}$/);
  assert.notEqual(first, second);
  assert.ok(isLocalOrderId('M4ZAP-0123456789ABCDEF01234567'));
  assert.throws(() => makeGatewayOrderId(1760000000000, 1000), TypeError);
});

test('custom deposit amounts ignore retired presets but remain within server bounds', () => {
  assert.ok(validCustomDepositAmount(parsePaise('37.50'), 1000, 10000000));
  assert.ok(!validCustomDepositAmount(parsePaise('5'), 1000, 10000000));
  assert.ok(!validCustomDepositAmount(parsePaise('100001'), 1000, 10000000));
});

test('checkout URLs must be HTTPS on the official ZapUPI host', () => {
  assert.ok(isZapupiPaymentUrl('https://pay.zapupi.com/instent-pay-abc'));
  assert.ok(!isZapupiPaymentUrl('http://pay.zapupi.com/pay'));
  assert.ok(!isZapupiPaymentUrl('https://pay.zapupi.com:8443/pay'));
  assert.ok(!isZapupiPaymentUrl('https://evil.example/pay'));
  assert.ok(!isZapupiPaymentUrl('https://pay.zapupi.com.evil.example/pay'));
});

test('payment verification requires both order and paid amounts to match exactly', () => {
  assert.ok(amountsMatch({ amount: '100.00', pay_amount: '100' }, 10000));
  assert.ok(!amountsMatch({ amount: '100.00', pay_amount: '99.99' }, 10000));
  assert.ok(!amountsMatch({ amount: '100.01', pay_amount: '100.01' }, 10000));
  assert.ok(!amountsMatch({ amount: '100.00' }, 10001));
});
