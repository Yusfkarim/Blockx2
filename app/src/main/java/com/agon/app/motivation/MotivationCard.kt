package com.agon.app.motivation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.BlockedAdvice
import com.agon.app.localization.tr

/**
 * Motivation section for the dashboard: a "protection streak" counter, best-streak record,
 * an achievement ladder and a daily admonition drawn from the project's 150-advice pool.
 * Purely motivational — it never changes any blocking behaviour. [tick] lets the caller force
 * a recomposition (e.g. once per day / on resume) without owning any state here.
 */
@Composable
fun MotivationCard(language: String, tick: Int = 0) {
    val context = LocalContext.current
    val info = remember(tick) {
        StreakStore.initialize(context)
        StreakStore.info()
    }
    // Deterministic advice-of-the-day: stable within a day, rotates across the 150-advice pool.
    val advice = remember(info.currentDays) {
        val pool = BlockedAdvice.ADVICES
        if (pool.isEmpty()) null else pool[(info.startMillis / 86_400_000L + info.currentDays).toInt().mod(pool.size)]
    }
    var confirmRestart by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            // ---- header ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("streak_title", language), fontWeight = FontWeight.Bold)
                    Text(
                        tr("streak_subtitle", language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- current + best streak ----
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StreakStat(
                    modifier = Modifier.weight(1f),
                    value = info.currentDays.toString(),
                    label = tr("streak_current", language),
                    highlighted = true,
                )
                StreakStat(
                    modifier = Modifier.weight(1f),
                    value = info.bestDays.toString(),
                    label = tr("streak_best", language),
                    highlighted = false,
                )
            }

            Spacer(Modifier.height(16.dp))

            // ---- achievement ladder ----
            Text(
                tr("streak_achievements", language),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StreakStore.MILESTONES.forEach { milestone ->
                    MilestoneChip(
                        days = milestone,
                        unlocked = info.currentDays >= milestone,
                        language = language,
                    )
                }
            }

            // ---- advice of the day ----
            if (advice != null) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                tr("streak_advice_title", language),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            advice.text(language),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 24.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { confirmRestart = true }) {
                    Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("streak_restart", language))
                }
            }
        }
    }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            icon = { Icon(Icons.Default.RestartAlt, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(tr("streak_restart_title", language), fontWeight = FontWeight.Bold) },
            text = { Text(tr("streak_restart_body", language)) },
            confirmButton = {
                TextButton(onClick = {
                    StreakStore.restart()
                    confirmRestart = false
                }) { Text(tr("streak_restart_confirm", language)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestart = false }) { Text(tr("cancel", language)) }
            },
        )
    }
}

@Composable
private fun StreakStat(modifier: Modifier, value: String, label: String, highlighted: Boolean) {
    val container = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val onContainer = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = container) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = onContainer)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = onContainer.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun MilestoneChip(days: Int, unlocked: Boolean, language: String) {
    val bg = if (unlocked) {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary,
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
    val content = if (unlocked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .size(width = 68.dp, height = 74.dp)
            .background(bg, RoundedCornerShape(18.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (unlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
            null,
            tint = content,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text("$days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = content)
        Text(tr("streak_days_short", language), style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.85f))
    }
}
