package com.agon.app.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun AccessibilityDisclosureDialog(
    language: String,
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
        icon = {
            Icon(
                Icons.Outlined.AccessibilityNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                when (language) {
                    "en" -> "AccessibilityService disclosure"
                    "ar" -> "إفصاح AccessibilityService"
                    else -> "ئاشکراکردنی AccessibilityService"
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    when (language) {
                        "en" ->
                            "UltraBlock X by LaAbrah uses Android AccessibilityService to detect blocked apps, " +
                                "browser screens, keywords, and restricted content based on the rules you choose.\n\n" +
                                "Why it is needed: to enforce the content-filtering and parental/protection features " +
                                "you turn on in the app.\n\n" +
                                "What it never does: it does not collect, transmit, or share personal data, record audio, " +
                                "or provide remote control access."
                        "ar" ->
                            "يستخدم UltraBlock X by LaAbrah خدمة AccessibilityService لاكتشاف التطبيقات المحظورة " +
                                "وشاشات المتصفح والكلمات والمحتوى المقيّد وفق القواعد التي تختارها.\n\n" +
                                "لماذا هي مطلوبة: لتطبيق تصفية المحتوى وحماية العائلة التي تفعّلها في التطبيق.\n\n" +
                                "ما لا تفعله: لا تجمع البيانات الشخصية ولا ترسلها ولا تشاركها، ولا تسجّل الصوت، ولا تتحكم عن بُعد."
                        else ->
                            "UltraBlock X by LaAbrah خزمەتگوزاری Accessibility بەکاردەهێنێت بۆ دۆزینەوەی ئەپی بلۆککراو، " +
                                "شاشەی براوسەر، وشە و ناوەڕۆکی سنووردار بەپێی یاساکانت.\n\n" +
                                "بۆچی پێویستە: بۆ فلتەرکردنی ناوەڕۆک و پاراستنی خێزان کە خۆت دەیکەیتەوە.\n\n" +
                                "چی ناکات: داتای کەسی کۆناکاتەوە و نانێرێت، دەنگ تۆمار ناکات، ئامێر لە دوورەوە کۆنترۆڵ ناکات."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    when (language) {
                        "en" -> "Tap Allow to open Android Accessibility settings."
                        "ar" -> "اضغط سماح لفتح إعدادات إمكانية الوصول."
                        else -> "ڕێگەبدە دابگرە بۆ کردنەوەی ڕێکخستنی Accessibility."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text(
                    when (language) {
                        "en" -> "Allow"
                        "ar" -> "سماح"
                        else -> "ڕێگەبدە"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    when (language) {
                        "en" -> "Not now"
                        "ar" -> "ليس الآن"
                        else -> "ئێستا نا"
                    },
                )
            }
        },
    )
}
