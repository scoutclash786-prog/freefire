'use strict';

const LOCAL_ORDER_RE = /^M4ZAP-[A-F0-9]{24}$/;

/** Convert an INR decimal to integer paise without floating-point rounding. */
function parsePaise(value) {
  if (value === null || value === undefined) return null;
  const text = String(value).trim();
  if (!/^(?:0|[1-9]\d{0,7})(?:\.\d{1,2})?$/.test(text)) return null;
  const [rupees, fraction = ''] = text.split('.');
  const paise = Number(rupees) * 100 + Number((fraction + '00').slice(0, 2));
  return Number.isSafeInteger(paise) && paise > 0 ? paise : null;
}

function formatRupees(paise) {
  if (!Number.isSafeInteger(paise) || paise < 0) throw new TypeError('Invalid paise');
  return `${Math.floor(paise / 100)}.${String(paise % 100).padStart(2, '0')}`;
}

function makeGatewayOrderId(nowMs, suffix) {
  const timestamp = BigInt(nowMs);
  const randomPart = BigInt(suffix);
  if (timestamp < 1n || randomPart < 0n || randomPart > 999n) throw new TypeError('Invalid gateway order ID parts');
  return (timestamp * 1000n + randomPart).toString();
}

function validCustomDepositAmount(amountPaise, minPaise, maxPaise) {
  const minimum = Number.isSafeInteger(minPaise) ? Math.max(minPaise, 100) : 1000;
  return Number.isSafeInteger(amountPaise) && Number.isSafeInteger(maxPaise) && amountPaise >= minimum && amountPaise <= maxPaise;
}

function parseAmountOptions(value) {
  if (value === null || value === undefined || String(value).trim() === '') return [];
  return [...new Set(String(value).split(/[,;\s]+/).map(parsePaise).filter((n) => n !== null))].sort((a, b) => a - b);
}

function parseZapupiPackages(value) {
  if (value === null || value === undefined || String(value).trim() === '') return [];
  const byAmount = new Map();
  for (const line of String(value).split(/\r?\n/)) {
    const separator = line.indexOf('|');
    const amountText = (separator < 0 ? line : line.slice(0, separator)).trim();
    const amountPaise = parsePaise(amountText);
    if (amountPaise === null || byAmount.has(amountPaise)) continue;
    const description = separator < 0 ? '' : line.slice(separator + 1).replace(/[\r\n\t]+/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 80);
    byAmount.set(amountPaise, { amountPaise, description });
  }
  return [...byAmount.values()].sort((a, b) => a.amountPaise - b.amountPaise);
}

function isLocalOrderId(value) {
  return typeof value === 'string' && LOCAL_ORDER_RE.test(value);
}

function isZapupiPaymentUrl(value) {
  try {
    const u = new URL(String(value));
    return u.protocol === 'https:' && u.hostname === 'pay.zapupi.com' && u.port === '' && !u.username && !u.password;
  } catch { return false; }
}

function amountsMatch(remote, expectedPaise) {
  if (!remote || !Number.isSafeInteger(expectedPaise)) return false;
  const orderPaise = parsePaise(remote.amount);
  const paidPaise = parsePaise(remote.pay_amount === undefined || remote.pay_amount === null ? remote.amount : remote.pay_amount);
  return orderPaise === expectedPaise && paidPaise === expectedPaise;
}

module.exports = { parsePaise, parseAmountOptions, parseZapupiPackages, formatRupees, makeGatewayOrderId, validCustomDepositAmount, isLocalOrderId, isZapupiPaymentUrl, amountsMatch };
