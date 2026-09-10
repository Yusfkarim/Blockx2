package com.agon.app.applock

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.localization.tr
import com.agon.app.settingsprotection.domain.AuthMode

/**
 * Full-screen gate that seals the whole app behind the configured PIN / username+password.
 *
 * Shown by [com.agon.app.MainActivity] before any real content whenever
 * [AppLockManager.mustChallenge] is true. Nothing behind it is composed, so no screen, button or
 * data is reachable until [onUnlocked] fires. Back press is consumed and cannot bypass it.
 */
@Composable
fun AppLockGate(
    language: String,
    onUnlocked: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: AppLockGateViewModel = viewModel(),
) {
    LaunchedEffect(viewModel) { viewModel.refreshAuthMode() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val shake = remember { Animatable(0f) }

    // The full-app gate consumes Back; modal verification gates return to their caller.
    BackHandler(enabled = true) { onBack?.invoke() }

    LaunchedEffect(state.unlocked) {
        if (state.unlocked) onUnlocked()
    }

    LaunchedEffect(state.error) {
        if (state.error) {
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

    val accent = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val biometricAvailable = remember {
        viewModel.biometricEnabled() && canUseBiometrics(context)
    }

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
                    .heightIn(max = if (imeVisible) 350.dp else 600.dp)
                    .verticalScroll(rememberScrollState())
                    .graphicsLayer { translationX = shake.value },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            if (onBack != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel(language))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(backLabel(language), style = MaterialTheme.typography.labelLarge)
                }
            }
            AnimatedVisibility(visible = !imeVisible) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.12f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Lock, null, Modifier.size(38.dp), tint = accent)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            Text(
                text = tr("app_lock_title", language),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            AnimatedVisibility(visible = !imeVisible || state.error) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (state.error) tr("app_lock_wrong", language) else tr("app_lock_subtitle", language),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(if (imeVisible) 10.dp else 28.dp))

            if (state.authMode == AuthMode.USERNAME_PASSWORD) {
                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::setUsername,
                    label = { Text(tr("username", language)) },
                    singleLine = true,
                    enabled = !state.verifying,
                    isError = state.error,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = state.pin,
                onValueChange = viewModel::setPin,
                label = {
                    Text(
                        if (state.authMode == AuthMode.USERNAME_PASSWORD) tr("password", language)
                        else tr("pin", language),
                    )
                },
                singleLine = true,
                enabled = !state.verifying,
                isError = state.error,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (state.authMode == AuthMode.USERNAME_PASSWORD) KeyboardType.Password else KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (state.verifying) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(tr("unlock", language), fontWeight = FontWeight.Bold)
                }
            }

                if (biometricAvailable && !imeVisible) {
                    Spacer(Modifier.height(18.dp))
                    IconButton(onClick = { launchBiometric(context, language, viewModel) }) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = tr("use_biometric", language),
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    // Auto-prompt biometric on first show when enabled.
    LaunchedEffect(biometricAvailable) {
        if (biometricAvailable) launchBiometric(context, language, viewModel)
    }
}

private fun backLabel(language: String): String = when (language) {
    "en" -> "Back"
    "ar" -> "رجوع"
    else -> "گەڕانەوە"
}

private fun canUseBiometrics(context: android.content.Context): Boolean = runCatching {
    BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
}.getOrDefault(false)

private fun launchBiometric(
    context: android.content.Context,
    language: String,
    viewModel: AppLockGateViewModel,
) {
    val activity = context as? FragmentActivity ?: return
    if (!canUseBiometrics(context)) return
    val prompt = BiometricPrompt(
        activity,
        androidx.core.content.ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                viewModel.onBiometricSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = Unit

            override fun onAuthenticationFailed() {
                viewModel.onBiometricRejected()
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("BlockX LaAbrah")
        .setSubtitle(
            when (language) {
                "en" -> "Verify your identity to open the app"
                "ar" -> "أكد هويتك لفتح التطبيق"
                else -> "ناسنامەت پشتڕاست بکەوە بۆ کردنەوەی ئەپەکە"
            },
        )
        .setNegativeButtonText(
            when (language) {
                "en" -> "Use PIN"
                "ar" -> "استخدم الرمز"
                else -> "PIN بەکاربهێنە"
            },
        )
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .setConfirmationRequired(false)
        .build()
    runCatching { prompt.authenticate(info) }
}

@Suppress("DEPRECATION")
private fun vibrateError(context: android.content.Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator?.vibrate(200)
        }
    }
}
