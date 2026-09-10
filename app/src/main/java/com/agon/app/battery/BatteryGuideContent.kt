package com.agon.app.battery

/**
 * Device-specific, localized copy for the battery optimization guide.
 *
 * Everything is plain data resolved on demand, so the guide costs nothing until it is opened.
 */
object BatteryGuideContent {

    /** One numbered instruction, optionally highlighting the exact option to choose. */
    data class Step(val text: String, val emphasis: String? = null)

    fun title(language: String): String = when (language) {
        "en" -> "Keep protection always on"
        "ar" -> "أبقِ الحماية مفعّلة دائماً"
        else -> "پاراستن هەمیشە چالاک بهێڵە"
    }

    fun whyTitle(language: String): String = when (language) {
        "en" -> "Why this matters"
        "ar" -> "لماذا هذا مهم"
        else -> "بۆچی ئەمە گرنگە"
    }

    /** Requirement 1: why the exemption is needed. */
    fun whyBody(language: String): String = when (language) {
        "en" -> "BlockX LaAbrah must keep running in the background to filter websites and watch " +
            "for blocked apps and keywords. Battery optimization puts background apps to sleep, " +
            "so protection has to be excluded from it to stay continuous."
        "ar" -> "يجب أن يبقى BlockX LaAbrah يعمل في الخلفية لتصفية المواقع ومراقبة التطبيقات " +
            "والكلمات المحظورة. تحسين البطارية يُنيم التطبيقات في الخلفية، لذا يجب استثناء " +
            "الحماية منه لتبقى مستمرة."
        else -> "پێویستە BlockX LaAbrah لە باکگراوند کار بکات بۆ فلتەرکردنی وێبسایت و چاودێری " +
            "ئەپ و وشەکانی بلۆککراو. ئۆپتیمایزی باتری ئەپەکان دەخەوێنێت، بۆیە دەبێت پاراستن " +
            "لێی جیا بکرێتەوە تا بەردەوام بێت."
    }

    fun riskTitle(language: String): String = when (language) {
        "en" -> "If it stays enabled"
        "ar" -> "إذا بقي مفعّلاً"
        else -> "ئەگەر چالاک بمێنێتەوە"
    }

    /** Requirement 2: the concrete consequence. */
    fun riskBody(language: String): String = when (language) {
        "en" -> "Android may stop Safe Browsing Shield or the accessibility service while the screen " +
            "is off or the app is closed. Blocked websites can then load, and blocked apps and " +
            "typed keywords may not be detected until protection restarts."
        "ar" -> "قد يوقف Android درع التصفح الآمن أو خدمة إمكانية الوصول عندما تكون الشاشة مطفأة أو " +
            "التطبيق مغلقاً. عندها قد تُفتح المواقع المحظورة، وقد لا يتم كشف التطبيقات " +
            "والكلمات المحظورة حتى تُعاد الحماية."
        else -> "Android ڕەنگە سپیەری تۆڕ یان Accessibility بوەستێنێت کاتێک شاشە " +
            "کوژاوەیە یان ئەپ داخراوە. ئەوسا وێبسایتی بلۆککراو دەکرێتەوە، و ڕەنگە ئەپ و " +
            "وشەی بلۆککراو نەدۆزرێنەوە هەتا پاراستن دووبارە دەست پێ بکات."
    }

    fun stepsTitle(language: String): String = when (language) {
        "en" -> "Steps for your device"
        "ar" -> "الخطوات لجهازك"
        else -> "هەنگاوەکان بۆ ئامێرەکەت"
    }

    fun openButton(language: String): String = when (language) {
        "en" -> "Open Battery Optimization Settings"
        "ar" -> "افتح إعدادات تحسين البطارية"
        else -> "کردنەوەی ڕێکخستنی ئۆپتیمایزی باتری"
    }

    fun retryButton(language: String): String = when (language) {
        "en" -> "Try again"
        "ar" -> "حاول مرة أخرى"
        else -> "دووبارە هەوڵ بدە"
    }

