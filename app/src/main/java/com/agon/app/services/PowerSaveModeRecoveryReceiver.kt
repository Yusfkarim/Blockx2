package com.agon.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Instant (0ms) recovery from any ultra / extreme / super power-saving mode, on every OEM.
 *
 * Those modes (Samsung Ultra Power Saving, MIUI/HyperOS Super Power Saving, ColorOS /
 * Realme UI / OxygenOS Super Power Saving, Honor/Huawei SuperSaving, Vivo OriginOS extreme
 * saving …) kill or throttle the protection stack while they are on — some skins even detach
 * the accessibility service. The moment the user leaves the mode, protection must come back
 * at 0ms, without a reboot and without opening the app manually.
 *
 * Three complementary channels, because a single one can never cover every vendor:
 *  1. THIS manifest receiver — fires even while our process is dead (the system action
 *     `android.os.action.POWER_SAVE_MODE_CHANGED` is exempt from the implicit-broadcast ban,
 *     plus the documented MIUI / EMUI vendor actions).
 *  2. The same receiver registered dynamically by [ProtectionGuardianService] while it lives
 *     (runtime-only delivery, for OEM builds that do not reach manifest receivers reliably).
 *  3. Settings observers in the guardian for vendors with no public broadcast at all
 *     (they flip well-known Settings keys, e.g. Samsung `ultra_power_saving_mode`, MIUI
 *     `power_supersave_mode_open`).
 *
 * Every channel converges on [KeepAliveScheduler.ensureProtectionRunning], an idempotent call
 * that instantly revives the guardian watchdog, the settings / App-Manager blocking path (the
 * accessibility service re-arms its guards on any foreground event while enabled), the VPN
 * when the user has it on, and the "accessibility disabled" alert when a vendor power mode
 * actually detached the service.
 */
class PowerSaveModeRecoveryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in HANDLED_ACTIONS) return
        // One call brings every protection surface back. Idempotent, cheap, 0ms path.
        runCatching { KeepAliveScheduler.ensureProtectionRunning(context) }
    }

    companion object {
        /**
         * Public + best-effort vendor power-save actions. Vendor strings are harmless no-ops on
         * devices where they are never emitted; the guardian's Settings observers cover the
         * vendors that expose no broadcast at all.
         */
        val HANDLED_ACTIONS = setOf(
            // AOSP / Pixel / stock Android battery saver (emitted by several OEM skins too).
            "android.os.action.POWER_SAVE_MODE_CHANGED",
            // Xiaomi / Redmi / POCO — MIUI & HyperOS (documented vendor action).
            "miui.intent.action.POWER_SAVE_MODE_CHANGED",
            // Huawei EMUI / Honor MagicOS super-saving paths.
            "huawei.intent.action.POWER_SAVE_MODE_CHANGED",
            "huawei.android.intent.action.POWER_SAVE_MODE_CHANGED",
            "com.hihonor.intent.action.POWER_SAVE_MODE_CHANGED",
            // ColorOS / OxygenOS / Realme UI and Funtouch / OriginOS custom paths.
            "oppo.intent.action.POWER_SAVE_MODE_CHANGED",
            "oplus.intent.action.POWER_SAVE_MODE_CHANGED",
            "vivo.intent.action.POWER_SAVE_MODE_CHANGED",
        )
    }
}
