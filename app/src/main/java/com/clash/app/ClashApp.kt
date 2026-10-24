package com.clash.app

import android.app.Application
import android.net.Uri
import coil.map.Mapper
import coil.request.Options
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.database.FirebaseDatabase

class ClashApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // keep settings/banners on the phone so they show instantly on next start
        try { FirebaseDatabase.getInstance().setPersistenceEnabled(true) } catch (_: Exception) { }
        try { com.clash.admin.AdminPush.ensureChannel(this) } catch (_: Exception) { }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(120)
        .components { add(TgImageMapper()) }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(250L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .build()
}

/** Lets every AsyncImage(...) accept "tgimg:<id>" and load it from the backend (cached by Coil). */
class TgImageMapper : Mapper<String, Uri> {
    override fun map(data: String, options: Options): Uri? =
        if (data.startsWith("tgimg:")) Uri.parse(BackendConfig.resolve(data)) else null
}
