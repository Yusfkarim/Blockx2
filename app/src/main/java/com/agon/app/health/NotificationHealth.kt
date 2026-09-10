package com.agon.app.health

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHealth {

    const val GUARDIAN_CHANNEL = "protection_guardian"
    const val ALERT_CHANNEL = "protection_alerts"

    fun areNotificationsHealthy(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return true
            val channel = nm.getNotificationChannel(GUARDIAN_CHANNEL)
            // Channel may not exist until guardian starts — treat missing as ok if app notifications on.
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(GUARDIAN_CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    GUARDIAN_CHANNEL,
                    "Device protection",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        if (nm.getNotificationChannel(ALERT_CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    ALERT_CHANNEL,
                    "Protection warnings",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
    }

    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
