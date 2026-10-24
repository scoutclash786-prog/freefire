package com.clash.admin

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import com.example.R

object AdminPush {
    private fun tokenKey(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    suspend fun registerCurrentDevice() {
        val uid = Session.uid.ifBlank { return }
        val token = FirebaseMessaging.getInstance().token.await().trim()
        if (token.isBlank()) return
        val db = FirebaseDatabase.getInstance().reference
        val role = db.child("users/$uid/role").get().await().value?.toString()
        if ((role != "admin" && role != "staff") || !db.child("panelLogin/$uid").get().await().exists()) return
        db.child("adminNotificationTokens/$uid/${tokenKey(token)}")
            .setValue(mapOf("token" to token, "updatedAt" to System.currentTimeMillis(), "platform" to "android")).await()
    }

    suspend fun unregisterCurrentDevice() {
        val uid = Session.uid.ifBlank { return }
        val token = FirebaseMessaging.getInstance().token.await().trim()
        if (token.isBlank()) return
        FirebaseDatabase.getInstance().reference.child("adminNotificationTokens/$uid/${tokenKey(token)}").removeValue().await()
    }

    fun persistRefreshedToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = FirebaseDatabase.getInstance().reference
                val role = db.child("users/$uid/role").get().await().value?.toString()
                if ((role != "admin" && role != "staff") || !db.child("panelLogin/$uid").get().await().exists()) return@launch
                db.child("adminNotificationTokens/$uid/${tokenKey(token)}")
                    .setValue(mapOf("token" to token, "updatedAt" to System.currentTimeMillis(), "platform" to "android")).await()
            } catch (_: Exception) { }
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "M4 Admin alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Withdrawal and support-ticket alerts"
                enableVibration(true)
            })
        }
    }

    const val CHANNEL_ID = "admin_events"
}

class AdminMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        AdminPush.persistRefreshedToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"]?.take(100)?.ifBlank { null } ?: "EliteXarena Admin"
        val body = message.data["body"]?.take(240).orEmpty()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        AdminPush.ensureChannel(this)
        val intent = Intent(this, AdminActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("admin_route", message.data["route"] ?: "home")
        }
        val pendingIntent = PendingIntent.getActivity(this, message.data["eventId"]?.hashCode() ?: System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, AdminPush.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(this).notify(message.data["eventId"]?.hashCode() ?: System.currentTimeMillis().toInt(), notification)
    }
}
