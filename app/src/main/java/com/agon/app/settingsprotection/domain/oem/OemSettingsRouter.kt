package com.agon.app.settingsprotection.domain.oem

import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.settingsprotection.domain.ProtectedScreenDetection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes package/class pairs to the correct OEM strategy before the core detector runs.
 */
@Singleton
class OemSettingsRouter @Inject constructor() {

    val generic = GenericSettingsHandler()
    private val samsung = SamsungSettingsHandler()
    private val xiaomi = XiaomiSettingsHandler()

    private val specialists: List<OemSettingsHandler> = listOf(samsung, xiaomi)

    fun isBenignSettingsSurface(packageName: String, activityClass: String): Boolean {
        for (handler in specialists) {
            if (handler.handlesPackage(packageName) &&
                handler.isBenignSettingsSurface(packageName, activityClass)
            ) {
                return true
            }
        }
        return false
    }

    fun detectEarly(
        packageName: String,
        activityClass: String,
        root: AccessibilityNodeInfo?,
    ): ProtectedScreenDetection? {
        for (handler in specialists) {
            if (!handler.handlesPackage(packageName)) continue
            handler.detectEarly(packageName, activityClass, root)?.let { return it }
        }
        return null
    }
}
