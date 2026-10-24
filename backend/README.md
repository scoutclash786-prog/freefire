# M4 ESPORTS — Image and Payment Backend (Node.js + Express, Railway)

Flow: **M4 ESPORTS Admin app → backend → Telegram Bot → Private Channel → Firebase (image metadata) → Player app**; wallet payments use **Player app → backend → ZapUPI → verified wallet credit in Firebase**; Player requests also trigger permission-filtered **Admin FCM alerts** for new accounts, deposits, withdrawals, and support activity.

* Admin app image चुनता है → backend `POST /api/upload/image` → Telegram private channel में file जाती है → Firebase में `images/<imageId>` metadata save होता है।
* Firebase के existing fields (`matches/<id>/image`, `games/<id>/image`, `banners/...`, `settings/...`) में अब URL की जगह `tgimg:<imageId>` save होता है (यही "bannerImageId" reference है)।
* Player app `tgimg:<imageId>` को `https://<backend>/api/images/<imageId>` से दिखाती है। Telegram या Bot Token app में कभी नहीं आता।
* Image एक बार Telegram से आती है → backend disk cache + app (Coil) cache में रहती है → अगली बार तुरंत।

## 1. Telegram Bot बनाना (BotFather)
1. Telegram में **@BotFather** खोलो → `/newbot`.
2. Bot का नाम और username दो (username `bot` पर खत्म होना चाहिए).
3. BotFather जो **token** देता है (`123456789:AA...`) वही `BOT_TOKEN` है। इसे किसी को मत दिखाओ।

## 2. Private Channel बनाना और Bot को Admin बनाना
1. Telegram → New Channel → **Private** चुनो (नाम कुछ भी, जैसे "Clash Images").
2. Channel खोलो → Channel name → **Administrators** → **Add Administrator** → अपना bot search करके चुनो।
3. Permissions में कम से कम **Post Messages** और **Delete Messages** ON रखो → Save.

## 3. CHANNEL_ID निकालना
1. Channel में कोई भी एक message खुद post करो (जैसे "test").
2. Browser में खोलो: `https://api.telegram.org/bot<BOT_TOKEN>/getUpdates`
3. Result में `"chat":{"id":-100xxxxxxxxxx,...}` दिखेगा। यह `-100...` वाली पूरी संख्या ही `CHANNEL_ID` है (minus के साथ).
4. `getUpdates` खाली आए तो channel में एक और message post करके दोबारा खोलो।

## 4. Firebase credentials (FIREBASE_CONFIG)
यह key सिर्फ Railway में रहेगी, phone या APK में नहीं।
1. Google Cloud Console → IAM & Admin → **Service accounts** → Create service account (नाम `clash-backend`).
2. Assign **Firebase Realtime Database Admin** for the existing database operations and **Firebase Cloud Messaging API Admin** for FCM sends (`cloudmessaging.messages.create`). Do not grant broad Project Editor solely for push messages.
3. उस account के **Keys** → Add key → Create new key → **JSON** → file download.
4. Railway variable `FIREBASE_CONFIG` में उस JSON की पूरी content paste करो (एक ही value). Base64 भी चलेगा: `base64 -w0 key.json`.
5. Important: पुरानी `firebase-adminsdk-...` key (जो chat में share हुई थी) Firebase/Google Cloud में **delete** कर दो।
6. Firebase Console → Project settings → Cloud Messaging में **FCM HTTP v1 API** enabled रखें.

## 5. GitHub पर push
```bash
cd backend
git init
git add .
git commit -m "Clash image backend"
git branch -M main
git remote add origin https://github.com/<your-user>/<repo>.git
git push -u origin main
```
`.gitignore` में `.env` और service-account files पहले से हैं, उन्हें push मत करना।
(अगर पूरा Clash2 project push कर रहे हो तो Railway में **Root Directory = backend** रखना।)

## 6. Railway पर Deploy
1. railway.app → New Project → **Deploy from GitHub repo** → अपना repo चुनो।
2. Service → **Variables** में ये जोड़ो:

