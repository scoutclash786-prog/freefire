package com.clash.app

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ImgCache {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun <T> safeFirst(f: Flow<T>, ms: Long = 3000): T? = try { withTimeoutOrNull(ms) { f.first() } } catch (e: Exception) { null }

    private suspend fun download(url: String, dest: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val tmp = File(dest.path + ".tmp")
            val con = URL(url).openConnection() as HttpURLConnection
            con.connectTimeout = 8000; con.readTimeout = 15000
            con.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            tmp.renameTo(dest)
        } catch (e: Exception) { false }
    }

    private fun decode(f: File): ImageBitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / s > 2200) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
    }

    /** Shows the saved loading image at once, then refreshes it if the admin changed it. */
    suspend fun loadSplash(ctx: Context, onBitmap: (ImageBitmap?) -> Unit) {
        val prefs = ctx.getSharedPreferences("clash", Context.MODE_PRIVATE)
        val f = File(ctx.filesDir, "splash.img")
        if (f.exists()) onBitmap(withContext(Dispatchers.IO) { decode(f) })
        val url = safeFirst(Repo.setting("splashImage"), 2500) ?: prefs.getString("splashUrl", "") ?: ""
        if (url.isBlank()) {
            if (f.exists()) { f.delete(); prefs.edit().remove("splashUrl").apply(); onBitmap(null) }
            return
        }
        if (url != prefs.getString("splashUrl", "") || !f.exists()) {
            val ok = withTimeoutOrNull(6000) { download(BackendConfig.resolve(url), f) } ?: false
            if (ok) { prefs.edit().putString("splashUrl", url).apply(); onBitmap(withContext(Dispatchers.IO) { decode(f) }) }
        }
    }

    fun prefetch(app: Context, urls: Collection<String>) {
        urls.filter { it.isNotBlank() }.forEach { app.imageLoader.enqueue(ImageRequest.Builder(app).data(it).build()) }
    }

    /** Downloads every important picture in the background so screens open with images already there. */
    fun warm(app: Context) {
        appScope.launch {
            try {
                val urls = mutableSetOf<String>()
                listOf("logoUrl", "ffImage", "privateImage", "earnSupportImage", "earnReferImage", "earnWhatsappImage")
                    .forEach { k -> safeFirst(Repo.setting(k))?.let { urls += it } }
                listOf("important", "welcome", "earn", "onboarding").forEach { s -> safeFirst(Repo.banners(s))?.forEach { urls += it.image } }
                listOf("freefire", "private").forEach { s -> safeFirst(Repo.games(s))?.forEach { urls += it.image } }
                prefetch(app, urls)
            } catch (_: Exception) { }
        }
    }
}
