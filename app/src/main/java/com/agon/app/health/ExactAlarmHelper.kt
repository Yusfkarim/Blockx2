package com.agon.app.health

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Optional SCHEDULE_EXACT_ALARM (Android 12+). Never uses USE_EXACT_ALARM (Play-restricted to
 * alarm clock / calendar apps).
 *
 * Keep-alive works without this permission via WorkManager + inexact AlarmManager fallbacks.
 */
object ExactAlarmHelper {

    /**
     * True when the platform allows exact / while-idle alarms for this app.
     * Below API 31 exact scheduling is unrestricted for normal apps.
     */
    fun isAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
    }

    /**
     * User action is optional: only surface a fix when API 31+ and permission denied.
     * Not a hard gate for setup — keep-alive has non-exact fallbacks.
     */
    fun requiresUserAction(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isAllowed(context)

    /** Opens the system screen to grant SCHEDULE_EXACT_ALARM (not USE_EXACT_ALARM). */
    fun settingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
