package com.agon.app.settingsprotection.domain.oem

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.settingsprotection.domain.ProtectedScreenDetection

/**
 * Samsung One UI whitelist-first gate (Galaxy M32 5G / Android 13 field report).
 *
 * On One UI, `com.android.settings` uses fragmented containers (SecSettings2, SubSettings).
 * Generic categories must pass freely; only high-risk app-management surfaces stay protected.
 */
class SamsungSettingsHandler : OemSettingsHandler {

    override fun handlesPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return pkg == "com.android.settings" ||
            pkg == "com.samsung.android.settings" ||
            pkg.startsWith("com.samsung.android.")
    }

    override fun isBenignSettingsSurface(packageName: String, activityClass: String): Boolean {
        val pkg = packageName.lowercase()
        if (pkg != "com.android.settings" && pkg != "com.samsung.android.settings") return false
        if (activityClass.isBlank()) return false
        val lowered = activityClass.lowercase()

        if (SAMSUNG_HIGH_RISK_CLASSES.any { lowered.contains(it) }) return false
        if (DEVICE_ADMIN_CLASSES.any { lowered.contains(it) }) return false
        if (ACCESSIBILITY_CLASSES.any { lowered.contains(it) }) return false
        if (lowered.contains("forcestop") || lowered.contains("force_stop") ||
            lowered.contains("uninstall")
        ) return false
        if (MANAGE_APPS_CLASSES.any { lowered.contains(it) }) return false
        if (BATTERY_PER_APP_SURFACE_CLASSES.any { lowered.contains(it) }) return false
        if (BATTERY_SYSTEM_SURFACE_CLASSES.any { lowered.contains(it) }) return false
        if (AUTOSTART_TOGGLE_SURFACE_CLASSES.any { lowered.contains(it) }) return false
        // Private DNS must never be whitelisted as benign.
        if (lowered.contains("privatedns") || lowered.contains("private_dns")) return false

        if (ONE_UI_BASE_CONTAINERS.any { lowered.contains(it) }) {
            Log.d(TAG, "Samsung One UI whitelist: container class=$lowered")
            return true
        }
        if (SAMSUNG_BENIGN_CATEGORY_MARKERS.any { lowered.contains(it) }) {
            Log.d(TAG, "Samsung One UI whitelist: category class=$lowered")
            return true
        }
        return false
    }

    override fun detectEarly(
        packageName: String,
        activityClass: String,
        root: AccessibilityNodeInfo?,
    ): ProtectedScreenDetection? = null

    companion object {
        private const val TAG = "SamsungSettingsHandler"

        val ONE_UI_BASE_CONTAINERS = listOf(
            "secsettings2activity",
            "secmainsettingsactivity",
            "subsettings",
            "settingshomepageactivity",
            "settingshomepage",
            "settingclasses",
            "samsungsettings",
        )

        val SAMSUNG_HIGH_RISK_CLASSES = listOf(
            "installedappdetails",
            "appinfodashboard",
            "applicationsdetails",
            "applicationdetails",
            "appinfoactivity",
            "appinfosettings",
            "appdetailactivity",
            "appdetailsactivity",
            "appmanagerdetail",
            "appinfo",
            "appcontrolactivity",
            "appcontrol",
            "appheader",
            "applicationsstate",
            "applicationsettingsactivity",
            "installedappdetailsactivity",
            "appinfofragment",
            "applicationdetailactivity",
        )

        val SAMSUNG_BENIGN_CATEGORY_MARKERS = listOf(
            "wifi", "wifisettings", "wifip2psettings",
            "bluetoothsettings", "nfcsettings",
            "connectionsettings", "connections",
            "airplanemode", "mobiledata", "simsettings",
            "displaysettings", "display", "soundsettings", "sound",
            "volumesettings", "notificationsettings", "notifications",
            "ringtones", "wallpaper", "themesettings",
            "lockscreen", "lockscreenandsecurity", "lockscreenpreferences",
            "screenlock", "biometrics", "biometricsettings",
            "fingerprint", "face", "iris", "smartlock",
            "privacysettings", "privacy",
            "accounts", "accountsettings", "accountsyncsettings",
            "userandaccounts", "usersettings",
            "devicecare", "devicecaremain", "maintenance",
            "batterysettings", "storagesettings", "storage",
            "datamanagement", "datasync",
            "aboutphone", "aboutdevice", "softwareupdate",
            "systemsettings", "generalsettings",
            "datetime", "dateandtime", "languageandinput",
            "localepicker", "locationsettings", "location",
            "inputcontrol", "customization", "personalization",
            "homescreen", "homesettings",
            "mode", "easysettings", "kidssettings",
            "drivingmode", "powersaving", "batterysaver",
            "advancedfeatures", "motionsettings",
            "pen", "spen", "samsungkeyboard",
            "defaultapps", "defaultapplications", "specialaccess",
            "assistandvoiceinput", "searchsettings",
            "settingsactivity", "settingsfragment",
        )

        // Shared risk markers (duplicated lightly so this handler stays self-contained).
        val DEVICE_ADMIN_CLASSES = listOf(
            "deviceadmin", "device_admin", "activeadmin", "enterpriseprivacy",
        )
        val ACCESSIBILITY_CLASSES = listOf(
            "accessibilitysettings", "accessibility", "accessibilityservice",
            "volumeaccessibility", "accessibilityactivity",
        )
        val MANAGE_APPS_CLASSES = listOf(
            "manageapplications", "installedapp", "allapps", "applicationsettings",
            "manageapps", "appmanager",
        )
        val BATTERY_PER_APP_SURFACE_CLASSES = listOf(
            "appbattery", "batterydetail", "powerusage", "advancedbattery",
        )
        val BATTERY_SYSTEM_SURFACE_CLASSES = listOf(
            "batteryoptimization", "batterysaver", "deviceoptimization", "powerallowlist",
        )
        val AUTOSTART_TOGGLE_SURFACE_CLASSES = listOf(
            "autostart", "startupmanager", "applaunch", "backgroundstart",
        )
    }
}