| Variable | Value |
|---|---|
| `BOT_TOKEN` | BotFather वाला token |
| `CHANNEL_ID` | `-100...` वाला id |
| `FIREBASE_CONFIG` | service-account JSON |
| `FIREBASE_DATABASE_URL` *(optional)* | `https://paynal-18aa1-default-rtdb.firebaseio.com` |
| `MAX_UPLOAD_MB` *(optional)* | `8` |
| `ZAPUPI_API_KEY` | ZapUPI merchant secret; backend only, never put it in Android or Firebase |
| `ZAPUPI_WEBHOOK_URL` | Public HTTPS backend URL ending `/api/payments/zapupi/webhook` |
| `ZAPUPI_MAX_AMOUNT_INR` *(optional)* | Maximum amount per top-up in INR (default `100000`) |
| `ZAPUPI_ALLOW_TEST_PAYMENTS` *(optional)* | Keep `false` in production; test-mode payments are not credited by default |
| `CACHE_MAX_MB` *(optional)* | `300` |

3. Deploy अपने आप चलेगा (`npm install` फिर `npm start`).
4. Service → **Settings → Networking → Generate Domain**। यह आपका Backend URL है: `https://xxxx.up.railway.app`.
5. *(optional)* Railway **Volume** mount `/data` करके `CACHE_DIR=/data/imgcache` रखो, तब redeploy के बाद भी cache बचा रहेगा।

## 7. Deployment verify करना
* `https://<backend>/health` → `{"ok":true}`
* `https://<backend>/health?deep=1` → `{"ok":true,"bot":"<botname>","channelAdmin":true,"canPost":true}`
  * `channelAdmin:false` या error आए तो bot channel का admin नहीं है या `CHANNEL_ID` गलत है।
* Railway **Deploy Logs** में `Telegram check: {...ok:true...}` दिखना चाहिए।

## 8. Android apps में Backend URL डालना
दोनों apps में यही एक constant बदलो, फिर दोनों apps दोबारा build करो:
* `app/src/main/java/com/clash/app/BackendConfig.kt`
* `admin/src/main/java/com/clash/admin/BackendConfig.kt`

```kotlin
const val BASE_URL = "https://xxxx.up.railway.app"
```

## 9. Firebase rules
Root `database.rules.json` में `images/` पर कोई rule नहीं है, इसलिए app उसे पढ़/लिख नहीं सकती; सिर्फ backend (Admin SDK) लिखता है। कुछ बदलने की जरूरत नहीं।

## 10. पुरानी images (ImgBB links) migrate करना
पुराने http links काम करते रहेंगे, लेकिन चाहो तो Telegram में ले जाओ:
```bash
cd backend && npm install
# env variables set करके (BOT_TOKEN, CHANNEL_ID, FIREBASE_CONFIG)
npm run migrate            # सिर्फ दिखाता है कि क्या बदलेगा
node migrate.js --apply    # असल में move करता है
```

## API
| Method | Path | Auth | काम |
|---|---|---|---|
| POST | `/api/upload/image` | Firebase ID token (admin/staff, panel login key वाला session) | multipart `file`, `type`, `refId` → `{success, imageId, messageId}` |
| GET | `/api/images/:imageId` | public | image serve (immutable cache headers, ETag) |
| DELETE | `/api/images/:imageId` | admin | Telegram message + metadata delete |
| POST | `/api/payments/zapupi/create-order` | signed-in Player | validates amount and creates ZapUPI checkout |
| POST | `/api/payments/zapupi/status` | signed-in order owner | verifies order through ZapUPI server-side status API |
| POST | `/api/payments/zapupi/webhook` | ZapUPI callback | callback is only a trigger; backend verifies status and amount before crediting |
| POST | `/api/notifications/admin-event` | signed-in Player | verifies a saved event and sends an alert to active Admin devices and staff with matching permissions |
| GET | `/health`, `/health?deep=1` | public | status |

Error response: `{success:false, error:"<code>", message:"..."}` — codes: `unauthorized`, `forbidden`, `too_large`, `invalid_image`, `telegram_channel`, `telegram_busy`, `telegram_error`, `timeout`, `firebase_error`, `rate_limited`.

For the complete ZapUPI setup, credential hygiene, and rollout checks, see [`../ZAPUPI_SETUP.md`](../ZAPUPI_SETUP.md). For the Admin notification IAM role, device permission, and Firebase rules rollout, see [`../ADMIN_ALERTS_SETUP.md`](../ADMIN_ALERTS_SETUP.md). Payments are never credited from client claims or webhook payloads alone; the backend checks the ZapUPI order-status endpoint and prevents duplicate wallet credits.

## Local test
```bash
npm install
npm test        # unit tests (image type check, error mapping, cache pruning, config parsing)
cp .env.example .env   # भरकर
export $(grep -v '^#' .env | xargs) && npm start
```
