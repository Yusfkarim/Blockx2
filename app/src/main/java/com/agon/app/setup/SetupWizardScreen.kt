package com.agon.app.setup

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.admin.ShieldDeviceAdminReceiver
import com.agon.app.admin.deviceAdminSettingsIntent
import com.agon.app.battery.BatteryOptimizationEntryPoint
import com.agon.app.data.ShieldRepository
import com.agon.app.data.ShieldState
import com.agon.app.localization.tr
import com.agon.app.settingsprotection.data.ProtectionCommitmentStore
import com.agon.app.settingsprotection.domain.PinOperationResult
import com.agon.app.settingsprotection.domain.PinPolicy
import com.agon.app.settingsprotection.domain.UsernamePolicy
import com.agon.app.settingsprotection.viewmodel.SettingsProtectionViewModel
import kotlinx.coroutines.flow.collectLatest

private const val STAGE_LANGUAGE = 0
private const val STAGE_CREDENTIAL = 1
private const val STAGE_TUTORIAL = 2
private const val STAGE_ADMIN = 3
private const val STAGE_ACCESSIBILITY = 4
private const val STAGE_BATTERY = 5
private const val STAGE_AUTOSTART = 6
private const val STAGE_NOTIFICATIONS = 7
private const val STAGE_DONE = 8
private const val REQUIRED_STEP_COUNT = 6

private enum class SelectedSetupMode {
    STRONG, PIN, USERNAME_PASSWORD
}

/**
 * Mandatory first-run setup. Language and a recovery-sensitive credential are configured before
 * the required Android permissions. A step cannot be skipped or advanced until its real system
 * state is active. VPN permission intentionally remains outside onboarding and optional.
 */
