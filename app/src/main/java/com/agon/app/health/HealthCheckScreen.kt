package com.agon.app.health

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.battery.BatteryOptimizationEntryPoint
import com.agon.app.data.ShieldState
import com.agon.app.localization.tr
import com.agon.app.services.KeepAliveScheduler
import com.agon.app.vpn.FamilyVpnService
import kotlinx.coroutines.launch

/**
 * Protection Health Score 0–100 with green/red rows, real keyword test, diagnostic export.
 */
@Composable
fun HealthCheckScreen(
    state: ShieldState,
    language: String,
    protectionViewModel: com.agon.app.settingsprotection.viewmodel.SettingsProtectionViewModel = viewModel(),
) {
    val context = LocalContext.current
    val batteryManager = remember(context) { BatteryOptimizationEntryPoint.resolve(context) }
    val autostartManager = remember(context) { AutostartManager(context) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var refreshKey by remember { mutableIntStateOf(0) }
    var testResult by remember { mutableStateOf<ProtectionTestResult?>(null) }

    // System VPN consent dialog result: only approval may bring the tunnel up; either way the
    // list re-evaluates so the card flips state without leaving/reopening this page.
    val vpnConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, FamilyVpnService::class.java),
                )
            }
        }
        refreshKey++
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                NotificationHealth.ensureChannels(context)
                ProtectionHealth.restartGuardian(context)
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snapshot = remember(state, refreshKey) {
        ProtectionHealth.evaluate(context, batteryManager, autostartManager)
    }
    val score = snapshot.score
    val allGood = snapshot.allRequiredOk

    // Truth check: the headline score must not float at 100% while the live DNS test says the
    // tunnel is down. Re-run the four-layer test automatically every time the page reloads.
    LaunchedEffect(refreshKey) {
        if (testResult == null) {
            testResult = runCatching { ProtectionHealth.runProtectionTest(context) }.getOrNull()
        }
    }
    val liveFailed = testResult?.success == false && testResult != null
    val displayScore = if (liveFailed) score.coerceAtMost(40) else score
    val displayAllGood = allGood && !liveFailed

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column {
                    Text(tr("health_title", language), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "${tr("health_subtitle", language)} · ${OemKnowledgeBase.skinName(snapshot.vendor)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                ScoreCard(
                    score = displayScore,
                    allGood = displayAllGood,
                    language = language,
                    failedCode = testResult?.code,
                )
            }

            // One-tap VPN repair right under the warning (MIUI field fix): re-asks system
            // consent when the tunnel is unprepared, restarts the service either way.
            val failed = testResult?.code
            if (failed == "tunnel_down" || failed == "engine_fail") {
                item(key = "vpn-repair") {
                    TextButton(
                        onClick = {
                            val consent = runCatching { android.net.VpnService.prepare(context) }.getOrNull()
                            if (consent != null) {
                                vpnConsentLauncher.launch(consent)
                            } else {
                                runCatching {
                                    com.agon.app.data.ShieldRepository.setVpnDesired(true)
                                    androidx.core.content.ContextCompat.startForegroundService(
                                        context,
                                        Intent(context, FamilyVpnService::class.java),
                                    )
                                }
                            }
                            testResult = null
                            refreshKey++
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            tr("health_vpn_restart", language),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            snapshot.items.forEach { item ->
                item(key = item.id.name) {
                    HealthRow(
                        icon = iconFor(item.id),
                        title = tr(item.titleKey, language),
                        desc = tr(item.descKey, language),
                        ok = item.ok,
                        required = item.required,
                        language = language,
                        onFix = when {
                            item.id == HealthCheckId.VPN_OPTIONAL -> {
                                {
                                    protectionViewModel.allowPermissionEnable()
                                    runCatching {
                                        val consent = VpnService.prepare(context)
                                        if (consent != null) {
                                            // Consent missing: system dialog first, the service
                                            // starts from its RESULT_OK callback above.
                                            vpnConsentLauncher.launch(consent)
                                        } else {
                                            // Consent already granted: start the tunnel now.
                                            ContextCompat.startForegroundService(
                                                context,
                                                Intent(context, FamilyVpnService::class.java),
                                            )
                                        }
                                    }
                                    refreshKey++
                                }
                            }
                            item.id == HealthCheckId.GUARDIAN_SERVICE -> {
                                {
                                    protectionViewModel.allowPermissionEnable()
                                    ProtectionHealth.restartGuardian(context)
                                    KeepAliveScheduler.schedule(context)
                                    refreshKey++
                                }
                            }
                            item.id == HealthCheckId.AUTOSTART -> {
                                {
                                    protectionViewModel.allowPermissionEnable()
                                    runCatching {
                                        item.fixIntent?.let {
                                            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        }
                                    }
                                    // After OEM screen, user confirms in Autostart prefs when they return
                                    // via Fix then mark — expose confirm by marking only from dedicated UI.
                                }
                            }
                            item.fixIntent != null -> {
                                {
                                    protectionViewModel.allowPermissionEnable()
                                    runCatching {
                                        context.startActivity(
                                            item.fixIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                            }
                            else -> null
                        },
                        onMarkAutostartDone = if (item.id == HealthCheckId.AUTOSTART && !item.ok) {
                            {
                                autostartManager.markConfigured()
                                refreshKey++
                            }
                        } else {
                            null
                        },
                    )
                }
            }

            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(tr("health_test_title", language), fontWeight = FontWeight.Bold)
                        Text(
                            tr("health_test_desc", language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                testResult = ProtectionHealth.runProtectionTest(context)
                                ProtectionHealth.restartGuardian(context)
                                refreshKey++
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Science, null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("health_test_run", language))
                        }
                        testResult?.let { result ->
                            Text(
                                if (result.success) {
                                    tr("health_test_ok", language)
                                } else {
                                    tr("health_test_fail", language) + " (${result.code})"
                                },
                                color = if (result.success) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        val report = DiagnosticReport.build(context, batteryManager, autostartManager)
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("dira-diagnostic", report))
                        val share = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, report)
                        runCatching {
                            context.startActivity(Intent.createChooser(share, "Diagnostic").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        scope.launch { snackbar.showSnackbar(tr("health_report_copied", language)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("health_diagnostic", language))
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

private fun iconFor(id: HealthCheckId): ImageVector = when (id) {
    HealthCheckId.ACCESSIBILITY -> Icons.Default.AccessibilityNew
    HealthCheckId.BATTERY -> Icons.Default.BatteryChargingFull
    HealthCheckId.AUTOSTART -> Icons.Default.RocketLaunch
    HealthCheckId.NOTIFICATIONS -> Icons.Default.Notifications
    HealthCheckId.EXACT_ALARM -> Icons.Default.Alarm
    HealthCheckId.DEVICE_ADMIN -> Icons.Default.Security
    HealthCheckId.GUARDIAN_SERVICE -> Icons.Default.Speed
    HealthCheckId.VPN_OPTIONAL -> Icons.Default.Shield
    HealthCheckId.KEYWORD_ENGINE -> Icons.Default.PlayArrow
}

@Composable
private fun ScoreCard(score: Int, allGood: Boolean, language: String, failedCode: String? = null) {
    animateFloatAsState(targetValue = score / 100f, label = "score")
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allGood) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = if (allGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(92.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (allGood) Icons.Default.Shield else Icons.Default.Error,
                        null,
                        Modifier.size(50.dp),
                        tint = if (allGood) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "$score%",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (allGood) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                tr("health_score", language),
                style = MaterialTheme.typography.labelMedium,
                color = if (allGood) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                tr(if (allGood) "health_all_good" else "health_issues", language),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (allGood) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            )
            if (failedCode != null) {
                // Honest failure callout: the four-layer live test is the source of truth,
                // not the static permission rows.
                Spacer(Modifier.height(10.dp))
                Text(
                    text = tr("health_live_broken", language),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun HealthRow(
    icon: ImageVector,
    title: String,
    desc: String,
    ok: Boolean,
    required: Boolean,
    language: String,
    onFix: (() -> Unit)?,
    onMarkAutostartDone: (() -> Unit)? = null,
) {
    // Incomplete steps always use error (red) surfaces so missing protection is obvious.
    Card(
        Modifier.fillMaxWidth().let { if (!ok && onFix != null) it.clickable { onFix() } else it },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ok) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (ok) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                },
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        null,
                        tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    color = if (ok) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                )
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ok) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                    },
                )
                if (!ok && required) {
                    Text(
                        tr("health_required", language),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            if (ok) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text(tr("health_active", language), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onFix != null) {
                        Button(
                            onClick = onFix,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) { Text(tr("health_fix", language)) }
                    }
                    if (onMarkAutostartDone != null) {
                        OutlinedButton(
                            onClick = onMarkAutostartDone,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(tr("step_autostart_confirm", language), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (onFix == null && onMarkAutostartDone == null) {
                        Text(
                            tr("health_inactive", language),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
