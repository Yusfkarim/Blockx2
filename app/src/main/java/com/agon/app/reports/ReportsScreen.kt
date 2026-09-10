package com.agon.app.reports

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agon.app.data.BlockLog
import com.agon.app.localization.tr
import com.agon.app.timelimit.AppTimeLimitStore
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

private const val DAY_MS = 86_400_000L

/**
 * Usage dashboard: everything parents ask first — how many attempts were blocked, what was
 * blocked most, and which apps ate the most time today. The screen is display-only: all data
 * already lives on the device (block logs + the time-limit usage ledger).
 */
@Composable
fun ReportsScreen(language: String, logs: List<BlockLog>) {
    val context = LocalContext.current
    remember { AppTimeLimitStore.initialize(context.applicationContext) }

    val stats = remember(logs) { buildStats(logs, language) }
    val usageToday = remember { AppTimeLimitStore.usageMillisToday() }
    val limits = remember { AppTimeLimitStore.limits() }
    val packageManager = context.packageManager

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Totals row
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    icon = Icons.Default.History,
                    value = stats.todayCount.toString(),
                    label = tr("reports_today", language),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = Icons.Default.BarChart,
                    value = stats.weekCount.toString(),
                    label = tr("reports_this_week", language),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = Icons.Default.Shield,
                    value = stats.totalCount.toString(),
                    label = tr("reports_all_time", language),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 7-day attempt chart
        item {
            ReportCard(title = "${tr("reports_blocked_attempts", language)} — ${tr("reports_last_7_days", language)}") {
                if (stats.weeklyBars.all { it.second <= 0f }) {
                    ReportEmptyHint(language)
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        stats.weeklyBars.forEach { (label, ratio) ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.62f)
                                        .height((8f + 84f * ratio).dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (ratio >= 0.999f) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + 0.45f * ratio),
                                        ),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Most blocked targets
        if (stats.topTargets.isNotEmpty()) {
            item {
                ReportCard(title = tr("reports_top_blocked", language)) {
                    stats.topTargets.forEachIndexed { index, (target, count) ->
                        ReportRow(
                            title = target,
                            value = count.toString(),
                            ratio = count.toFloat() / stats.topTargets.first().second.toFloat(),
                        )
                        if (index < stats.topTargets.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        // By source (app / website / keyword …)
        if (stats.bySource.isNotEmpty()) {
            item {
                ReportCard(title = tr("reports_by_source", language)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        stats.bySource.take(4).forEach { (source, count) ->
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Column(
                                    Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = source,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Which apps consumed the most time today (limited apps are the tracked ones)
        item {
            ReportCard(title = tr("reports_top_apps", language)) {
                if (usageToday.isEmpty()) {
                    ReportEmptyHint(language, key = "reports_no_usage")
                } else {
                    val top = usageToday.entries
                        .sortedByDescending { it.value }
                        .take(5)
                    if (top.all { it.value <= 0L }) {
                        ReportEmptyHint(language, key = "reports_no_usage")
                    } else {
                        val maxUsed = top.first().value.coerceAtLeast(1L)
                        val unit = tr("reports_minutes_used", language)
                        top.forEachIndexed { index, (pkg, usedMs) ->
                            val usedMinutes = (usedMs / 60_000L).toInt()
                            val label = remember(pkg) {
                                runCatching {
                                    packageManager.getApplicationLabel(
                                        packageManager.getApplicationInfo(pkg, 0),
                                    ).toString()
                                }.getOrDefault(pkg)
                            }
                            val limit = limits[pkg]
                            ReportRow(
                                icon = Icons.Default.HourglassTop,
                                title = if (limit != null) {
                                    "$label — $usedMinutes/$limit $unit"
                                } else {
                                    "$label — $usedMinutes $unit"
                                },
                                value = "",
                                ratio = usedMs.toFloat() / maxUsed.toFloat(),
                            )
                            if (index < top.lastIndex) Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                ReportCard(title = tr("reports_blocked_attempts", language)) {
                    ReportEmptyHint(language)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------

private class ReportStats(
    val todayCount: Int,
    val weekCount: Int,
    val totalCount: Int,
    val weeklyBars: List<Pair<String, Float>>,
    val topTargets: List<Pair<String, Int>>,
    val bySource: List<Pair<String, Int>>,
)

private fun buildStats(logs: List<BlockLog>, language: String): ReportStats {
    val now = System.currentTimeMillis()
    val todayIndex = now / DAY_MS

    val todayCount = logs.count { it.timestamp / DAY_MS == todayIndex }
    val weekCount = logs.count { todayIndex - it.timestamp / DAY_MS in 0..6 }

    val dayCounts = IntArray(7)
    logs.forEach { log ->
        val daysAgo = (todayIndex - log.timestamp / DAY_MS).toInt()
        if (daysAgo in 0..6) dayCounts[6 - daysAgo]++
    }
    val maxDay = dayCounts.maxOrNull() ?: 0
    val labels = dayLabels(language)
    val bars = dayCounts.mapIndexed { index, count ->
        labels[index] to (if (maxDay == 0) 0f else count / maxDay.toFloat())
    }

    val topTargets = logs.groupingBy { it.domain }
        .eachCount()
        .toList()
        .sortedByDescending { it.second }
        .take(8)

    val bySource = logs.groupingBy { it.source }
        .eachCount()
        .toList()
        .sortedByDescending { it.second }

    return ReportStats(
        todayCount = todayCount,
        weekCount = weekCount,
        totalCount = logs.size,
        weeklyBars = bars,
        topTargets = topTargets,
        bySource = bySource,
    )
}

/** Short weekday labels for the last 7 days, oldest first, in the active language. */
private fun dayLabels(language: String): List<String> {
    val locale = when (language) {
        "ar" -> Locale.forLanguageTag("ar")
        "en" -> Locale.ENGLISH
        else -> Locale.forLanguageTag("ckb")
    }
    val symbols = DateFormatSymbols(locale)
    val todayIndex = System.currentTimeMillis() / DAY_MS
    return (6 downTo 0).map { daysAgo ->
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = (todayIndex - daysAgo) * DAY_MS
        symbols.shortWeekdays[calendar.get(Calendar.DAY_OF_WEEK)]
    }.reversed().reversed() // keep oldest → today order
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReportCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun ReportRow(title: String, value: String, ratio: Float, icon: ImageVector? = null) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (value.isNotEmpty()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ratio.coerceIn(0.04f, 1f))
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun ReportEmptyHint(language: String, key: String = "reports_empty") {
    Text(
        text = tr(key, language) + if (key == "reports_empty") "\n" + tr("reports_empty_hint", language) else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