@Composable
fun SetupWizardScreen(
    state: ShieldState,
    language: String,
    onFinish: () -> Unit,
    protectionViewModel: SettingsProtectionViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val protectionState by protectionViewModel.state.collectAsStateWithLifecycle()
    val batteryManager = remember(context) { BatteryOptimizationEntryPoint.resolve(context) }
    val autostartManager = remember(context) { com.agon.app.health.AutostartManager(context) }
    var stage by rememberSaveable { mutableIntStateOf(STAGE_LANGUAGE) }
    var selectedLanguage by rememberSaveable { mutableStateOf<String?>(null) }
    var environmentRevision by remember { mutableIntStateOf(0) }
    var notificationRequestAttempted by rememberSaveable { mutableStateOf(false) }
    var showAccessibilityDisclosure by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        notificationRequestAttempted = true
        environmentRevision++
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ShieldRepository.refresh()
                protectionViewModel.refreshEnvironment()
                environmentRevision++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activeLanguage = selectedLanguage ?: language
    val adminActive = remember(environmentRevision) { isDeviceAdminActive(context) }
    val accessibilityActive = protectionState.accessibilityConnected || state.accessibilityEnabled
    val batteryUnrestricted = remember(environmentRevision) {
        runCatching { batteryManager.readState().isUnrestricted || batteryManager.isExempt() }.getOrDefault(false)
    }
    val autostartOk = remember(environmentRevision) { autostartManager.isSatisfied() }
    val notificationsEnabled = remember(environmentRevision) { notificationsEnabled(context) }
    val exactAlarmOk = remember(environmentRevision) {
        com.agon.app.health.ExactAlarmHelper.isAllowed(context) ||
            !com.agon.app.health.ExactAlarmHelper.requiresUserAction(context)
    }

    BackHandler(enabled = true) { }

    val setupStep = when (stage) {
        STAGE_CREDENTIAL -> 1
        STAGE_ADMIN -> 2
        STAGE_ACCESSIBILITY -> 3
        STAGE_BATTERY -> 4
        STAGE_AUTOSTART -> 5
        STAGE_NOTIFICATIONS -> 6
        else -> 0
    }
    val progress by animateFloatAsState(
        targetValue = if (stage == STAGE_DONE) 1f else setupStep.toFloat() / REQUIRED_STEP_COUNT,
        label = "mandatory-setup-progress",
    )

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (setupStep > 0) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
                Text(
                    "${tr("wizard_step", activeLanguage)} $setupStep ${tr("wizard_of", activeLanguage)} $REQUIRED_STEP_COUNT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime),
                contentAlignment = if (stage == STAGE_CREDENTIAL) Alignment.BottomCenter else Alignment.Center,
            ) {
                AnimatedContent(targetState = stage, label = "mandatory-setup-stage") { current ->
                    when (current) {
                        STAGE_LANGUAGE -> LanguagePane(
                            selectedLanguage = selectedLanguage,
                            onSelect = { selected ->
                                selectedLanguage = selected
                                ShieldRepository.setLanguage(selected)
                            },
                            onContinue = { stage = STAGE_CREDENTIAL },
                        )

                        STAGE_CREDENTIAL -> CredentialPane(
                            language = activeLanguage,
                            configured = protectionState.pinConfigured || ProtectionCommitmentStore.isStrongActive(context),
                            viewModel = protectionViewModel,
                            onBack = { stage = STAGE_LANGUAGE },
                            onNext = { stage = STAGE_TUTORIAL },
                        )

                        STAGE_TUTORIAL -> PermissionTutorialWizard(
                            language = activeLanguage,
                            onClose = { stage = STAGE_CREDENTIAL },
                            onComplete = { stage = STAGE_ADMIN },
                        )

                        STAGE_ADMIN -> PermissionPane(
                            icon = Icons.Default.AdminPanelSettings,
                            title = tr("step_admin_title", activeLanguage),
                            description = tr("step_admin_desc", activeLanguage),
                            active = adminActive,
                            language = activeLanguage,
                            onOpenSettings = {
                                protectionViewModel.allowPermissionEnable()
                                context.startActivity(deviceAdminSettingsIntent(context))
                            },
                            onNext = { stage = STAGE_ACCESSIBILITY },
                        )

                        STAGE_ACCESSIBILITY -> PermissionPane(
                            icon = Icons.Default.AccessibilityNew,
                            title = tr("step_accessibility_title", activeLanguage),
                            description = tr("step_accessibility_desc", activeLanguage),
                            active = accessibilityActive,
                            language = activeLanguage,
                            onOpenSettings = { showAccessibilityDisclosure = true },
                            onNext = { stage = STAGE_BATTERY },
                        )

                        STAGE_BATTERY -> PermissionPane(
                            icon = Icons.Default.BatteryChargingFull,
                            title = tr("step_battery_title", activeLanguage),
                            description = tr("step_battery_desc", activeLanguage),
                            active = batteryUnrestricted,
                            language = activeLanguage,
                            onOpenSettings = {
                                protectionViewModel.allowPermissionEnable()
                                batteryManager.resolveSettingsIntent()?.let { target ->
                                    context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            },
                            onNext = { stage = STAGE_AUTOSTART },
                        )

                        STAGE_AUTOSTART -> AutostartSetupPane(
                            language = activeLanguage,
                            active = autostartOk,
                            onOpenSettings = {
                                protectionViewModel.allowPermissionEnable()
                                autostartManager.resolveSettingsIntent()?.let { context.startActivity(it) }
                            },
                            onConfirmConfigured = {
                                autostartManager.markConfigured()
                                environmentRevision++
                            },
                            onNext = { stage = STAGE_NOTIFICATIONS },
                        )

                        STAGE_NOTIFICATIONS -> PermissionPane(
                            icon = Icons.Default.Notifications,
                            title = tr("step_notifications_title", activeLanguage),
                            description = tr("step_notifications_desc", activeLanguage),
                            active = notificationsEnabled,
                            language = activeLanguage,
                            onOpenSettings = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                                    !notificationRequestAttempted
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                    )
                                }
                            },
                            onNext = {
                                // Exact alarm: open settings if needed, but do not hard-block finish on all OEMs.
                                if (!exactAlarmOk) {
                                    runCatching {
                                        context.startActivity(
                                            com.agon.app.health.ExactAlarmHelper.settingsIntent(context),
                                        )
                                    }
                                }
                                com.agon.app.health.NotificationHealth.ensureChannels(context)
                                com.agon.app.services.KeepAliveScheduler.schedule(context)
                                stage = STAGE_DONE
                            },
                        )

                        else -> DonePane(
                            language = activeLanguage,
                            canFinish = true,
                            onFinish = onFinish,
                        )
                    }
                }
            }
        }
    }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            language = activeLanguage,
            onAllow = {
                showAccessibilityDisclosure = false
                protectionViewModel.allowPermissionEnable()
                AccessibilityConsent.openSettings(context)
            },
            onDismiss = { showAccessibilityDisclosure = false },
        )
    }
}

/**
 * Mandatory repair gate used after onboarding. If a required system protection is revoked, app
 * content is not composed until the user restores it. The first missing permission is shown.
 */
