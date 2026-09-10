package com.agon.app.settingsprotection.domain.oem

import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.settingsprotection.domain.ProtectedScreenDetection
import com.agon.app.settingsprotection.domain.ProtectedScreenType

/**
 * Xiaomi / Redmi / POCO (MIUI + HyperOS) security-center and app-manager surfaces.
 * Sensitive sub-sections (autostart, permissions, manage-apps) are gated; the root dashboard
 * stays open for harmless tools (cleaner, scan, data usage).
 */
class XiaomiSettingsHandler : OemSettingsHandler {

    override fun handlesPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return pkg.startsWith("com.miui.") ||
            pkg.startsWith("com.xiaomi.") ||
            pkg == "com.lbe.security.miui" ||
            pkg.contains("securitycenter") ||
            pkg.contains("securitycore") ||
            pkg.contains("appmanager")
    }

    override fun isBenignSettingsSurface(packageName: String, activityClass: String): Boolean = false

    override fun detectEarly(
        packageName: String,
        activityClass: String,
        root: AccessibilityNodeInfo?,
    ): ProtectedScreenDetection? {
        val klass = activityClass.lowercase()
        if (klass.isEmpty()) return null
        val sensitive = SENSITIVE_CLASS_MARKERS.any { klass.contains(it) }
        if (!sensitive) return null
        return ProtectedScreenDetection(
            type = ProtectedScreenType.AUTOSTART_ENTRY,
            settingsPackage = packageName,
            screenClass = activityClass.ifBlank { packageName },
            confidence = 93,
        )
    }

    companion object {
        val SENSITIVE_CLASS_MARKERS = listOf(
            "autostart",
            "startupmanager",
            "startup_app",
            "applaunch",
            "app_launch",
            "permissionmanager",
            "permissionseditor",
            "powerhide",
            "powerkeeper",
            "hiddenapps",
            "appmanager",
            "applicationsmanager",
            "manageapplications",
            "installedapp",
            "appdetail",
            "appinfo",
        )
    }
}