    fun closeButton(language: String): String = when (language) {
        "en" -> "Close"
        "ar" -> "إغلاق"
        else -> "داخستن"
    }

    fun doneButton(language: String): String = when (language) {
        "en" -> "Done"
        "ar" -> "تم"
        else -> "تەواو"
    }

    fun howToButton(language: String): String = when (language) {
        "en" -> "How to enable"
        "ar" -> "كيفية التمكين"
        else -> "چۆنیەتی چالاککردن"
    }

    /** Requirement 6/7: verification outcome after returning from settings. */
    fun verifiedTitle(language: String): String = when (language) {
        "en" -> "Battery optimization is disabled"
        "ar" -> "تم إيقاف تحسين البطارية"
        else -> "ئۆپتیمایزی باتری ناچالاک کراوە"
    }

    fun verifiedBody(language: String): String = when (language) {
        "en" -> "Protection can now run continuously in the background."
        "ar" -> "يمكن للحماية الآن العمل بشكل مستمر في الخلفية."
        else -> "ئێستا پاراستن دەتوانێت بەردەوام لە باکگراوند کار بکات."
    }

    fun stillEnabledTitle(language: String): String = when (language) {
        "en" -> "Still optimized"
        "ar" -> "لا يزال مُحسّناً"
        else -> "هێشتا ئۆپتیمایز کراوە"
    }

    fun stillEnabledBody(language: String): String = when (language) {
        "en" -> "Android still reports this app as optimized. Open the settings again and make " +
            "sure the correct option is selected for BlockX LaAbrah."
        "ar" -> "لا يزال Android يعتبر هذا التطبيق مُحسّناً. افتح الإعدادات مرة أخرى وتأكد من " +
            "اختيار الخيار الصحيح لتطبيق BlockX LaAbrah."
        else -> "Android هێشتا ئەم ئەپە وەک ئۆپتیمایزکراو تۆمار دەکات. دووبارە ڕێکخستنەکان " +
            "بکەوە و دڵنیا بە کە هەڵبژاردەی ڕاست بۆ BlockX LaAbrah هەڵبژێردراوە."
    }

    fun unavailableBody(language: String): String = when (language) {
        "en" -> "This device does not expose a battery optimization screen. Open Settings, find " +
            "BlockX LaAbrah in the app list, then allow unrestricted background activity."
        "ar" -> "هذا الجهاز لا يوفر شاشة تحسين البطارية. افتح الإعدادات وابحث عن BlockX LaAbrah " +
            "في قائمة التطبيقات، ثم اسمح بالنشاط غير المقيّد في الخلفية."
        else -> "ئەم ئامێرە پەڕەی ئۆپتیمایزی باتری پیشان نادات. ڕێکخستن بکەوە، BlockX LaAbrah " +
            "لە لیستی ئەپەکان بدۆزەوە، پاشان چالاکی بێسنوور لە باکگراوند ڕێگە پێبدە."
    }

    /** The exact option label to pick, which differs per skin. */
    fun optionLabel(vendor: DeviceVendor, language: String): String = when (vendor) {
        DeviceVendor.XIAOMI, DeviceVendor.REDMI, DeviceVendor.POCO -> when (language) {
            "en" -> "No restrictions"
            "ar" -> "بلا قيود"
            else -> "بێ سنوورداری"
        }
        DeviceVendor.SAMSUNG -> when (language) {
            "en" -> "Unrestricted"
            "ar" -> "غير مقيّد"
            else -> "بێ سنوور"
        }
        DeviceVendor.HUAWEI, DeviceVendor.HONOR -> when (language) {
            "en" -> "Manual management"
            "ar" -> "إدارة يدوية"
            else -> "بەڕێوەبردنی دەستی"
        }
        else -> when (language) {
            "en" -> "Don't optimize"
            "ar" -> "عدم التحسين"
            else -> "ئۆپتیمایز مەکە"
        }
    }