@Composable
fun RequiredProtectionGate(
    language: String,
    adminActive: Boolean,
    accessibilityActive: Boolean,
    batteryUnrestricted: Boolean,
    protectionViewModel: SettingsProtectionViewModel = viewModel(),
) {
    val context = LocalContext.current
    val batteryManager = remember(context) { BatteryOptimizationEntryPoint.resolve(context) }
    val autostartManager = remember(context) { com.agon.app.health.AutostartManager(context) }
    val autostartOk = autostartManager.isSatisfied()
    BackHandler(enabled = true) { }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !adminActive -> PermissionPane(
                    icon = Icons.Default.AdminPanelSettings,
                    title = tr("step_admin_title", language),
                    description = tr("step_admin_desc", language),
                    active = false,
                    language = language,
                    onOpenSettings = {
                        protectionViewModel.allowPermissionEnable()
                        context.startActivity(deviceAdminSettingsIntent(context))
                    },
                    onNext = {},
                )

                !accessibilityActive -> PermissionPane(
                    icon = Icons.Default.AccessibilityNew,
                    title = tr("step_accessibility_title", language),
                    description = tr("step_accessibility_desc", language),
                    active = false,
                    language = language,
                    onOpenSettings = {
                        protectionViewModel.allowPermissionEnable()
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onNext = {},
                )

                !batteryUnrestricted -> PermissionPane(
                    icon = Icons.Default.BatteryChargingFull,
                    title = tr("step_battery_title", language),
                    description = tr("step_battery_desc", language),
                    active = false,
                    language = language,
                    onOpenSettings = {
                        protectionViewModel.allowPermissionEnable()
                        batteryManager.resolveSettingsIntent()?.let { target ->
                            context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                    onNext = {},
                )

                !autostartOk -> AutostartSetupPane(
                    language = language,
                    active = false,
                    onOpenSettings = {
                        protectionViewModel.allowPermissionEnable()
                        autostartManager.resolveSettingsIntent()?.let { context.startActivity(it) }
                    },
                    onConfirmConfigured = { autostartManager.markConfigured() },
                    onNext = {},
                )
            }
        }
    }
}

