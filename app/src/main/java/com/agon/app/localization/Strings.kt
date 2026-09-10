package com.agon.app.localization

/**
 * Central key-based translations for the features added on top of the original screens
 * (time limits, setup wizard, health check and the strengthened uninstall protection).
 *
 * Kept separate from [appText] so existing screens are untouched. Every key resolves in all
 * three supported languages (Kurdish default, Arabic, English); an unknown key falls back to
 * the Kurdish string and finally the key itself, so the UI never shows a blank.
 */
fun tr(key: String, language: String): String {
    val table = STRINGS[key] ?: return key
    return table[language] ?: table["ku"] ?: key
}

private val STRINGS: Map<String, Map<String, String>> = mapOf(
    // ---- bottom navigation additions ----
    "nav_health" to mapOf("ku" to "تەندروستی", "ar" to "الحالة", "en" to "Health"),
    "nav_limits" to mapOf("ku" to "کات", "ar" to "الوقت", "en" to "Time"),

    // ---- time limits ----
    "time_limits" to mapOf("ku" to "سنووری کات", "ar" to "حدود الوقت", "en" to "Time Limits"),
    "time_limits_subtitle" to mapOf(
        "ku" to "سنووری ڕۆژانە بۆ ئەپەکان دابنێ",
        "ar" to "ضع حدوداً يومية للتطبيقات",
        "en" to "Set daily limits for apps",
    ),
    "add_limit" to mapOf("ku" to "زیادکردنی سنوور", "ar" to "إضافة حد", "en" to "Add limit"),
    "no_limits" to mapOf("ku" to "هیچ سنوورێک دانەنراوە", "ar" to "لا توجد حدود", "en" to "No limits set"),
    "no_limits_hint" to mapOf(
        "ku" to "بۆ ئەپێک وەک یوتیوب سنووری کاتی ڕۆژانە دابنێ",
        "ar" to "ضع حداً يومياً لتطبيق مثل يوتيوب",
        "en" to "Set a daily limit for an app like YouTube",
    ),
    "choose_app" to mapOf("ku" to "ئەپێک هەڵبژێرە", "ar" to "اختر تطبيقاً", "en" to "Choose an app"),
    "search_apps" to mapOf("ku" to "گەڕان بۆ ئەپەکان", "ar" to "ابحث عن التطبيقات", "en" to "Search apps"),
    "daily_limit" to mapOf("ku" to "سنووری ڕۆژانە", "ar" to "الحد اليومي", "en" to "Daily limit"),
    "minutes" to mapOf("ku" to "خولەک", "ar" to "دقيقة", "en" to "minutes"),
    "minutes_left" to mapOf("ku" to "%d خولەک ماوە", "ar" to "بقيت %d دقيقة", "en" to "%d min left"),
    "limit_reached" to mapOf("ku" to "کاتەکە تەواو بوو", "ar" to "انتهى الوقت", "en" to "Limit reached"),
    "add_time_today" to mapOf("ku" to "کاتی زیاتر ئەمڕۆ", "ar" to "وقت إضافي اليوم", "en" to "More time today"),
    "remove" to mapOf("ku" to "لابردن", "ar" to "إزالة", "en" to "Remove"),
    "cancel" to mapOf("ku" to "پاشگەزبوونەوە", "ar" to "إلغاء", "en" to "Cancel"),
    "save" to mapOf("ku" to "پاشەکەوتکردن", "ar" to "حفظ", "en" to "Save"),

    // ---- health check ----
    "health_title" to mapOf("ku" to "دۆخی پاراستن", "ar" to "حالة الحماية", "en" to "Protection Health"),
    "health_subtitle" to mapOf(
        "ku" to "دڵنیابە هەموو پاراستنەکان چالاکن",
        "ar" to "تأكد أن جميع وسائل الحماية مفعّلة",
        "en" to "Make sure every protection is active",
    ),
    "health_all_good" to mapOf(
        "ku" to "هەموو پاراستنەکان چالاکن",
        "ar" to "جميع وسائل الحماية مفعّلة",
        "en" to "All protections are active",
    ),
    "health_issues" to mapOf(
        "ku" to "هەندێک پاراستن پێویستی بە ڕێکخستنە",
        "ar" to "بعض وسائل الحماية تحتاج إعداداً",
        "en" to "Some protections need attention",
    ),
    "health_fix" to mapOf("ku" to "چاککردن", "ar" to "إصلاح", "en" to "Fix"),
    "health_active" to mapOf("ku" to "چالاکە", "ar" to "مفعّل", "en" to "Active"),
    "health_inactive" to mapOf("ku" to "ناچالاکە", "ar" to "غير مفعّل", "en" to "Inactive"),
    "health_required" to mapOf("ku" to "پێویستە — تەواو نەکراوە", "ar" to "مطلوب — غير مكتمل", "en" to "Required — incomplete"),
    "health_score" to mapOf("ku" to "ئاستی پاراستن", "ar" to "مستوى الحماية", "en" to "Protection level"),

    "check_vpn" to mapOf("ku" to "سپیەری تۆڕ", "ar" to "درع التصفح الآمن", "en" to "Safe Browsing Shield"),
    "check_vpn_desc" to mapOf("ku" to "پاراستنی وێب و تۆڕ", "ar" to "حماية الويب والاتصال", "en" to "Web and connection protection"),
    "check_accessibility" to mapOf("ku" to "خزمەتگوزاری Accessibility", "ar" to "خدمة إمكانية الوصول", "en" to "Accessibility service"),
    "check_accessibility_desc" to mapOf("ku" to "پاراستنی ئەپ و وشەی کلیلی", "ar" to "حماية التطبيقات والكلمات", "en" to "App and keyword protection"),
    "check_admin" to mapOf("ku" to "مۆڵەتی بەڕێوەبەر", "ar" to "صلاحية المدير", "en" to "Device admin"),
    "check_admin_desc" to mapOf("ku" to "پاراستن لە لابردن", "ar" to "الحماية من الحذف", "en" to "Uninstall protection"),
    "check_battery" to mapOf("ku" to "ئۆپتیمایزی باتری", "ar" to "تحسين البطارية", "en" to "Battery optimization"),
    "check_battery_desc" to mapOf("ku" to "کارکردن لە پاشبنەما", "ar" to "العمل في الخلفية", "en" to "Background execution"),
    "check_notifications" to mapOf("ku" to "ئاگادارکردنەوەکان", "ar" to "الإشعارات", "en" to "Notifications"),
    "check_notifications_desc" to mapOf("ku" to "ئاگاداری دۆخی پاراستن", "ar" to "تنبيهات حالة الحماية", "en" to "Protection status alerts"),
    "check_autostart" to mapOf("ku" to "دەستپێکردنی خۆکار", "ar" to "التشغيل التلقائي", "en" to "Autostart"),
    "check_autostart_desc" to mapOf(
        "ku" to "دوای کوژاندنەوەی OEM دووبارە دەست پێ بکات",
        "ar" to "إعادة التشغيل بعد إيقاف النظام للتطبيق",
        "en" to "Survive OEM background killers",
    ),
    "check_exact_alarm" to mapOf("ku" to "زەنگی ورد (Exact alarm)", "ar" to "منبه دقيق", "en" to "Exact alarms"),
    "check_exact_alarm_desc" to mapOf(
        "ku" to "بۆ زیندووکردنەوەی پاراستن لە کاتی دیاریکراو",
        "ar" to "لإعادة تشغيل الحماية في الوقت المحدد",
        "en" to "Keeps protection revival on schedule",
    ),
    "check_guardian" to mapOf("ku" to "خزمەتگوزاری پاراستن", "ar" to "خدمة الحماية", "en" to "Guardian service"),
    "check_guardian_desc" to mapOf(
        "ku" to "Foreground serviceی هەمیشە-لەکار",
        "ar" to "خدمة المقدمة الدائمة",
        "en" to "Always-on foreground supervisor",
    ),
    "check_keyword_engine" to mapOf("ku" to "مۆتۆری وشە", "ar" to "محرك الكلمات", "en" to "Keyword engine"),
    "check_keyword_engine_desc" to mapOf(
        "ku" to "لیستی وشە و BlockEngine ئامادەن",
        "ar" to "قائمة الكلمات والمحرك جاهزان",
        "en" to "Keyword list and engine ready",
    ),
    "health_test_title" to mapOf("ku" to "تاقیکردنەوەی ڕاستەقینە", "ar" to "اختبار حقيقي", "en" to "Live protection test"),
    "health_test_desc" to mapOf(
        "ku" to "پشکنینی مۆتۆری وشە + Accessibility — نەک تەنها مۆڵەت.",
        "ar" to "يفحص محرك الكلمات وإمكانية الوصول — وليس الأذونات فقط.",
        "en" to "Checks keyword engine + accessibility — not permissions only.",
    ),
    "health_test_run" to mapOf("ku" to "تاقیکردنەوەی پاراستن", "ar" to "اختبار الحماية", "en" to "Test protection"),
    "health_test_ok" to mapOf("ku" to "سەرکەوتوو — بلۆک ئامادەیە", "ar" to "نجاح — الحظر جاهز", "en" to "Passed — blocking is ready"),
    "health_test_fail" to mapOf("ku" to "شکست — پاراستن تەواو نییە", "ar" to "فشل — الحماية غير مكتملة", "en" to "Failed — protection incomplete"),
    "health_diagnostic" to mapOf("ku" to "ڕاپۆرتی دۆخی تەکنیکی", "ar" to "تقرير تشخيصي", "en" to "Diagnostic report"),
    "health_report_copied" to mapOf("ku" to "ڕاپۆرت کۆپی/هاوبەش کرا", "ar" to "تم نسخ/مشاركة التقرير", "en" to "Report copied / shared"),
    "step_autostart_title" to mapOf("ku" to "دەستپێکردنی خۆکار", "ar" to "التشغيل التلقائي", "en" to "Autostart"),
    "step_autostart_desc" to mapOf(
        "ku" to "لەسەر ئەم مۆبایلە دەستپێکردنی خۆکار چالاک بکە تا پاراستن نەمرێت.",
        "ar" to "فعّل التشغيل التلقائي على هذا الهاتف حتى لا تتوقف الحماية.",
        "en" to "Enable autostart on this phone so protection is not killed.",
    ),
    "step_autostart_confirm" to mapOf(
        "ku" to "چالاکم کرد — بەردەوامبە",
        "ar" to "لقد فعّلته — متابعة",
        "en" to "I enabled it — continue",
    ),

    // ---- uninstall protection strength ----
    "protection_level" to mapOf("ku" to "ئاستی پاراستن لە لابردن", "ar" to "مستوى الحماية من الحذف", "en" to "Uninstall protection level"),
    "level_basic" to mapOf("ku" to "بنەڕەتی", "ar" to "أساسي", "en" to "Basic"),
    "level_basic_desc" to mapOf(
        "ku" to "تەنها بە Accessibility پارێزراوە. بۆ پاراستنی بەهێزتر مۆڵەتی بەڕێوەبەر چالاک بکە.",
        "ar" to "محمي عبر إمكانية الوصول فقط. فعّل صلاحية المدير لحماية أقوى.",
        "en" to "Protected by accessibility only. Enable device admin for stronger protection.",
    ),
    "level_admin" to mapOf("ku" to "بەهێز (بەڕێوەبەر)", "ar" to "قوي (مدير)", "en" to "Strong (Admin)"),
    "level_admin_desc" to mapOf(
        "ku" to "مۆڵەتی بەڕێوەبەر چالاکە. لابردن پێویستی بە PIN هەیە.",
        "ar" to "صلاحية المدير مفعّلة. الحذف يتطلب رمز PIN.",
        "en" to "Device admin active. Uninstall requires the PIN.",
    ),
    "level_owner" to mapOf("ku" to "زۆر بەهێز (Device Owner)", "ar" to "قوي جداً (مالك الجهاز)", "en" to "Maximum (Device Owner)"),
    "level_owner_desc" to mapOf(
        "ku" to "سیستەم خۆی ڕێگری لە لابردن دەکات. بەهێزترین ئاستی پاراستن.",
        "ar" to "النظام نفسه يمنع الحذف. أقوى مستوى حماية.",
        "en" to "The system itself blocks uninstall. The strongest protection.",
    ),
    // ---- setup wizard ----
    "wizard_title" to mapOf("ku" to "ڕێکخستنی پاراستن", "ar" to "إعداد الحماية", "en" to "Set up protection"),
    "wizard_welcome_title" to mapOf("ku" to "بەخێربێیت بۆ BlockX LaAbrah", "ar" to "مرحباً بك في BlockX LaAbrah", "en" to "Welcome to BlockX LaAbrah"),
    "wizard_welcome_desc" to mapOf(
        "ku" to "بۆ تەواوکردنی پاراستن، پێویستە چەند مۆڵەتێک چالاک بکەیت. ئەم ڕێبەرە هەنگاو بە هەنگاو یارمەتیت دەدات.",
        "ar" to "لإكمال الحماية، عليك تفعيل بعض الأذونات. سيرشدك هذا الدليل خطوة بخطوة.",
        "en" to "To complete protection, you need to enable a few permissions. This guide walks you through each step.",
    ),
    "wizard_start" to mapOf("ku" to "دەستپێکردن", "ar" to "ابدأ", "en" to "Get started"),
    "wizard_step" to mapOf("ku" to "هەنگاو", "ar" to "خطوة", "en" to "Step"),
    "wizard_of" to mapOf("ku" to "لە", "ar" to "من", "en" to "of"),
    "wizard_next" to mapOf("ku" to "دواتر", "ar" to "التالي", "en" to "Next"),
    "wizard_skip" to mapOf("ku" to "تێپەڕاندن", "ar" to "تخطٍّ", "en" to "Skip"),
    "wizard_finish" to mapOf("ku" to "تەواوکردن", "ar" to "إنهاء", "en" to "Finish"),
    "wizard_done" to mapOf("ku" to "پاراستن ئامادەیە", "ar" to "الحماية جاهزة", "en" to "Protection is ready"),
    "wizard_done_desc" to mapOf(
        "ku" to "تەواو بوو! ئێستا BlockX LaAbrah ئامێرەکەت دەپارێزێت.",
        "ar" to "اكتمل الإعداد! يحمي BlockX LaAbrah جهازك الآن.",
        "en" to "Setup is complete. BlockX LaAbrah is now protecting your device.",
    ),
    "wizard_enable" to mapOf("ku" to "چالاککردن", "ar" to "تفعيل", "en" to "Enable"),
    "wizard_enabled" to mapOf("ku" to "چالاک کراوە، بڕۆ بۆ هەنگاوی دواتر", "ar" to "تم التفعيل، انتقل إلى الخطوة التالية", "en" to "Enabled — continue to the next step"),
    "wizard_required_hint" to mapOf(
        "ku" to "ئەم مۆڵەتە پێویستە و تا چالاک نەکرێت ناتوانیت بچیتە هەنگاوی دواتر.",
        "ar" to "هذا الإذن مطلوب، ولا يمكنك الانتقال إلى الخطوة التالية حتى تفعّله.",
        "en" to "This permission is required. You cannot continue until it is enabled.",
    ),
    "open_setup" to mapOf("ku" to "کردنەوەی ڕێبەری ڕێکخستن", "ar" to "فتح دليل الإعداد", "en" to "Open setup guide"),

    "setup_credential_title" to mapOf("ku" to "ڕەمزی پاراستن دابنێ", "ar" to "إعداد بيانات الحماية", "en" to "Create protection credentials"),
    "setup_credential_desc" to mapOf(
        "ku" to "PIN یان ناوی بەکارهێنەر و وشەی نهێنی هەڵبژێرە. ئەم زانیارییە بۆ گۆڕینی ڕێکخستنە پارێزراوەکان پێویستە.",
        "ar" to "اختر رمز PIN أو اسم مستخدم وكلمة مرور. ستحتاج هذه البيانات لتغيير الإعدادات المحمية.",
        "en" to "Choose a PIN or a username and password. These credentials are required to change protected settings.",
    ),
    "setup_credential_warning" to mapOf(
        "ku" to "ئاگاداری: ڕەمزەکەت لە شوێنێکی پارێزراو هەڵبگرە. ئەگەر لەبیرت بچێتەوە، ناتوانیت ڕێکخستنە پارێزراوەکان بگۆڕیت.",
        "ar" to "تنبيه: احفظ بياناتك في مكان آمن. إذا نسيتها فلن تتمكن من تغيير الإعدادات المحمية.",
        "en" to "Important: save your credentials somewhere secure. If you forget them, you cannot change protected settings.",
    ),
    "setup_method_pin" to mapOf("ku" to "PIN", "ar" to "رمز PIN", "en" to "PIN"),
    "setup_method_username" to mapOf("ku" to "ناو و وشەی نهێنی", "ar" to "اسم مستخدم وكلمة مرور", "en" to "Username & password"),
    "setup_username" to mapOf("ku" to "ناوی بەکارهێنەر", "ar" to "اسم المستخدم", "en" to "Username"),
    "setup_pin" to mapOf("ku" to "PIN", "ar" to "رمز PIN", "en" to "PIN"),
    "setup_password" to mapOf("ku" to "وشەی نهێنی", "ar" to "كلمة المرور", "en" to "Password"),
    "setup_confirm_secret" to mapOf("ku" to "دووبارە نووسینەوەی ڕەمز", "ar" to "تأكيد الرمز أو كلمة المرور", "en" to "Confirm credential"),
    "setup_back" to mapOf("ku" to "گەڕانەوە", "ar" to "رجوع", "en" to "Back"),
    "setup_save_credential" to mapOf("ku" to "پاشەکەوتکردن", "ar" to "حفظ البيانات", "en" to "Save credentials"),
    "setup_credential_ready" to mapOf("ku" to "ڕەمزی پاراستن ئامادەیە", "ar" to "بيانات الحماية جاهزة", "en" to "Protection credentials are ready"),
    "setup_credential_ready_desc" to mapOf("ku" to "بە سەرکەوتوویی پاشەکەوت کرا، بڕۆ بۆ هەنگاوی دواتر.", "ar" to "تم الحفظ بنجاح، انتقل إلى الخطوة التالية.", "en" to "Saved successfully — continue to the next step."),
    "setup_credential_invalid" to mapOf("ku" to "ڕەمز دەبێت ٤ بۆ ٢٠ پیت بێت و ناو دەبێت ٣ بۆ ٢٠ پیت بێت.", "ar" to "يجب أن يكون الرمز من 4 إلى 20 حرفاً واسم المستخدم من 3 إلى 20 حرفاً.", "en" to "The credential must be 4–20 characters and the username 3–20 characters."),
    "setup_credential_mismatch" to mapOf("ku" to "دوو ڕەمزەکە وەک یەک نین.", "ar" to "القيمتان غير متطابقتين.", "en" to "The credentials do not match."),
    "setup_credential_storage_error" to mapOf("ku" to "کۆگای پارێزراو بەردەست نییە.", "ar" to "التخزين الآمن غير متاح.", "en" to "Secure storage is unavailable."),
    "setup_credential_error" to mapOf("ku" to "زانیارییەکان پاشەکەوت نەکران.", "ar" to "تعذر حفظ البيانات.", "en" to "The credentials could not be saved."),

    "step_accessibility_title" to mapOf("ku" to "پاراستنی ئەپ و وشە", "ar" to "حماية التطبيقات والكلمات", "en" to "App & keyword protection"),
    "step_accessibility_desc" to mapOf(
        "ku" to "خزمەتگوزاری Accessibility چالاک بکە بۆ پشکنینی ئەپ و وشە نەگونجاوەکان.",
        "ar" to "فعّل خدمة إمكانية الوصول لفحص التطبيقات والكلمات غير المناسبة.",
        "en" to "Turn on the accessibility service to inspect apps and inappropriate keywords.",
    ),
    "step_admin_title" to mapOf("ku" to "پاراستن لە لابردن", "ar" to "الحماية من الحذف", "en" to "Uninstall protection"),
    "step_admin_desc" to mapOf(
        "ku" to "مۆڵەتی بەڕێوەبەر چالاک بکە تاکو ئەپەکە بەبێ پشتڕاستکردنەوە نەسڕدرێتەوە.",
        "ar" to "فعّل صلاحية المدير لمنع حذف التطبيق من دون التحقق من بيانات الحماية.",
        "en" to "Enable device admin to prevent uninstalling the app without credential verification.",
    ),
    "step_battery_title" to mapOf("ku" to "کارکردن لە پاشبنەما", "ar" to "العمل في الخلفية", "en" to "Background execution"),
    "step_battery_desc" to mapOf(
        "ku" to "ئۆپتیمایزی باتری ناچالاک بکە تاکو پاراستن هەمیشە کاربکات.",
        "ar" to "أوقف تحسين البطارية حتى تعمل الحماية دائماً.",
        "en" to "Disable battery optimization so protection always runs.",
    ),
    "step_notifications_title" to mapOf("ku" to "مۆڵەتی ئاگادارکردنەوە", "ar" to "إذن الإشعارات", "en" to "Notification permission"),
    "step_notifications_desc" to mapOf(
        "ku" to "مۆڵەتی ئاگادارکردنەوە بدە تاکو دۆخی پاراستن و هەوڵە بلۆککراوەکان ببینیت.",
        "ar" to "امنح إذن الإشعارات لعرض حالة الحماية وتنبيهات المحاولات المحظورة.",
        "en" to "Allow notifications to see protection status and blocked-attempt alerts.",
    ),

    // ---- app lock (whole-app gate) ----
    "app_lock" to mapOf("ku" to "پاراستنی چوونە ژوورەوەی ئەپ", "ar" to "قفل الدخول للتطبيق", "en" to "App Lock"),
    "app_lock_desc" to mapOf(
        "ku" to "پێش کردنەوەی ئەپەکە داوای PIN یان ووشەی نهێنی بکە",
        "ar" to "اطلب رمز PIN أو كلمة المرور قبل فتح التطبيق",
        "en" to "Require a PIN or password before the app opens",
    ),
    "app_lock_title" to mapOf("ku" to "BlockX LaAbrah قفڵە", "ar" to "BlockX LaAbrah مقفل", "en" to "BlockX LaAbrah is locked"),
    "app_lock_subtitle" to mapOf(
        "ku" to "بۆ کردنەوە ڕەمزەکەت بنووسە",
        "ar" to "أدخل رمزك للفتح",
        "en" to "Enter your code to continue",
    ),
    "app_lock_wrong" to mapOf("ku" to "ڕەمزەکە هەڵەیە، دووبارە هەوڵبدەرەوە", "ar" to "الرمز غير صحيح، حاول مجدداً", "en" to "Wrong code, try again"),
    "app_lock_needs_pin" to mapOf(
        "ku" to "سەرەتا لە بەشی «پاراستنی ڕێکخستنەکان» PIN یان ووشەی نهێنی دابنێ",
        "ar" to "أولاً عيّن رمز PIN أو كلمة مرور من قسم «حماية الإعدادات»",
        "en" to "First set a PIN or password in the Settings Protection section",
    ),
    "pin" to mapOf("ku" to "PIN", "ar" to "الرمز", "en" to "PIN"),
    "password" to mapOf("ku" to "ووشەی نهێنی", "ar" to "كلمة المرور", "en" to "Password"),
    "username" to mapOf("ku" to "ناوی بەکارهێنەر", "ar" to "اسم المستخدم", "en" to "Username"),
    "unlock" to mapOf("ku" to "کردنەوە", "ar" to "فتح", "en" to "Unlock"),
    "use_biometric" to mapOf("ku" to "بەکارهێنانی پەنجەمۆر", "ar" to "استخدام البصمة", "en" to "Use fingerprint"),

    // ---- usage report dashboard ----
    "reports_title" to mapOf("ku" to "ڕاپۆرتی بەکارهێنان", "ar" to "تقرير الاستخدام", "en" to "Usage report"),
    "reports_subtitle" to mapOf(
        "ku" to "ئامارەکانی پاراستن بەپێی ڕۆژ و هەفتە",
        "ar" to "إحصاءات الحماية اليومية والأسبوعية",
        "en" to "Daily and weekly protection statistics",
    ),
    "reports_today" to mapOf("ku" to "ئەمڕۆ", "ar" to "اليوم", "en" to "Today"),
    "reports_this_week" to mapOf("ku" to "ئەم حەفتەیە", "ar" to "هذا الأسبوع", "en" to "This week"),
    "reports_all_time" to mapOf("ku" to "کۆی گشتی", "ar" to "الإجمالي", "en" to "All time"),
    "reports_blocked_attempts" to mapOf("ku" to "هەوڵە بلۆککراوەکان", "ar" to "محاولات الحظر", "en" to "Blocked attempts"),
    "reports_last_7_days" to mapOf("ku" to "٧ ڕۆژی ڕابردوو", "ar" to "آخر ٧ أيام", "en" to "Last 7 days"),
    "reports_by_source" to mapOf("ku" to "بەپێی سەرچاوە", "ar" to "حسب المصدر", "en" to "By source"),
    "reports_top_blocked" to mapOf("ku" to "زۆرترین بلۆککراوەکان", "ar" to "الأكثر حظراً", "en" to "Most blocked"),
    "reports_top_apps" to mapOf("ku" to "ئەپە بەکارهێنراوەکان (ئەمڕۆ)", "ar" to "التطبيقات المستخدمة (اليوم)", "en" to "Apps used today"),
    "reports_no_usage" to mapOf(
        "ku" to "هێشتا داتای بەکارهێنان نییە؛ سنووری کات بۆ ئەپەکان دابنێ بۆ پشکنین",
        "ar" to "لا توجد بيانات استخدام بعد؛ عيّن حدوداً زمنية لتتبع التطبيقات",
        "en" to "No usage data yet; set app time limits to start tracking",
    ),
    "reports_minutes_used" to mapOf("ku" to "خولەک", "ar" to "دقيقة", "en" to "min"),
    "reports_empty" to mapOf("ku" to "هێشتا هیچ ڕووداوێک تۆمار نەکراوە", "ar" to "لا توجد أحداث مسجلة بعد", "en" to "Nothing recorded yet"),
    "reports_empty_hint" to mapOf(
        "ku" to "کاتێک پاراستن شتێک بلۆک دەکات، لێرە دەردەکەوێت",
        "ar" to "عندما يحظر الحماية شيئاً، سيظهر هنا",
        "en" to "Blocked attempts will appear here",
    ),
    "filtered_count" to mapOf("ku" to "ژمارە: %d", "ar" to "العدد: %d", "en" to "Count: %d"),

    // ---- block schedules (bedtime / study / prayer) ----
    "schedule_section" to mapOf("ku" to "خشتەی کاتی بلۆککردن", "ar" to "جدول الحظر", "en" to "Block schedule"),
    "schedule_section_sub" to mapOf(
        "ku" to "لە کاتە دیاریکراوەکاندا هەموو ئەپەکان ئۆتۆماتیک دادەخرێن",
        "ar" to "تُغلق التطبيقات تلقائياً في أوقات محددة",
        "en" to "Apps close automatically during set times",
    ),
    "schedule_add" to mapOf("ku" to "خشتەی نوێ", "ar" to "جدول جديد", "en" to "Add schedule"),
    "schedule_empty" to mapOf("ku" to "هیچ خشتەیەک دانەنراوە", "ar" to "لا يوجد جدول", "en" to "No schedules set"),
    "schedule_empty_hint" to mapOf(
        "ku" to "بۆ نموونە کاتی خەوتن «٢٢:٠٠ – ٠٧:٠٠» زیاد بکە",
        "ar" to "أضف مثلاً وقت النوم «٢٢:٠٠ – ٠٧:٠٠»",
        "en" to "For example add bedtime 22:00 – 07:00",
    ),
    "schedule_name" to mapOf("ku" to "ناوی خشتە", "ar" to "اسم الجدول", "en" to "Schedule name"),
    "schedule_from" to mapOf("ku" to "لە کاتژمێر", "ar" to "من الساعة", "en" to "From"),
    "schedule_to" to mapOf("ku" to "بۆ کاتژمێر", "ar" to "إلى الساعة", "en" to "To"),
    "schedule_days" to mapOf("ku" to "ڕۆژەکان", "ar" to "الأيام", "en" to "Days"),
    "schedule_every_day" to mapOf("ku" to "هەموو ڕۆژێک", "ar" to "كل يوم", "en" to "Every day"),
    "schedule_preset_bedtime" to mapOf("ku" to "کاتی خەوتن", "ar" to "وقت النوم", "en" to "Bedtime"),
    "schedule_preset_study" to mapOf("ku" to "کاتی خوێندن", "ar" to "وقت الدراسة", "en" to "Study time"),
    "schedule_preset_prayer" to mapOf("ku" to "کاتی نوێژ", "ar" to "وقت الصلاة", "en" to "Prayer time"),
    "schedule_active_now" to mapOf("ku" to "ئێستا چالاکە", "ar" to "نشط الآن", "en" to "Active now"),
    "schedule_add_warning_title" to mapOf(
        "ku" to "ئاگاداری",
        "ar" to "تنبيه",
        "en" to "Warning",
    ),
    "schedule_add_warning_body" to mapOf(
        "ku" to "ئاگاداری: لە ماوەی بلۆککردنە دیاریکراوەکەدا ناتوانیت مۆبایلەکە بەکاربهێنیت بۆ دەستگەیشتن بە ناوەڕۆک یان ئەو ئەپانەی بلۆکەکە دەیانگرێتەوە. دڵنیابە لە هەڵبژاردنی کاتی گونجاو پێش بەردەوامبوون.",
        "ar" to "تنبيه: خلال فترة الحظر المحددة لن تتمكن من استخدام الهاتف للوصول إلى المحتوى أو التطبيقات التي يشملها الحظر. تأكد من اختيار الوقت المناسب قبل المتابعة.",
        "en" to "Warning: during the selected blocking period you will not be able to use the phone to access the content or apps covered by the block. Make sure you choose a suitable time before continuing.",
    ),
    "schedule_add_warning_confirm" to mapOf(
        "ku" to "بەردەوامبوون",
        "ar" to "متابعة",
        "en" to "Continue",
    ),
    "schedule_max_duration_hint" to mapOf(
        "ku" to "زۆرترین ماوەی بلۆککردن: ١٠ کاتژمێر",
        "ar" to "الحد الأقصى لمدة الحظر: 10 ساعات",
        "en" to "Maximum block duration: 10 hours",
    ),
    "schedule_duration_invalid" to mapOf(
        "ku" to "کاتی هەڵبژێردراو نادروستە (١ خولەک بۆ ١٠ کاتژمێر)",
        "ar" to "الوقت المحدد غير صالح (من دقيقة واحدة إلى 10 ساعات)",
        "en" to "Selected window is invalid (1 minute to 10 hours)",
    ),
    "schedule_edit_locked" to mapOf(
        "ku" to "قوفڵکراوە بۆ گۆڕین و سڕینەوە",
        "ar" to "مقفل ضد التعديل والحذف",
        "en" to "Locked from editing and deleting",
    ),
    "schedule_edit_locked_remaining" to mapOf(
        "ku" to "ماوەی کراوەبوون:",
        "ar" to "الوقت المتبقي للفتح:",
        "en" to "Unlocks in:",
    ),
    "schedule_edit_locked_active" to mapOf(
        "ku" to "لە کاتی چالاکیدا ناتوانرێت بگۆڕدرێت — چاوەڕێی تەواوبوونی کاتەکە بکە",
        "ar" to "لا يمكن التعديل أثناء فترة الحظر النشطة — انتظر حتى انتهائها",
        "en" to "Cannot change during an active block — wait until it ends",
    ),
    "limit_delete_locked_title" to mapOf(
        "ku" to "قوفڵکراوە بۆ سڕینەوە",
        "ar" to "مقفل ضد الحذف",
        "en" to "Locked from deleting",
    ),
    "schedule_delete_locked_title" to mapOf(
        "ku" to "قوفڵکراوە بۆ سڕینەوە",
        "ar" to "مقفل ضد الحذف",
        "en" to "Locked from deleting",
    ),
    "schedule_delete_locked_body" to mapOf(
        "ku" to "ناتوانیت خشتەی «%s» بسڕیتەوە پێش تەواوبوونی ٤٨ کاتژمێر لە دروستکردنی.",
        "ar" to "لا يمكنك حذف جدول «%s» قبل مرور 48 ساعة من إنشائه.",
        "en" to "You cannot delete the schedule \"%s\" before 48 hours have passed since it was created.",
    ),
    "blocklist_delete_locked_title" to mapOf(
        "ku" to "قوفڵکراوە بۆ سڕینەوە",
        "ar" to "مقفل ضد الحذف",
        "en" to "Locked from deleting",
    ),
    "blocklist_delete_locked_body" to mapOf(
        "ku" to "ناتوانیت «%s» بسڕیتەوە پێش تەواوبوونی ٤٨ کاتژمێر لە زیادکردنی.",
        "ar" to "لا يمكنك حذف «%s» قبل مرور 48 ساعة من إضافته.",
        "en" to "You cannot delete \"%s\" before 48 hours have passed since it was added.",
    ),
    "limit_delete_locked_body" to mapOf(
        "ku" to "ناتوانیت سنووری کاتی «%s» بسڕیتەوە پێش تەواوبوونی ٤٨ کاتژمێر لە دروستکردنی.",
        "ar" to "لا يمكنك حذف حد الوقت لتطبيق «%s» قبل مرور 48 ساعة من إنشائه.",
        "en" to "You cannot delete the time limit for \"%s\" before 48 hours have passed since it was created.",
    ),
    "ok" to mapOf("ku" to "باشە", "ar" to "حسناً", "en" to "OK"),
    "schedule_duration_label" to mapOf(
        "ku" to "ماوەی بلۆککردن",
        "ar" to "مدة الحظر",
        "en" to "Block duration",
    ),
    "schedule_urge" to mapOf(
        "ku" to "ئارەزووی کتوپڕ",
        "ar" to "الرغبة الملحة",
        "en" to "Urgent craving",
    ),
    "schedule_urge_sub" to mapOf(
        "ku" to "بلۆکی دەستبەجێ بۆ بەرگرتن لە ئارەزووی کتوپڕ",
        "ar" to "حظر فوري قصير لمقاومة الرغبة الملحة",
        "en" to "Short instant block to resist a sudden urge",
    ),
    "schedule_urge_duration" to mapOf(
        "ku" to "ماوە (خولەک)",
        "ar" to "المدة (دقائق)",
        "en" to "Duration (minutes)",
    ),
    "schedule_urge_range_hint" to mapOf(
        "ku" to "لە ٣ بۆ ٣٠ خولەک",
        "ar" to "من 3 إلى 30 دقيقة",
        "en" to "From 3 to 30 minutes",
    ),
    "schedule_urge_start" to mapOf(
        "ku" to "دەستپێکردنی بلۆک",
        "ar" to "ابدأ الحظر الآن",
        "en" to "Start blocking now",
    ),
    "schedule_urge_warning" to mapOf(
        "ku" to "لە ماوەی بلۆکەکەدا ناتوانیت ئەپ و ناوەڕۆکەکان بەکاربهێنیت تا کاتەکە تەواو دەبێت.",
        "ar" to "خلال مدة الحظر لن تتمكن من استخدام التطبيقات والمحتوى حتى انتهاء الوقت.",
        "en" to "While the block is active you cannot use apps and content until the time ends.",
    ),
    "schedule_starts_in" to mapOf(
        "ku" to "دوای ئەمە دەست پێدەکات",
        "ar" to "يبدأ بعد",
        "en" to "Starts in",
    ),
    "schedule_time_left" to mapOf(
        "ku" to "کاتی ماوە",
        "ar" to "الوقت المتبقي",
        "en" to "Time left",
    ),
    "schedule_advice_title" to mapOf(
        "ku" to "ئامۆژگاری",
        "ar" to "نصيحة",
        "en" to "Advice",
    ),
    "block_ended" to mapOf(
        "ku" to "کاتی بلۆک تەواو بوو",
        "ar" to "انتهى وقت الحظر",
        "en" to "The block has ended",
    ),
    "time_am" to mapOf("ku" to "ب.ن", "ar" to "ص", "en" to "AM"),
    "time_pm" to mapOf("ku" to "د.ن", "ar" to "م", "en" to "PM"),
    "time_hour" to mapOf("ku" to "کاتژمێر", "ar" to "الساعة", "en" to "Hour"),
    "time_minute" to mapOf("ku" to "خولەک", "ar" to "الدقيقة", "en" to "Minute"),
    "time_period" to mapOf("ku" to "کات", "ar" to "الفترة", "en" to "Period"),

    // ---- motivation / protection streak (dashboard) ----
    "streak_title" to mapOf("ku" to "بەردەوامی و هاندان", "ar" to "التحفيز والاستمرار", "en" to "Motivation & streak"),
    "streak_subtitle" to mapOf(
        "ku" to "ژمارەی ڕۆژانی پاراستنی بەردەوامت",
        "ar" to "عدد أيام استمرارك في الحماية",
        "en" to "Your days of continuous protection",
    ),
    "streak_current" to mapOf("ku" to "ئێستا", "ar" to "الحالي", "en" to "Current"),
    "streak_best" to mapOf("ku" to "باشترین", "ar" to "الأفضل", "en" to "Best"),
    "streak_days_short" to mapOf("ku" to "ڕۆژ", "ar" to "يوم", "en" to "days"),
    "streak_achievements" to mapOf("ku" to "دەستکەوتەکان", "ar" to "الإنجازات", "en" to "Achievements"),
    "streak_advice_title" to mapOf("ku" to "ئامۆژگاری ئەمڕۆ", "ar" to "نصيحة اليوم", "en" to "Today's advice"),
    "streak_restart" to mapOf("ku" to "دەستپێکردنەوە", "ar" to "إعادة البدء", "en" to "Restart"),
    "streak_restart_title" to mapOf("ku" to "دەستپێکردنەوەی ژمێرەر", "ar" to "إعادة بدء العدّاد", "en" to "Restart the counter"),
    "streak_restart_body" to mapOf(
        "ku" to "ژمێرەری بەردەوامیت دەگەڕێتەوە بۆ سفر و لە ئەمڕۆوە دەست پێدەکاتەوە. باشترین ڕیکۆردت دەپارێزرێت.",
        "ar" to "سيعود عدّاد الاستمرار إلى الصفر ويبدأ من اليوم. سيتم الاحتفاظ بأفضل رقم قياسي لديك.",
        "en" to "Your streak counter will reset to zero and start again from today. Your best record is kept.",
    ),
    "streak_restart_confirm" to mapOf("ku" to "دەستپێبکەوە", "ar" to "إعادة البدء", "en" to "Restart"),

    // ---- scheduled quiet-time blocked screen ----
    "block_schedule_title" to mapOf("ku" to "کاتی ڕێکخراو", "ar" to "وقت مجدول", "en" to "Quiet time"),
    "block_schedule_message" to mapOf(
        "ku" to "«%1\$s» لە ماوەی «%2\$s» بلۆککراوە. تا کۆتایی ماوەکە دووبارە هەوڵ بدەرەوە.",
        "ar" to "«%1\$s» محظور خلال «%2\$s». حاول مجدداً بعد انتهاء الوقت.",
        "en" to "\"%1\$s\" is blocked during \"%2\$s\". Try again when the window ends.",
    ),

    // ---- new app quarantine ----
    "quarantine_title" to mapOf("ku" to "قەرەنتینەی ئەپی نوێ", "ar" to "حجر التطبيقات الجديدة", "en" to "New app quarantine"),
    "quarantine_subtitle" to mapOf(
        "ku" to "هەر ئەپێکی نوێی دابەزراو لێرە چاوەڕێی پەسەندکردنت دەکات",
        "ar" to "كل تطبيق مثبت حديثاً ينتظر موافقتك هنا",
        "en" to "Every newly installed app waits for your approval here",
    ),
    "quarantine_empty_title" to mapOf("ku" to "هیچ ئەپێک لە چاوەڕوانیدا نییە", "ar" to "لا توجد تطبيقات قيد الانتظار", "en" to "No apps waiting"),
    "quarantine_empty_hint" to mapOf(
        "ku" to "ئەگەر منداڵەکەت ئەپێکی نوێ دابمەزرێنێت، لێرە دەردەکەوێت",
        "ar" to "إذا ثبّت طفلك تطبيقاً جديداً، سيظهر هنا",
        "en" to "If your child installs a new app, it will appear here",
    ),
    "quarantine_approve" to mapOf("ku" to "ڕێگەپێدان", "ar" to "موافقة", "en" to "Approve"),
    "quarantine_toggle" to mapOf("ku" to "قەرەنتینەی ئەپە نوێکان", "ar" to "حظر التطبيقات الجديدة", "en" to "Quarantine new apps"),
    "quarantine_toggle_sub" to mapOf(
        "ku" to "ئەپی نوێی دابەزراو بلۆک دەکرێت هەتا بە ڕەمز پەسەندی دەکەیت",
        "ar" to "أي تطبيق جديد يُحظر حتى توافق عليه بالرمز",
        "en" to "Newly installed apps stay blocked until approved with the PIN",
    ),
    "quarantine_pending_card" to mapOf(
        "ku" to "ئەپی نوێ چاوەڕوانە: %d",
        "ar" to "تطبيقات جديدة بانتظار الموافقة: %d",
        "en" to "New apps awaiting approval: %d",
    ),
    "quarantine_pending_sub" to mapOf(
        "ku" to "بۆ بینین و پەسەندکردنیان پەنجە بنێ",
        "ar" to "اعرضها ووافق عليها",
        "en" to "Tap to review and approve",
    ),
    "block_quarantine_title" to mapOf("ku" to "ئەپی نوێ — پەسەندکردن پێویستە", "ar" to "تطبيق جديد — الموافقة مطلوبة", "en" to "New app — approval needed"),
    "block_quarantine_message" to mapOf(
        "ku" to "«%1\$s» بەم دواییە دامەزراوە. بۆ کردنەوەی پێویستە باوان بە ڕەمزی پاراستن ڕەزامەندی بدەن.",
        "ar" to "تم تثبيت «%1\$s» للتو. يجب أن يوافق الوالدان برمز الحماية قبل فتحه.",
        "en" to "\"%1\$s\" was just installed. A parent must approve it with the protection PIN before it can open.",
    ),

    // ---- reels / short-video protection section ----
    "reels_section" to mapOf(
        "ku" to "بلۆککردنی ڕیلز",
        "ar" to "حظر الريلز والفيديوهات القصيرة",
        "en" to "Reels & Shorts protection",
    ),
    "reels_section_sub" to mapOf(
        "ku" to "کورتەڤیدیۆکانی ناو ئەپەکان دەخەمڵێنێت",
        "ar" to "يمنع الفيديوهات القصيرة داخل التطبيقات",
        "en" to "Blocks short-video feeds inside apps",
    ),
    "reels_instagram" to mapOf("ku" to "ڕێڵزی ئینستاگرام", "ar" to "ريلز إنستغرام", "en" to "Instagram Reels"),
    "reels_facebook" to mapOf("ku" to "ڕێڵزی فەیسبووک", "ar" to "ريلز فيسبوك", "en" to "Facebook Reels"),
    "reels_youtube" to mapOf("ku" to "شۆرتسی یوتیوب", "ar" to "شورتس يوتيوب", "en" to "YouTube Shorts"),
    "youtube_restrict_title" to mapOf(
        "ku" to "دۆخی سنوورداری یوتیوب (YouTube Restricted Mode)",
        "ar" to "وضع يوتيوب المقيد (YouTube Restricted Mode)",
        "en" to "YouTube Restricted Mode",
    ),
    "youtube_restrict_desc" to mapOf(
        "ku" to "کاتێک ئەم هەڵبژاردنە چالاک دەکرێت، یوتیوب (ئەپ و ماڵپەڕ) لەسەر زۆری سنووردار دەکرێت — ڤیدیۆ نەگونجاوەکان شاردرێنەوە و کۆمێنتەکان بەخۆکارانە دادەخرێن بە قیودی ئەمنیەتی فەرمییەتای Google.",
        "ar" to "عند تفعيل هذا الخيار، يتم إجبار يوتيوب (التطبيق والموقع) على إخفاء مقاطع الفيديو غير اللائقة وحجب التعليقات تلقائياً عبر قيود أمان Google الرسمية.",
        "en" to "When enabled, YouTube (app and website) is forced via Google's official safety restrictions to hide inappropriate videos and withhold comments automatically.",
    ),
    "youtube_restrict_hint" to mapOf(
        "ku" to "لەوانەیە پێویست بێت ئەپی یوتیوب لە پاشبنەما دابخەیت و دووبارەی بکەیتەوە بۆ سڕینەوەی کاش و جێبەجێبوونی قیود دەستبەجێ.",
        "ar" to "قد تحتاج لإغلاق تطبيق يوتيوب من الخلفية وإعادة فتحه لمسح الكاش وتطبيق القيود فوراً.",
        "en" to "You may need to close the YouTube app from the background and reopen it to clear the cache and apply restrictions immediately.",
    ),
    "telegram_search_disable" to mapOf(
        "ku" to "ناچالاککردنی دوگمەی گەڕان لە تلیگرام",
        "ar" to "تعطيل زر البحث في تلجرام",
        "en" to "Disable search button in Telegram",
    ),
    "reels_enable_warning_title" to mapOf(
        "ku" to "تکایە ئاگادار بە",
        "ar" to "تنبيه",
        "en" to "Heads up",
    ),
    "reels_enable_warning_body" to mapOf(
        "ku" to "چالاککردنی ئەم بژاردە ڕێگری دەکات لە دەستگەیشتن بە ڕیلز و کورتەڤیدیۆکان. دوای چالاککردن، ناتوانیت ڕیلز بەکاربهێنیت.",
        "ar" to "عند تفعيل هذا الخيار سيتم منع الوصول إلى الريلز والفيديوهات القصيرة، ولن تتمكن من استخدام الريلز.",
        "en" to "Enabling this option blocks access to Reels and short videos, so you will not be able to use Reels.",
    ),
    "reels_enable_warning_confirm" to mapOf(
        "ku" to "تێدەگەم",
        "ar" to "فهمت",
        "en" to "I understand",
    ),
    "reels_enable_warning_cancel" to mapOf(
        "ku" to "هەڵوەشاندنەوە",
        "ar" to "إلغاء",
        "en" to "Cancel",
    ),
    "days_picker_title" to mapOf(
        "ku" to "بۆ چەند ڕۆژ بلۆک بکە؟",
        "ar" to "لكم يوم تريد الحظر؟",
        "en" to "For how many days?",
    ),
    "duration_test_3_min" to mapOf(
        "ku" to "تاقیکردنەوە · ٣ خولەک",
        "ar" to "تجربة · 3 دقائق",
        "en" to "Test · 3 minutes",
    ),
    "vpn_block" to mapOf(
        "ku" to "بلۆککردنی VPN و ئەپە دەربازکەرەکان",
        "ar" to "حظر تطبيقات VPN وتجاوز الحظر",
        "en" to "Block VPN & bypass apps",
    ),
    "vpn_enable_warning_body" to mapOf(
        "ku" to "چالاککردنی ئەم بژاردە ڕێگری دەکات لە کردنەوەی ئەپەکانی VPN، پڕۆکسی و Tor.",
        "ar" to "عند تفعيل هذا الخيار سيتم منع فتح تطبيقات VPN والبروكسي و Tor.",
        "en" to "Enabling this option blocks opening VPN, proxy and Tor apps.",
    ),

    // ---- notification shade lock + extended duration picker ----
    "shade_lock_title" to mapOf(
        "ku" to "قفلکردنی دابەزاندنی نۆتیفەیشن",
        "ar" to "حظر سحب شريط الإشعارات",
        "en" to "Block Notification Pull-Down",
    ),
    "shade_lock_subtitle" to mapOf(
        "ku" to "ڕێگری لە دابەزاندنی شەریتی نۆتیفەیشن و پانێڵی سەرەوە",
        "ar" to "يمنع إنزال شريط الإشعارات واللوحة العلوية",
        "en" to "Prevents pulling down the notification shade and quick panel",
    ),
    "settings_lock_title" to mapOf(
        "ku" to "قفلکردنی تەواوی سێتینگی مۆبایل",
        "ar" to "قفل إعدادات الهاتف بالكامل",
        "en" to "Full Phone Settings Lock",
    ),
    "settings_lock_confirm_title" to mapOf(
        "ku" to "ئاگاداری",
        "ar" to "تنبيه",
        "en" to "Warning",
    ),
    "settings_lock_confirm_body" to mapOf(
        "ku" to "ئاگاداری: چالاککردنی ئەم بژاردە دەبێتە هۆی قفڵکردنی تەواوی ئەپی سێتینگ و ڕێگری لە دەستگەیشتنی پێی تاوەکو تەواوبوونی ماوەی دیاریکراو.",
        "ar" to "تنبيه: تفعيل هذا الخيار سيؤدي إلى قفل تطبيق الإعدادات بالكامل ومنع الوصول إليه حتى انتهاء المدة المحددة.",
        "en" to "Warning: Enabling this option will lock the Settings app completely and prevent access to it until the selected duration ends.",
    ),
    "settings_lock_confirm_button" to mapOf(
        "ku" to "بەردەوامبوون",
        "ar" to "متابعة",
        "en" to "Continue",
    ),
    "settings_lock_subtitle" to mapOf(
        "ku" to "ئەم تایبەتمەندییە کاتێک داگیرسێنە کە زانیت پاراستنی سێتینگی ئەپەکە بە باشی ئیش ناکات لەسەر مۆبایلەکەت، تاوەکو ڕێگری بکات لە سڕینەوەی ئەپەکە",
        "ar" to "قم بتفعيل هذا الخيار إذا لاحظت أن حماية الإعدادات المحددة للتطبيق لا تعمل بشكل جيد على هاتفك لمنع حذف التطبيق",
        "en" to "Enable this option if specific app settings protection is not working properly on your device to prevent uninstallation",
    ),
    "duration_dialog_title" to mapOf(
        "ku" to "بۆ چەنێک ئەتەوێت ئیش بکات؟",
        "ar" to "كم من الوقت تريد أن يعمل؟",
        "en" to "How long do you want it to run?",
    ),
    "duration_3m" to mapOf("ku" to "٣ خولەک (تاقیکردنەوە)", "ar" to "٣ دقائق (تجربة)", "en" to "3 Minutes (Test)"),
    "duration_12h" to mapOf("ku" to "١٢ کاتژمێر", "ar" to "١٢ ساعة", "en" to "12 Hours"),
    "duration_1d" to mapOf("ku" to "ڕۆژێک", "ar" to "يوم واحد", "en" to "1 Day"),
    "duration_2d" to mapOf("ku" to "٢ ڕۆژ", "ar" to "يومان", "en" to "2 Days"),
    "duration_3d" to mapOf("ku" to "٣ ڕۆژ", "ar" to "٣ أيام", "en" to "3 Days"),
    "duration_1w" to mapOf("ku" to "هەفتەیەک", "ar" to "أسبوع", "en" to "1 Week"),
    "duration_14d" to mapOf("ku" to "١٤ ڕۆژ", "ar" to "١٤ يوماً", "en" to "14 Days"),
    "duration_20d" to mapOf("ku" to "٢٠ ڕۆژ", "ar" to "٢٠ يوماً", "en" to "20 Days"),
    "duration_25d" to mapOf("ku" to "٢٥ ڕۆژ", "ar" to "٢٥ يوماً", "en" to "25 Days"),
    "duration_30d" to mapOf("ku" to "٣٠ ڕۆژ", "ar" to "٣٠ يوماً", "en" to "30 Days"),
    "duration_40d" to mapOf("ku" to "٤٠ ڕۆژ", "ar" to "٤٠ يوماً", "en" to "40 Days"),
    "duration_60d" to mapOf("ku" to "٦٠ ڕۆژ", "ar" to "٦٠ يوماً", "en" to "60 Days"),
    "duration_90d" to mapOf("ku" to "٩٠ ڕۆژ", "ar" to "٩٠ يوماً", "en" to "90 Days"),
    "duration_120d" to mapOf("ku" to "١٢٠ ڕۆژ", "ar" to "١٢٠ يوماً", "en" to "120 Days"),
    "duration_150d" to mapOf("ku" to "١٥٠ ڕۆژ", "ar" to "١٥٠ يوماً", "en" to "150 Days"),
    "duration_190d" to mapOf("ku" to "١٩٠ ڕۆژ", "ar" to "١٩٠ يوماً", "en" to "190 Days"),
    "duration_220d" to mapOf("ku" to "٢٢٠ ڕۆژ", "ar" to "٢٢٠ يوماً", "en" to "220 Days"),
    "duration_300d" to mapOf("ku" to "٣٠٠ ڕۆژ", "ar" to "٣٠٠ يوماً", "en" to "300 Days"),
    "duration_365d" to mapOf("ku" to "٣٦٥ ڕۆژ (ساڵ تەواو)", "ar" to "٣٦٥ يوماً (سنة كاملة)", "en" to "365 Days (Full Year)"),
    "duration_forever" to mapOf("ku" to "بۆ هەتاهەتایە", "ar" to "إلى الأبد", "en" to "Forever"),

    // ---- telegram global-search gate ----
    "telegram_search_blocked_toast" to mapOf(
        "ku" to "ببورە، گەڕانە گشتی پابەندە لەبەر پاراستنت",
        "ar" to "عذراً، البحث العام محظور لحمايتك",
        "en" to "Sorry, global search is blocked for your protection",
    ),

    // ---- system VPN settings interception (extends "Block VPN & bypass apps") ----
    "system_vpn_settings_blocked_toast" to mapOf(
        "ku" to "ببورە، ڕێکخستنی VPN بلۆککراوە لەبەر پاراستنت",
        "ar" to "عذراً، إعدادات VPN محظورة لحمايتك",
        "en" to "Sorry, VPN settings are blocked for your protection",
    ),

    // ---- Full Phone Settings Lock ----
    "full_settings_lock_blocked_toast" to mapOf(
        "ku" to "ببورە، ئەپی سێتینگ بە تەواوی قفڵکراوە تاوەکو تەواوبوونی ماوەکە",
        "ar" to "عذراً، تطبيق الإعدادات مقفل بالكامل حتى انتهاء المدة",
        "en" to "Sorry, Settings is fully locked until the timer ends",
    ),


    // ---- timed app lock (vNext) ----
    "timed_lock_section" to mapOf(
        "ku" to "قه مانگی کاتی ئەپەکان (Timed App Lock)",
        "ar" to "الحظر الزمني للتطبيقات (Timed App Lock)",
        "en" to "Timed App Lock",
    ),
    "timed_lock_section_sub" to mapOf(
        "ku" to "ئەپێک بۆ ماوەی دیاریکراو قفڵ بکە؛ کاتێک تەواو بوو، خۆکار دەکرێتەوە — هەرگیز ناتوانیت زوو بیکەیتەوە",
        "ar" to "قفل تطبيقاً لفترة محددة؛ ينتهي تلقائياً ولا يمكن فكه مبكراً",
        "en" to "Lock an app for a chosen time; auto-releases, cannot be undone early",
    ),
    "timed_lock_title" to mapOf(
        "ku" to "قفڵی کاتی ئەپەکان",
        "ar" to "الحظر الزمني للتطبيقات",
        "en" to "Timed App Lock",
    ),
    "timed_lock_choose_app" to mapOf(
        "ku" to "ئەپەکە هەڵبژێرە:",
        "ar" to "اختر التطبيق:",
        "en" to "Choose the app:",
    ),
    "timed_lock_search" to mapOf("ku" to "گەڕان بۆ ئەپەکان", "ar" to "ابحث عن التطبيقات", "en" to "Search apps"),
    "timed_lock_duration" to mapOf(
        "ku" to "ماوەی قفڵ:",
        "ar" to "مدة الحظر:",
        "en" to "Lock duration:",
    ),
    "timed_lock_hours" to mapOf("ku" to "کاتژمێر", "ar" to "ساعة", "en" to "h"),
    "timed_lock_minutes" to mapOf("ku" to "خولەک", "ar" to "دقيقة", "en" to "min"),
    "timed_lock_apply" to mapOf(
        "ku" to "جێبەجێکردنی قفڵی کاتی",
        "ar" to "تطبيق الحظر الزمني",
        "en" to "Apply timed lock",
    ),
    "timed_lock_confirm_title" to mapOf(
        "ku" to "دڵنیایت؟",
        "ar" to "هل أنت متأكد؟",
        "en" to "Are you sure?",
    ),
    "timed_lock_confirm_body" to mapOf(
        "ku" to "لە کاتی چالاککردنی قفڵی کاتی بۆ ئەم ئەپە، ناتوانیت بیهێنیتەوە یان کاتەکە کەم بکەیتەوە یان بیگۆڕیت هەتا کۆتایی هاتنی ماوەکە.",
        "ar" to "بمجرد تفعيل الحظر الزمني للتطبيق، لن تتمكن من فكه أو تقليل مدته أو تعديل إعداداته حتى انتهاء المدة المحددة.",
        "en" to "Once the timed lock is enabled, you cannot unlock, shorten or edit it until the chosen duration fully ends.",
    ),
    "timed_lock_confirm_cta" to mapOf(
        "ku" to "پەسەندکردن و قفڵکردن",
        "ar" to "تأكيد وقفل التطبيق",
        "en" to "Confirm & lock app",
    ),


    // ---- force-safe-search toggle (vNext) ----
    "safesearch_force" to mapOf(
        "ku" to "نەخچی گەڕانی سەلامەت (Force SafeSearch)",
        "ar" to "إجبار البحث الآمن (Force SafeSearch)",
        "en" to "Force SafeSearch",
    ),
    "safesearch_force_sub" to mapOf(
        "ku" to "بە خۆکار دەگۆڕدرێتەوە بۆ گەڕانە پارێزراوەکان لەسەر Google و Bing و DuckDuckGo و YouTube Restricted Mode",
        "ar" to "حجب النتائج غير اللائقة تلقائياً — Google و Bing و DuckDuckGo ووضع تقييد YouTube",
        "en" to "Blocks unsafe results automatically on Google, Bing, DuckDuckGo + YouTube Restricted Mode",
    ),

    "health_live_broken" to mapOf(
        "ku" to "سپیەری تۆڕ کار ناکات — تکایە دووبارە چالاکی بکەرەوە",
        "ar" to "درع VPN غير متصل — يرجى إعادة تشغيله",
        "en" to "VPN shield is down — please restart it",
    ),

    "health_vpn_restart" to mapOf(
        "ku" to "دووبارە چالاککردنی سپیەری تۆڕ ئێستا",
        "ar" to "إعادة تفعيل درع VPN الآن",
        "en" to "Restart VPN shield now",
    ),
)