    /**
     * Requirement 3 and 4: the generic four-step flow, specialised per manufacturer where the
     * real menu path differs.
     */
    fun steps(vendor: DeviceVendor, language: String): List<Step> {
        val option = optionLabel(vendor, language)
        val open = when (language) {
            "en" -> "Tap \"Open Battery Optimization Settings\" below."
            "ar" -> "اضغط \"افتح إعدادات تحسين البطارية\" بالأسفل."
            else -> "لە خوارەوە \"کردنەوەی ڕێکخستنی ئۆپتیمایزی باتری\" دابگرە."
        }
        val back = when (language) {
            "en" -> "Return to BlockX LaAbrah. The setting is checked automatically."
            "ar" -> "ارجع إلى BlockX LaAbrah. سيتم التحقق من الإعداد تلقائياً."
            else -> "بگەڕێوە بۆ BlockX LaAbrah. ڕێکخستنەکە خۆکارانە پشکنین دەکرێت."
        }

        val middle: List<Step> = when (vendor) {
            DeviceVendor.SAMSUNG -> listOf(
                Step(
                    when (language) {
                        "en" -> "Open Battery, then Background usage limits."
                        "ar" -> "افتح البطارية ثم حدود الاستخدام في الخلفية."
                        else -> "باتری بکەوە، پاشان سنووری بەکارهێنانی باکگراوند."
                    },
                ),
                Step(
                    when (language) {
                        "en" -> "Make sure BlockX LaAbrah is not in Sleeping or Deep sleeping apps."
                        "ar" -> "تأكد أن BlockX LaAbrah ليس في التطبيقات النائمة أو النائمة بعمق."
                        else -> "دڵنیا بە BlockX LaAbrah لە ئەپەکانی خەوتوو نییە."
                    },
                ),
                Step(
                    when (language) {
                        "en" -> "In app battery settings choose:"
                        "ar" -> "في إعدادات بطارية التطبيق اختر:"
                        else -> "لە ڕێکخستنی باتری ئەپ هەڵبژێرە:"
                    },
                    option,
                ),
            )

            DeviceVendor.XIAOMI, DeviceVendor.REDMI, DeviceVendor.POCO -> listOf(
                Step(
                    when (language) {
                        "en" -> "Select BlockX LaAbrah in the battery saver list."
                        "ar" -> "اختر BlockX LaAbrah من قائمة موفّر البطارية."
                        else -> "BlockX LaAbrah لە لیستی پاشەکەوتکەری باتری هەڵبژێرە."
                    },
                ),
                Step(
                    when (language) {
                        "en" -> "Choose:"
                        "ar" -> "اختر:"
                        else -> "هەڵبژێرە:"
                    },
                    option,
                ),
                Step(
                    when (language) {
                        "en" -> "Also enable Autostart for BlockX LaAbrah in Security settings."
                        "ar" -> "فعّل أيضاً التشغيل التلقائي لـ BlockX LaAbrah في إعدادات الأمان."
                        else -> "هەروەها Autostart بۆ BlockX LaAbrah لە ڕێکخستنی ئاسایش چالاک بکە."
                    },
                ),
            )

            DeviceVendor.HUAWEI, DeviceVendor.HONOR -> listOf(
                Step(
                    when (language) {
                        "en" -> "Open App launch and find BlockX LaAbrah."
                        "ar" -> "افتح تشغيل التطبيقات وابحث عن BlockX LaAbrah."
                        else -> "App launch بکەوە و BlockX LaAbrah بدۆزەوە."
                    },
                ),
                Step(
                    when (language) {
                        "en" -> "Switch it to:"
                        "ar" -> "حوّله إلى:"
                        else -> "بگۆڕە بۆ:"
                    },
                    option,
                ),
                Step(
                    when (language) {
                        "en" -> "Enable Auto-launch, Secondary launch and Run in background."
                        "ar" -> "فعّل التشغيل التلقائي والثانوي والعمل في الخلفية."
                        else -> "Auto-launch و Secondary launch و Run in background چالاک بکە."
                    },
                ),
            )

            DeviceVendor.OPPO, DeviceVendor.REALME, DeviceVendor.ONEPLUS -> listOf(
                Step(
                    when (language) {
                        "en" -> "Find BlockX LaAbrah in the battery or power list."
                        "ar" -> "ابحث عن BlockX LaAbrah في قائمة البطارية أو الطاقة."
                        else -> "BlockX LaAbrah لە لیستی باتری بدۆزەوە."
                    },
                ),
                Step(
                    when (language) {
                        "en" -> "Choose:"
                        "ar" -> "اختر:"
                        else -> "هەڵبژێرە:"
                    },
                    option,
                ),
                Step(
                    when (language) {
                        "en" -> "Allow background running and auto-startup for the app."
                        "ar" -> "اسمح بالعمل في الخلفية والتشغيل التلقائي للتطبيق."
                        else -> "کارکردن لە باکگراوند و دەستپێکردنی خۆکار ڕێگە پێبدە."
                    },
                ),
            )

            DeviceVendor.VIVO -> listOf(
                Step(
                    when (language) {
                        "en" -> "Select BlockX LaAbrah in high background power consumption."
                        "ar" -> "اختر BlockX LaAbrah في استهلاك الطاقة العالي بالخلفية."
                        else -> "BlockX LaAbrah لە بەکارهێنانی بەرزی وزە هەڵبژێرە."
                    },
                ),
                Step(
                    when (language) {
                        "en" -> "Allow it, then choose:"
                        "ar" -> "اسمح به ثم اختر:"
                        else -> "ڕێگە پێبدە، پاشان هەڵبژێرە:"
                    },
                    option,
                ),
                Step(
                    when (language) {
                        "en" -> "Enable Background app refresh in iManager."
                        "ar" -> "فعّل تحديث التطبيق في الخلفية من iManager."
                        else -> "Background app refresh لە iManager چالاک بکە."
                    },
                ),
            )

            DeviceVendor.MOTOROLA, DeviceVendor.PIXEL, DeviceVendor.ASUS, DeviceVendor.NOTHING, DeviceVendor.GENERIC -> listOf(
                Step(
                    when (language) {
                        "en" -> "Select BlockX LaAbrah from the app list."
                        "ar" -> "اختر BlockX LaAbrah من قائمة التطبيقات."
                        else -> "BlockX LaAbrah لە لیستی ئەپەکان هەڵبژێرە."
                    },
                ),
                Step(
                    when (language) {
                        "en" -> "Choose:"
                        "ar" -> "اختر:"
                        else -> "هەڵبژێرە:"
                    },
                    option,
                ),
            )
        }

        return buildList {
            add(Step(open))
            addAll(middle)
            add(Step(back))
        }
    }

