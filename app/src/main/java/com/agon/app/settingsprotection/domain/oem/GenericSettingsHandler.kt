package com.agon.app.settingsprotection.domain.oem

import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.settingsprotection.domain.ProtectedScreenDetection

/**
 * AOSP / Pixel / generic OEM fallbacks: wireless pairing exclusion and launcher filtering.
 */
class GenericSettingsHandler : OemSettingsHandler {

    override fun handlesPackage(packageName: String): Boolean = true

    override fun isBenignSettingsSurface(packageName: String, activityClass: String): Boolean = false

    override fun detectEarly(
        packageName: String,
        activityClass: String,
        root: AccessibilityNodeInfo?,
    ): ProtectedScreenDetection? = null

    fun isWirelessPairingSurface(packageName: String, activityClass: String): Boolean {
        val pkg = packageName.lowercase()
        if (pkg == "com.android.bluetooth" || pkg.endsWith(".bluetooth") ||
            pkg == "com.android.nfc" || pkg.endsWith(".nfc") ||
            pkg.contains("hce") || pkg.contains("beaming")
        ) return true
        val klass = activityClass.lowercase()
        return WIRELESS_CLASS_MARKERS.any { klass.contains(it) }
    }

    fun isLauncherPackage(packageName: String): Boolean {
        val lowered = packageName.lowercase()
        return lowered in LAUNCHER_PACKAGES ||
            lowered.contains("launcher") ||
            lowered.endsWith(".home")
    }

    companion object {
        val WIRELESS_CLASS_MARKERS = listOf(
            "bluetoothpairing",
            "bluetoothpairingrequest",
            "bluetoothpairingdialog",
            "bluetoothdevicepicker",
            "bluetoothdialogactivity",
            "bluetoothopp",
            "oppnotification",
            "nfc",
        )

        val LAUNCHER_PACKAGES = setOf(
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.android.systemui",
        )
    }
}
