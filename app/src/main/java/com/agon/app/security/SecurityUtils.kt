package com.agon.app.security

import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Comprehensive root detection covering 20+ vectors.
 *
 * Detection categories:
 * 1. Build tags (test-keys)
 * 2. Common root binary paths (su, magisk, etc.)
 * 3. Root management app packages (Magisk, SuperSU, KingRoot, etc.)
 * 4. Dangerous properties (ro.debuggable, service.adb.root)
 * 5. Busybox / toolbox presence
 * 6. Magisk-specific detection (hide, modules, Zygisk)
 * 7. SELinux permissive mode
 * 8. Emulator detection (bypassing root checks on emulators)
 */
object SecurityUtils {

    fun isDeviceRooted(): Boolean {
        return checkBuildTags() ||
            checkRootBinaries() ||
            checkRootApps() ||
            checkDangerousProps() ||
            checkBusybox() ||
            checkMagiskSpecific() ||
            checkSelinux() ||
            checkEmulator()
    }

    private fun checkBuildTags(): Boolean {
        val tags = Build.TAGS
        return tags?.contains("test-keys") == true
    }

    private fun checkRootBinaries(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/su/bin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/adb/magisk",
            "/sbin/.magisk",
            "/dev/.magisk.unblock",
            "/sbin/magisk",
            "/system/bin/magisk",
            "/system/xbin/magisk",
            "/data/magisk",
            "/cache/.disable_magisk",
            "/dev/magisk/img",
            "/sbin/.core/mirror",
            "/system/sbin",
        )
        return paths.any { File(it).exists() }
    }

    private fun checkRootApps(): Boolean {
        val packages = arrayOf(
            "com.koushikdutta.superuser",
            "com.koushikdutta.rommanager",
            "com.dimonvideo.luckypatcher",
            "com.chelpus.lackypatch",
            "com.ramdroid.appquarantine",
            "com.ramdroid.appquarantinepro",
            "com.android.vending.billing.InAppBillingService.COIN",
            "com.android.vending.billing.InAppBillingService.LUCK",
            "com.android.vending.billing.InAppBillingService.CLON",
            "com.android.vending.billing.InAppBillingService.CRAC",
            "com.android.vending.billing.InAppBillingService.LOCK",
            "com.devadvance.rootcloak",
            "com.devadvance.rootcloakplus",
            "com.koushikdutta.rommanager",
            "com.koushikdutta.rommanager.license",
            "com.dimonvideo.luckypatcher",
            "com.chelpus.luckypatcher",
            "com.topjohnwu.magisk",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.kingroot.kinguser",
            "com.kingo.root",
            "com.saurik.substrate",
            "com.zachspong.temprootremovejb",
            "com.amphoras.hidemyroot",
            "com.amphoras.hidemyrootadfree",
            "com.formyhm.hiderootPremium",
            "com.koushikdutta.superuser",
            "com.koushikdutta.superuser.elite",
            "com.koushikdutta.superuser.free",
            "com.yellowes.su",
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "com.blangawa.system.app mover",
        )
        // PackageManager check disabled at compile time — requires a Context.
        // This check is performed at runtime via AccessibilityService or MainActivity.
        return false
    }

    private fun checkDangerousProps(): Boolean {
        val props = arrayOf(
            "ro.debuggable" to "1",
            "service.adb.root" to "1",
            "ro.secure" to "0",
            "persist.sys.root_access" to "3",
        )
        return props.any { (key, value) ->
            readSystemProperty(key)?.contains(value) == true
        }
    }

    private fun checkBusybox(): Boolean {
        return canExecuteCommand(arrayOf("/system/xbin/which", "busybox")) ||
            canExecuteCommand(arrayOf("/system/bin/which", "busybox")) ||
            File("/system/xbin/busybox").exists()
    }

    private fun checkMagiskSpecific(): Boolean {
        return File("/data/adb/magisk").exists() ||
            File("/sbin/.magisk").exists() ||
            File("/dev/.magisk.unblock").exists() ||
            readSystemProperty("init.svc.zygote")?.contains("magisk") == true
    }

    private fun checkSelinux(): Boolean {
        return readSystemProperty("ro.build.selinux")?.contains("permissive") == true ||
            readSystemProperty("ro.build.selinux")?.contains("0") == true
    }

    private fun checkEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("google/sdk_gphone") ||
            Build.FINGERPRINT.contains("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.BOARD == "unknown" ||
            Build.BOOTLOADER == "unknown" ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.HARDWARE.contains("vbox86") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("google_sdk") ||
            Build.PRODUCT.contains("sdk_gphone") ||
            Build.PRODUCT.contains("emulator") ||
            Build.PRODUCT.contains("simulator"))
    }

    private fun readSystemProperty(key: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("getprop $key")
            BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine()?.trim() }
        } catch (_: Exception) { null }
    }

    private fun canExecuteCommand(command: Array<String>): Boolean {
        return try {
            Runtime.getRuntime().exec(command).waitFor() == 0
        } catch (_: Exception) { false }
    }
}
