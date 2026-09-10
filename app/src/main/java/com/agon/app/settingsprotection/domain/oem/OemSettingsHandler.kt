package com.agon.app.settingsprotection.domain.oem

import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.settingsprotection.domain.ProtectedScreenDetection

/**
 * OEM-specific strategy for settings-surface classification.
 * [com.agon.app.settingsprotection.domain.SettingsScreenDetector] composes these handlers so
 * vendor quirks stay out of the core detector file.
 */
interface OemSettingsHandler {
    /** Package families this handler claims (lowercase contains/equals checks). */
    fun handlesPackage(packageName: String): Boolean

    /**
     * Return true when this OEM surface is known-benign and must skip the heavy detector path
     * (e.g. Samsung One UI Wi-Fi / Display pages).
     */
    fun isBenignSettingsSurface(packageName: String, activityClass: String): Boolean = false

    /**
     * Optional early detection for OEM-only sensitive surfaces. Null means fall through.
     */
    fun detectEarly(
        packageName: String,
        activityClass: String,
        root: AccessibilityNodeInfo?,
    ): ProtectedScreenDetection? = null
}
