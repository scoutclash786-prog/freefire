package com.clash.admin

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/** Gets Google access tokens from a service-account key kept on this phone. */
object GToken {
    private val cache = HashMap<String, Pair<String, Long>>()

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun post(url: String, type: String, body: ByteArray, auth: String? = null): Pair<Int, String> {
        val con = URL(url).openConnection() as HttpURLConnection
        con.requestMethod = "POST"; con.doOutput = true; con.connectTimeout = 20000; con.readTimeout = 30000
        con.setRequestProperty("Content-Type", type)
        if (auth != null) con.setRequestProperty("Authorization", "Bearer $auth")
        con.outputStream.use { it.write(body) }
        val code = con.responseCode
        val text = (if (code in 200..299) con.inputStream else con.errorStream)?.bufferedReader()?.readText() ?: ""
        return code to text
    }

    fun get(sa: JSONObject, scope: String): String {
        val ck = sa.getString("client_email") + scope
        cache[ck]?.let { if (System.currentTimeMillis() < it.second) return it.first }
        val now = System.currentTimeMillis() / 1000
        val header = b64("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val claims = b64(JSONObject().put("iss", sa.getString("client_email")).put("scope", scope)
            .put("aud", "https://oauth2.googleapis.com/token").put("iat", now).put("exp", now + 3600).toString().toByteArray())
        val pem = sa.getString("private_key").replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace("\\n", "").replace("\n", "").replace("\r", "").replace(" ", "")
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.decode(pem, Base64.DEFAULT)))
        val sig = Signature.getInstance("SHA256withRSA").apply { initSign(key); update("$header.$claims".toByteArray()) }.sign()
        val jwt = "$header.$claims.${b64(sig)}"
        val (code, text) = post("https://oauth2.googleapis.com/token", "application/x-www-form-urlencoded",
            ("grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8") + "&assertion=" + jwt).toByteArray())
        if (code != 200) throw Exception("Key rejected ($code). Check that this is the right service account file.")
        val tok = JSONObject(text).getString("access_token")
        cache[ck] = tok to System.currentTimeMillis() + 50 * 60 * 1000
        return tok
    }
}

/** Push notifications through Firebase Cloud Messaging (HTTP v1). */
object Fcm {
    fun sa(c: Context): String = c.getSharedPreferences("admin", Context.MODE_PRIVATE).getString("sa", "") ?: ""
    fun setSa(c: Context, json: String) { c.getSharedPreferences("admin", Context.MODE_PRIVATE).edit().putString("sa", json).apply() }

    suspend fun send(c: Context, target: String, isTopic: Boolean, title: String, body: String, image: String): String? = withContext(Dispatchers.IO) {
        try {
            if (title.isBlank()) return@withContext "Notification title is required"
            val sa = JSONObject(sa(c).ifBlank { return@withContext "Add the service account key first" })
            val data = JSONObject().put("title", title.trim())
            if (body.isNotBlank()) data.put("body", body.trim())
            if (image.isNotBlank()) data.put("image", image.trim())
            // Data-only pushes let the Player service render its full-color app logo as largeIcon.
            // The photo key is absent unless Admin explicitly selected an image.
            val msg = JSONObject().put(if (isTopic) "topic" else "token", target).put("data", data)
                .put("android", JSONObject().put("priority", "HIGH"))
            val (code, text) = GToken.post("https://fcm.googleapis.com/v1/projects/${sa.getString("project_id")}/messages:send", "application/json",
                JSONObject().put("message", msg).toString().toByteArray(), GToken.get(sa, "https://www.googleapis.com/auth/firebase.messaging"))
            if (code in 200..299) null else "Send failed ($code): " + (runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull() ?: text.take(120))
        } catch (e: Exception) { e.message ?: "Send failed" }
    }
}

/** Changes another player's login email / password. Needs a second key with the role "Firebase Authentication Admin". */
object AuthAdmin {
    fun key(c: Context): String = c.getSharedPreferences("admin", Context.MODE_PRIVATE).getString("authsa", "") ?: ""
    fun setKey(c: Context, json: String) { c.getSharedPreferences("admin", Context.MODE_PRIVATE).edit().putString("authsa", json).apply() }

    suspend fun update(c: Context, uid: String, email: String?, password: String?): String? = withContext(Dispatchers.IO) {
        try {
            val sa = JSONObject(key(c).ifBlank { return@withContext "Add the Auth key file first (button on this screen)" })
            val body = JSONObject().put("localId", uid)
            email?.let { body.put("email", it) }; password?.let { body.put("password", it) }
            val (code, text) = GToken.post("https://identitytoolkit.googleapis.com/v1/projects/${sa.getString("project_id")}/accounts:update", "application/json",
                body.toString().toByteArray(), GToken.get(sa, "https://www.googleapis.com/auth/cloud-platform"))
            if (code in 200..299) null else "Auth update failed ($code): " + (runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull() ?: text.take(120))
        } catch (e: Exception) { e.message ?: "Auth update failed" }
    }
}
