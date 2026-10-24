package com.clash.admin

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.FirebaseDatabase

class AdminApp : Application() {
    companion object {
        var instance: AdminApp? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("EliteXAdmin", "Uncaught exception on thread ${thread.name}", throwable)
            try {
                val prefs = getSharedPreferences("admin_crash", Context.MODE_PRIVATE)
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

        try { AdminPush.ensureChannel(this) } catch (_: Throwable) { }
    }
}

fun initFirebase(context: Context): FirebaseApp? {
    val apps = FirebaseApp.getApps(context)
    if (apps.isNotEmpty()) return apps.first()
    return try {
        val options = FirebaseOptions.Builder()
            .setApplicationId("1:922527769978:android:a2bf512c9536301b4f62c3")
            .setApiKey("AIzaSyAjvtJ7eylMRFSnP1AmOShImF6L5toZi-w")
            .setProjectId("paynal-18aa1")
            .setDatabaseUrl("https://paynal-18aa1-default-rtdb.firebaseio.com")
            .setStorageBucket("paynal-18aa1.firebasestorage.app")
            .setGcmSenderId("922527769978")
            .build()
        FirebaseApp.initializeApp(context, options)
    } catch (_: Throwable) {
        try {
            FirebaseApp.initializeApp(context)
        } catch (_: Throwable) {
            try { FirebaseApp.getInstance() } catch (_: Throwable) { null }
        }
    }
}
