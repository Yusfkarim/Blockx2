package com.agon.app.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Draw-over-apps capability gate. The blocking UI currently ships as full-screen activities,
 * so this permission is a soft preflight: we ask exactly ONCE per install (persisted flag)
 * when the accessibility service connects, never nagging the user again afterwards and never
 * colliding with an in-flight block screen.
 */
object OverlayAccessGate {

    private const val PREFS = "overlay_access_gate"
    private const val KEY_PROMPTED = "prompted_once"

    /** True when the user already granted the overlay permission. */
    fun isGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** True exactly once per install while the grant is missing — safe to call repeatedly. */
    fun maybePromptOnce(context: Context): Boolean {
        if (isGranted(context)) return false
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PROMPTED, false)) return false

        prefs.edit().putBoolean(KEY_PROMPTED, true).apply()
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        return true
    }
}
