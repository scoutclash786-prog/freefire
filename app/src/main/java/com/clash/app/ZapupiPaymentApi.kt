package com.clash.app

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

 data class ZapupiOrder(val orderId: String, val paymentUrl: String)

object ZapupiPaymentApi {
    suspend fun createOrder(amount: Double): ZapupiOrder {
        val response = post("/api/payments/zapupi/create-order", JSONObject().put("amount", amount))
        val orderId = response.optString("orderId")
        val paymentUrl = response.optString("paymentUrl")
        val uri = Uri.parse(paymentUrl)
        if (orderId.isBlank() || uri.scheme != "https" || uri.host != "pay.zapupi.com") {
            throw IllegalStateException("Payment provider returned an invalid checkout link")
        }
        return ZapupiOrder(orderId, paymentUrl)
    }

    suspend fun checkStatus(orderId: String): String {
        val response = post("/api/payments/zapupi/status", JSONObject().put("orderId", orderId))
        return response.optString("status", "pending").lowercase()
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject {
        val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
            ?: throw IllegalStateException("Please sign in again to make a payment")
        return withContext(Dispatchers.IO) {
            val connection = (URL(BackendConfig.BASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            try {
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val json = try { JSONObject(text) } catch (_: Exception) { JSONObject() }
                if (code !in 200..299 || !json.optBoolean("success", false)) {
                    throw IllegalStateException(json.optString("message").ifBlank { "Payment service is unavailable. Please try again." })
                }
                json
            } finally {
                connection.disconnect()
            }
        }
    }
}
