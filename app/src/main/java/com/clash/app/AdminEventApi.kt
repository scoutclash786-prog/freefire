package com.clash.app

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Best-effort event signal; the underlying Player request remains successful if push delivery is unavailable. */
object AdminEventApi {
    suspend fun notifyBestEffort(type: String, entityId: String, relatedId: String = "") {
        try {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: return
            withContext(Dispatchers.IO) {
                val body = JSONObject().put("type", type).put("entityId", entityId)
                if (relatedId.isNotBlank()) body.put("relatedId", relatedId)
                val connection = (URL(BackendConfig.BASE_URL.trimEnd('/') + "/api/notifications/admin-event").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 4_000
                    readTimeout = 5_000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    stream?.use { it.readBytes() }
                } finally {
                    connection.disconnect()
                }
            }
        } catch (_: Exception) {
            // Alerts must never undo a successfully persisted deposit, withdrawal, or support message.
        }
    }
}
