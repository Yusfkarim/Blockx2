package com.agon.app

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.content.ComponentName
import android.app.admin.DevicePolicyManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.net.Uri
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.agon.app.blocklist.data.ShortVideoPreferences
import com.agon.app.blocklist.domain.ShortVideoPolicy
import com.agon.app.blocklist.domain.formatTimedRemaining
import com.agon.app.data.BlockLog
import com.agon.app.quarantine.NewAppQuarantineStore
import com.agon.app.reports.ReportsScreen
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.battery.BatteryOptimizationEntryPoint
import com.agon.app.battery.BatteryOptimizationSetting
import com.agon.app.settingsprotection.ui.ProtectionPauseGate
import com.agon.app.settingsprotection.ui.SettingsProtectionSection
import com.agon.app.data.ShieldRepository
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.PlayArrow
import com.agon.app.settingsprotection.data.ProtectionCommitmentStore
import com.agon.app.settingsprotection.domain.PinPolicy
import com.agon.app.settingsprotection.domain.UsernamePolicy
import com.agon.app.vpn.FamilyVpnService
import com.agon.app.localization.LocalAppLanguage
import com.agon.app.localization.appText
import androidx.compose.material.icons.outlined.NotificationsOff
import com.agon.app.localization.tr
import com.agon.app.settingsprotection.ui.ExtendedDurationDialog
import com.agon.app.blocklist.ui.BlocklistRoute
import com.agon.app.health.HealthCheckScreen
import com.agon.app.setup.PermissionTutorialWizard
import com.agon.app.setup.SetupWizardScreen
import com.agon.app.setup.UninstallProtectionCard
import com.agon.app.timelimit.TimeLimitScreen
import dagger.hilt.android.AndroidEntryPoint
import com.agon.app.data.ShieldState
import com.agon.app.ui.theme.AgonAppTheme
import com.agon.app.admin.ShieldDeviceAdminReceiver
import com.agon.app.admin.deviceAdminSettingsIntent
import com.agon.app.services.ProtectionGuardianService
import com.agon.app.applock.AppLockGate
import com.agon.app.applock.AppLockManager
import com.agon.app.applock.SensitiveActionGate
import com.agon.app.applock.rememberSensitiveActionGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.agon.app.ui.routes.FamilyShieldScaffold
import com.agon.app.ui.routes.SafeBrowsingShieldScreen

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @javax.inject.Inject lateinit var batteryOptimization: com.agon.app.battery.BatteryOptimizationManager
    private val batteryOptimizationDisabled = mutableStateOf(false)
    private val batteryDialogDismissed = mutableStateOf(false)
    private val requiredPermissionRevision = mutableIntStateOf(0)
    private val locked = mutableStateOf(false)
    // Holds the result of the remote update check (null = not yet known / up to date).
    private val updateStatus = mutableStateOf<com.agon.app.update.UpdateStatus>(com.agon.app.update.UpdateStatus.UpToDate)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ShieldRepository.initialize(this)
        AppLockManager.initialize(this)
        com.agon.app.timelimit.BlockScheduleStore.initialize(this)
        NewAppQuarantineStore.initialize(this)
        locked.value = AppLockManager.mustChallenge()
        refreshBatteryOptimizationState()
        batteryDialogDismissed.value = getSharedPreferences("family_shield", MODE_PRIVATE).getBoolean("battery_dialog_dismissed", false)
        ContextCompat.startForegroundService(this, Intent(this, ProtectionGuardianService::class.java))
        checkForUpdates()
        setContent {
            val state by ShieldRepository.state.collectAsStateWithLifecycle()
            val dark = when (state.theme) { "dark" -> true; "light" -> false; else -> androidx.compose.foundation.isSystemInDarkTheme() }
            AgonAppTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalAppLanguage provides state.language, LocalLayoutDirection provides if (state.language == "en") LayoutDirection.Ltr else LayoutDirection.Rtl) {
                    val update = updateStatus.value
                    var optionalDismissed by remember { mutableStateOf(false) }
                    if (update is com.agon.app.update.UpdateStatus.Available && !update.mandatory && !optionalDismissed) {
                        com.agon.app.update.OptionalUpdateDialog(
                            manifest = update.manifest,
                            language = state.language,
                            onDismiss = { optionalDismissed = true },
                        )
                    }
                    if (update is com.agon.app.update.UpdateStatus.Available && update.mandatory) {
                        // Mandatory update: block the whole app until the user goes to the store.
                        com.agon.app.update.MandatoryUpdateGate(manifest = update.manifest, language = state.language)
                    } else if (locked.value) {
                        AppLockGate(language = state.language, onUnlocked = { locked.value = false })
                    } else {
                        var setupDone by remember { mutableStateOf(getSharedPreferences("family_shield", MODE_PRIVATE).getBoolean("setup_wizard_done", false)) }
                        if (!setupDone) {
                            SetupWizardScreen(state = state, language = state.language, onFinish = {
                                getSharedPreferences("family_shield", MODE_PRIVATE).edit().putBoolean("setup_wizard_done", true).apply()
                                setupDone = true
                            })
                        } else {
                            // Private DNS interstitial removed per product decision: the VPN
                            // filter already skips DNS blocking while Private DNS is active, so
                            // the home screen must stay reachable without any gate.
                            FamilyShieldApp(state)
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks for a newer version in the background: first via Google Play's In-App Update flow
     * (when installed from Play), then via the remote manifest (covers side-loaded installs). Any
     * failure is ignored so a network problem never blocks the protection app.
     */
    private fun checkForUpdates() {
        // Play immediate flow — only starts a flow when Play actually has an update.
        com.agon.app.update.PlayUpdateManager.startImmediateIfAvailable(this)
        // Remote manifest check off the main thread.
        Thread {
            val status = runCatching { com.agon.app.update.UpdateChecker.check(applicationContext) }
                .getOrDefault(com.agon.app.update.UpdateStatus.UpToDate)
            runOnUiThread { updateStatus.value = status }
        }.apply { isDaemon = true }.start()
    }

    override fun onResume() {
        super.onResume()
        ShieldRepository.refresh()
        refreshBatteryOptimizationState()
        requiredPermissionRevision.intValue += 1
        // markForegrounded() re-opens the unlocked window when the user comes back inside
        // BACKGROUND_GRACE_MS (e.g. opened Settings → App Info → Clear cache and came back).
        // mustChallenge() handles the genuine "left the device" case.
        val returnedQuickly = AppLockManager.markForegrounded()
        if (!returnedQuickly && AppLockManager.mustChallenge()) locked.value = true
        // Resume any interrupted Play immediate update so a partial update is completed.
        com.agon.app.update.PlayUpdateManager.startImmediateIfAvailable(this)
    }

    override fun onStop() {
        super.onStop()
        // Defer the actual lock to the background grace window — opening another app briefly
        // (Settings, App Info, system dialogs, Recents) must not require a fresh PIN.
        if (AppLockManager.isEnabled()) {
            AppLockManager.markBackgrounded()
        }
    }

    private fun refreshBatteryOptimizationState() {
        batteryOptimizationDisabled.value = batteryOptimization.isExempt()
    }

    private fun requestBatteryOptimizationExemption() {
        val target = batteryOptimization.resolveSettingsIntent() ?: return
        runCatching { startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun dismissBatteryDialog() {
        getSharedPreferences("family_shield", MODE_PRIVATE).edit().putBoolean("battery_dialog_dismissed", true).apply()
        batteryDialogDismissed.value = true
    }
}

@Composable
private fun BatteryOptimizationDialog(language: String, onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    val title = when (language) {
        "en" -> "Battery optimization must be disabled"
        "ar" -> "يجب إيقاف تحسين البطارية"
        else -> "پێویستە ئۆپتیمایزی باتری ناچالاک بکرێت"
    }
    val message = when (language) {
        "en" -> "Android may stop BlockX LaAbrah in the background. Disable battery optimization so blocked apps, websites, and keywords remain protected after you close the app."
        "ar" -> "قد يوقف Android تطبيق BlockX LaAbrah في الخلفية. أوقف تحسين البطارية لكي تستمر حماية التطبيقات والمواقع والكلمات المحظورة بعد إغلاق التطبيق."
        else -> "Android ڕەنگە BlockX LaAbrah لە باکگراوند بوەستێنێت. ئۆپتیمایزی باتری ناچالاک بکە تا دوای داخستنی ئەپیش پاراستنی ئەپ، وێبسایت و وشە بلۆککراوەکان بەردەوام بێت."
    }
    val instruction = when (language) {
        "en" -> "On the next screen, choose Allow or Don't optimize."
        "ar" -> "في الشاشة التالية اختر سماح أو عدم التحسين."
        else -> "لە پەڕەی داهاتوودا Allow یان Don't optimize هەڵبژێرە."
    }
    val button = when (language) {
        "en" -> "Disable battery optimization"
        "ar" -> "إيقاف تحسين البطارية"
        else -> "کوژاندنەوەی ئۆپتیمایزی باتری"
    }
    val dismissText = when (language) {
        "en" -> "Don't show again"
        "ar" -> "لا تعرض مرة أخرى"
        else -> "دووبارە پیشان مەدە"
    }
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Outlined.Autorenew, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
        text = { Column { Text(message); Spacer(Modifier.height(12.dp)); Text(instruction, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) } },
        confirmButton = { Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text(button, textAlign = TextAlign.Center) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(dismissText, textAlign = TextAlign.Center) } },
    )
}

@Composable
private fun FamilyShieldApp(state: ShieldState) {
    var showHealth by rememberSaveable { mutableStateOf(false) }
    var showReports by rememberSaveable { mutableStateOf(false) }
    var showQuarantine by rememberSaveable { mutableStateOf(false) }
    val tabGate = rememberSensitiveActionGate(state.language)
    val context = LocalContext.current

    if (showHealth) {
        HealthCheckOverlay(state = state, onClose = { showHealth = false })
        return
    }
    if (showReports) {
        ReportsOverlay(state = state, onClose = { showReports = false })
        return
    }
    if (showQuarantine) {
        QuarantineOverlay(state = state, onClose = { showQuarantine = false })
        return
    }

    FamilyShieldScaffold(
        state = state,
        safeBrowsing = { SafeBrowsingShieldScreen(state = state) },
        dashboard = {
            Dashboard(
                state,
                onOpenHealth = { showHealth = true },
                onOpenReports = { showReports = true },
                onOpenQuarantine = { tabGate.run { showQuarantine = true } },
            )
        },
        blocklist = { BlocklistRoute() },
        timeLimits = { TimeLimitScreen(language = state.language) },
        history = { HistoryScreen(state) },
        settings = { SettingsScreen(state) },
        supportOverlay = { visible, onDismiss ->
            SupportOverlay(
                visible = visible,
                language = state.language,
                onDismiss = onDismiss,
                onSupport = {
                    openSupportLink(context)
                    onDismiss()
                },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthCheckOverlay(state: ShieldState, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("health_title", state.language), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Home, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            HealthCheckScreen(state = state, language = state.language)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsOverlay(state: ShieldState, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("reports_title", state.language), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ReportsScreen(language = state.language, logs = state.logs)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuarantineOverlay(
    state: ShieldState,
    onClose: () -> Unit,
    protectionViewModel: com.agon.app.settingsprotection.viewmodel.SettingsProtectionViewModel = viewModel(),
) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    val gate = rememberSensitiveActionGate(state.language)
    val protectionState by protectionViewModel.state.collectAsStateWithLifecycle()
    val refreshTick = remember { mutableIntStateOf(0) }
    val pending = remember(refreshTick.intValue) { NewAppQuarantineStore.pending() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("quarantine_title", state.language), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item {
                Text(
                    tr("quarantine_subtitle", state.language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (pending.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Check,
                        title = tr("quarantine_empty_title", state.language),
                        subtitle = tr("quarantine_empty_hint", state.language),
                    )
                }
            }
            items(pending, key = { it }) { packageName ->
                QuarantineRow(
                    packageName = packageName,
                    language = state.language,
                    onApprove = {
                        val action = {
                            NewAppQuarantineStore.approve(packageName)
                            refreshTick.intValue = refreshTick.intValue + 1
                        }
                        if (protectionState.pinConfigured) gate.requireCredential(action) else action()
                    },
                )
            }
        }
        gate.Host()
    }
}

@Composable
private fun QuarantineRow(packageName: String, language: String, onApprove: () -> Unit) {
    val context = LocalContext.current
    val label = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0),
            ).toString()
        }.getOrDefault(packageName)
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PackageIcon(packageName, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(12.dp))
            Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = onApprove) {
                Text(tr("quarantine_approve", language), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Renders a target application's real launcher icon (works for adaptive drawables as well). */
@Composable
private fun PackageIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawable = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        if (drawable != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    android.widget.ImageView(viewContext).apply {
                        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                },
                update = { imageView -> imageView.setImageDrawable(drawable) },
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** Amber attention card on the dashboard while newly installed apps await approval. */
@Composable
private fun QuarantinePendingCard(count: Int, language: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.NewReleases, null, tint = MaterialTheme.colorScheme.onTertiary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tr("quarantine_pending_card", language).format(count),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    tr("quarantine_pending_sub", language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                )
            }
            Icon(Icons.Default.OpenInNew, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    state: ShieldState,
    onOpenHealth: () -> Unit = {},
    onOpenReports: () -> Unit = {},
    onOpenQuarantine: () -> Unit = {},
    protectionViewModel: com.agon.app.settingsprotection.viewmodel.SettingsProtectionViewModel = viewModel(),
) {
    val context = LocalContext.current

    var showCommitmentEndedDialog by remember { mutableStateOf(false) }
    var showCommitmentDurationDialog by remember { mutableStateOf(false) }

    val commitmentStopped = ProtectionCommitmentStore.isStopped(context)
    // Expiry is checked on a slow 5-second loop (not every second) so the home list is not
    // recomposed continuously — that was the cause of the scroll lag. The visible per-second
    // countdown ticks locally inside CommitmentCountdownCard instead.
    LaunchedEffect(Unit) {
        while (true) {
            if (!ProtectionCommitmentStore.isStopped(context) &&
                ProtectionCommitmentStore.isExpired(context) &&
                !showCommitmentDurationDialog
            ) {
                showCommitmentEndedDialog = true
            }
            kotlinx.coroutines.delay(5000L)
        }
    }

    if (showCommitmentEndedDialog) {
        CommitmentEndedDialog(
            language = state.language,
            onContinue = {
                showCommitmentEndedDialog = false
                showCommitmentDurationDialog = true
            },
            onStop = {
                showCommitmentEndedDialog = false
                stopProtection(context)
            },
        )
    }
    if (showCommitmentDurationDialog) {
        CommitmentDurationDialog(
            language = state.language,
            protectionViewModel = protectionViewModel,
            onDismiss = { showCommitmentDurationDialog = false },
            onPick = { days, isStrong ->
                showCommitmentDurationDialog = false
                // Standard/Strong only — never starts FamilyVpnService / Safe Browsing.
                com.agon.app.ui.routes.ProtectionSettingsRoute.applyProtectionCommitment(
                    context,
                    days,
                    isStrong,
                )
                startProtection(context)
            },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = appText("سڵاو", state.language),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("BlockX LaAbrah", fontWeight = FontWeight.ExtraBold)
                        Text(
                            text = appText("دۆخی پاراستنی ئامێرەکەت لێرەیە", state.language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
        item { ShareAndSupportRow(language = state.language) }
        item { PremiumHealthCard(state = state, language = state.language, onClick = onOpenHealth) }
        item {
            CommitmentCountdownCard(
                language = state.language,
                stopped = commitmentStopped,
                onRestart = { showCommitmentDurationDialog = true },
            )
        }
        item {
            DurationLockCard(
                language = state.language,
                icon = Icons.Outlined.NotificationsOff,
                title = tr("shade_lock_title", state.language),
                subtitle = tr("shade_lock_subtitle", state.language),
                kind = DurationLockKind.SHADE,
            )
        }
        item {
            DurationLockCard(
                language = state.language,
                icon = Icons.Default.Lock,
                title = tr("settings_lock_title", state.language),
                subtitle = tr("settings_lock_subtitle", state.language),
                kind = DurationLockKind.SETTINGS,
                requiresConfirmation = true,
            )
        }
        item { AppReviewCard(language = state.language) }
        item { com.agon.app.motivation.MotivationCard(language = state.language) }
        val quarantinePending = NewAppQuarantineStore.pendingCount()
        if (quarantinePending > 0) {
            item {
                QuarantinePendingCard(
                    count = quarantinePending,
                    language = state.language,
                    onClick = onOpenQuarantine,
                )
            }
        }
        // Product change: the "Usage report" card was removed from the home screen entirely.
        // (The reports overlay screen itself stays intact in code; nothing else references or
        // depends on the removed row.)
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/**
 * Groups the existing "share the app" card with a compact, visually-matching "support the app"
 * card directly beside it. The support card reuses the same gradient, corner radius, translucency,
 * glow and typography so the home screen identity is unchanged.
 */
@Composable
private fun ShareAndSupportRow(language: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShareAppCard(language = language)
        SupportAppCard(language = language)
    }
}

/**
 * Compact "support the app" card shown beside the share card. Tapping it opens the external
 * support link in the device browser.
 */
@Composable
private fun SupportAppCard(language: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val title = when (language) {
        "en" -> "\u2764\uFE0F Support the app"
        "ar" -> "\u2764\uFE0F \u062f\u0639\u0645 \u0627\u0644\u062a\u0637\u0628\u064a\u0642"
        else -> "\u2764\uFE0F پشتیوانی ئەپەکە"
    }
    val subtitle = when (language) {
        "en" -> "Your support helps us keep improving the app and keep it free for everyone."
        "ar" -> "\u062f\u0639\u0645\u0643 \u064a\u0633\u0627\u0639\u062f\u0646\u0627 \u0639\u0644\u0649 \u0627\u0644\u0627\u0633\u062a\u0645\u0631\u0627\u0631 \u0641\u064a \u062a\u0637\u0648\u064a\u0631 \u0627\u0644\u062a\u0637\u0628\u064a\u0642 \u0648\u0625\u0628\u0642\u0627\u0626\u0647 \u0645\u062a\u0627\u062d\u0627\u064b \u0644\u0644\u062c\u0645\u064a\u0639."
        else -> "پشتیوانیت یارمەتیمان دەدات بۆ بەردەوامبوون لە پەرەپێدانی ئەپەکە و بەخۆڕایی مانەوەی بۆ هەمووان."
    }
    val supportBrush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.95f),
        ),
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(supportBrush, RoundedCornerShape(24.dp))
                .clickable { openSupportLink(context) }
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color.White.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Opens the external support link in the device browser, safely handling a missing browser. */
private fun openSupportLink(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_LINK_URL))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private const val SUPPORT_LINK_URL = "https://creators.sa/yousifkareem"

/**
 * Share-the-app call to action shown at the very top of the Home screen. Shows a localized promo
 * card with two actions: Copy (puts the message on the clipboard) and Share (opens the Android
 * share sheet). Title, subtitle and the promo message all follow the app language.
 */

/** In-App Review prompt card shown on the Home screen until the user completes the review flow. */
@Composable
private fun AppReviewCard(language: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity ?: return
    val scope = rememberCoroutineScope()
    val hasRated = remember { com.agon.app.review.AppReviewState.shouldHideReviewButton(context) }
    if (hasRated) return

    var reviewInProgress by remember { mutableStateOf(false) }

    val title = when (language) {
        "en" -> "⭐ Rate the app"
        "ar" -> "⭐ قيّم التطبيق"
        else -> "⭐ بەهاوبەشکردنی ئەپ"
    }
    val subtitle = when (language) {
        "en" -> "Your feedback helps us improve and protect more families"
        "ar" -> "ملاحظاتك تساعدنا على التحسين وحماية المزيد من العائلات"
        else -> "ڕایەکەت یارمەتیمان دەدات بۆ باشتربوون و پاراستنی زیاتری خێزانەکان"
    }
    val rateBrush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.92f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
        ),
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(rateBrush, RoundedCornerShape(24.dp))
                .clickable(enabled = !reviewInProgress) {
                    reviewInProgress = true
                    scope.launch(Dispatchers.Main) {
                        runCatching {
                            com.agon.app.review.InAppReviewManager.requestReview(activity)
                        }
                        com.agon.app.review.AppReviewState.markRated(context)
                        reviewInProgress = false
                    }
                }
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color.White.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (reviewInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text("⭐", fontSize = 22.sp)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareAppCard(language: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shareMessage = shareAppMessage(language)
    val title = when (language) {
        "en" -> "Share the app with your loved ones"
        "ar" -> "مشاركة التطبيق مع أحبائك"
        else -> "هاوبەشکردنی ئەپ لەگەڵ خۆشەویستانت"
    }
    val subtitle = when (language) {
        "en" -> "Spread protection and help those you love browse safely"
        "ar" -> "انشر الحماية وساعد من تحب على تصفح آمن"
        else -> "پاراستن بڵاوبکەرەوە و یارمەتی خۆشەویستانت بدە بۆ گەڕانێکی سەلامەت"
    }
    val copyLabel = when (language) {
        "en" -> "Copy"
        "ar" -> "نسخ"
        else -> "کۆپیکردن"
    }
    val shareLabel = when (language) {
        "en" -> "Share"
        "ar" -> "مشاركة"
        else -> "هاوبەشکردن"
    }
    val copiedToast = when (language) {
        "en" -> "Text copied"
        "ar" -> "تم نسخ النص"
        else -> "دەق کۆپی کرا"
    }
    val shareBrush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.95f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
        ),
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(shareBrush, RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color.White.copy(alpha = 0.22f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShareActionButton(
                        icon = Icons.Default.ContentCopy,
                        label = copyLabel,
                        modifier = Modifier.weight(1f),
                    ) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("BlockX LaAbrah", shareMessage))
                        Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                    }
                    ShareActionButton(
                        icon = Icons.Default.Share,
                        label = shareLabel,
                        modifier = Modifier.weight(1f),
                    ) {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                        }
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A translucent pill action used inside [ShareAppCard] for the Copy and Share actions. */
@Composable
private fun ShareActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.20f))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun shareAppMessage(language: String): String = when (language) {
    "en" -> SHARE_APP_MESSAGE_EN
    "ar" -> SHARE_APP_MESSAGE_AR
    else -> SHARE_APP_MESSAGE_KU
}


private val SHARE_APP_MESSAGE_AR: String = """
🛡️ درع لا أبرح
تطبيق حجب متكامل للمواقع والتطبيقات الإباحية، يعمل 100٪ بدون إنترنت، مع خصوصية كاملة وأداء سريع.

💎 حجب أكثر من 2 مليون موقع إباحي.
💎 حظر كلمات البحث الإباحية.
💎 حظر YouTube Shorts وFacebook Reels وInstagram Reels.
💎 حظر التطبيقات وتحديد مدة استخدامها.
💎 حماية الإعدادات بكلمة مرور.
💎 الرغبة الملحة مع عداد تنازلي يساعدك على تجاوز لحظة الرغبة ومنع الوصول للمحتوى المحظور حتى انتهاء الوقت.
💎 جدول حظر التطبيقات للتحكم في أوقات الاستخدام ومنع فتح التطبيقات خلال الأوقات المحددة.
💎 خفيف على البطارية ويعمل بكفاءة عالية.

💡 لم نكتفِ بتقليد تطبيقات الحجب، بل أخذنا أفضل ما فيها وعالجنا أبرز نقاط ضعفها، مع التركيز على قوة الحجب والخصوصية وسهولة الاستخدام.
مجاني بالكامل ❤️

📱 رابط التحميل للأندرويد: https://play.google.com/store/apps/details?id=com.familyshield.protection
""".trimIndent()

private val SHARE_APP_MESSAGE_KU: String = """
🛡️ درع لا أبرح
ئەپێکی تەواوی بلۆککردنی ماڵپەڕ و ئەپە ئیباحییەکان، ١٠٠٪ بەبێ ئینتەرنێت کاردەکات، لەگەڵ تایبەتمەندی تەواو و کارایی خێرا.

💎 بلۆککردنی زیاتر لە ٢ ملیۆن ماڵپەڕی ئیباحی.
💎 بلۆککردنی وشەکانی گەڕانی ئیباحی.
💎 بلۆککردنی YouTube Shorts و Facebook Reels و Instagram Reels.
💎 بلۆککردنی ئەپەکان و دیاریکردنی ماوەی بەکارهێنانیان.
💎 پاراستنی ڕێکخستنەکان بە ووشەی نهێنی.
💎 ئارەزووی توند لەگەڵ ژمێرەری پێچەوانە کە یارمەتیت دەدات لە ساتی ئارەزوودا تێپەڕ بکەیت و ڕێگری دەکات لە گەیشتن بە ناوەڕۆکی بلۆککراو تا کۆتایی کات.
💎 خشتەی بلۆککردنی ئەپەکان بۆ کۆنترۆڵکردنی کاتەکانی بەکارهێنان و ڕێگریکردن لە کردنەوەی ئەپەکان لە ماوە دیاریکراوەکاندا.
💎 سووکە لەسەر باتری و بە کارایی بەرزەوە کاردەکات.

💡 تەنها لاسایی ئەپەکانی بلۆککردنمان نەکردەوە، بەڵکو باشترینیانمان وەرگرت و گرنگترین خاڵە لاوازەکانیانمان چاککرد، لەگەڵ جەخت لەسەر هێزی بلۆککردن و تایبەتمەندی و ئاسانی بەکارهێنان.
بەتەواوی بەخۆڕاییە ❤️

📱 لینکی داگرتن بۆ ئەندرۆید: https://play.google.com/store/apps/details?id=com.familyshield.protection
""".trimIndent()

private val SHARE_APP_MESSAGE_EN: String = """
🛡️ Dira La Abrah
A complete blocker for pornographic websites and apps. Works 100% offline, with full privacy and fast performance.

💎 Blocks over 2 million pornographic websites.
💎 Blocks pornographic search terms.
💎 Blocks YouTube Shorts, Facebook Reels and Instagram Reels.
💎 Blocks apps and sets their usage time limits.
💎 Protects settings with a password.
💎 Urge control with a countdown timer that helps you get past the moment of urge and prevents access to blocked content until the time ends.
💎 App-blocking schedule to control usage times and prevent opening apps during set hours.
💎 Light on the battery and runs with high efficiency.

💡 We didn't just imitate blocking apps — we took the best of them and fixed their biggest weaknesses, focusing on blocking strength, privacy and ease of use.
Completely free ❤️

📱 Android download link: https://play.google.com/store/apps/details?id=com.familyshield.protection
""".trimIndent()


@Composable
private fun ProtectionHero(state: ShieldState, title: String, subtitle: String, language: String) {
    val pulse by rememberInfiniteTransition(label = "shieldPulse").animateFloat(
        initialValue = 0.92f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(animation = tween(2400, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "shieldPulseScale",
    )
    val pausedNow = state.pausedUntil > System.currentTimeMillis()
    val activeServices = listOf(state.accessibilityEnabled, state.vpnRunning, !pausedNow).count { it }
    val ringProgress by animateFloatAsState(
        targetValue = activeServices / 3f,
        animationSpec = tween(700),
        label = "heroRing",
    )
    val baseColor = MaterialTheme.colorScheme.primary
    val heroBrush = Brush.linearGradient(
        listOf(
            baseColor.copy(alpha = 0.95f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
        ),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroBrush, RoundedCornerShape(28.dp))
                .padding(22.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(68.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            progress = { ringProgress },
                            modifier = Modifier.fillMaxSize(),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            strokeWidth = 4.dp,
                            strokeCap = StrokeCap.Round,
                        )
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp).graphicsLayer { scaleX = pulse; scaleY = pulse },
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroStat(label = appText("پاراستن", language), value = formatNumber(state.filteredRequests + state.blockedDomains), modifier = Modifier.weight(1f))
                    HeroStat(label = appText("بلۆککراو", language), value = formatNumber(state.blockedDomains), modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                WeeklySparkline(logs = state.logs)
            }
        }
    }
}

/** Seven muted bars summarising blocked events over the last seven days. */
@Composable
private fun WeeklySparkline(logs: List<BlockLog>) {
    val bars = remember(logs) { weeklyBlockRatios(logs) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { ratio ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((6f + 30f * ratio).dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.28f + 0.55f * ratio)),
            )
        }
    }
}

private fun weeklyBlockRatios(logs: List<BlockLog>): List<Float> {
    val dayMs = 86_400_000L
    val todayIndex = System.currentTimeMillis() / dayMs
    val counts = IntArray(7)
    logs.forEach { log ->
        val daysAgo = (todayIndex - log.timestamp / dayMs).toInt()
        if (daysAgo in 0..6) counts[6 - daysAgo]++
    }
    val max = counts.maxOrNull().takeIf { it != null && it > 0 } ?: return List(7) { 0f }
    return counts.map { it / max.toFloat() }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.16f),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
            Text(value, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun PremiumHealthCard(state: ShieldState, language: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MonitorHeart, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("health_title", language), fontWeight = FontWeight.Bold)
                Text(tr("health_subtitle", language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.OpenInNew, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

/**
 * "Block notification pull-down" option. Sitting directly under the Protection Health card per
 * the product contract: enabling runs the extended duration dialog first, THEN arms the lock
 * for the chosen window (or forever); disabling is immediate and free.
 */
/** The two duration-locked protection options. */
private enum class DurationLockKind { SHADE, SETTINGS }

/** Store adapters keep the card generic (same contract for both locks). */
private fun lockEnabled(context: android.content.Context, kind: DurationLockKind): Boolean = when (kind) {
    DurationLockKind.SHADE -> com.agon.app.services.NotificationShadeLock.isEnabled(context)
    DurationLockKind.SETTINGS -> com.agon.app.services.PhoneSettingsLock.isEnabled(context)
}

private fun lockEnableFor(context: android.content.Context, kind: DurationLockKind, millis: Long) {
    when (kind) {
        DurationLockKind.SHADE -> com.agon.app.services.NotificationShadeLock.enableFor(context, millis)
        DurationLockKind.SETTINGS -> com.agon.app.services.PhoneSettingsLock.enableFor(context, millis)
    }
}

private fun lockRemainingMillis(context: android.content.Context, kind: DurationLockKind): Long = when (kind) {
    DurationLockKind.SHADE -> com.agon.app.services.NotificationShadeLock.remainingMillis(context)
    DurationLockKind.SETTINGS -> com.agon.app.services.PhoneSettingsLock.remainingMillis(context)
}

/**
 * Shared card for the duration-locked options (sit under the Protection Health card, in this
 * order: shade lock, then Settings lock). Contract:
 *  - Enabling routes through the extended duration dialog FIRST (test option "٣ خولەک" leads),
 *    then arms the chosen window — never before a duration is picked. When [requiresConfirmation]
 *    is true (the Full Phone Settings Lock card), a pop-up warning dialog is shown FIRST and
 *    must be explicitly confirmed before the duration dialog ever appears.
 *  - While armed, the switch is DISABLED: self-serve deactivation is impossible until the
 *    deadline (a verified pause/PIN flow still exists in Settings for emergencies).
 *  - A live one-second countdown sits under the subtitle and unlocks the switch the moment the
 *    store's lazy expiry flips the flag off.
 */
@Composable
private fun DurationLockCard(
    language: String,
    icon: ImageVector,
    title: String,
    subtitle: String,
    kind: DurationLockKind,
    requiresConfirmation: Boolean = false,
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(lockEnabled(context, kind)) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showDurationDialog by remember { mutableStateOf(false) }
    var countdownLabel by remember { mutableStateOf("") }

    // Live countdown ticker: only ever spins while the lock is armed; expiry flips the switch
    // back off inside this very loop (the store clears itself on read, so no double-arming).
    LaunchedEffect(enabled, kind) {
        while (enabled) {
            val remaining = lockRemainingMillis(context, kind)
            countdownLabel = when {
                remaining == com.agon.app.services.NotificationShadeLock.FOREVER_MILLIS ->
                    tr("duration_forever", language)
                remaining <= 0L -> {
                    enabled = false
                    ""
                }
                else -> formatCommitmentRemaining(remaining, language)
            }
            delay(1_000L)
        }
        countdownLabel = ""
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(tr("settings_lock_confirm_title", language), fontWeight = FontWeight.Bold) },
            text = { Text(tr("settings_lock_confirm_body", language)) },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        showDurationDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(tr("settings_lock_confirm_button", language), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text(tr("cancel", language)) }
            },
        )
    }

    if (showDurationDialog) {
        ExtendedDurationDialog(
            language = language,
            onDismiss = { showDurationDialog = false },
            onPick = { durationMillis ->
                lockEnableFor(context, kind, durationMillis)
                enabled = true
                showDurationDialog = false
            },
        )
    }

    // Premium glassmorphism: animated accent + soft glow elevation + press-scale,
    // identical on every lock card — one shared visual language.
    val cardElevation by animateDpAsState(
        targetValue = if (enabled) 10.dp else 3.dp,
        animationSpec = tween(300),
        label = "durationLockElevation",
    )
    val lockInteractionSource = remember { MutableInteractionSource() }
    val lockPressed by lockInteractionSource.collectIsPressedAsState()
    val lockScale by animateFloatAsState(
        targetValue = if (lockPressed) 0.97f else 1f,
        animationSpec = tween(120),
        label = "durationLockPress",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = lockScale; scaleY = lockScale }
            .clickable(enabled = !enabled, interactionSource = lockInteractionSource) {
                if (requiresConfirmation) showConfirmDialog = true else showDurationDialog = true
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    // No inner rectangles any more (product decision): the card body IS the
                    // shared green glass gradient — texts, icons and the switch sit directly on it.
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                        ),
                    ),
                )
                .padding(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    if (enabled && countdownLabel.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = tr("schedule_time_left", language) + "  " + countdownLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                // Disabled while armed: manual deactivation is impossible until the countdown ends.
                Switch(
                    checked = enabled,
                    onCheckedChange = null,
                    enabled = !enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color.White.copy(alpha = 0.40f),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.92f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.24f),
                        checkedBorderColor = Color.Transparent,
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}

@Composable
private fun StatusRow(icon: ImageVector, title: String, subtitle: String, active: Boolean, onClick: () -> Unit) {
    val accent = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = accent.copy(alpha = 0.15f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accent)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.OpenInNew, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(
    state: ShieldState,
    protectionViewModel: com.agon.app.settingsprotection.viewmodel.SettingsProtectionViewModel = viewModel(),
) {
    // This tab is now dedicated to Reels/Shorts blocking; the raw event log was removed.
    val gate = rememberSensitiveActionGate(state.language)
    val protectionState by protectionViewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            TopAppBar(title = {
                Text(tr("reels_section", state.language), fontWeight = FontWeight.Bold)
            })
        }
        item {
            ReelsProtectionCard(
                language = state.language,
                runProtected = { action ->
                    if (protectionState.pinConfigured) gate.requireCredential(action) else action()
                },
            )
        }
    }
    gate.Host()
}

/**
 * Per-platform Reels/Shorts switches: Instagram first, then Facebook, then YouTube.
 * Turning a protection toggle OFF requires the protection PIN; enabling stays free.
 */
@Composable
private fun ReelsProtectionCard(
    language: String,
    runProtected: (() -> Unit) -> Unit = { it() },
) {
    val context = LocalContext.current
    val prefs = remember(context) { ShortVideoPreferences(context.applicationContext) }
    val telegramPrefs = remember(context) {
        com.agon.app.blocklist.data.TelegramSearchPreferences(context.applicationContext)
    }
    val vpnPrefs = remember(context) {
        com.agon.app.blocklist.data.VpnProtectionPreferences(context.applicationContext)
    }
    val refreshTick = remember { mutableIntStateOf(0) }
    // Pending enable confirmation. Every attempt to turn a protection ON (a Reels platform,
    // Telegram search or VPN/circumvention apps) is confirmed through a dialog that also asks
    // for how many days the block should stay active. Surfaced at the root of this composable so
    // it obeys the standard Compose lifecycle instead of being spawned from inside a non-Composable
    // onCheckedChange callback.
    var pendingEnable by remember { androidx.compose.runtime.mutableStateOf<EnableTarget?>(null) }
    val platforms = remember {
        listOf(
            Triple(ShortVideoPolicy.Platform.INSTAGRAM, "reels_instagram", Icons.Default.Movie),
            Triple(ShortVideoPolicy.Platform.FACEBOOK, "reels_facebook", Icons.Default.PlayCircle),
            Triple(ShortVideoPolicy.Platform.YOUTUBE, "reels_youtube", Icons.Default.SmartDisplay),
        )
    }
    // Live 1-second clock so the disable-lock countdown stays fresh without user interaction.
    // elapsedRealtime is used so a wall-clock change cannot reset or skip the tick.
    var nowMillis by remember { androidx.compose.runtime.mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) { nowMillis = android.os.SystemClock.elapsedRealtime(); kotlinx.coroutines.delay(1_000L) }
    }
    pendingEnable?.let { target ->
        val (titleKey, bodyKey) = when (target) {
            is EnableTarget.Platform -> "reels_enable_warning_title" to "reels_enable_warning_body"
            EnableTarget.Telegram -> "reels_enable_warning_title" to "reels_enable_warning_body"
            EnableTarget.Vpn -> "reels_enable_warning_title" to "vpn_enable_warning_body"
        }
        EnableProtectionDialog(
            language = language,
            title = tr(titleKey, language),
            body = tr(bodyKey, language),
            onConfirm = { durationMs ->
                when (target) {
                    is EnableTarget.Platform -> prefs.setEnabled(target.platform, true, durationMs = durationMs)
                    EnableTarget.Telegram -> telegramPrefs.setEnabled(true, durationMs = durationMs)
                    EnableTarget.Vpn -> vpnPrefs.setEnabled(true, durationMs = durationMs)
                }
                pendingEnable = null
                refreshTick.intValue += 1
            },
            onDismiss = {
                pendingEnable = null
                refreshTick.intValue += 1
            },
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    tr("reels_section_sub", language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            platforms.forEach { (platform, key, icon) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    val checked = remember(platform, refreshTick.intValue, nowMillis / 1000) { prefs.isEnabled(platform) }
                    val locked = remember(platform, refreshTick.intValue, nowMillis / 1000) { prefs.isLocked(platform) }
                    val remaining = remember(platform, refreshTick.intValue, nowMillis / 1000) { prefs.remainingMillis(platform) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr(key, language),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (locked && remaining > 0L) {
                            Text(
                                formatTimedRemaining(remaining, language),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Switch(
                        checked = checked,
                        enabled = !locked,
                        onCheckedChange = { value ->
                            // Enabling ALWAYS shows the confirmation dialog. Once a duration is
                            // chosen the switch stays locked ON until TimedBlockingStore expires.
                            if (!value) {
                                if (prefs.isLocked(platform)) {
                                    refreshTick.intValue += 1
                                    return@Switch
                                }
                                prefs.setEnabled(platform, false); refreshTick.intValue += 1
                            } else {
                                pendingEnable = EnableTarget.Platform(platform)
                            }
                        },
                    )
                }
            }
            // Telegram search protection disables Telegram's in-app search instead.
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                val telegramChecked = remember(refreshTick.intValue, nowMillis / 1000) { telegramPrefs.isEnabled() }
                val telegramLocked = remember(refreshTick.intValue, nowMillis / 1000) { telegramPrefs.isLocked() }
                val telegramRemaining = remember(refreshTick.intValue, nowMillis / 1000) { telegramPrefs.remainingMillis() }
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("telegram_search_disable", language),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (telegramLocked && telegramRemaining > 0L) {
                        Text(
                            formatTimedRemaining(telegramRemaining, language),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Switch(
                    checked = telegramChecked,
                    enabled = !telegramLocked,
                    onCheckedChange = { value ->
                        if (!value) {
                            if (telegramPrefs.isLocked()) {
                                refreshTick.intValue += 1
                                return@Switch
                            }
                            telegramPrefs.setEnabled(false); refreshTick.intValue += 1
                        } else {
                            pendingEnable = EnableTarget.Telegram
                        }
                    },
                )
            }
            // VPN / circumvention-app blocking: it blocks dedicated VPN / proxy / Tor apps instead
            // of a feed. Only the user can switch it on/off.
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    tr("vpn_block", language),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val vpnChecked = remember(refreshTick.intValue, nowMillis / 1000) { vpnPrefs.isEnabled() }
                Switch(
                    checked = vpnChecked,
                    onCheckedChange = { value ->
                        if (!value) {
                            vpnPrefs.setEnabled(false); refreshTick.intValue += 1
                        } else {
                            pendingEnable = EnableTarget.Vpn
                        }
                    },
                )
            }
            // YouTube Restricted Mode: plain on/off content preference — it only steers YouTube's
            // DNS answers and therefore needs no PIN/commitment flow like the protection toggles.
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.SmartDisplay, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                val ytChecked = remember(refreshTick.intValue) {
                    com.agon.app.blocklist.data.YoutubeRestrictStore.isEnabled()
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("youtube_restrict_title", language),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        tr("youtube_restrict_desc", language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (ytChecked) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            tr("youtube_restrict_hint", language),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Switch(
                    checked = ytChecked,
                    onCheckedChange = { value ->
                        com.agon.app.blocklist.data.YoutubeRestrictStore.setEnabled(context, value)
                        if (value) {
                            // The rewrite only lives inside the tunnel: if the web shield is
                            // enabled but its service is down (after reboot/OEM kill), revive it
                            // immediately so the toggle visibly works right away. No-op when the
                            // user never enabled the web shield.
                            val shield = ShieldRepository.state.value
                            if (shield.vpnEnabled && !shield.vpnRunning) {
                                runCatching {
                                    androidx.core.content.ContextCompat.startForegroundService(
                                        context,
                                        Intent(context, com.agon.app.vpn.FamilyVpnService::class.java),
                                    )
                                }
                            }
                        }
                        refreshTick.intValue += 1
                    },
                )
            }
        }
    }
}

/** Which protection is being turned ON, so the enable dialog shows the right confirmation. */
private sealed class EnableTarget {
    class Platform(val platform: ShortVideoPolicy.Platform) : EnableTarget()
    object Telegram : EnableTarget()
    object Vpn : EnableTarget()
}

/** Selectable block durations (days) for Reels/Telegram/VPN protection. */
private val PROTECTION_DAY_OPTIONS = listOf(3, 7, 15, 30, 60, 90, 180, 365)

/** Short test lock so the user can verify Reels/Shorts blocking works before committing days. */
private const val PROTECTION_TEST_MINUTES = 3
private const val PROTECTION_TEST_DURATION_MS = PROTECTION_TEST_MINUTES * 60_000L
private const val DAY_MS_UI = 86_400_000L

private fun protectionDayLabel(days: Int, language: String): String = when (language) {
    "en" -> "$days days"
    "ar" -> "$days يوم"
    else -> "$days ڕۆژ"
}

/**
 * Enable confirmation with a block-duration picker. Includes an optional short "test" duration
 * (3 minutes) beside the multi-day choices so the user can verify blocking works, then later
 * re-enable with a full day-based lock. After the chosen duration the protection auto-disables.
 */
@Composable
private fun EnableProtectionDialog(
    language: String,
    title: String,
    body: String,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // Default to the first multi-day option; user may pick the 3-minute test instead.
    var selectedMs by remember {
        androidx.compose.runtime.mutableLongStateOf(PROTECTION_DAY_OPTIONS.first().toLong() * DAY_MS_UI)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(body)
                Spacer(Modifier.height(14.dp))
                Text(tr("days_picker_title", language), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                // Test option first — optional short lock to verify the feature works.
                FilterChip(
                    selected = selectedMs == PROTECTION_TEST_DURATION_MS,
                    onClick = { selectedMs = PROTECTION_TEST_DURATION_MS },
                    label = {
                        Text(
                            tr("duration_test_3_min", language),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                PROTECTION_DAY_OPTIONS.chunked(3).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { d ->
                            val ms = d.toLong() * DAY_MS_UI
                            FilterChip(
                                selected = selectedMs == ms,
                                onClick = { selectedMs = ms },
                                label = {
                                    Text(
                                        protectionDayLabel(d, language),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(selectedMs) }) {
                Text(tr("reels_enable_warning_confirm", language))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(tr("reels_enable_warning_cancel", language))
            }
        },
    )
}

@Composable
private fun EmptyStateCard(icon: ImageVector, title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: ShieldState,
    protectionViewModel: com.agon.app.settingsprotection.viewmodel.SettingsProtectionViewModel = viewModel(),
) {
    var showPermissionTutorial by rememberSaveable { mutableStateOf(false) }
    if (showPermissionTutorial) {
        PermissionTutorialWizard(language = state.language, onClose = { showPermissionTutorial = false }, onComplete = { showPermissionTutorial = false })
        return
    }
    val context = LocalContext.current
    val batteryManager = remember(context) { BatteryOptimizationEntryPoint.resolve(context) }
    val adminComponent = remember { ComponentName(context, ShieldDeviceAdminReceiver::class.java) }
    var adminActive by remember { mutableStateOf(context.getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent)) }
    val adminSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        adminActive = context.getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent)
        if (adminActive) ContextCompat.startForegroundService(context, Intent(context, ProtectionGuardianService::class.java))
    }
    var pauseRequested by remember { mutableStateOf(false) }
    // Extended pause: the duration is picked FIRST (before any credential challenge), then the
    // existing gate runs, then the pause is applied with the chosen window.
    var pauseDurationRequested by remember { mutableStateOf(false) }
    var pendingPauseMillis by remember { mutableLongStateOf(ShieldRepository.PAUSE_MAX_MILLIS) }
    if (pauseDurationRequested) {
        ExtendedDurationDialog(
            language = state.language,
            onDismiss = { pauseDurationRequested = false },
            onPick = { millis ->
                pauseDurationRequested = false
                pendingPauseMillis = millis
                pauseRequested = true
            },
        )
    }
    if (pauseRequested) {
        ProtectionPauseGate(language = state.language, onDismiss = { pauseRequested = false }, onVerified = { pauseRequested = false; ShieldRepository.pauseProtection(pendingPauseMillis) })
    }
    val actionGate = rememberSensitiveActionGate(state.language)
    val protectionState by protectionViewModel.state.collectAsStateWithLifecycle()
    val paused = state.pausedUntil > System.currentTimeMillis()
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item { TopAppBar(title = { Text(appText("ڕێکخستن", state.language), fontWeight = FontWeight.Bold) }) }

        item { SettingsHeader(icon = Icons.Default.Shield, title = appText("پاراستن", state.language), subtitle = appText("هەموو قەپاغەکانی پاراستن لێرەیە", state.language)) }

        item {
            val pauseTitle = appText("وەستاندنی پاراستن (٣ خولەک)", state.language)
            val pauseSubtitle = if (paused) {
                val remaining = (state.pausedUntil - System.currentTimeMillis()).coerceAtLeast(0L)
                val minutes = (remaining + 59_999L) / 60_000L
                appText("دوای %d خولەک دەگەڕێتەوە - بۆ گەڕانەوە دەستبکە", state.language).replace("%d", minutes.toString())
            } else {
                appText("کاتی دەیکوژێنێتەوە، خۆکارانە دەگەڕێتەوە", state.language)
            }
            ToggleSetting(Icons.Outlined.Autorenew, pauseTitle, pauseSubtitle, paused) { enable ->
                if (enable && ProtectionCommitmentStore.isStrongActive(context)) {
                    pauseRequested = true
                } else {
                    // The extended duration dialog comes first (its own strings promise a
                    // time choice before anything is armed); the existing credential gate then
                    // runs right before the pause is actually applied.
                    actionGate.run { if (enable) pauseDurationRequested = true else ShieldRepository.resumeProtection() }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { showPermissionTutorial = true },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.HelpOutline, null, tint = MaterialTheme.colorScheme.primary) }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(text = appText("فێرکاری مۆڵەتەکان", state.language), fontWeight = FontWeight.Bold)
                        Text(text = appText("هەنگاوەکانی Accessibility و Device Admin و باتری ببینەوە.", state.language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.OpenInNew, null, tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
        item { SettingsProtectionSection(language = state.language, viewModel = protectionViewModel) }

        // ⭐ Rate the app — In-App Review
        item {
            val rateContext = LocalContext.current
            val rateActivity = rateContext as? android.app.Activity
            val rateScope = rememberCoroutineScope()
            var rateInProgress by remember { mutableStateOf(false) }
            val rateTitle = when (state.language) {
                "en" -> "⭐ Rate the app"
                "ar" -> "⭐ قيّم التطبيق"
                else -> "⭐ بەهاوبەشکردنی ئەپ"
            }
            val rateSubtitle = when (state.language) {
                "en" -> "Help us improve with your feedback"
                "ar" -> "ساعدنا على التحسين بملاحظاتك"
                else -> "بە ڕایەکەت یارمەتیمان بدە بۆ باشتربوون"
            }
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = !rateInProgress && rateActivity != null) {
                    if (rateActivity == null) return@clickable
                    rateInProgress = true
                    rateScope.launch(Dispatchers.Main) {
                        runCatching {
                            com.agon.app.review.InAppReviewManager.requestReview(rateActivity)
                        }
                        com.agon.app.review.AppReviewState.markRated(rateContext)
                        rateInProgress = false
                    }
                },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Box(contentAlignment = Alignment.Center) {
                            if (rateInProgress) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp) else Text("⭐", fontSize = 22.sp)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(rateTitle, fontWeight = FontWeight.Bold)
                        Text(rateSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item { SettingsHeader(icon = Icons.Outlined.DarkMode, title = appText("ڕووکار و زمان", state.language), subtitle = appText("ڕووکار، زمان و تێبینیەکان", state.language)) }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.DarkMode, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Text(appText("دۆخی ڕووکار", state.language), fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "سیستەم", "light" to "ڕووناک", "dark" to "تاریک").forEach { (value, label) ->
                            FilterChip(
                                selected = state.theme == value,
                                onClick = { ShieldRepository.setTheme(value) },
                                shape = RoundedCornerShape(50),
                                label = { Text(appText(label, state.language)) },
                                leadingIcon = if (state.theme == value) {
                                    { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { val next = when (state.language) { "ku" -> "ar"; "ar" -> "en"; else -> "ku" }; ShieldRepository.setLanguage(next) },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.primary) }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(appText("زمان", state.language), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(when (state.language) { "ar" -> "العربية"; "en" -> "English"; else -> "کوردی" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.OpenInNew, null, tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "BlockX LaAbrah \u00b7 ${BuildConfig.VERSION_NAME}",
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    appText("هیچ داتایەک بۆ سێرڤەر نانێردرێت؛ هەموو شتێک لەسەر ئامێرەکەت دەمێنێتەوە.", state.language),
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    actionGate.Host()
}

@Composable
private fun SettingsHeader(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleSetting(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = accent.copy(alpha = 0.15f),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

private fun formatNumber(value: Long): String = if (value > 999) "%.1fK".format(value / 1000f) else value.toString()

private fun formatDuration(ms: Long): String { val seconds = (ms / 1000).coerceAtLeast(0); return "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60) }

/**
 * Full-screen support prompt shown above the home screen once setup is complete. Uses a dimmed
 * translucent scrim plus a glassmorphism/glow card that matches the app's visual identity. Appears
 * with a light fade + scale animation and can be dismissed easily; it never permanently blocks the
 * app. RTL-aware and responsive (the card scrolls on very small screens).
 */
@Composable
private fun SupportOverlay(
    visible: Boolean,
    language: String,
    onDismiss: () -> Unit,
    onSupport: () -> Unit,
) {
    val heading = when (language) {
        "en" -> "Support the app's future"
        "ar" -> "\u0627\u062f\u0639\u0645 \u0627\u0633\u062a\u0645\u0631\u0627\u0631 \u0627\u0644\u062a\u0637\u0628\u064a\u0642"
        else -> "پشتیوانی بەردەوامی ئەپەکە بکە"
    }
    val subtitle = when (language) {
        "en" -> "Help keep our apps free and stable for everyone"
        "ar" -> "\u0633\u0627\u0647\u0645 \u0641\u064a \u0625\u0628\u0642\u0627\u0621 \u062a\u0637\u0628\u064a\u0642\u0627\u062a\u0646\u0627 \u0645\u062c\u0627\u0646\u064a\u0629 \u0648\u0645\u0633\u062a\u0642\u0631\u0629 \u0644\u0644\u062c\u0645\u064a\u0639"
        else -> "بەشداری بکە لە بەخۆڕایی و جێگیر مانەوەی ئەپەکانمان بۆ هەمووان"
    }
    val body = when (language) {
        "en" -> "This app is completely free, and your support helps us keep developing it, improving it and adding new features that serve everyone."
        "ar" -> "\u0647\u0630\u0627 \u0627\u0644\u062a\u0637\u0628\u064a\u0642 \u0645\u062c\u0627\u0646\u064a \u0628\u0627\u0644\u0643\u0627\u0645\u0644\u060c \u0648\u062f\u0639\u0645\u0643 \u064a\u0633\u0627\u0639\u062f\u0646\u0627 \u0639\u0644\u0649 \u0627\u0644\u0627\u0633\u062a\u0645\u0631\u0627\u0631 \u0641\u064a \u062a\u0637\u0648\u064a\u0631\u0647 \u0648\u062a\u062d\u0633\u064a\u0646\u0647 \u0648\u0625\u0636\u0627\u0641\u0629 \u0645\u064a\u0632\u0627\u062a \u062c\u062f\u064a\u062f\u0629 \u062a\u062e\u062f\u0645 \u0627\u0644\u062c\u0645\u064a\u0639."
        else -> "ئەم ئەپە بەتەواوی بەخۆڕاییە، و پشتیوانیت یارمەتیمان دەدات بۆ بەردەوامبوون لە پەرەپێدان و باشترکردنی و زیادکردنی تایبەتمەندی نوێ کە خزمەتی هەمووان بکات."
    }
    val supportBtn = when (language) {
        "en" -> "\u2764\uFE0F Contribute to the app  \u2197"
        "ar" -> "\u2764\uFE0F \u0633\u0627\u0647\u0645 \u0641\u064a \u0627\u0633\u062a\u0645\u0631\u0627\u0631 \u0627\u0644\u062a\u0637\u0628\u064a\u0642  \u2197"
        else -> "\u2764\uFE0F بەشداری بکە لە بەردەوامی ئەپەکە  \u2197"
    }
    val closeBtn = when (language) {
        "en" -> "\u2715 Close"
        "ar" -> "\u2715 \u0625\u063a\u0644\u0627\u0642"
        else -> "\u2715 داخستن"
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(180)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB3000000))
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(260)) + scaleIn(initialScale = 0.9f, animationSpec = tween(260)),
                exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.9f, animationSpec = tween(160)),
            ) {
                val cardBrush = Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.97f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.94f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.95f),
                    ),
                )
                val scroll = rememberScrollState()
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(vertical = 24.dp)
                        // Swallow taps on the card so the scrim's dismiss click doesn't fire.
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            onClick = {},
                        ),
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .background(cardBrush, RoundedCornerShape(30.dp))
                            .padding(horizontal = 24.dp, vertical = 26.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scroll),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(78.dp)
                                    .background(Color.White.copy(alpha = 0.20f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                            Spacer(Modifier.height(18.dp))
                            Text(
                                text = heading,
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = subtitle,
                                color = Color.White.copy(alpha = 0.92f),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = body,
                                color = Color.White.copy(alpha = 0.88f),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp,
                            )
                            Spacer(Modifier.height(24.dp))
                            Surface(
                                onClick = onSupport,
                                shape = RoundedCornerShape(18.dp),
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = supportBtn,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 15.dp),
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = closeBtn,
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = onDismiss)
                                    .padding(horizontal = 22.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Home-screen card showing the remaining protection-commitment time as a live countdown. When the
 * user has stopped protection it turns into a "restart protection" card instead. Nothing is shown
 * when no commitment exists. A permanent commitment shows a "protection is permanent" badge.
 */
@Composable
private fun CommitmentCountdownCard(
    language: String,
    stopped: Boolean,
    onRestart: () -> Unit,
) {
    val context = LocalContext.current

    // Stopped state: offer a clear restart action.
    if (stopped) {
        // Product restyle: no pink/orange anywhere — the card now wears the project's own
        // green glassmorphic gradient (identical to the neighbouring rate/share cards).
        val brush = Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.92f),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
            ),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush, RoundedCornerShape(24.dp))
                    .clickable(onClick = onRestart)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(46.dp).background(Color.White.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when (language) {
                            "en" -> "Protection is stopped"
                            "ar" -> "\u0627\u0644\u062d\u0645\u0627\u064a\u0629 \u0645\u062a\u0648\u0642\u0641\u0629"
                            else -> "\u067e\u0627\u0631\u0627\u0633\u062a\u0646 \u0648\u06d5\u0633\u062a\u0627\u0648\u06d5"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = when (language) {
                            "en" -> "Tap to restart protection"
                            "ar" -> "\u0627\u0636\u063a\u0637 \u0644\u0625\u0639\u0627\u062f\u0629 \u062a\u0634\u063a\u064a\u0644 \u0627\u0644\u062d\u0645\u0627\u064a\u0629"
                            else -> "\u062f\u06d5\u0633\u062a \u0628\u0646\u06ce \u0628\u06c6 \u062f\u06d5\u0633\u062a\u067e\u06ce\u06a9\u0631\u062f\u0646\u06d5\u0648\u06d5\u06cc \u067e\u0627\u0631\u0627\u0633\u062a\u0646"
                        },
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        return
    }

    if (!ProtectionCommitmentStore.hasCommitment(context)) return

    val brush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.92f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.94f),
        ),
    )
    val isStrong = ProtectionCommitmentStore.isStrongActive(context)
    val forever = ProtectionCommitmentStore.isForever(context)
    // Ledger-driven countdown: the remaining time always comes from the tamper-proof
    // accumulative store (never from device clock − end timestamp), so moving the device date
    // forward or backward can neither end the commitment early nor extend it.
    var remaining by remember {
        androidx.compose.runtime.mutableLongStateOf(ProtectionCommitmentStore.remainingMillis(context))
    }
    if (!forever) {
        LaunchedEffect(Unit) {
            while (true) {
                remaining = ProtectionCommitmentStore.remainingMillis(context)
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush, RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(46.dp).background(Color.White.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (isStrong) Icons.Default.Lock else Icons.Default.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isStrong) {
                        when (language) {
                            "en" -> "\uD83D\uDD12 Strong Protection"
                            "ar" -> "\uD83D\uDD12 \u0627\u0644\u062d\u0645\u0627\u064a\u0629 \u0627\u0644\u0642\u0648\u064a\u0629"
                            else -> "\uD83D\uDD12 \u067e\u0627\u0631\u0627\u0633\u062a\u0646\u06cc \u0628\u06d5\u0647\u06ce\u0632"
                        }
                    } else {
                        when (language) {
                            "en" -> "Protection commitment"
                            "ar" -> "\u0645\u062f\u0629 \u0627\u0644\u0627\u0644\u062a\u0632\u0627\u0645 \u0628\u0627\u0644\u062d\u0645\u0627\u064a\u0629"
                            else -> "\u0645\u0627\u0648\u06d5\u06cc \u067e\u0627\u0628\u06d5\u0646\u062f\u06cc \u0628\u06d5 \u067e\u0627\u0631\u0627\u0633\u062a\u0646"
                        }
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = if (forever) {
                        when (language) {
                            "en" -> "Protection is permanent"
                            "ar" -> "\u0627\u0644\u062d\u0645\u0627\u064a\u0629 \u062f\u0627\u0626\u0645\u0629"
                            else -> "\u067e\u0627\u0631\u0627\u0633\u062a\u0646 \u0647\u06d5\u062a\u0627\u0647\u06d5\u062a\u0627\u06cc\u06cc\u06d5"
                        }
                    } else if (isStrong) {
                        ProtectionCommitmentStore.formatStrongRemaining(remaining, language)
                    } else {
                        formatCommitmentRemaining(remaining, language)
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Formats a remaining-millis value as "Dd HH:MM:SS" in the active language's number style. */
private fun formatCommitmentRemaining(remainingMillis: Long, language: String): String {
    val totalSeconds = remainingMillis / 1000L
    val days = totalSeconds / 86400L
    val hours = (totalSeconds % 86400L) / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val dayWord = when (language) {
        "en" -> "d"
        "ar" -> "\u064a\u0648\u0645"
        else -> "\u0695\u06c6\u0698"
    }
    val clock = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    return "$days $dayWord  $clock"
}

/**
 * Asked when the timed commitment runs out: keep protection running (pick a new duration) or stop.
 */
@Composable
private fun CommitmentEndedDialog(
    language: String,
    onContinue: () -> Unit,
    onStop: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* must choose */ },
        icon = { Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                when (language) {
                    "en" -> "Your protection period has ended"
                    "ar" -> "\u0627\u0646\u062a\u0647\u062a \u0645\u062f\u0629 \u0627\u0644\u062a\u0632\u0627\u0645\u0643 \u0628\u0627\u0644\u062d\u0645\u0627\u064a\u0629"
                    else -> "\u0645\u0627\u0648\u06d5\u06cc \u067e\u0627\u0628\u06d5\u0646\u062f\u06cc\u062a \u0628\u06d5 \u067e\u0627\u0631\u0627\u0633\u062a\u0646 \u062a\u06d5\u0648\u0627\u0648 \u0628\u0648\u0648"
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                when (language) {
                    "en" -> "Do you want to keep protection running, or stop it?"
                    "ar" -> "\u0647\u0644 \u062a\u0631\u064a\u062f \u0627\u0633\u062a\u0645\u0631\u0627\u0631 \u0627\u0644\u062d\u0645\u0627\u064a\u0629 \u0623\u0645 \u0625\u064a\u0642\u0627\u0641\u0647\u0627\u061f"
                    else -> "\u0626\u0627\u06cc\u0627 \u062f\u06d5\u062a\u06d5\u0648\u06ce\u062a \u067e\u0627\u0631\u0627\u0633\u062a\u0646 \u0628\u06d5\u0631\u062f\u06d5\u0648\u0627\u0645 \u0628\u06ce\u062a \u06cc\u0627\u0646 \u0628\u0648\u06d5\u0633\u062a\u06ce\u062a\u061f"
                },
            )
        },
        confirmButton = {
            Button(onClick = onContinue) {
                Text(
                    when (language) {
                        "en" -> "Continue"
                        "ar" -> "\u0627\u0633\u062a\u0645\u0631\u0627\u0631"
                        else -> "\u0628\u06d5\u0631\u062f\u06d5\u0648\u0627\u0645\u0628\u0648\u0648\u0646"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onStop) {
                Text(
                    when (language) {
                        "en" -> "Stop protection"
                        "ar" -> "\u0625\u064a\u0642\u0627\u0641 \u0627\u0644\u062d\u0645\u0627\u064a\u0629"
                        else -> "\u0648\u06d5\u0633\u062a\u0627\u0646\u062f\u0646\u06cc \u067e\u0627\u0631\u0627\u0633\u062a\u0646"
                    },
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

/** Duration picker used both when continuing after expiry and when restarting after a stop. */
@Composable
private fun CommitmentDurationDialog(
    language: String,
    protectionViewModel: com.agon.app.settingsprotection.viewmodel.SettingsProtectionViewModel,
    onDismiss: () -> Unit,
    onPick: (days: Int, isStrong: Boolean) -> Unit,
) {
    var isStrongProtection by remember { mutableStateOf(false) }
    var useUsernamePassword by remember { mutableStateOf(false) }
    var selectedDays by remember { mutableStateOf<Int?>(ProtectionCommitmentStore.TEST_5_MINUTES) }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    val foreverLabel = when (language) {
        "en" -> "Forever"
        "ar" -> "\u0625\u0644\u0649 \u0627\u0644\u0623\u0628\u062f"
        else -> "\u0647\u06d5\u062a\u0627\u0647\u06d5\u062a\u0627\u06cc\u06cc"
    }
    fun dayLabel(days: Int): String = ProtectionCommitmentStore.formatOptionLabel(days, language)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (isStrongProtection) Icons.Default.Lock else Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                when (language) {
                    "en" -> "Choose Protection Mode & Duration"
                    "ar" -> "اختر نوع ومدة الحماية"
                    else -> "جۆر و ماوەی پاراستن دیاری بکە"
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Mode selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !isStrongProtection,
                        onClick = { isStrongProtection = false },
                        label = {
                            Text(
                                when (language) {
                                    "en" -> "Standard"
                                    "ar" -> "حماية عادية"
                                    else -> "ئاسایی"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = isStrongProtection,
                        onClick = { isStrongProtection = true },
                        label = {
                            Text(
                                when (language) {
                                    "en" -> "🔒 Strong"
                                    "ar" -> "🔒 حماية قوية"
                                    else -> "🔒 بەهێز"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (isStrongProtection) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = when (language) {
                                "en" -> "🔒 In Strong Protection, settings and uninstall protection cannot be bypassed or disabled during this period."
                                "ar" -> "🔒 في الحماية القوية لا يمكن إيقاف الحماية أو تجاوز الإعدادات حتى انتهاء المدة المحددة."
                                else -> "🔒 لە پاراستنی بەهێزدا، ناتوانرێت پاراستن بوەستێنرێت تا ماوەکە تەواو دەبێت."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                } else {
                    Text(
                        text = when (language) {
                            "en" -> "Choose lock method:"
                            "ar" -> "اختر طريقة القفل:"
                            else -> "شێوازی قوفڵ هەڵبژێرە:"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = !useUsernamePassword,
                            onClick = { useUsernamePassword = false },
                            label = {
                                Text(
                                    when (language) {
                                        "en" -> "PIN"
                                        "ar" -> "رمز PIN"
                                        else -> "PIN"
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = useUsernamePassword,
                            onClick = { useUsernamePassword = true },
                            label = {
                                Text(
                                    when (language) {
                                        "en" -> "Username & Password"
                                        "ar" -> "اسم مستخدم وكلمة مرور"
                                        else -> "ناو و وشەی نهێنی"
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (useUsernamePassword) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it.filter { ch -> !ch.isWhitespace() }.take(UsernamePolicy.MAX_LENGTH) },
                            label = {
                                Text(
                                    when (language) {
                                        "en" -> "Username"
                                        "ar" -> "اسم المستخدم"
                                        else -> "ناوی بەکارهێنەر"
                                    },
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = PinPolicy.sanitize(it) },
                        label = {
                            Text(
                                if (useUsernamePassword) {
                                    when (language) {
                                        "en" -> "Password"
                                        "ar" -> "كلمة المرور"
                                        else -> "وشەی نهێنی"
                                    }
                                } else {
                                    when (language) {
                                        "en" -> "PIN"
                                        "ar" -> "رمز PIN"
                                        else -> "PIN"
                                    }
                                },
                            )
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pinConfirm,
                        onValueChange = { pinConfirm = PinPolicy.sanitize(it) },
                        label = {
                            Text(
                                when (language) {
                                    "en" -> "Confirm"
                                    "ar" -> "تأكيد"
                                    else -> "دڵنیاکردنەوە"
                                },
                            )
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text(
                    text = when (language) {
                        "en" -> "Duration:"
                        "ar" -> "المدة:"
                        else -> "ماوە:"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )

                val dayList = if (isStrongProtection) ProtectionCommitmentStore.STRONG_DAY_OPTIONS else ProtectionCommitmentStore.DAY_OPTIONS
                dayList.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowItems.forEach { days ->
                            FilterChip(
                                selected = selectedDays == days,
                                onClick = { selectedDays = days },
                                label = {
                                    Text(dayLabel(days), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (!isStrongProtection) {
                    FilterChip(
                        selected = selectedDays == ProtectionCommitmentStore.FOREVER.toInt(),
                        onClick = { selectedDays = ProtectionCommitmentStore.FOREVER.toInt() },
                        label = { Text(foreverLabel, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            val chosen = selectedDays
            if (isStrongProtection) {
                Button(
                    onClick = {
                        if (chosen == null) return@Button
                        onPick(chosen, true)
                    },
                    enabled = chosen != null,
                ) {
                    Text(
                        when (language) {
                            "en" -> "Save"
                            "ar" -> "حفظ"
                            else -> "پاشەکەوت"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                val lockValid = PinPolicy.isValid(pin) && pin == pinConfirm &&
                    (!useUsernamePassword || UsernamePolicy.isValid(username))
                Button(
                    onClick = {
                        if (chosen == null || !lockValid) return@Button
                        if (useUsernamePassword) {
                            protectionViewModel.saveUsernamePassword(username.trim(), pin, pinConfirm, "")
                        } else {
                            protectionViewModel.savePin(pin, pinConfirm, "")
                        }
                        onPick(chosen, false)
                    },
                    enabled = chosen != null && lockValid,
                ) {
                    Text(
                        when (language) {
                            "en" -> "Save & continue"
                            "ar" -> "حفظ ومتابعة"
                            else -> "پاشەکەوت و بەردەوام"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    when (language) {
                        "en" -> "Cancel"
                        "ar" -> "\u0625\u0644\u063a\u0627\u0621"
                        else -> "\u067e\u0627\u0634\u06af\u06d5\u0632\u0628\u0648\u0648\u0646\u06d5\u0648\u06d5"
                    },
                )
            }
        },
    )
}

/**
 * Stops Standard protection commitment only.
 * Does **not** stop Safe Browsing VPN (fully decoupled).
 */
private fun stopProtection(context: android.content.Context) {
    com.agon.app.ui.routes.ProtectionSettingsRoute.stopStandardProtectionOnly(context)
    ShieldRepository.refresh()
}

/**
 * Clears the "stopped" flag after choosing a new Standard/Strong duration.
 * Does **not** start FamilyVpnService.
 */
private fun startProtection(context: android.content.Context) {
    ProtectionCommitmentStore.setStopped(context, false)
    ShieldRepository.refresh()
}

/**
 * Full-screen gate shown when a fixed system Private DNS (DNS-over-TLS) hostname is configured,
 * which would route DNS around the filtering tunnel. The user is asked to turn it off before the
 * protected home screen is reachable. Matches the app's existing gate styling and is RTL-aware.
 */
@Composable
private fun PrivateDnsGate(language: String, hostname: String, onRecheck: () -> Unit) {
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(Modifier.size(110.dp), CircleShape, color = MaterialTheme.colorScheme.errorContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Dns, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(26.dp))
            Text(
                text = when (language) {
                    "en" -> "Disable Private DNS"
                    "ar" -> "\u0623\u0648\u0642\u0641 \u0627\u0644\u0640 Private DNS"
                    else -> "Private DNS \u0646\u0627\u0686\u0627\u0644\u0627\u06a9 \u0628\u06a9\u06d5"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = when (language) {
                    "en" -> "A private DNS server is set on this device, which sends web requests around the protection and disables filtering. Turn Private DNS off (or set it to \u201cAutomatic\u201d) to keep protection working."
                    "ar" -> "\u064a\u0648\u062c\u062f \u062e\u0627\u062f\u0645 Private DNS \u0645\u0636\u0628\u0648\u0637 \u0639\u0644\u0649 \u0627\u0644\u062c\u0647\u0627\u0632\u060c \u0648\u0647\u0648 \u064a\u0631\u0633\u0644 \u0637\u0644\u0628\u0627\u062a \u0627\u0644\u0625\u0646\u062a\u0631\u0646\u062a \u062e\u0627\u0631\u062c \u0627\u0644\u062d\u0645\u0627\u064a\u0629 \u0648\u064a\u0648\u0642\u0641 \u0627\u0644\u0641\u0644\u062a\u0631\u0629. \u0623\u0648\u0642\u0641 \u0627\u0644\u0640 Private DNS (\u0623\u0648 \u0627\u062c\u0639\u0644\u0647 \u201c\u062a\u0644\u0642\u0627\u0626\u064a\u201d) \u0644\u0643\u064a \u062a\u0633\u062a\u0645\u0631 \u0627\u0644\u062d\u0645\u0627\u064a\u0629."
                    else -> "\u0644\u06d5\u0633\u06d5\u0631 \u0626\u0627\u0645\u06ce\u0631\u06d5\u06a9\u06d5 \u0633\u06ce\u0631\u06a4\u06d5\u0631\u06cc Private DNS \u062f\u0627\u0646\u0631\u0627\u0648\u06d5\u060c \u06a9\u06d5 \u062f\u0627\u0648\u0627\u06a9\u0627\u0631\u06cc\u06cc\u06d5\u06a9\u0627\u0646 \u0644\u06d5 \u062f\u06d5\u0631\u06d5\u0648\u06d5\u06cc \u067e\u0627\u0631\u0627\u0633\u062a\u0646 \u062f\u06d5\u0646\u06ce\u0631\u06ce\u062a \u0648 \u0641\u0644\u062a\u06d5\u0631\u06a9\u0631\u062f\u0646 \u0644\u0627\u062f\u06d5\u0628\u0627\u062a. Private DNS \u0646\u0627\u0686\u0627\u0644\u0627\u06a9 \u0628\u06a9\u06d5 (\u06cc\u0627\u0646 \u0628\u06cc\u06a9\u06d5 \u0628\u06d5 \u201c\u0626\u06c6\u062a\u06c6\u0645\u0627\u062a\u06cc\u06a9\u201d) \u0628\u06c6 \u0626\u06d5\u0648\u06d5\u06cc \u067e\u0627\u0631\u0627\u0633\u062a\u0646 \u0628\u06d5\u0631\u062f\u06d5\u0648\u0627\u0645 \u0628\u06ce\u062a."
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hostname.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(
                        hostname,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
            Button(
                onClick = {
                    val opened = runCatching {
                        context.startActivity(Intent("android.settings.PRIVATE_DNS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        true
                    }.getOrDefault(false)
                    if (!opened) {
                        runCatching { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Settings, null, Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    when (language) {
                        "en" -> "Open Private DNS settings"
                        "ar" -> "\u0641\u062a\u062d \u0625\u0639\u062f\u0627\u062f\u0627\u062a Private DNS"
                        else -> "\u06a9\u0631\u062f\u0646\u06d5\u0648\u06d5\u06cc \u0695\u06ce\u06a9\u062e\u0633\u062a\u0646\u06cc Private DNS"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRecheck) {
                Text(
                    when (language) {
                        "en" -> "I turned it off \u2014 re-check"
                        "ar" -> "\u0623\u0648\u0642\u0641\u062a\u0647 \u2014 \u0623\u0639\u062f \u0627\u0644\u0641\u062d\u0635"
                        else -> "\u0646\u0627\u0686\u0627\u0644\u0627\u06a9\u0645 \u06a9\u0631\u062f \u2014 \u062f\u0648\u0648\u0628\u0627\u0631\u06d5 \u067e\u0634\u06a9\u0646\u06cc\u0646"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
