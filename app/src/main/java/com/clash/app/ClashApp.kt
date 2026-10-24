package com.clash.app

import android.app.Application
import android.content.Context
import android.net.Uri
import coil.map.Mapper
import coil.request.Options
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

class ClashApp : Application(), ImageLoaderFactory {
    companion object {
        var instance: ClashApp? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Global uncaught exception protection to prevent silent crashes
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("EliteXarena", "Uncaught exception on thread ${thread.name}", throwable)
            try {
                val prefs = getSharedPreferences("clash_crash", Context.MODE_PRIVATE)
                val sw = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(sw))
                prefs.edit().putString("last_error", "${throwable.javaClass.name}: ${throwable.message}\n$sw")
                    .putLong("ts", System.currentTimeMillis()).commit()
            } catch (_: Throwable) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        initFirebase(this)

        try {
            FirebaseDatabase.getInstance("https://paynal-18aa1-default-rtdb.firebaseio.com").setPersistenceEnabled(true)
        } catch (_: Throwable) {
            try { FirebaseDatabase.getInstance().setPersistenceEnabled(true) } catch (_: Throwable) { }
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(120)
        .components { add(TgImageMapper()) }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(250L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .build()
}

fun initFirebase(context: android.content.Context): com.google.firebase.FirebaseApp? {
    val apps = com.google.firebase.FirebaseApp.getApps(context)
    if (apps.isNotEmpty()) return apps.first()
    return try {
        val options = com.google.firebase.FirebaseOptions.Builder()
            .setApplicationId("1:922527769978:android:a2bf512c9536301b4f62c3")
            .setApiKey("AIzaSyAjvtJ7eylMRFSnP1AmOShImF6L5toZi-w")
            .setProjectId("paynal-18aa1")
            .setDatabaseUrl("https://paynal-18aa1-default-rtdb.firebaseio.com")
            .setStorageBucket("paynal-18aa1.firebasestorage.app")
            .setGcmSenderId("922527769978")
            .build()
        com.google.firebase.FirebaseApp.initializeApp(context, options)
    } catch (_: Throwable) {
        try {
            com.google.firebase.FirebaseApp.initializeApp(context)
        } catch (_: Throwable) {
            try { com.google.firebase.FirebaseApp.getInstance() } catch (_: Throwable) { null }
        }
    }
}

/** Lets every AsyncImage(...) accept "tgimg:<id>" and load it from the backend (cached by Coil). */
class TgImageMapper : Mapper<String, Uri> {
    override fun map(data: String, options: Options): Uri? =
        if (data.startsWith("tgimg:")) Uri.parse(BackendConfig.resolve(data)) else null
}
