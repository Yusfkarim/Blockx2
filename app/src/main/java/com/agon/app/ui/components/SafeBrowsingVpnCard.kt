package com.agon.app.ui.components

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.agon.app.data.ShieldRepository
import com.agon.app.data.ShieldState
import com.agon.app.vpn.CommitmentTimerModule
import com.agon.app.vpn.FamilyVpnService
import kotlinx.coroutines.delay

/**
 * Independent Safe Browsing (VPN) control card — not tied to Standard/Strong protection.
 */
@Composable
fun SafeBrowsingVpnCard(state: ShieldState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var locked by remember { mutableStateOf(CommitmentTimerModule.isActive(context)) }
    var remaining by remember { mutableLongStateOf(CommitmentTimerModule.remainingMillis(context)) }
    var showPicker by remember { mutableStateOf(false) }
    var pendingOption by remember { mutableStateOf<CommitmentTimerModule.DurationOption?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            CommitmentTimerModule.tick(context)
            locked = CommitmentTimerModule.isActive(context)
            remaining = CommitmentTimerModule.remainingMillis(context)
            if (locked && state.vpnEnabled && !state.vpnRunning) {
                runCatching {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, FamilyVpnService::class.java),
                    )
                }
            }
            delay(1_000L)
        }
    }

    val prepareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val option = pendingOption ?: return@rememberLauncherForActivityResult
            armVpn(context, option)
            pendingOption = null
            showPicker = false
            locked = true
            remaining = CommitmentTimerModule.remainingMillis(context)
        } else {
            pendingOption = null
        }
    }

    fun requestEnable(option: CommitmentTimerModule.DurationOption) {
        val prepare = VpnService.prepare(context)
        if (prepare != null) {
            pendingOption = option
            prepareLauncher.launch(prepare)
        } else {
            armVpn(context, option)
            showPicker = false
            locked = true
            remaining = CommitmentTimerModule.remainingMillis(context)
        }
    }

    if (showPicker) {
        VpnDurationDialog(
            language = state.language,
            onDismiss = { showPicker = false },
            onConfirm = { requestEnable(it) },
        )
    }

    val brush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.88f),
        ),
    )
    val title = when (state.language) {
        "en" -> "Safe Browsing Shield"
        "ar" -> "درع التصفح الآمن"
        else -> "درع التصفح الآمن"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(brush, RoundedCornerShape(24.dp))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (locked) Icons.Default.Lock else Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .padding(10.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold)
                    Text(
                        text = when {
                            locked -> CommitmentTimerModule.formatRemaining(remaining, state.language)
                            state.vpnRunning -> when (state.language) {
                                "en" -> "VPN filter running (unlocked)"
                                "ar" -> "التصفية تعمل (غير مقفلة)"
                                else -> "فلتەر کاردەکات (بێ قفل)"
                            }
                            else -> when (state.language) {
                                "en" -> "Independent of Standard/Strong protection"
                                "ar" -> "مستقل عن الحماية العادية/القوية"
                                else -> "سەربەخۆ لە پاراستنی ئاسایی/بەهێز"
                            }
                        },
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = state.vpnEnabled || locked,
                    enabled = !locked,
                    onCheckedChange = { enable ->
                        if (locked) return@Switch
                        if (enable) {
                            showPicker = true
                        } else {
                            ShieldRepository.setVpnDesired(false)
                            runCatching {
                                context.startService(
                                    Intent(context, FamilyVpnService::class.java)
                                        .setAction(FamilyVpnService.ACTION_STOP),
                                )
                            }
                            ShieldRepository.refresh()
                        }
                    },
                )
            }
            if (locked) {
                Spacer(Modifier.height(8.dp))
                Text(
                    when (state.language) {
                        "en" -> "VPN locked until timer ends — other protection settings stay free"
                        "ar" -> "VPN مقفل حتى انتهاء الوقت — باقي الحماية تبقى قابلة للتعديل"
                        else -> "VPN قفلکراوە تا کۆتایی کات — پاراستنی تر ئازاد دەمێنێت"
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun VpnDurationDialog(
    language: String,
    onDismiss: () -> Unit,
    onConfirm: (CommitmentTimerModule.DurationOption) -> Unit,
) {
    var selected by remember { mutableStateOf(CommitmentTimerModule.DurationOption.MINUTES_5) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (language) {
                    "en" -> "VPN lock duration"
                    "ar" -> "مدة قفل درع التصفح"
                    else -> "ماوەی قفلی درع التصفح"
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    when (language) {
                        "en" -> "Locks Safe Browsing VPN only. Standard/Strong protection is unchanged."
                        "ar" -> "يقفل VPN فقط. الحماية العادية/القوية لا تتأثر."
                        else -> "تەنها VPN قفل دەکات. پاراستنی ئاسایی/بەهێز ناگۆڕدرێت."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                CommitmentTimerModule.ALL_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = selected == option,
                        onClick = { selected = option },
                        label = { Text(CommitmentTimerModule.label(option, language)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected) }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when (language) {
                        "en" -> "Enable & lock VPN"
                        "ar" -> "تفعيل وقفل VPN"
                        else -> "چالاک و قفلی VPN"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(when (language) { "en" -> "Cancel"; "ar" -> "إلغاء"; else -> "پاشگەزبوونەوە" })
            }
        },
    )
}

private fun armVpn(context: android.content.Context, option: CommitmentTimerModule.DurationOption) {
    if (!CommitmentTimerModule.start(context, option)) return
    ShieldRepository.setVpnDesired(true)
    runCatching {
        ContextCompat.startForegroundService(
            context,
            Intent(context, FamilyVpnService::class.java),
        )
    }
    ShieldRepository.refresh()
}