@Composable
private fun LanguagePane(
    selectedLanguage: String?,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SetupIcon(Icons.Default.Language, active = selectedLanguage != null)
        Text(
            text = "زمان هەڵبژێرە · اختر اللغة · Choose language",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "هەموو هەنگاوەکان بە زمانی هەڵبژێردراو پیشان دەدرێن\nستظهر جميع الخطوات باللغة المختارة\nAll steps will use your selected language",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        listOf(
            "ku" to "کوردی",
            "ar" to "العربية",
            "en" to "English",
        ).forEach { (code, label) ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(code) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedLanguage == code) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    if (selectedLanguage == code) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Button(
            onClick = onContinue,
            enabled = selectedLanguage != null,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(
                when (selectedLanguage) {
                    "ar" -> "متابعة"
                    "en" -> "Continue"
                    else -> "بەردەوامبوون"
                },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CredentialPane(
    language: String,
    configured: Boolean,
    viewModel: SettingsProtectionViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val commitmentContext = LocalContext.current
    var setupMode by rememberSaveable { mutableStateOf(SelectedSetupMode.STRONG) }
    var username by rememberSaveable { mutableStateOf("") }
    var secret by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var commitmentDays by rememberSaveable { mutableStateOf<Int?>(ProtectionCommitmentStore.TEST_5_MINUTES) }

    LaunchedEffect(viewModel) {
        viewModel.results.collectLatest { result ->
            saving = false
            error = when (result) {
                PinOperationResult.Success -> {
                    onNext()
                    null
                }
                PinOperationResult.InvalidLength -> tr("setup_credential_invalid", language)
                PinOperationResult.Mismatch -> tr("setup_credential_mismatch", language)
                PinOperationResult.StorageFailure -> tr("setup_credential_storage_error", language)
                else -> tr("setup_credential_error", language)
            }
        }
    }

    val isFormValid = when (setupMode) {
        SelectedSetupMode.STRONG -> commitmentDays != null
        SelectedSetupMode.PIN -> PinPolicy.isValid(secret) && secret == confirmation && commitmentDays != null
        SelectedSetupMode.USERNAME_PASSWORD -> UsernamePolicy.isValid(username) &&
            PinPolicy.isValid(secret) && secret == confirmation && commitmentDays != null
    }

    if (configured) {
        CompletedPane(
            icon = Icons.Default.Lock,
            title = tr("setup_credential_ready", language),
            description = tr("setup_credential_ready_desc", language),
            language = language,
            onNext = onNext,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("setup_back", language))
            }
            Spacer(Modifier.size(4.dp))
            Text(tr("setup_back", language), style = MaterialTheme.typography.labelLarge)
        }
        SetupIcon(Icons.Default.Lock, active = false)
        Text(
            text = when (language) {
                "en" -> "Protection Mode & Security"
                "ar" -> "نوع الحماية والأمان"
                else -> "جۆری پاراستن و ئاسایش"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = when (language) {
                "en" -> "Choose between Strict Timed Strong Protection (No PIN required) or Standard PIN Protection."
                "ar" -> "اختر بين الحماية القوية المحددة بمدة (بدون الحاجة لرمز PIN) أو الحماية العادية برمز PIN."
                else -> "لە نێوان پاراستنی بەهێزی کاتی (بەبێ پێویستی بە PIN) یان پاراستنی ئاسایی بە PIN هەڵبژێرە."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // Protection Mode Chips: Strong vs PIN vs Username
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = setupMode == SelectedSetupMode.STRONG,
                onClick = {
                    setupMode = SelectedSetupMode.STRONG
                    commitmentDays = ProtectionCommitmentStore.TEST_5_MINUTES
                    error = null
                },
                label = {
                    Text(
                        when (language) {
                            "en" -> "🔒 Strong"
                            "ar" -> "🔒 حماية قوية"
                            else -> "🔒 بەهێز"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    )
                },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = setupMode == SelectedSetupMode.PIN,
                onClick = {
                    setupMode = SelectedSetupMode.PIN
                    commitmentDays = ProtectionCommitmentStore.TEST_5_MINUTES
                    error = null
                },
                label = {
                    Text(
                        when (language) {
                            "en" -> "🔑 PIN Code"
                            "ar" -> "🔑 رمز الـ PIN"
                            else -> "🔑 ڕەمزی PIN"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    )
                },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = setupMode == SelectedSetupMode.USERNAME_PASSWORD,
                onClick = {
                    setupMode = SelectedSetupMode.USERNAME_PASSWORD
                    commitmentDays = ProtectionCommitmentStore.TEST_5_MINUTES
                    error = null
                },
                label = {
                    Text(
                        when (language) {
                            "en" -> "👤 User"
                            "ar" -> "👤 اسم مستخدم"
                            else -> "👤 بەکارهێنەر"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }

        if (setupMode == SelectedSetupMode.STRONG) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = when (language) {
                        "en" -> "🔒 Strong Protection: Non-bypassable strict lock without PIN. Settings, protection toggles, and uninstall will be locked until the selected duration ends completely."
                        "ar" -> "🔒 الحماية القوية: قفل صارم ومباشر بدون رمز PIN. لا يمكن إيقاف الحماية أو إلغاء تثبيت التطبيق أو الدخول للإعدادات المحمية حتى انتهاء المدة المحددة."
                        else -> "🔒 پاراستنی بەهێز: قوفڵی توند و کاتی بەبێ پێویستی بە PIN. ناتوانرێت پاراستن بوەستێنرێت یان ئەپەکە بسڕدرێتەوە یان دەستکاری ڕێکخستنەکان بکرێت تا تەواوبوونی کاتی دیاریکراو."
                    },
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            CommitmentPicker(
                language = language,
                isStrong = true,
                selectedDays = commitmentDays,
                onSelect = { commitmentDays = it },
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = tr("setup_credential_warning", language),
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (setupMode == SelectedSetupMode.USERNAME_PASSWORD) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.filter { char -> !char.isWhitespace() }.take(UsernamePolicy.MAX_LENGTH); error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(tr("setup_username", language)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
            }
            OutlinedTextField(
                value = secret,
                onValueChange = {
                    secret = if (setupMode == SelectedSetupMode.PIN) {
                        PinPolicy.sanitize(it)
                    } else {
                        it.filter { char -> !char.isWhitespace() }.take(PinPolicy.MAX_LENGTH)
                    }
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        tr(
                            if (setupMode == SelectedSetupMode.PIN) "setup_pin" else "setup_password",
                            language,
                        ),
                    )
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (setupMode == SelectedSetupMode.PIN) KeyboardType.NumberPassword else KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
            )
            OutlinedTextField(
                value = confirmation,
                onValueChange = {
                    confirmation = if (setupMode == SelectedSetupMode.PIN) {
                        PinPolicy.sanitize(it)
                    } else {
                        it.filter { char -> !char.isWhitespace() }.take(PinPolicy.MAX_LENGTH)
                    }
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(tr("setup_confirm_secret", language)) },
                singleLine = true,
                isError = error != null || (confirmation.isNotEmpty() && secret != confirmation),
                supportingText = error?.let { message -> ({ Text(message) }) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (setupMode == SelectedSetupMode.PIN) KeyboardType.NumberPassword else KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            )

            CommitmentPicker(
                language = language,
                isStrong = false,
                selectedDays = commitmentDays,
                onSelect = { commitmentDays = it },
            )
        }

        Button(
            onClick = {
                val chosen = commitmentDays ?: return@Button
                if (setupMode == SelectedSetupMode.STRONG) {
                    ProtectionCommitmentStore.setStrongDays(commitmentContext, chosen)
                    onNext()
                } else {
                    if (chosen == ProtectionCommitmentStore.FOREVER.toInt()) {
                        ProtectionCommitmentStore.setForever(commitmentContext)
                    } else {
                        ProtectionCommitmentStore.setDays(commitmentContext, chosen)
                    }
                    saving = true
                    if (setupMode == SelectedSetupMode.PIN) {
                        viewModel.savePin(secret, confirmation, "")
                    } else {
                        viewModel.saveUsernamePassword(username, secret, confirmation, "")
                    }
                }
            },
            enabled = isFormValid && !saving,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(
                when (setupMode) {
                    SelectedSetupMode.STRONG -> when (language) {
                        "en" -> "Activate Strong Protection"
                        "ar" -> "تفعيل الحماية القوية"
                        else -> "چالاککردنی پاراستنی بەهێز"
                    }
                    else -> tr("setup_save_credential", language)
                },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Lets the user choose how long the protection commitment lasts before the credential is saved.
 */
@Composable
private fun CommitmentPicker(
    language: String,
    isStrong: Boolean,
    selectedDays: Int?,
    onSelect: (Int) -> Unit,
) {
    val heading = when (language) {
        "en" -> if (isStrong) "Choose Strong Protection Duration:" else "Commitment duration:"
        "ar" -> if (isStrong) "اختر مدة الحماية القوية:" else "لمدة كم يجب طلب رمز PIN للحماية؟"
        else -> if (isStrong) "ماوەی پاراستنی بەهێز هەڵبژێرە:" else "بۆ چەند ماوەیەک PIN داوا بکرێت؟"
    }
    val foreverLabel = when (language) {
        "en" -> "Forever"
        "ar" -> "إلى الأبد"
        else -> "هەتاهەتایی"
    }
    fun dayLabel(option: Int): String =
        ProtectionCommitmentStore.formatOptionLabel(option, language)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            heading,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        val options = if (isStrong) ProtectionCommitmentStore.STRONG_DAY_OPTIONS else ProtectionCommitmentStore.NORMAL_DAY_OPTIONS
        options.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { days ->
                    FilterChip(
                        selected = selectedDays == days,
                        onClick = { onSelect(days) },
                        label = {
                            Text(
                                dayLabel(days),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (!isStrong) {
            FilterChip(
                selected = selectedDays == ProtectionCommitmentStore.FOREVER.toInt(),
                onClick = { onSelect(ProtectionCommitmentStore.FOREVER.toInt()) },
                label = {
                    Text(
                        foreverLabel,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PermissionPane(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    active: Boolean,
    language: String,
    onOpenSettings: () -> Unit,
    onNext: () -> Unit,
) {
    if (active) {
        CompletedPane(
            icon = icon,
            title = title,
            description = tr("wizard_enabled", language),
            language = language,
            onNext = onNext,
        )
        return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SetupIcon(icon, active = false)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(tr("wizard_enable", language), fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(tr("wizard_skip", language), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CompletedPane(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    language: String,
    onNext: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SetupIcon(icon, active = true)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(tr("wizard_next", language), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SetupIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
) {
    Surface(
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(112.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (active) Icons.Default.CheckCircle else icon,
                contentDescription = null,
                modifier = Modifier.size(58.dp),
                tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DonePane(language: String, canFinish: Boolean, onFinish: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SetupIcon(Icons.Default.Shield, active = canFinish)
        Text(tr("wizard_done", language), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(
            if (canFinish) tr("wizard_done_desc", language) else tr("wizard_required_hint", language),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onFinish,
            enabled = canFinish,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text(tr("wizard_finish", language), fontWeight = FontWeight.Bold)
        }
    }
}

private fun isDeviceAdminActive(context: Context): Boolean {
    val component = ComponentName(context, ShieldDeviceAdminReceiver::class.java)
    return runCatching {
        context.getSystemService(DevicePolicyManager::class.java)?.isAdminActive(component) == true
    }.getOrDefault(false)
}

private fun notificationsEnabled(context: Context): Boolean {
    val runtimePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return runtimePermission && NotificationManagerCompat.from(context).areNotificationsEnabled()
}
