package com.agon.app.battery

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Green used for the success indicator; readable on both light and dark surfaces. */
private val SuccessGreen = Color(0xFF2E7D32)
private val SuccessGreenDark = Color(0xFF7BD88F)

/**
 * Battery optimization row for the existing Settings list.
 *
 * Behaviour:
 *  - reads the real state through `PowerManager.isIgnoringBatteryOptimizations()`,
 *  - tapping the row opens the official exemption dialog, falling back to the closest vendor
 *    battery screen when a skin blocks it,
 *  - the state is re-checked when the dialog returns and on every ON_RESUME, never polled,
 *  - once exempt the row is inert: no request is ever issued again.
 */
@Composable
fun BatteryOptimizationSetting(
    manager: BatteryOptimizationManager,
    language: String,
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(manager.readState()) }
    val exempt = state.isUnrestricted
    var attempted by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }

    // Result of the official dialog is authoritative, so re-read the platform state on return.
    val requestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        state = manager.readState()
        attempted = true
    }

    fun requestExemption() {
        // Never ask again once the exemption is already granted.
        val current = manager.readState()
        if (current.isUnrestricted) {
            state = current
            return
        }
        val official = manager.resolveOfficialRequestIntent()
        if (official != null) {
            val launched = runCatching { requestLauncher.launch(official) }.isSuccess
            if (launched) return
        }
        // Manufacturer blocked or hid the standard intent: go to the closest battery screen.
        val fallback = manager.resolveFallbackIntent()
        if (fallback != null) {
            val launched = runCatching {
                requestLauncher.launch(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (launched) return
        }
        // Nothing resolvable: the written guide is the remaining supported path.
        showGuide = true
    }

    // Re-verify whenever the user comes back to the app. Event driven, no polling.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, manager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state = manager.readState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val successColor = if (androidx.compose.foundation.isSystemInDarkTheme()) {
        SuccessGreenDark
    } else {
        SuccessGreen
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // Once exempt the row is informational only.
            .clickable(enabled = !exempt) { requestExemption() },
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = if (exempt) {
                        successColor.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (exempt) Icons.Default.CheckCircle else Icons.Outlined.BatterySaver,
                            contentDescription = null,
                            tint = if (exempt) successColor else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(titleText(language), fontWeight = FontWeight.Bold)
                    Text(
                        text = when (state.status) {
                            BatteryStatus.UNRESTRICTED -> successText(language)
                            BatteryStatus.RESTRICTED -> restrictedText(language)
                            BatteryStatus.OPTIMIZED -> disabledText(language)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (exempt) successColor else MaterialTheme.colorScheme.error,
                    )
                }
                if (exempt) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = successColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            // Shown only after a request that did not result in an exemption.
            AnimatedVisibility(visible = exempt) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = successColor.copy(alpha = 0.12f),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = successColor,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = successBannerText(language),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = successColor,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = !exempt && attempted) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = warningText(language),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            if (!exempt) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showGuide = true }) {
                        Text(
                            text = BatteryGuideContent.howToButton(language),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { requestExemption() }) {
                        if (attempted) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = if (attempted) retryText(language) else fixText(language),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    if (showGuide) {
        BatteryOptimizationGuideSheet(
            manager = manager,
            language = language,
            onDismiss = {
                showGuide = false
                state = manager.readState()
            },
        )
    }
}

private fun titleText(language: String) = when (language) {
    "en" -> "Battery optimization"
    "ar" -> "تحسين البطارية"
    else -> "ئۆپتیمایزی باتری"
}

private fun successText(language: String) = when (language) {
    "en" -> "Unrestricted - protection runs continuously"
    "ar" -> "غير مقيّد - الحماية تعمل باستمرار"
    else -> "بێ سنوور - پاراستن بەردەوامە"
}

/** The confirmation message required once the app is already unrestricted. */
private fun successBannerText(language: String) = when (language) {
    "en" -> "Battery optimization disabled successfully."
    "ar" -> "تم تعطيل تحسين البطارية بنجاح."
    else -> "ئۆپتیمایزی باتری بە سەرکەوتوویی ناچالاک کرا."
}

/** "Restricted" is worse than the default and needs its own wording. */
private fun restrictedText(language: String) = when (language) {
    "en" -> "Restricted - Android is blocking background protection"
    "ar" -> "مقيّد - يمنع Android الحماية في الخلفية"
    else -> "سنووردار - Android ڕێگری لە پاراستنی باکگراوند دەکات"
}

private fun disabledText(language: String) = when (language) {
    "en" -> "Enabled - protection may stop in the background"
    "ar" -> "مفعّل - قد تتوقف الحماية في الخلفية"
    else -> "چالاک - ڕەنگە پاراستن بوەستێت"
}

private fun warningText(language: String) = when (language) {
    "en" -> "Battery optimization is still on. Android may stop background protection, so blocked apps, websites and keywords can slip through. Tap Retry and choose \"Don't optimize\" or \"Allow\"."
    "ar" -> "تحسين البطارية ما زال مفعّلاً. قد يوقف Android الحماية في الخلفية فتتجاوز التطبيقات والمواقع والكلمات المحظورة الحجب. اضغط إعادة المحاولة واختر \"عدم التحسين\" أو \"سماح\"."
    else -> "ئۆپتیمایزی باتری هێشتا چالاکە. Android ڕەنگە پاراستنی باکگراوند بوەستێنێت و ئەپ و وێبسایت و وشە بلۆککراوەکان تێپەڕن. دووبارە هەوڵبدەرەوە و \"Don't optimize\" یان \"Allow\" هەڵبژێرە."
}

private fun fixText(language: String) = when (language) {
    "en" -> "Disable"
    "ar" -> "تعطيل"
    else -> "ناچالاککردن"
}

private fun retryText(language: String) = when (language) {
    "en" -> "Retry"
    "ar" -> "إعادة المحاولة"
    else -> "دووبارە هەوڵدان"
}
