package com.agon.app.blocklist.domain

import android.content.Context
import android.media.AudioManager
import android.telecom.TelecomManager

/**
 * Phone-call safety policy for every blocking overlay (urge / panic mode included).
 *
 * Two independent guarantees, by design:
 *  1. Dialer and in-call UI packages are NEVER blocked or covered — the user must always reach
 *     the answer/hang-up controls of a real phone call, even deep inside an urge window.
 *  2. While a call is ringing or ongoing, block overlays are suspended globally, so an incoming
 *     call notification is never hidden behind a full-screen block and a running call is never
 *     pushed off screen. Call state is read from [AudioManager.getMode], which requires no
 *     runtime permission on any Android version.
 */
object PhoneCallPolicy {

    /** Stock + OEM dialer and in-call UI packages (Google, Samsung, Xiaomi, Oppo, Huawei…). */
    private val DIALER_PACKAGES = setOf(
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer",
        "com.android.server.telecom",
        "com.android.phone",
        "com.android.incallui",
        "com.samsung.android.incallui",
        "com.miui.incallui",
        "com.coloros.incallui",
        "com.oplus.incallui",
        "com.huawei.incallui",
        "com.hihonor.incallui",
        "com.vivo.incallui",
        "com.oneplus.incallui",
    )

    /** True when [packageName] is (or is currently acting as) the dialer / in-call UI. */
    fun isDialerOrInCallPackage(context: Context, packageName: String): Boolean {
        if (packageName in DIALER_PACKAGES) return true
        val lowered = packageName.lowercase()
        if (lowered.contains("incallui") || lowered.contains(".dialer") || lowered.endsWith(".phone")) {
            return true
        }
        return runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == packageName
        }.getOrDefault(false)
    }

    /**
     * True while a phone call (or a VoIP call, which shares the same audio route) is ringing or
     * ongoing. Audio route modes are queryable without any runtime permission, unlike
     * TelephonyManager.getCallState on Android 12+.
     */
    fun isCallActive(context: Context): Boolean = runCatching {
        val mode = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.mode
        mode == AudioManager.MODE_RINGTONE ||
            mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION
    }.getOrDefault(false)

    /**
     * True only while the phone is actively RINGING — the one moment the incoming-call UI must
     * own the screen so the user sees the answer controls. An ongoing (off-hook) call does NOT
     * relax enforcement: every protection layer (blocked apps, keywords, websites) keeps working
     * at full strength while the user talks — only the dialer surface itself is exempt.
     */
    fun isRinging(context: Context): Boolean = runCatching {
        (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.mode == AudioManager.MODE_RINGTONE
    }.getOrDefault(false)

    /** The single guard every blocking surface must consult before presenting UI. */
    fun mustSuppressBlocking(context: Context, packageName: String): Boolean =
        isDialerOrInCallPackage(context, packageName) || isRinging(context)
}
