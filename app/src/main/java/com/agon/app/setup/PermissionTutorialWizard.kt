package com.agon.app.setup

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agon.app.R

private data class TutorialPage(
    val imageRes: Int,
    val icon: ImageVector,
    val title: String,
    val summary: String,
    val steps: List<String>,
)

/** Visual permission tutorial shown before Android permission requests and callable from Settings. */
@Composable
fun PermissionTutorialWizard(
    language: String,
    onClose: () -> Unit,
    onComplete: () -> Unit = onClose,
) {
    val pages = remember(language) { tutorialPages(language) }
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val page = pages[pageIndex]

    BackHandler {
        if (pageIndex > 0) pageIndex-- else onClose()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { if (pageIndex > 0) pageIndex-- else onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel(language))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tutorialTitle(language),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = pageCounter(language, pageIndex + 1, pages.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Image(
                        painter = painterResource(page.imageRes),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(205.dp).padding(10.dp),
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = page.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(page.steps.size) { stepIndex ->
                StepCard(number = stepIndex + 1, text = page.steps[stepIndex])
            }

            if (pageIndex == pages.lastIndex) {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = oemTitle(language),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                text = oemBody(language),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { showHelp = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(helpLabel(language), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            if (pageIndex < pages.lastIndex) pageIndex++ else onComplete()
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) {
                        Text(
                            text = if (pageIndex < pages.lastIndex) nextLabel(language) else finishLabel(language),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    if (showHelp) {
        DeviceHelpDialog(language = language, onDismiss = { showHelp = false })
    }
}

@Composable
private fun StepCard(number: Int, text: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Text(number.toString(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
            Text(text = text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DeviceHelpDialog(language: String, onDismiss: () -> Unit) {
    var deviceName by rememberSaveable { mutableStateOf("") }
    var guide by rememberSaveable { mutableStateOf<String?>(null) }
    val detectedDevice = remember { "${Build.MANUFACTURER} ${Build.MODEL}".trim() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
        title = { Text(helpTitle(language), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(helpIntro(language, detectedDevice), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it.take(60); guide = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(deviceLabel(language)) },
                    placeholder = { Text("Samsung, Xiaomi, Redmi, POCO, Oppo…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                guide?.let { result ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Text(
                            text = result,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    guide = deviceSpecificGuide(deviceName.ifBlank { detectedDevice }, language)
                },
            ) {
                Text(generateGuideLabel(language))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(closeLabel(language)) }
        },
    )
}

private fun tutorialPages(language: String): List<TutorialPage> = listOf(
    TutorialPage(
        imageRes = R.drawable.tutorial_accessibility,
        icon = Icons.Default.CheckCircle,
        title = when (language) {
            "ar" -> "تفعيل خدمة إمكانية الوصول"
            "en" -> "Enable Accessibility Service"
            else -> "چالاککردنی Accessibility Service"
        },
        summary = when (language) {
            "ar" -> "تتيح هذه الخدمة فحص التطبيقات المحظورة وحماية الإعدادات في الخلفية."
            "en" -> "This service detects blocked apps and protects system settings in the background."
            else -> "ئەم خزمەتگوزارییە ئەپە بلۆککراوەکان دەدۆزێتەوە و ڕێکخستنەکان لە باکگراوند دەپارێزێت."
        },
        steps = localizedSteps(
            language,
            ku = listOf(
                "دوگمەی چالاککردن دەست بنێ تا پەڕەی Accessibility بکرێتەوە.",
                "لە لیستی Installed apps یان Downloaded apps، BlockX LaAbrah هەڵبژێرە.",
                "Use service چالاک بکە و لە پەیامی دڵنیابوونەوەدا Allow هەڵبژێرە.",
            ),
            ar = listOf(
                "اضغط زر التفعيل لفتح صفحة إمكانية الوصول.",
                "من التطبيقات المثبتة أو المحمّلة اختر BlockX LaAbrah.",
                "فعّل استخدام الخدمة ثم اختر سماح في رسالة التأكيد.",
            ),
            en = listOf(
                "Tap Enable to open Android Accessibility settings.",
                "Under Installed or Downloaded apps, choose BlockX LaAbrah.",
                "Turn on Use service, then choose Allow in the confirmation dialog.",
            ),
        ),
    ),
    TutorialPage(
        imageRes = R.drawable.tutorial_device_admin,
        icon = Icons.Default.CheckCircle,
        title = when (language) {
            "ar" -> "تفعيل مسؤول الجهاز"
            "en" -> "Enable Device Administrator"
            else -> "چالاککردنی Device Admin"
        },
        summary = when (language) {
            "ar" -> "تضيف هذه الصلاحية خطوة تحقق قبل إزالة التطبيق."
            "en" -> "This permission adds a verification step before the app can be removed."
            else -> "ئەم مۆڵەتە پێش لابردنی ئەپ هەنگاوێکی پشتڕاستکردنەوە زیاد دەکات."
        },
        steps = localizedSteps(
            language,
            ku = listOf(
                "دوگمەی چالاککردن دەست بنێ بۆ کردنەوەی Security settings.",
                "Device admin apps یان Device administrators بکەرەوە.",
                "BlockX LaAbrah هەڵبژێرە و Activate this device admin چالاک بکە.",
            ),
            ar = listOf(
                "اضغط زر التفعيل لفتح إعدادات الأمان.",
                "افتح تطبيقات مسؤول الجهاز أو مسؤولي الجهاز.",
                "اختر BlockX LaAbrah ثم اضغط تفعيل مسؤول الجهاز.",
            ),
            en = listOf(
                "Tap Enable to open Security settings.",
                "Open Device admin apps or Device administrators.",
                "Choose BlockX LaAbrah and activate the device administrator.",
            ),
        ),
    ),
    TutorialPage(
        imageRes = R.drawable.tutorial_battery,
        icon = Icons.Default.CheckCircle,
        title = when (language) {
            "ar" -> "إيقاف تحسين البطارية"
            "en" -> "Disable Battery Optimization"
            else -> "کوژاندنەوەی Battery Optimization"
        },
        summary = when (language) {
            "ar" -> "استثناء التطبيق يمنع Android من إيقاف الحماية في الخلفية."
            "en" -> "Excluding the app prevents Android from pausing background protection."
            else -> "جیاکردنەوەی ئەپ ڕێگری لەوە دەکات Android پاراستنی باکگراوند بوەستێنێت."
        },
        steps = localizedSteps(
            language,
            ku = listOf(
                "Battery optimization یان Battery usage بکەرەوە.",
                "All apps هەڵبژێرە و BlockX LaAbrah لە لیستەکە بدۆزەوە.",
                "Don't optimize، Unrestricted یان No restrictions هەڵبژێرە.",
            ),
            ar = listOf(
                "افتح تحسين البطارية أو استخدام البطارية.",
                "اختر كل التطبيقات وابحث عن BlockX LaAbrah في القائمة.",
                "اختر عدم التحسين أو غير مقيّد أو بلا قيود.",
            ),
            en = listOf(
                "Open Battery optimization or Battery usage.",
                "Choose All apps and find BlockX LaAbrah in the list.",
                "Choose Don't optimize, Unrestricted, or No restrictions.",
            ),
        ),
    ),
    TutorialPage(
        imageRes = R.drawable.tutorial_oem,
        icon = Icons.Default.PhoneAndroid,
        title = oemTitle(language),
        summary = oemBody(language),
        steps = localizedSteps(
            language,
            ku = listOf(
                "Recent Apps بکەرەوە و ئەگەر ئەپ قوفڵ کراوە، قوفڵەکە کاتیانە بکەرەوە.",
                "بگەڕێوە بۆ Settings و مۆڵەتەکە لەوێ چالاک بکە.",
                "لە Xiaomi/Redmi/POCO، Autostart و No restrictions ـیش چالاک بکە؛ لە Huawei/Oppo/Vivo/Realme، Allow background activity هەڵبژێرە.",
            ),
            ar = listOf(
                "افتح التطبيقات الحديثة، وإذا كان التطبيق مقفلاً فأزل القفل مؤقتاً.",
                "ارجع إلى الإعدادات وفعّل الإذن من هناك.",
                "في Xiaomi/Redmi/POCO فعّل التشغيل التلقائي وبلا قيود؛ وفي Huawei/Oppo/Vivo/Realme اسمح بالنشاط في الخلفية.",
            ),
            en = listOf(
                "Open Recent Apps; if the app is locked there, unlock it temporarily.",
                "Return to Settings and enable the permission from its system page.",
                "On Xiaomi/Redmi/POCO enable Autostart and No restrictions; on Huawei/Oppo/Vivo/Realme allow background activity.",
            ),
        ),
    ),
)

private fun localizedSteps(language: String, ku: List<String>, ar: List<String>, en: List<String>): List<String> =
    when (language) { "ar" -> ar; "en" -> en; else -> ku }

private fun deviceSpecificGuide(device: String, language: String): String {
    val name = device.lowercase()
    val family = when {
        listOf("xiaomi", "redmi", "poco", "miui", "hyperos").any(name::contains) -> "xiaomi"
        listOf("huawei", "honor", "emui", "magicos").any(name::contains) -> "huawei"
        listOf("oppo", "realme", "oneplus", "coloros", "oxygenos").any(name::contains) -> "oppo"
        listOf("vivo", "iqoo", "funtouch", "originos").any(name::contains) -> "vivo"
        listOf("samsung", "galaxy", "one ui").any(name::contains) -> "samsung"
        else -> "generic"
    }
    return when (family) {
        "xiaomi" -> when (language) {
            "ar" -> "الإعدادات ← التطبيقات ← إدارة التطبيقات ← BlockX LaAbrah: فعّل التشغيل التلقائي. ثم البطارية ← بلا قيود. لخدمة إمكانية الوصول: الإعدادات الإضافية ← إمكانية الوصول ← التطبيقات المحمّلة. إذا رفض النظام التفعيل، افتح التطبيقات الحديثة وأزل قفل التطبيق مؤقتاً ثم أعد المحاولة."
            "en" -> "Settings → Apps → Manage apps → BlockX LaAbrah: enable Autostart. Then Battery → No restrictions. For Accessibility: Additional settings → Accessibility → Downloaded apps. If Android refuses, open Recents, temporarily remove the app lock, and retry."
            else -> "Settings ← Apps ← Manage apps ← BlockX LaAbrah: Autostart چالاک بکە. پاشان Battery ← No restrictions. بۆ Accessibility: Additional settings ← Accessibility ← Downloaded apps. ئەگەر ڕەتکرایەوە، Recent Apps بکەرەوە و قوفڵی ئەپ کاتیانە لابە."
        }
        "huawei" -> when (language) {
            "ar" -> "الإعدادات ← التطبيقات ← تشغيل التطبيقات ← BlockX LaAbrah ← إدارة يدوية: اسمح بالتشغيل التلقائي والثانوي والعمل في الخلفية. ثم البطارية ← المزيد من إعدادات البطارية ← البقاء متصلاً أثناء السكون."
            "en" -> "Settings → Apps → App launch → BlockX LaAbrah → Manage manually: allow auto-launch, secondary launch and background activity. Then Battery → More battery settings → Stay connected while asleep."
            else -> "Settings ← Apps ← App launch ← BlockX LaAbrah ← Manage manually: auto-launch، secondary launch و background activity ڕێگە پێبدە. پاشان Battery ← More battery settings."
        }
        "oppo" -> when (language) {
            "ar" -> "الإعدادات ← التطبيقات ← إدارة التطبيقات ← BlockX LaAbrah ← استخدام البطارية: اسمح بالنشاط في الخلفية والتشغيل التلقائي. قد تحتاج إلى قفل التطبيق في شاشة التطبيقات الحديثة مؤقتاً أثناء الإعداد."
            "en" -> "Settings → Apps → App management → BlockX LaAbrah → Battery usage: allow background activity and auto launch. You may need to temporarily lock the app in Recents while configuring it."
            else -> "Settings ← Apps ← App management ← BlockX LaAbrah ← Battery usage: background activity و auto launch ڕێگە پێبدە. ڕەنگە پێویست بێت لە Recents کاتیانە قوفڵی بکەیت."
        }
        "vivo" -> when (language) {
            "ar" -> "الإعدادات ← البطارية ← إدارة استهلاك الطاقة في الخلفية ← BlockX LaAbrah ← استهلاك طاقة عالٍ في الخلفية. ثم مدير الأذونات ← التشغيل التلقائي."
            "en" -> "Settings → Battery → Background power consumption management → BlockX LaAbrah → High background power usage. Then Permission manager → Autostart."
            else -> "Settings ← Battery ← Background power consumption management ← BlockX LaAbrah ← High background power usage. پاشان Permission manager ← Autostart."
        }
        "samsung" -> when (language) {
            "ar" -> "الإعدادات ← العناية بالجهاز ← البطارية ← حدود استخدام الخلفية: تأكد أن BlockX LaAbrah ليس ضمن التطبيقات النائمة. ثم معلومات التطبيق ← البطارية ← غير مقيّد."
            "en" -> "Settings → Device care → Battery → Background usage limits: ensure BlockX LaAbrah is not in Sleeping apps. Then App info → Battery → Unrestricted."
            else -> "Settings ← Device care ← Battery ← Background usage limits: دڵنیا بە BlockX LaAbrah لە Sleeping apps نییە. پاشان App info ← Battery ← Unrestricted."
        }
        else -> when (language) {
            "ar" -> "افتح معلومات تطبيق BlockX LaAbrah من إعدادات Android، ثم فعّل الأذونات المطلوبة واختر البطارية ← غير مقيّد أو عدم التحسين. إذا لم يظهر الخيار، ابحث عن التطبيقات الخاصة أو مسؤولي الجهاز أو التطبيقات المحمّلة داخل الإعدادات."
            "en" -> "Open BlockX LaAbrah App info in Android Settings, enable the required permissions, then choose Battery → Unrestricted or Don't optimize. If an option is hidden, search Settings for Special app access, Device administrators, or Downloaded apps."
            else -> "لە Android Settings زانیاری ئەپی BlockX LaAbrah بکەرەوە، مۆڵەتە پێویستەکان چالاک بکە و Battery ← Unrestricted یان Don't optimize هەڵبژێرە. ئەگەر هەڵبژاردەکە دیار نەبوو، Special app access، Device administrators یان Downloaded apps بگەڕێ."
        }
    }
}

private fun tutorialTitle(language: String) = when (language) { "ar" -> "دليل الأذونات"; "en" -> "Permission tutorial"; else -> "فێرکاری مۆڵەتەکان" }
private fun pageCounter(language: String, page: Int, total: Int) = when (language) { "ar" -> "الخطوة $page من $total"; "en" -> "Step $page of $total"; else -> "هەنگاوی $page لە $total" }
private fun oemTitle(language: String) = when (language) { "ar" -> "ملاحظات مهمة لبعض الهواتف"; "en" -> "Important notes for some phones"; else -> "تێبینی گرنگ بۆ هەندێک مۆبایل" }
private fun oemBody(language: String) = when (language) { "ar" -> "في Xiaomi وRedmi وPOCO وHuawei وOppo وVivo وRealme قد تمنع واجهة النظام التفعيل المباشر. أغلق شاشة التطبيقات الحديثة أو عد إلى الإعدادات وفعّل الإذن يدوياً."; "en" -> "On Xiaomi, Redmi, POCO, Huawei, Oppo, Vivo and Realme, the system skin may prevent direct activation. Close Recents or return to Settings and enable the permission manually."; else -> "لە Xiaomi، Redmi، POCO، Huawei، Oppo، Vivo و Realme ڕووکارەکە ڕەنگە چالاککردنی ڕاستەوخۆ ڕەت بکاتەوە. Recent Apps دابخە یان بگەڕێوە Settings و مۆڵەتەکە بە دەست چالاک بکە." }
private fun helpLabel(language: String) = when (language) { "ar" -> "مساعدة"; "en" -> "Help"; else -> "یارمەتی" }
private fun helpTitle(language: String) = when (language) { "ar" -> "مساعدة خاصة بجهازك"; "en" -> "Help for your device"; else -> "یارمەتی بۆ ئامێرەکەت" }
private fun helpIntro(language: String, device: String) = when (language) { "ar" -> "اكتب الشركة أو الطراز للحصول على إرشادات خاصة. الجهاز المكتشف: $device"; "en" -> "Enter the brand or model for tailored instructions. Detected device: $device"; else -> "براند یان مۆدێل بنووسە بۆ ڕێنمایی تایبەت. ئامێری دۆزراوە: $device" }
private fun deviceLabel(language: String) = when (language) { "ar" -> "الشركة أو طراز الهاتف"; "en" -> "Phone brand or model"; else -> "براند یان مۆدێلی مۆبایل" }
private fun generateGuideLabel(language: String) = when (language) { "ar" -> "اعرض الإرشادات"; "en" -> "Show guide"; else -> "ڕێنمایی پیشان بدە" }
private fun backLabel(language: String) = when (language) { "ar" -> "رجوع"; "en" -> "Back"; else -> "گەڕانەوە" }
private fun closeLabel(language: String) = when (language) { "ar" -> "إغلاق"; "en" -> "Close"; else -> "داخستن" }
private fun nextLabel(language: String) = when (language) { "ar" -> "التالي"; "en" -> "Next"; else -> "دواتر" }
private fun finishLabel(language: String) = when (language) { "ar" -> "ابدأ الإعداد"; "en" -> "Start setup"; else -> "دەست بە ڕێکخستن بکە" }
