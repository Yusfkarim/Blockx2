package com.agon.app.battery

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Material 3 battery optimization guide.
 *
 * Verification is lifecycle driven: the exemption state is re-read on every ON_RESUME, so when
 * the user comes back from the system settings the result is reflected immediately without any
 * polling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryOptimizationGuideSheet(
    manager: BatteryOptimizationManager,
    language: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val vendor = remember(manager) { manager.vendor }
    val steps = remember(vendor, language) { BatteryGuideContent.steps(vendor, language) }
    val settingsIntent = remember(manager) { manager.resolveSettingsIntent() }

    var exempt by remember { mutableStateOf(manager.isExempt()) }
    var attempted by remember { mutableStateOf(false) }

    // Requirement 6: verify automatically once the user returns to the app.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, manager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) exempt = manager.isExempt()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 22.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GuideHeader(exempt = exempt, manager = manager, vendor = vendor, language = language)

            if (exempt) {
                StatusCard(
                    icon = Icons.Default.CheckCircle,
                    title = BatteryGuideContent.verifiedTitle(language),
                    body = BatteryGuideContent.verifiedBody(language),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                // Requirement 7: warn when the setting is still enabled after an attempt.
                AnimatedVisibility(visible = attempted) {
                    StatusCard(
                        icon = Icons.Default.Warning,
                        title = BatteryGuideContent.stillEnabledTitle(language),
                        body = BatteryGuideContent.stillEnabledBody(language),
                        container = MaterialTheme.colorScheme.errorContainer,
                        onContainer = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                InfoCard(
                    icon = Icons.Outlined.Info,
                    title = BatteryGuideContent.whyTitle(language),
                    body = BatteryGuideContent.whyBody(language),
                )
                InfoCard(
                    icon = Icons.Outlined.BatteryAlert,
                    title = BatteryGuideContent.riskTitle(language),
                    body = BatteryGuideContent.riskBody(language),
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                )

                Text(
                    text = BatteryGuideContent.stepsTitle(language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        steps.forEachIndexed { index, step ->
                            StepRow(index = index + 1, step = step)
                        }
                    }
                }

                if (settingsIntent == null) {
                    Text(
                        text = BatteryGuideContent.unavailableBody(language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            if (exempt) {
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(BatteryGuideContent.doneButton(language), fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        attempted = true
                        val target = settingsIntent ?: return@Button
                        runCatching {
                            context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                    enabled = settingsIntent != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (attempted) {
                            BatteryGuideContent.retryButton(language)
                        } else {
                            BatteryGuideContent.openButton(language)
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(BatteryGuideContent.closeButton(language))
                }
            }
        }
    }
}

@Composable
private fun GuideHeader(
    exempt: Boolean,
    manager: BatteryOptimizationManager,
    vendor: DeviceVendor,
    language: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = if (exempt) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (exempt) Icons.Default.CheckCircle else Icons.Outlined.BatterySaver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = BatteryGuideContent.title(language),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = BatteryGuideContent.vendorCaption(vendor, language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = manager.deviceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun StepRow(index: Int, step: BatteryGuideContent.Step) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(26.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = index.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = step.text, style = MaterialTheme.typography.bodyMedium)
            step.emphasis?.let { option ->
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = option,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainer,
    onContainer: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = onContainer, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, fontWeight = FontWeight.Bold, color = onContainer)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = onContainer.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    container: androidx.compose.ui.graphics.Color,
    onContainer: androidx.compose.ui.graphics.Color,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = onContainer)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = onContainer)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.9f),
                )
            }
        }
    }
}
