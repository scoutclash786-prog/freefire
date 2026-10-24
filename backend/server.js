'use strict';
const express = require('express');
const multer = require('multer');
const rateLimit = require('express-rate-limit');
const fs = require('fs');
const lib = require('./lib');
const zapupi = require('./zapupi');
const adminEvents = require('./admin-events');
const { ApiError, cleanType, cleanRef, isImageId } = require('./util');

const app = express();
app.set('trust proxy', 1);
app.disable('x-powered-by');
app.use((req, res, next) => { res.set('X-Content-Type-Options', 'nosniff'); next(); });
app.use(express.json({ limit: '24kb', type: 'application/json' }));

// ---- ZapUPI wallet payments (API key stays in Railway environment variables) ----
app.use('/api/payments/zapupi', zapupi.router);
app.use('/api/notifications', adminEvents.router);

const uploadLimiter = rateLimit({ windowMs: 60 * 1000, limit: 40, standardHeaders: true, legacyHeaders: false,
  handler: (req, res) => res.status(429).json({ success: false, error: 'rate_limited', message: 'Too many uploads. Wait a minute' }) });
const imageLimiter = rateLimit({ windowMs: 60 * 1000, limit: 1200, standardHeaders: true, legacyHeaders: false });

const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: lib.MAX_UPLOAD_MB * 1024 * 1024, files: 1, fields: 6 } });

// ---- health ----
app.get(['/', '/health'], async (req, res) => {
  if (req.query.deep !== '1') return res.json({ ok: true });
  try { res.json(await lib.deepCheck()); }
  catch (e) { res.status(e.status || 500).json({ ok: false, error: e.code || 'server_error', message: e.message }); }
});

// ---- upload (admin / staff only) ----
app.post('/api/upload/image', uploadLimiter,
  async (req, res, next) => { try { req.user = await lib.verifyAdmin(req.headers.authorization, 'media_upload'); next(); } catch (e) { next(e); } },
  (req, res, next) => upload.single('file')(req, res, (err) => {
    if (!err) return next();
    if (err.code === 'LIMIT_FILE_SIZE') return next(new ApiError(413, 'too_large', `Image is too large (max ${lib.MAX_UPLOAD_MB} MB)`));
    next(new ApiError(400, 'bad_request', 'Could not read the upload'));
  }),
  async (req, res, next) => {
    try {
      if (!req.file) throw new ApiError(400, 'no_file', 'No image received');
      const out = await lib.storeImage({
        buffer: req.file.buffer,
        type: cleanType(req.body.type),
        refId: cleanRef(req.body.refId),
        uid: req.user.uid,
      });
      res.json({ success: true, imageId: out.imageId, messageId: out.messageId });
    } catch (e) { next(e); }
  });

// ---- image proxy (public; the app never sees Telegram) ----
app.get('/api/images/:id', imageLimiter, async (req, res, next) => {
  try {
    const id = req.params.id;
    if (!isImageId(id)) throw new ApiError(404, 'not_found', 'Image not found');
    const etag = `"${id}"`;
    if (req.headers['if-none-match'] === etag) {
      res.set({ ETag: etag, 'Cache-Control': 'public, max-age=31536000, immutable' });
      return res.status(304).end();
    }
    const meta = await lib.getMeta(id);
    if (!meta) throw new ApiError(404, 'not_found', 'Image not found');
    const file = await lib.ensureCached(id, meta);
    res.set({ ETag: etag, 'Cache-Control': 'public, max-age=31536000, immutable', 'Content-Type': meta.mime || 'image/jpeg' });
    fs.createReadStream(file).on('error', next).pipe(res);
  } catch (e) { next(e); }
});

// ---- delete (admin only) ----
app.delete('/api/images/:id', uploadLimiter, async (req, res, next) => {
  try {
    await lib.verifyAdmin(req.headers.authorization, 'media_delete');
    if (!isImageId(req.params.id)) throw new ApiError(404, 'not_found', 'Image not found');
    res.json({ success: await lib.removeImage(req.params.id) });
  } catch (e) { next(e); }
});

app.use((req, res, next) => next(new ApiError(404, 'not_found', 'Not found')));
app.use((err, req, res, next) => {
  const e = err instanceof ApiError ? err : new ApiError(500, 'server_error', 'Server error');
  if (!(err instanceof ApiError)) console.error(err);
  if (res.headersSent) return res.end();
  res.status(e.status).json({ success: false, error: e.code, message: e.message });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, '0.0.0.0', () => {
  console.log(`Image backend listening on ${PORT}`);
  lib.deepCheck().then((r) => console.log('Telegram check:', JSON.stringify(r)), (e) => console.error('Telegram check FAILED:', e.message));
});
