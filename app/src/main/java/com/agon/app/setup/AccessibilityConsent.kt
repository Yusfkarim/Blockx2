package com.agon.app.setup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Opens the system Accessibility settings page. */
object AccessibilityConsent {
    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching { context.startActivity(intent); true }.getOrDefault(false)
        if (!opened) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
