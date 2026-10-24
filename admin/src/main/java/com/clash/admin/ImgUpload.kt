package com.clash.admin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

class UploadException(message: String) : Exception(message)

/** Uploads a photo to the Railway backend, which stores it in the private Telegram channel. Returns "tgimg:<imageId>". */
object ImgUpload {
    private fun base(): String {
        val b = BackendConfig.BASE_URL.trim().trimEnd('/')
        if (b.contains("YOUR-BACKEND") || !b.startsWith("http")) throw UploadException("Backend URL is not set. Edit BackendConfig.kt and rebuild the app.")
        return b
    }

    private suspend fun token(force: Boolean): String =
        AR.auth.currentUser?.getIdToken(force)?.await()?.token ?: throw UploadException("Authentication failed. Log in again.")

    suspend fun upload(c: Context, uri: Uri, type: String, refId: String, maxSide: Int): String = withContext(Dispatchers.IO) {
        val bytes = compress(c, uri, maxSide)
        var res = send(bytes, type, refId, token(false))
        if (res.first == 401) res = send(bytes, type, refId, token(true))
        val (code, text) = res
        val json = try { JSONObject(text) } catch (e: Exception) { null }
        if (code in 200..299 && json?.optBoolean("success") == true) return@withContext "tgimg:" + json.getString("imageId")
        throw UploadException(json?.optString("message")?.takeIf { it.isNotBlank() } ?: when (code) {
            401, 403 -> "Authentication failed. Log in again."
            413 -> "Image is too large."
            415 -> "This is not a valid image."
            in 500..599 -> "Backend is not available right now. Try again."
            else -> "Upload failed ($code)."
        })
    }

    private fun send(bytes: ByteArray, type: String, refId: String, token: String): Pair<Int, String> {
        val boundary = "----ClashBoundary" + System.currentTimeMillis()
        val body = ByteArrayOutputStream()
        fun field(name: String, value: String) { body.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray()) }
        field("type", type); field("refId", refId)
        body.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"image\"\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
        body.write(bytes); body.write("\r\n--$boundary--\r\n".toByteArray())
        val data = body.toByteArray()
        try {
            val con = URL(base() + "/api/upload/image").openConnection() as HttpURLConnection
            con.requestMethod = "POST"; con.doOutput = true; con.connectTimeout = 15000; con.readTimeout = 45000
            con.setRequestProperty("Authorization", "Bearer $token")
            con.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            con.setFixedLengthStreamingMode(data.size)
            con.outputStream.use { it.write(data) }
            val code = con.responseCode
            val text = (if (code in 200..299) con.inputStream else con.errorStream)?.bufferedReader()?.readText() ?: ""
            return code to text
        } catch (e: UploadException) { throw e }
        catch (e: UnknownHostException) { throw UploadException("No internet connection.") }
        catch (e: ConnectException) { throw UploadException("Cannot reach the backend. Check internet or the backend.") }
        catch (e: SocketTimeoutException) { throw UploadException("Upload timed out. Try again.") }
        catch (e: IOException) { throw UploadException("Network error. Try again.") }
    }

    /** Best effort: removes an old image from Telegram + Firebase metadata after it was replaced. */
    suspend fun deleteQuiet(c: Context, ref: String) {
        if (!ref.startsWith("tgimg:")) return
        try {
            val t = token(false)
            withContext(Dispatchers.IO) {
                val con = URL(base() + "/api/images/" + ref.removePrefix("tgimg:")).openConnection() as HttpURLConnection
                con.requestMethod = "DELETE"; con.connectTimeout = 10000; con.readTimeout = 15000
                con.setRequestProperty("Authorization", "Bearer $t")
                con.responseCode; con.disconnect()
            }
        } catch (_: Exception) { }
    }

    private fun compress(c: Context, uri: Uri, maxSide: Int): ByteArray {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / s > 2400) s *= 2
        val bmp = c.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
            ?: throw UploadException("This is not a valid image.")
        val sc = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        val out = if (sc < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt(), (bmp.height * sc).toInt(), true) else bmp
        val bos = ByteArrayOutputStream()
        if (out.hasAlpha()) out.compress(Bitmap.CompressFormat.PNG, 100, bos) else out.compress(Bitmap.CompressFormat.JPEG, 80, bos)
        return bos.toByteArray()
    }
}
