package com.agon.app.settingsprotection.ui

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.settingsprotection.data.ProtectionCommitmentStore
import com.agon.app.settingsprotection.domain.AuthMode
import com.agon.app.settingsprotection.domain.ProtectedScreenType
import com.agon.app.settingsprotection.viewmodel.PinLockViewModel
import kotlinx.coroutines.flow.collectLatest

/** Material 3 PIN keypad / Strong Protection overlay shown above the protected system settings window. */
@Composable
fun PinLockScreen(
    viewModel: PinLockViewModel,
    language: String,
    onCancel: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }

    BackHandler(onBack = onCancel)

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            if (event is PinLockViewModel.Event.WrongPinFeedback) {
                vibrateError(context)
                shake.snapTo(0f)
                shake.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 420
                        0f at 0
                        -18f at 60
                        16f at 120
                        -12f at 180
                        9f at 240
                        -5f at 300
                        0f at 420
                    },
                )
            }
        }
    }

    val accent = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = if (imeVisible) Alignment.BottomCenter else Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (imeVisible) 370.dp else 640.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel(language))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(backLabel(language), style = MaterialTheme.typography.labelLarge)
                }

                if (state.isStrongProtectionActive) {
                    // Strong Protection is active: display countdown & prevent access without showing PIN keypad.
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.size(76.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = strongLockTitle(language),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = strongLockMessage(language),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = strongRemainingPrefix(language) +
                                        ProtectionCommitmentStore.formatStrongRemaining(
                                            state.remainingStrongMillis,
                                            language,
                                        ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = strongLockWarning(language),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(returnHomeLabel(language), fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Standard Protection: PIN keypad / password entry to unlock.
                    val badgeScale by animateFloatAsState(
                        targetValue = if (state.error) 1.06f else 1f,
                        animationSpec = tween(220),
                        label = "badge",
                    )
                    AnimatedVisibility(visible = !imeVisible) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                modifier = Modifier
                                    .size(72.dp)
                                    .scale(badgeScale),
                                shape = CircleShape,
                                color = if (state.error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (state.error) Icons.Default.Lock else Icons.Default.Shield,
                                        contentDescription = null,
                                        modifier = Modifier.size(38.dp),
                                        tint = accent,
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    Text(
                        text = lockTitle(language),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    AnimatedVisibility(visible = !imeVisible || state.error) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (state.error) wrongPinMessage(language) else screenMessage(state.screenType, language),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(if (imeVisible) 10.dp else 30.dp))
                    if (state.authMode == AuthMode.USERNAME_PASSWORD) {
                        OutlinedTextField(
                            value = state.username,
                            onValueChange = { viewModel.setUsername(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(x = shake.value.dp),
                            singleLine = true,
                            enabled = !state.verifying,
                            isError = state.error,
                            label = { Text(usernameLabel(language)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    if (state.authMode == AuthMode.USERNAME_PASSWORD) {
                        OutlinedTextField(
                            value = state.pin,
                            onValueChange = { viewModel.setPin(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(x = shake.value.dp),
                            singleLine = true,
                            enabled = !state.verifying,
                            isError = state.error,
                            label = { Text(passwordLabel(language)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                            trailingIcon = {
                                if (state.biometricEnabled) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "Biometric",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clickable {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.requestBiometric()
                                            },
                                    )
                                }
                            },
                        )
                    } else {
                        PinDotsRow(
                            expected = state.expectedLength,
                            filled = state.pin.length,
                            error = state.error,
                            modifier = Modifier.offset(x = shake.value.dp),
                        )
                        Spacer(Modifier.height(if (imeVisible) 8.dp else 22.dp))
                        Keypad(
                            enabled = !state.verifying,
                            biometricEnabled = state.biometricEnabled,
                            language = language,
                            onDigit = { digit ->
                                vibrateTick(context)
                                viewModel.append(digit)
                            },
                            onBackspace = {
                                vibrateTick(context)
                                viewModel.backspace()
                            },
                            onBiometric = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.requestBiometric()
                            },
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) {
                        if (state.verifying) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        }
                    }

                    if (state.authMode == AuthMode.USERNAME_PASSWORD) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.submit()
                            },
                            enabled = state.canSubmit,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(unlockLabel(language), fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = warningMessage(language),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Animated PIN progress dots; the row participates in the wrong-PIN shake. */
@Composable
private fun PinDotsRow(
    expected: Int,
    filled: Int,
    error: Boolean,
    modifier: Modifier = Modifier,
) {
    val dotCount = expected.coerceIn(4, 12)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(dotCount) { index ->
            val isFilled = index < filled
            val dotScale by animateFloatAsState(
                targetValue = if (isFilled) 1f else 0.82f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "pinDot",
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .scale(dotScale)
                    .clip(CircleShape)
                    .background(
                        when {
                            error -> MaterialTheme.colorScheme.error
                            isFilled -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ),
            )
        }
    }
}

private val ARABIC_INDIC_DIGITS = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")

private fun localizedDigit(digit: Char, language: String): String =
    if (language == "en") digit.toString() else ARABIC_INDIC_DIGITS[digit - '0']

@Composable
private fun Keypad(
    enabled: Boolean,
    biometricEnabled: Boolean,
    language: String,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { digit ->
                    KeypadKey(enabled = enabled, onClick = { onDigit(digit) }) {
                        Text(
                            text = localizedDigit(digit, language),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (biometricEnabled) {
                KeypadKey(enabled = enabled, onClick = onBiometric) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = "Biometric",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            } else {
                Spacer(Modifier.size(72.dp))
            }
            KeypadKey(enabled = enabled, onClick = { onDigit('0') }) {
                Text(
                    text = localizedDigit('0', language),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            KeypadKey(enabled = enabled, onClick = onBackspace) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun KeypadKey(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.45f
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(72.dp)
            .alpha(alpha),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

private fun vibrateTick(context: android.content.Context) {
    val vibrator = resolveVibrator(context) ?: return
    if (!vibrator.hasVibrator()) return
    runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(18, 60))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(18)
        }
    }
}

private fun vibrateError(context: android.content.Context) {
    val vibrator = resolveVibrator(context) ?: return
    if (!vibrator.hasVibrator()) return
    runCatching {
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 60, 70, 120), intArrayOf(0, 180, 0, 220), -1),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 60, 70, 120), -1)
        }
    }
}

private fun resolveVibrator(context: android.content.Context): Vibrator? = runCatching {
    if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
}.getOrNull()

private fun backLabel(language: String): String = when (language) {
    "en" -> "Back"
    "ar" -> "رجوع"
    else -> "گەڕانەوە"
}

private fun lockTitle(language: String): String = when (language) {
    "en" -> "Protected settings"
    "ar" -> "إعدادات محمية"
    else -> "ڕێکخستنی پارێزراو"
}

private fun passcodeLabel(language: String): String = when (language) {
    "en" -> "Passcode"
    "ar" -> "رمز الحماية"
    else -> "ڕەمز"
}

private fun usernameLabel(language: String): String = when (language) {
    "en" -> "Username"
    "ar" -> "اسم المستخدم"
    else -> "ناوی بەکارهێنەر"
}

private fun passwordLabel(language: String): String = when (language) {
    "en" -> "Password"
    "ar" -> "كلمة المرور"
    else -> "وشەی نهێنی"
}

private fun unlockLabel(language: String): String = when (language) {
    "en" -> "Unlock"
    "ar" -> "فتح"
    else -> "کردنەوە"
}

private fun wrongPinMessage(language: String): String = when (language) {
    "en" -> "Incorrect PIN. Returning to the home screen."
    "ar" -> "الرمز غير صحيح. العودة إلى الشاشة الرئيسية."
    else -> "PIN هەڵەیە. گەڕانەوە بۆ شاشەی سەرەکی."
}

private fun warningMessage(language: String): String = when (language) {
    "en" -> "BlockX LaAbrah protects these settings. Enter the configured protection credential."
    "ar" -> "يحمي BlockX LaAbrah هذه الإعدادات. أدخل بيانات الحماية التي تم إعدادها."
    else -> "BlockX LaAbrah ئەم ڕێکخستنانە دەپارێزێت. زانیارییەکانی پاراستنی دانراو بنووسە."
}

private fun strongLockTitle(language: String): String = when (language) {
    "en" -> "🔒 Strong Protection Active"
    "ar" -> "🔒 الحماية القوية مفعلة"
    else -> "🔒 پاراستنی بەهێز چالاکە"
}

private fun strongLockMessage(language: String): String = when (language) {
    "en" -> "You cannot access protection settings until the protection period ends."
    "ar" -> "لا يمكنك الوصول إلى إعدادات الحماية حتى انتهاء المدة."
    else -> "ناتوانیت دەستت بگات بە ڕێکخستنەکانی پاراستن تا تەواوبوونی ماوەکە."
}

private fun strongRemainingPrefix(language: String): String = when (language) {
    "en" -> "Remaining time: "
    "ar" -> "الوقت المتبقي: "
    else -> "کاتی ماوە: "
}

private fun strongLockWarning(language: String): String = when (language) {
    "en" -> "Protection cannot be disabled until the specified duration ends."
    "ar" -> "لا يمكن إلغاء الحماية حتى انتهاء المدة المحددة."
    else -> "ناتوانرێت پاراستن لاببرێت تا تەواوبوونی ماوەی دیاریکراو."
}

private fun returnHomeLabel(language: String): String = when (language) {
    "en" -> "Return to Home"
    "ar" -> "الرجوع إلى الشاشة الرئيسية"
    else -> "گەڕانەوە بۆ شاشەی سەرەکی"
}

private fun screenMessage(type: ProtectedScreenType, language: String): String = when (language) {
    "en" -> when (type) {
        ProtectedScreenType.UNINSTALL -> "Enter your PIN to allow uninstalling BlockX LaAbrah."
        ProtectedScreenType.FORCE_STOP -> "Enter your PIN to allow force stopping protection."
        ProtectedScreenType.CLEAR_DATA -> "Enter your PIN to allow clearing protection data."
        ProtectedScreenType.PERMISSIONS -> "Enter your PIN to change app permissions."
        ProtectedScreenType.DEVICE_ADMIN -> "Enter your PIN to change device administrator settings."
        ProtectedScreenType.SECURITY_SETTINGS -> "Enter your PIN to open security settings."
        ProtectedScreenType.MANAGE_APPS -> "Enter your PIN to manage installed apps."
        ProtectedScreenType.ACCESSIBILITY_SETTINGS -> "Enter your PIN to change accessibility settings."
        ProtectedScreenType.SAFE_MODE -> "Enter your PIN to allow Safe mode (Safe mode turns protection off)."
        ProtectedScreenType.POWER_MENU -> "Enter your PIN to allow Power off or Restart."
        ProtectedScreenType.FACTORY_RESET -> "Enter your PIN to allow factory reset / erase all data."
        ProtectedScreenType.SYSTEM_UPDATE_DELETE -> "Enter your PIN to allow deleting the system update."
        ProtectedScreenType.DEVELOPER_OPTIONS -> "Enter your PIN to open Developer options (USB debugging / OEM unlocking)."
        else -> "Enter your PIN to open the App info screen."
    }
    "ar" -> when (type) {
        ProtectedScreenType.UNINSTALL -> "أدخل الرمز للسماح بإلغاء تثبيت BlockX LaAbrah."
        ProtectedScreenType.FORCE_STOP -> "أدخل الرمز للسماح بالإيقاف القسري للحماية."
        ProtectedScreenType.CLEAR_DATA -> "أدخل الرمز للسماح بمحو بيانات الحماية."
        ProtectedScreenType.PERMISSIONS -> "أدخل الرمز لتغيير أذونات التطبيق."
        ProtectedScreenType.DEVICE_ADMIN -> "أدخل الرمز لتغيير إعدادات مسؤول الجهاز."
        ProtectedScreenType.SECURITY_SETTINGS -> "أدخل الرمز لفتح إعدادات الأمان."
        ProtectedScreenType.MANAGE_APPS -> "أدخل الرمز لإدارة التطبيقات المثبتة."
        ProtectedScreenType.ACCESSIBILITY_SETTINGS -> "أدخل الرمز لتغيير إعدادات إمكانية الوصول."
        ProtectedScreenType.SAFE_MODE -> "أدخل الرمز للسماح بالوضع الآمن (الوضع الآمن يوقف الحماية)."
        ProtectedScreenType.POWER_MENU -> "أدخل الرمز للسماح بإيقاف التشغيل أو إعادة التشغيل."
        ProtectedScreenType.FACTORY_RESET -> "أدخل الرمز للسماح بإعادة ضبط المصنع / مسح كل البيانات."
        ProtectedScreenType.SYSTEM_UPDATE_DELETE -> "أدخل الرمز للسماح بحذف تحديث النظام."
        ProtectedScreenType.DEVELOPER_OPTIONS -> "أدخل الرمز لفتح خيارات المطور (تصحيح USB / إلغاء قفل OEM)."
        else -> "أدخل الرمز لفتح صفحة معلومات التطبيق."
    }
    else -> when (type) {
        ProtectedScreenType.UNINSTALL -> "PIN بنووسە بۆ ڕێگەدان بە لابردنی BlockX LaAbrah."
        ProtectedScreenType.FORCE_STOP -> "PIN بنووسە بۆ ڕێگەدان بە ڕاگرتنی بەزۆری پاراستن."
        ProtectedScreenType.CLEAR_DATA -> "PIN بنووسە بۆ ڕێگەدان بە سڕینەوەی داتای پاراستن."
        ProtectedScreenType.PERMISSIONS -> "PIN بنووسە بۆ گۆڕینی مۆڵەتەکانی ئەپ."
        ProtectedScreenType.DEVICE_ADMIN -> "PIN بنووسە بۆ گۆڕینی ڕێکخستنی بەڕێوەبەری ئامێر."
        ProtectedScreenType.SECURITY_SETTINGS -> "PIN بنووسە بۆ کردنەوەی ڕێکخستنی ئاسایش."
        ProtectedScreenType.MANAGE_APPS -> "PIN بنووسە بۆ بەڕێوەبردنی ئەپە دامەزراوەکان."
        ProtectedScreenType.ACCESSIBILITY_SETTINGS -> "PIN بنووسە بۆ گۆڕینی ڕێکخستنی ڕەسەنایەتی (Accessibility)."
        ProtectedScreenType.SAFE_MODE -> "PIN بنووسە بۆ ڕێگەدان بە دۆخی سەلامەت (Safe mode پاراستن دەکوژێتەوە)."
        ProtectedScreenType.POWER_MENU -> "PIN بنووسە بۆ ڕێگەدان بە کوژاندنەوە یان ڕیبوت."
        ProtectedScreenType.FACTORY_RESET -> "PIN بنووسە بۆ ڕێگەدان بە ڕێکخستنەوەی کارگە / سڕینەوەی هەموو داتا."
        ProtectedScreenType.SYSTEM_UPDATE_DELETE -> "PIN بنووسە بۆ ڕێگەدان بە سڕینەوەی نوێکردنەوەی سیستەم."
        ProtectedScreenType.DEVELOPER_OPTIONS -> "PIN بنووسە بۆ کردنەوەی بژاردەکانی گەشەپێدەر (دیبەگی USB / کردنەوەی قوفڵی OEM)."
        else -> "PIN بنووسە بۆ کردنەوەی پەڕەی زانیاری ئەپ."
    }
}
