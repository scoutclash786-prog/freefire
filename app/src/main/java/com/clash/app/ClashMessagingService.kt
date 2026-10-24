package com.clash.app

import com.example.R
import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.net.URL

class ClashMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().reference.child("users/$uid/fcmToken").setValue(token)
    }

    // Admin pushes are data-only so this service renders both foreground and background notifications.
    // This keeps the full-color M4 logo as the app large-icon and uses a photo only when one was selected.
    override fun onMessageReceived(m: RemoteMessage) {
        val title = brandAppName(m.notification?.title ?: m.data["title"] ?: "EliteXarena")
        val body = m.notification?.body ?: m.data["body"] ?: ""
        val img = (m.notification?.imageUrl?.toString() ?: m.data["image"])?.let { BackendConfig.resolve(it) }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val logoOptions = BitmapFactory.Options().apply { inSampleSize = 4 }
        val appLogo = BitmapFactory.decodeResource(resources, R.drawable.m4_esports_logo, logoOptions)
        val b = NotificationCompat.Builder(this, "clash_default").setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true).setContentIntent(pi).setPriority(NotificationCompat.PRIORITY_HIGH)
        if (appLogo != null) b.setLargeIcon(appLogo)
        if (!img.isNullOrBlank()) try {
            val bmp = BitmapFactory.decodeStream(URL(img).openStream())
            if (bmp != null) b.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bmp).setSummaryText(body))
        } catch (_: Exception) { }
        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), b.build())
    }
}
