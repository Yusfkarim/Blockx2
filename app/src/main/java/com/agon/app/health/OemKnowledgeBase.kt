package com.agon.app.health

import com.agon.app.battery.DeviceVendor

/** Per-skin copy for Autostart / battery survival. Only the current vendor is shown in UI. */
object OemKnowledgeBase {

    fun skinName(vendor: DeviceVendor): String = when (vendor) {
        DeviceVendor.XIAOMI, DeviceVendor.REDMI, DeviceVendor.POCO -> "MIUI / HyperOS"
        DeviceVendor.SAMSUNG -> "One UI"
        DeviceVendor.OPPO, DeviceVendor.REALME -> "ColorOS"
        DeviceVendor.VIVO -> "Funtouch / OriginOS"
        DeviceVendor.HUAWEI -> "EMUI"
        DeviceVendor.HONOR -> "MagicOS"
        DeviceVendor.ONEPLUS -> "OxygenOS / ColorOS"
        DeviceVendor.ASUS -> "ZenUI"
        DeviceVendor.NOTHING -> "Nothing OS"
        DeviceVendor.MOTOROLA -> "My UX"
        DeviceVendor.PIXEL -> "Pixel / AOSP"
        DeviceVendor.GENERIC -> "Android"
    }

    fun autostartTitle(vendor: DeviceVendor, language: String): String {
        val skin = skinName(vendor)
        return when (language) {
            "en" -> "Autostart — $skin"
            "ar" -> "التشغيل التلقائي — $skin"
            else -> "دەستپێکردنی خۆکار — $skin"
        }
    }

    fun autostartSteps(vendor: DeviceVendor, language: String): List<String> {
        val en = when (vendor) {
            DeviceVendor.XIAOMI, DeviceVendor.REDMI, DeviceVendor.POCO -> listOf(
                "Open Autostart management.",
                "Find BlockX LaAbrah and turn Autostart ON.",
                "Also set Battery saver → No restrictions.",
                "Lock the app in Recents (padlock).",
            )
            DeviceVendor.SAMSUNG -> listOf(
                "Open Device care → Battery → Background usage limits.",
                "Remove BlockX LaAbrah from Sleeping / Deep sleeping apps.",
                "Allow background activity if shown.",
            )
            DeviceVendor.OPPO, DeviceVendor.REALME, DeviceVendor.ONEPLUS -> listOf(
                "Open Startup / Autolaunch apps.",
                "Enable BlockX LaAbrah.",
                "Battery → Unrestricted / Allow background.",
            )
            DeviceVendor.VIVO -> listOf(
                "Open Background app management / Autostart.",
                "Allow BlockX LaAbrah high background power consumption.",
                "Turn on Autostart if listed.",
            )
            DeviceVendor.HUAWEI, DeviceVendor.HONOR -> listOf(
                "Open App launch / Startup manager.",
                "Set BlockX LaAbrah to Manage manually.",
                "Enable Auto-launch, Secondary launch, Run in background.",
            )
            DeviceVendor.ASUS -> listOf(
                "Open Mobile Manager → Autostart.",
                "Enable BlockX LaAbrah.",
            )
            else -> listOf(
                "Open App info → Battery → Unrestricted.",
                "Allow background activity if available.",
            )
        }
        if (language == "en") return en
        if (language == "ar") return when (vendor) {
            DeviceVendor.XIAOMI, DeviceVendor.REDMI, DeviceVendor.POCO -> listOf(
                "افتح إدارة التشغيل التلقائي.",
                "فعّل التشغيل التلقائي لـ BlockX LaAbrah.",
                "اضبط البطارية على بلا قيود.",
                "اقفل التطبيق من التطبيقات الحديثة.",
            )
            DeviceVendor.SAMSUNG -> listOf(
                "العناية بالجهاز → البطارية → حدود الاستخدام في الخلفية.",
                "أزل BlockX LaAbrah من التطبيقات النائمة.",
                "اسمح بالنشاط في الخلفية إن وُجد.",
            )
            else -> listOf(
                "افتح إعدادات التشغيل التلقائي / بدء التطبيقات.",
                "فعّل BlockX LaAbrah.",
                "اضبط البطارية على بلا قيود.",
            )
        }
        return when (vendor) {
            DeviceVendor.XIAOMI, DeviceVendor.REDMI, DeviceVendor.POCO -> listOf(
                "بەڕێوەبردنی دەستپێکردنی خۆکار بکەرەوە.",
                "BlockX LaAbrah بدۆزەرەوە و Autostart هەڵبکە.",
                "باتری → بێ سنوور (No restrictions).",
                "لە Recent Appsدا قفڵی ئەپەکە بکە.",
            )
            DeviceVendor.SAMSUNG -> listOf(
                "Device care → Battery → Background usage limits.",
                "BlockX LaAbrah لە Sleeping apps دەربهێنە.",
                "Background activity ڕێگە پێبدە.",
            )
            else -> listOf(
                "ڕێکخستنی Autostart / Startup بکەرەوە.",
                "BlockX LaAbrah چالاک بکە.",
                "باتری بکە بە Unrestricted.",
            )
        }
    }
}
