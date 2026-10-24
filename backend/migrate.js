'use strict';
// Moves old image links (ImgBB / any http link) stored in Firebase into Telegram.
//   node migrate.js          -> only shows what would change
//   node migrate.js --apply  -> uploads to Telegram and rewrites the Firebase fields to tgimg:<id>
const lib = require('./lib');

const apply = process.argv.includes('--apply');
const LISTS = ['games', 'banners', 'matches', 'products', 'announcements'];
const SETTINGS = ['logoUrl', 'splashImage', 'ffImage', 'privateImage', 'earnSupportImage', 'earnReferImage', 'earnWhatsappImage'];
const isOld = (v) => typeof v === 'string' && /^https?:\/\//i.test(v) && !v.includes('/api/images/');

(async () => {
  const jobs = [];
  for (const col of LISTS) {
    const snap = await lib.db.ref(col).get();
    snap.forEach((c) => { const v = (c.val() || {}).image; if (isOld(v)) jobs.push({ path: `${col}/${c.key}/image`, url: v, type: col, refId: c.key }); });
  }
  const st = (await lib.db.ref('settings').get()).val() || {};
  for (const k of SETTINGS) if (isOld(st[k])) jobs.push({ path: `settings/${k}`, url: st[k], type: 'settings', refId: '' });

  console.log(`${jobs.length} old image link(s) found.`);
  const done = new Map();
  let ok = 0, failed = 0;
  for (const j of jobs) {
    console.log(`${apply ? 'moving' : 'would move'}  ${j.path}  <-  ${j.url}`);
    if (!apply) continue;
    try {
      let ref = done.get(j.url);
      if (!ref) {
        const r = await fetch(j.url, { signal: AbortSignal.timeout(30000) });
        if (!r.ok) throw new Error(`download failed (${r.status})`);
        const buffer = Buffer.from(await r.arrayBuffer());
        if (buffer.length > lib.MAX_UPLOAD_MB * 1024 * 1024) throw new Error('image too large');
        const out = await lib.storeImage({ buffer, type: j.type, refId: j.refId, uid: 'migration' });
        ref = 'tgimg:' + out.imageId;
        done.set(j.url, ref);
      }
      await lib.db.ref(j.path).set(ref);
      ok++;
    } catch (e) { failed++; console.error(`  FAILED ${j.path}: ${e.message}`); }
  }
  if (apply) console.log(`Done. moved=${ok} failed=${failed}`);
  else console.log('Dry run only. Run "node migrate.js --apply" to move them.');
  process.exit(failed ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