    /** Short vendor caption shown under the title. */
    fun vendorCaption(vendor: DeviceVendor, language: String): String {
        val skin = when (vendor) {
            DeviceVendor.SAMSUNG -> "One UI"
            DeviceVendor.XIAOMI, DeviceVendor.REDMI, DeviceVendor.POCO -> "MIUI / HyperOS"
            DeviceVendor.OPPO, DeviceVendor.REALME -> "ColorOS"
            DeviceVendor.ONEPLUS -> "OxygenOS"
            DeviceVendor.VIVO -> "Funtouch OS"
            DeviceVendor.HUAWEI -> "EMUI"
            DeviceVendor.HONOR -> "MagicOS"
            DeviceVendor.MOTOROLA -> "My UX"
            DeviceVendor.PIXEL -> "Pixel"
            DeviceVendor.ASUS -> "ZenUI"
            DeviceVendor.NOTHING -> "Nothing OS"
            DeviceVendor.GENERIC -> return when (language) {
                "en" -> "Standard Android steps"
                "ar" -> "خطوات أندرويد القياسية"
                else -> "هەنگاوی ستاندارد"
            }
        }
        return when (language) {
            "en" -> "$skin instructions"
            "ar" -> "تعليمات $skin"
            else -> "ڕێنمایی $skin"
        }
    }
}
