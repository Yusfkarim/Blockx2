package com.agon.app.settingsprotection.data

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.agon.app.MainActivity
import com.agon.app.R
import com.agon.app.settingsprotection.domain.FailedAttempt
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Publishes the localized tamper alert shown after a blocked settings-access attempt. */
@Singleton
class ProtectionNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission")
    fun notifyBlockedAttempt(attempt: FailedAttempt) {
        if (!hasPermission()) return
        val language = context.getSharedPreferences("family_shield", Context.MODE_PRIVATE)
            .getString("language", "ku") ?: "ku"
        ensureChannel(language)
        val time = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(attempt.timestamp))
        val reason = reasonText(attempt.method, language)
        val screen = screenText(attempt.screenType, language)
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titleText(language))
            .setContentText("$reason · $screen")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailText(reason, screen, time, language)))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(pending)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return manager.areNotificationsEnabled()
        val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && manager.areNotificationsEnabled()
    }

    private fun ensureChannel(language: String) {
        if (Build.VERSION.SDK_INT < 26) return
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        val name = when (language) {
            "en" -> "Settings protection"
            "ar" -> "حماية الإعدادات"
            else -> "پاراستنی ڕێکخستنەکان"
        }
        val description = when (language) {
            "en" -> "Alerts when someone tries to open protected system settings"
            "ar" -> "تنبيهات عند محاولة فتح إعدادات النظام المحمية"
            else -> "ئاگاداری کاتێک کەسێک هەوڵی کردنەوەی ڕێکخستنی پارێزراو دەدات"
        }
        system.createNotificationChannel(
            NotificationChannel(CHANNEL, name, NotificationManager.IMPORTANCE_HIGH).apply {
                this.description = description
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            },
        )
    }

    private fun titleText(language: String) = when (language) {
        "en" -> "Protected settings access blocked"
        "ar" -> "تم حظر الوصول إلى الإعدادات المحمية"
        else -> "دەستپێگەیشتن بە ڕێکخستنی پارێزراو بلۆک کرا"
    }

    private fun reasonText(method: String, language: String): String = when (method) {
        "BACK_PRESS" -> when (language) { "en" -> "Verification cancelled"; "ar" -> "تم إلغاء التحقق"; else -> "پشتڕاستکردنەوە هەڵوەشایەوە" }
        "TIMEOUT" -> when (language) { "en" -> "Verification timed out"; "ar" -> "انتهت مهلة التحقق"; else -> "کاتی پشتڕاستکردنەوە تەواو بوو" }
        "BIOMETRIC" -> when (language) { "en" -> "Biometric verification failed"; "ar" -> "فشل التحقق الحيوي"; else -> "پشتڕاستکردنەوەی بایۆمەتریک سەرکەوتوو نەبوو" }
        else -> when (language) { "en" -> "Incorrect credential"; "ar" -> "بيانات الدخول غير صحيحة"; else -> "زانیاری چوونەژوورەوە هەڵەیە" }
    }

    private fun screenText(type: String, language: String): String {
        val key = type.uppercase(Locale.ROOT)
        return when (language) {
            "en" -> key.lowercase(Locale.ROOT).replace('_', ' ')
            "ar" -> when (key) {
                "DEVICE_ADMIN" -> "مسؤول الجهاز"
                "ACCESSIBILITY_SETTINGS" -> "إمكانية الوصول"
                "UNINSTALL" -> "إلغاء التثبيت"
                "FORCE_STOP" -> "الإيقاف القسري"
                "CLEAR_DATA" -> "محو البيانات"
                "PERMISSIONS" -> "الأذونات"
                "SECURITY_SETTINGS" -> "إعدادات الأمان"
                "MANAGE_APPS" -> "إدارة التطبيقات"
                else -> "معلومات التطبيق"
            }
            else -> when (key) {
                "DEVICE_ADMIN" -> "بەڕێوەبەری ئامێر"
                "ACCESSIBILITY_SETTINGS" -> "Accessibility"
                "UNINSTALL" -> "لابردنی ئەپ"
                "FORCE_STOP" -> "ڕاگرتنی بەزۆر"
                "CLEAR_DATA" -> "سڕینەوەی داتا"
                "PERMISSIONS" -> "مۆڵەتەکان"
                "SECURITY_SETTINGS" -> "ڕێکخستنی ئاسایش"
                "MANAGE_APPS" -> "بەڕێوەبردنی ئەپەکان"
                else -> "زانیاری ئەپ"
            }
        }
    }

    private fun detailText(reason: String, screen: String, time: String, language: String) = when (language) {
        "en" -> "$reason while opening $screen at $time. BlockX LaAbrah returned the device to Home."
        "ar" -> "$reason أثناء فتح $screen في $time. أعاد BlockX LaAbrah الجهاز إلى الشاشة الرئيسية."
        else -> "$reason لەکاتی کردنەوەی $screen لە $time. BlockX LaAbrah ئامێرەکەی گەڕاندەوە بۆ سەرەکی."
    }

    private companion object {
        const val CHANNEL = "settings_protection_alerts"
        const val NOTIFICATION_ID = 4711
        const val REQUEST_CODE = 4712
    }
}
