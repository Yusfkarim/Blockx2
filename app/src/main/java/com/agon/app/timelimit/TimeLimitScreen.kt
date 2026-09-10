package com.agon.app.timelimit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.applock.rememberSensitiveActionGate
import com.agon.app.localization.tr
import com.agon.app.BlockedAdvice
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Screen for configuring per-app daily time limits.
 * Fully localized (Kurdish / Arabic / English) and driven entirely by [TimeLimitViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeLimitScreen(
    language: String,
    viewModel: TimeLimitViewModel = viewModel(),
    protectionViewModel: com.agon.app.settingsprotection.viewmodel.SettingsProtectionViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val protectionState by protectionViewModel.state.collectAsStateWithLifecycle()
    val actionGate = rememberSensitiveActionGate(language)
    val runProtectedAction: (() -> Unit) -> Unit = { action ->
        if (protectionState.pinConfigured) actionGate.requireCredential(action) else action()
    }
    LaunchedEffect(Unit) {
        viewModel.refresh()
        // The accessibility service settles the limited app as BlockX LaAbrah opens.
        delay(300L)
        viewModel.refresh()
    }
    var showPicker by remember { mutableStateOf(false) }
    var editPackage by remember { mutableStateOf<String?>(null) }
    var schedulesTick by remember { mutableIntStateOf(0) }
    var showScheduleEditor by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<BlockSchedule?>(null) }
    var showUrgeDialog by remember { mutableStateOf(false) }
    // When the trash on a still-locked daily limit is tapped, hold its remaining time so a
    // dialog can tell the user how long until deletion is allowed.
    var lockedLimitInfo by remember { mutableStateOf<Pair<String, Long>?>(null) }
    // When the trash on a still-locked schedule is tapped, hold (name, remainingMillis) so a
    // dialog can tell the user how long until deletion is allowed. remaining == 0 means the
    // lock is only because the window is actively blocking right now.
    var lockedScheduleInfo by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val schedules = remember(schedulesTick) { BlockScheduleStore.schedules() }
    // Shared 1-second ticker driving every live countdown on this screen.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val urgeUntil = BlockScheduleStore.urgeBlockUntil().takeIf { it > nowMillis } ?: 0L

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // ===== Section: Urgent craving =====
        item {
            SectionHeader(
                icon = Icons.Default.Bolt,
                title = tr("schedule_urge", language),
                subtitle = tr("schedule_urge_sub", language),
            )
        }
        item {
            UrgeBlockCard(
                language = language,
                activeUntil = urgeUntil,
                nowMillis = nowMillis,
                onStart = { showUrgeDialog = true },
            )
        }

        item { SectionDivider() }

        // ===== Section: Block schedule =====
        item {
            SectionHeader(
                icon = Icons.Default.Bedtime,
                title = tr("schedule_section", language),
                subtitle = tr("schedule_section_sub", language),
                trailing = {
                    AssistChip(
                        // Creating a new schedule is intentionally PIN-free.
                        onClick = { editingSchedule = null; showScheduleEditor = true },
                        label = { Text(tr("schedule_add", language)) },
                    )
                },
            )
        }
        if (schedules.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 22.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(tr("schedule_empty", language), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr("schedule_empty_hint", language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        items(schedules, key = { it.id }) { schedule ->
            val editLocked = BlockScheduleStore.isEditLocked(schedule, nowMillis)
            ScheduleCard(
                schedule = schedule,
                language = language,
                nowMillis = nowMillis,
                editLocked = editLocked,
                // Enabling/disabling a schedule is intentionally PIN-free and always allowed.
                onToggle = { enable ->
                    BlockScheduleStore.setEnabled(schedule.id, enable); schedulesTick++
                },
                // Editing and deleting are blocked during the 48h cool-down; afterwards they are
                // allowed with no PIN. The guards below are a no-op while locked.
                onEdit = {
                    if (!BlockScheduleStore.isEditLocked(schedule, System.currentTimeMillis())) {
                        editingSchedule = schedule; showScheduleEditor = true
                    }
                },
                onRemove = {
                    val now = System.currentTimeMillis()
                    if (BlockScheduleStore.isEditLocked(schedule, now)) {
                        val name = schedule.name.ifBlank { tr("schedule_section", language) }
                        lockedScheduleInfo = name to BlockScheduleStore.editLockRemaining(schedule, now)
                    } else {
                        BlockScheduleStore.remove(schedule.id); schedulesTick++
                    }
                },
            )
        }

        item { SectionDivider() }

        // ===== Section: Time limits =====
        item {
            SectionHeader(
                icon = Icons.Default.HourglassTop,
                title = tr("time_limits", language),
                subtitle = tr("time_limits_subtitle", language),
                trailing = {
                    IconButton(onClick = { viewModel.refresh(); showPicker = true }) {
                        Icon(Icons.Default.Add, tr("add_limit", language), tint = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        }
        if (state.entries.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 22.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.HourglassTop, null, Modifier.size(46.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(10.dp))
                        Text(tr("no_limits", language), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr("no_limits_hint", language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { viewModel.refresh(); showPicker = true }) { Text(tr("add_limit", language)) }
                    }
                }
            }
        }
        items(state.entries, key = { it.packageName }) { entry ->
            val limitDeleteLocked = AppTimeLimitStore.isDeleteLocked(entry.packageName, nowMillis)
            TimeLimitCard(
                entry = entry,
                language = language,
                onEdit = {
                    runProtectedAction {
                        editPackage = entry.packageName
                        viewModel.refresh()
                    }
                },
                onReset = { runProtectedAction { viewModel.resetUsage(entry.packageName) } },
                // Deleting a daily time limit is PIN-free, but only after the 48h cool-down.
                // While still locked, a dialog explains the remaining time instead.
                onRemove = {
                    val now = System.currentTimeMillis()
                    if (AppTimeLimitStore.isDeleteLocked(entry.packageName, now)) {
                        lockedLimitInfo = entry.appName to AppTimeLimitStore.deleteLockRemaining(entry.packageName, now)
                    } else {
                        viewModel.removeLimit(entry.packageName)
                    }
                },
            )
        }
    }

    if (showPicker) {
        AppPickerDialog(
            language = language,
            apps = state.installedApps.map { it.packageName to it.name },
            iconFor = { pkg -> state.installedApps.firstOrNull { it.packageName == pkg }?.icon },
            onDismiss = { showPicker = false },
            onPick = { pkg -> showPicker = false; editPackage = pkg },
        )
    }

    if (showUrgeDialog) {
        UrgeBlockDialog(
            language = language,
            onDismiss = { showUrgeDialog = false },
            onStart = { minutes ->
                BlockScheduleStore.startUrgeBlock(minutes, tr("schedule_urge", language))
                showUrgeDialog = false
            },
        )
    }

    if (showScheduleEditor) {
        ScheduleEditorDialog(
            language = language,
            existing = editingSchedule,
            onDismiss = { showScheduleEditor = false },
            onConfirm = { schedule ->
                val apply: () -> Unit = {
                    BlockScheduleStore.upsert(schedule)
                    schedulesTick++
                }
                // Creating a new schedule (id == 0L) is intentionally PIN-free;
                // saving changes to an existing schedule stays PIN-protected.
                if (schedule.id == 0L) apply() else runProtectedAction(apply)
                showScheduleEditor = false
            },
        )
    }

    editPackage?.let { pkg ->
        val existing = state.entries.firstOrNull { it.packageName == pkg }?.limitMinutes ?: 30
        MinutesDialog(
            language = language,
            initial = existing,
            onDismiss = { editPackage = null },
            onConfirm = { minutes -> viewModel.setLimit(pkg, minutes); editPackage = null },
        )
    }

    lockedLimitInfo?.let { (appName, remaining) ->
        AlertDialog(
            onDismissRequest = { lockedLimitInfo = null },
            icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(tr("limit_delete_locked_title", language), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(tr("limit_delete_locked_body", language).replace("%s", appName))
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            tr("schedule_edit_locked_remaining", language) + "  " + formatLockRemaining(remaining),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { lockedLimitInfo = null }) { Text(tr("ok", language)) }
            },
        )
    }

    lockedScheduleInfo?.let { (scheduleName, remaining) ->
        AlertDialog(
            onDismissRequest = { lockedScheduleInfo = null },
            icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(tr("schedule_delete_locked_title", language), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(tr("schedule_delete_locked_body", language).replace("%s", scheduleName))
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = if (remaining > 0L) {
                                tr("schedule_edit_locked_remaining", language) + "  " + formatLockRemaining(remaining)
                            } else {
                                tr("schedule_edit_locked_active", language)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { lockedScheduleInfo = null }) { Text(tr("ok", language)) }
            },
        )
    }

    actionGate.Host()
}

@Composable
private fun TimeLimitCard(
    entry: TimeLimitEntry,
    language: String,
    onEdit: () -> Unit,
    onReset: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = entry.icon
                if (icon != null) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            DrawableIcon(icon, Modifier.size(30.dp))
                        }
                    }
                } else {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.HourglassTop, null, tint = MaterialTheme.colorScheme.primary) }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.appName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val label = if (entry.exhausted) tr("limit_reached", language)
                    else tr("minutes_left", language).replace("%d", entry.remainingMinutes.toString())
                    Text(label, style = MaterialTheme.typography.bodySmall, color = if (entry.exhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, tr("remove", language), tint = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { entry.progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = if (entry.exhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${entry.usedMinutes} / ${entry.limitMinutes} ${tr("minutes", language)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = onReset,
                    label = { Text(tr("add_time_today", language)) },
                    leadingIcon = { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerDialog(
    language: String,
    apps: List<Pair<String, String>>,
    iconFor: (String) -> android.graphics.drawable.Drawable?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) {
        if (query.isBlank()) apps else apps.filter { it.second.contains(query, true) || it.first.contains(query, true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("choose_app", language), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text(tr("search_apps", language)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().height(320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered, key = { it.first }) { (pkg, name) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(pkg) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val icon = iconFor(pkg)
                            if (icon != null) {
                                DrawableIcon(icon, Modifier.size(34.dp))
                            } else {
                                Icon(Icons.Default.HourglassTop, null, Modifier.size(34.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("cancel", language)) } },
    )
}

@Composable
private fun DrawableIcon(
    drawable: android.graphics.drawable.Drawable,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            android.widget.ImageView(context).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        },
        update = { imageView ->
            if (imageView.drawable !== drawable) imageView.setImageDrawable(drawable)
        },
    )
}

@Composable
private fun MinutesDialog(
    language: String,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var minutes by remember { mutableStateOf(initial) }
    val presets = listOf(15, 30, 45, 60, 90, 120)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.HourglassTop, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(tr("daily_limit", language), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "$minutes ${tr("minutes", language)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = minutes == preset,
                            onClick = { minutes = preset },
                            label = { Text("$preset") },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(minutes) }) { Text(tr("save", language)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("cancel", language)) } },
    )
}

// ---------------------------------------------------------------------------
// Block schedules (bedtime / study / prayer windows)
// ---------------------------------------------------------------------------

/** Consistent, self-contained section header (icon + title + subtitle + optional trailing action). */
@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Clear visual separator between the three independent sections. */
@Composable
private fun SectionDivider() {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(6.dp))
    }
}

private fun formatMinutesOfDay(minutes: Int): String = "%02d:%02d".format(minutes / 60 % 24, minutes % 60)

// ---- 12-hour clock helpers (display + selection only; internal storage stays minutes-of-day) ----
/** 1..12 hour for a minutes-of-day value (12 for both midnight and noon). */
private fun hour12Of(minutes: Int): Int {
    val h = (minutes / 60) % 12
    return if (h == 0) 12 else h
}

private fun minuteOf(minutes: Int): Int = minutes % 60

/** True for the PM half of the day (12:00 PM .. 11:59 PM). */
private fun isPmOf(minutes: Int): Boolean = ((minutes / 60) % 24) >= 12

/**
 * Builds a minutes-of-day value from a 12-hour selection. Handles the two tricky cases
 * exactly: 12:xx AM maps to 00:xx (h24 = 0) and 12:xx PM maps to 12:xx (h24 = 12).
 */
private fun minutesFrom12(hour12: Int, minute: Int, isPm: Boolean): Int {
    val h = hour12 % 12
    val h24 = if (isPm) h + 12 else h
    return (h24 * 60 + minute.coerceIn(0, 59)) % 1440
}

private fun periodLabel(minutes: Int, language: String): String =
    if (isPmOf(minutes)) tr("time_pm", language) else tr("time_am", language)

/** Localised 12-hour label, e.g. "11:30 م" / "11:30 PM" / "11:30 د.ن". */
private fun formatMinutesOfDay12(minutes: Int, language: String): String =
    "%d:%02d %s".format(hour12Of(minutes), minuteOf(minutes), periodLabel(minutes, language))

/** Formats a window length (in minutes) as H:MM, e.g. 9:30 for nine and a half hours. */
private fun formatDurationHm(totalMinutes: Int): String = "%d:%02d".format(totalMinutes / 60, totalMinutes % 60)

/** Live countdown as HH:MM:SS, e.g. 00:15:30. */
private fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis + 999) / 1000
    return "%02d:%02d:%02d".format(totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
}

/**
 * Remaining edit-lock time to the second, e.g. "1d 05:30:12" or "05:30:12". Precise down to the
 * second (not a whole-day granularity), and derived from the real clock deadline.
 */
private fun formatLockRemaining(millis: Long): String {
    val totalSeconds = ((millis + 999) / 1000).coerceAtLeast(0L)
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val clock = "%02d:%02d:%02d".format(hours, minutes, seconds)
    return if (days > 0) "${days}d $clock" else clock
}

/** Prominent "time left" countdown: MM:SS under an hour (e.g. 29:42), HH:MM:SS from one hour up. */
private fun formatTimeLeft(millis: Long): String {
    val totalSeconds = ((millis + 999) / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

/**
 * "Urgent craving" quick block: one tap starts an immediate block of 3..30 minutes.
 * While active the card shows a live remaining-time countdown and the start action is hidden;
 * the block ends automatically when the countdown reaches zero.
 */
@Composable
private fun UrgeBlockCard(
    language: String,
    activeUntil: Long,
    nowMillis: Long,
    onStart: () -> Unit,
) {
    val active = activeUntil > nowMillis
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !active, onClick = onStart),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            },
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (!active) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Bolt,
                                null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr("schedule_urge_start", language),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            tr("schedule_urge_range_hint", language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                        )
                    }
                    Icon(
                        Icons.Default.Add,
                        tr("schedule_urge_start", language),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            if (active) {
                ActiveBlockPanel(
                    windowEndMillis = activeUntil,
                    nowMillis = nowMillis,
                    language = language,
                )
            }
        }
    }
}

@Composable
private fun UrgeBlockDialog(
    language: String,
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit,
) {
    var minutes by remember { mutableIntStateOf(BlockScheduleStore.URGE_DEFAULT_MINUTES) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(tr("schedule_urge", language), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        tr("schedule_urge_warning", language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(tr("schedule_urge_duration", language), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "$minutes",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { minutes = it.toInt().coerceIn(BlockScheduleStore.URGE_MIN_MINUTES, BlockScheduleStore.URGE_MAX_MINUTES) },
                    valueRange = BlockScheduleStore.URGE_MIN_MINUTES.toFloat()..BlockScheduleStore.URGE_MAX_MINUTES.toFloat(),
                    steps = BlockScheduleStore.URGE_MAX_MINUTES - BlockScheduleStore.URGE_MIN_MINUTES - 1,
                )
                Text(
                    tr("schedule_urge_range_hint", language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(minutes) }) { Text(tr("schedule_urge_start", language)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("cancel", language)) } },
    )
}

private fun dayLetter(dayOfWeek: Int, language: String): String = when (language) {
    "en" -> listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
    "ar" -> listOf("أح", "اث", "ثل", "أر", "خم", "جم", "سب")
    else -> listOf("ی", "د", "س", "چ", "پ", "ه", "ش")
}[dayOfWeek - 1]

private fun daysSummary(days: Set<Int>, language: String): String {
    if (days.size >= 7) return tr("schedule_every_day", language)
    return (1..7).filter { it in days }.joinToString(" ") { dayLetter(it, language) }
}

@Composable
private fun ScheduleCard(
    schedule: BlockSchedule,
    language: String,
    nowMillis: Long,
    editLocked: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val isActive = schedule.enabled && BlockScheduleStore.isActiveAt(schedule, nowMillis)
    // Real persisted deadline of the running window (stays correct after restart/background).
    val windowEndMillis = if (isActive) BlockScheduleStore.activeEndMillis(schedule, nowMillis) else 0L
    val startsInMillis = if (schedule.enabled && !isActive) {
        val next = BlockScheduleStore.nextStartMillis(schedule, nowMillis)
        if (next > 0L) (next - nowMillis).coerceAtLeast(0L) else 0L
    } else {
        0L
    }
    Card(
        Modifier.fillMaxWidth().clickable(enabled = !editLocked, onClick = onEdit),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (schedule.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Bedtime,
                            null,
                            tint = if (schedule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        schedule.name.ifBlank { tr("schedule_section", language) },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${formatMinutesOfDay12(schedule.startMinutes, language)} – ${formatMinutesOfDay12(schedule.endMinutes, language)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        daysSummary(schedule.days, language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (startsInMillis > 0L) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = tr("schedule_starts_in", language) + "  " + formatCountdown(startsInMillis),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
                Switch(checked = schedule.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        tr("remove", language),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (isActive) {
                Spacer(Modifier.height(14.dp))
                ActiveBlockPanel(
                    windowEndMillis = windowEndMillis,
                    nowMillis = nowMillis,
                    language = language,
                )
            }
        }
    }
}

/**
 * Shared "active block" panel shown at the top of an active urge / schedule card:
 * a prominent, live "time left" countdown plus a single admonition from the project's
 * 150-advice pool in the app's current language. The countdown derives from
 * [windowEndMillis] (a real persisted deadline), so it stays correct after the app is
 * closed or returns from the background, and it stops the instant the window ends.
 * Direction (RTL for Arabic/Kurdish, LTR for English) is inherited from the app-wide
 * LocalLayoutDirection, so text alignment follows the active language automatically.
 */
@Composable
private fun ActiveBlockPanel(
    windowEndMillis: Long,
    nowMillis: Long,
    language: String,
) {
    val context = LocalContext.current
    // One admonition chosen per active window, kept stable while that window lasts.
    val advice = remember(windowEndMillis) { BlockedAdvice.next(context) }
    val remaining = (windowEndMillis - nowMillis).coerceAtLeast(0L)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    tr("schedule_time_left", language),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    formatTimeLeft(remaining),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.TipsAndUpdates,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            tr("schedule_advice_title", language),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
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
    }
}

@Composable
private fun ScheduleEditorDialog(
    language: String,
    existing: BlockSchedule?,
    onDismiss: () -> Unit,
    onConfirm: (BlockSchedule) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var start by remember(existing?.id) { mutableStateOf(existing?.startMinutes ?: 22 * 60) }
    var end by remember(existing?.id) { mutableStateOf(existing?.endMinutes ?: 7 * 60) }
    var days by remember(existing?.id) { mutableStateOf(existing?.days ?: (1..7).toSet()) }
    // New schedules must be explicitly confirmed after a clear warning about the block window.
    var pendingNewSchedule by remember(existing?.id) { mutableStateOf<BlockSchedule?>(null) }

    pendingNewSchedule?.let { candidate ->
        AlertDialog(
            onDismissRequest = { pendingNewSchedule = null },
            icon = { Icon(Icons.Default.Bedtime, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(tr("schedule_add_warning_title", language), fontWeight = FontWeight.Bold) },
            text = { Text(tr("schedule_add_warning_body", language)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingNewSchedule = null
                        onConfirm(candidate)
                    },
                ) { Text(tr("schedule_add_warning_confirm", language)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingNewSchedule = null }) { Text(tr("cancel", language)) }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Bedtime, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(tr("schedule_section", language), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (existing == null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            tr("schedule_add_warning_body", language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    label = { Text(tr("schedule_name", language)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { name = tr("schedule_preset_bedtime", language); start = 22 * 60; end = 7 * 60 },
                        label = { Text(tr("schedule_preset_bedtime", language)) },
                    )
                    AssistChip(
                        onClick = { name = tr("schedule_preset_study", language); start = 16 * 60; end = 19 * 60 },
                        label = { Text(tr("schedule_preset_study", language)) },
                    )
                    AssistChip(
                        onClick = { name = tr("schedule_preset_prayer", language); start = 12 * 60; end = 14 * 60 },
                        label = { Text(tr("schedule_preset_prayer", language)) },
                    )
                }
                Spacer(Modifier.height(14.dp))
                // "From" and "To" are fully independent: changing the hour, minute or AM/PM of
                // one never alters the other. Any over-limit or invalid window is reported below
                // and blocked at save time, so the two pickers stay unlinked while editing.
                TimePicker12(label = tr("schedule_from", language), language = language, minutes = start) { value ->
                    start = value
                }
                Spacer(Modifier.height(14.dp))
                TimePicker12(label = tr("schedule_to", language), language = language, minutes = end) { value ->
                    end = value
                }
                Spacer(Modifier.height(8.dp))
                val durationMinutes = BlockScheduleStore.durationMinutes(start, end)
                val tooLong = durationMinutes > BlockScheduleStore.MAX_DURATION_MINUTES
                val sameTime = start == end
                val invalidWindow = tooLong || sameTime
                Text(
                    tr("schedule_duration_label", language) + ": " + formatDurationHm(durationMinutes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (invalidWindow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (invalidWindow) tr("schedule_duration_invalid", language) else tr("schedule_max_duration_hint", language),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (invalidWindow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Text(tr("schedule_days", language), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (1..7).forEach { dayOfWeek ->
                        val selected = days.contains(dayOfWeek)
                        FilterChip(
                            selected = selected,
                            onClick = { days = if (selected) days - dayOfWeek else days + dayOfWeek },
                            label = { Text(dayLetter(dayOfWeek, language), style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (days.isEmpty() || start == end) return@TextButton
                    if (BlockScheduleStore.durationMinutes(start, end) > BlockScheduleStore.MAX_DURATION_MINUTES) return@TextButton
                    val schedule = BlockSchedule(
                        id = existing?.id ?: 0L,
                        name = name.ifBlank { tr("schedule_preset_bedtime", language) },
                        startMinutes = start,
                        endMinutes = end,
                        days = days,
                        enabled = existing?.enabled ?: true,
                    )
                    if (existing == null) {
                        pendingNewSchedule = schedule
                    } else {
                        onConfirm(schedule)
                    }
                },
            ) { Text(tr("save", language)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("cancel", language)) } },
    )
}

/**
 * 12-hour time picker with independent hour (1..12), minute (5-min steps) and AM/PM controls.
 * Emits a minutes-of-day value through [onChange] so the internal storage and block-duration
 * maths are unaffected; only the way the time is shown and chosen changes.
 */
@Composable
private fun TimePicker12(label: String, language: String, minutes: Int, onChange: (Int) -> Unit) {
    val hour12 = hour12Of(minutes)
    val minute = minuteOf(minutes)
    val isPm = isPmOf(minutes)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatMinutesOfDay12(minutes, language),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StepperBox(
                modifier = Modifier.weight(1f),
                title = tr("time_hour", language),
                value = "%d".format(hour12),
                onIncrement = { onChange(minutesFrom12(if (hour12 == 12) 1 else hour12 + 1, minute, isPm)) },
                onDecrement = { onChange(minutesFrom12(if (hour12 == 1) 12 else hour12 - 1, minute, isPm)) },
            )
            StepperBox(
                modifier = Modifier.weight(1f),
                title = tr("time_minute", language),
                value = "%02d".format(minute),
                onIncrement = { onChange(minutesFrom12(hour12, (minute + 1) % 60, isPm)) },
                onDecrement = { onChange(minutesFrom12(hour12, (minute - 1 + 60) % 60, isPm)) },
            )
            PeriodToggle(
                modifier = Modifier.weight(1f),
                title = tr("time_period", language),
                isPm = isPm,
                amLabel = tr("time_am", language),
                pmLabel = tr("time_pm", language),
                onSelect = { pm -> onChange(minutesFrom12(hour12, minute, pm)) },
            )
        }
    }
}

/** A compact labelled stepper: title on top, then [ up-arrow / value / down-arrow ]. */
@Composable
private fun StepperBox(
    modifier: Modifier,
    title: String,
    value: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                RepeatingIconButton(
                    onStep = onIncrement,
                    icon = Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                RepeatingIconButton(
                    onStep = onDecrement,
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                )
            }
        }
    }
}

/**
 * An icon button that fires once on a single tap and, while held down, keeps firing
 * automatically with a smooth, gradually accelerating cadence — a classic long-press
 * repeat control. Releasing the finger stops it immediately. It intentionally does not
 * rely on repeated tapping. Visual size and icon tint match the previous IconButton so the
 * picker's look is unchanged.
 */
@Composable
private fun RepeatingIconButton(
    onStep: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
) {
    val currentOnStep by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .size(34.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    // onPress fires on every touch-down, so it covers the single-tap case
                    // (one change) as well as the long-press case (continuous repeat).
                    onPress = {
                        val job = scope.launch {
                            // Immediate single change the moment the finger lands.
                            currentOnStep()
                            var delayMs = 380L
                            // Hold threshold: a normal quick tap releases before this elapses,
                            // so it only ever produces the single change above.
                            delay(delayMs)
                            while (isActive) {
                                currentOnStep()
                                // Smoothly accelerate the cadence down to a 60 ms floor.
                                if (delayMs > 60L) delayMs = (delayMs * 82L / 100L).coerceAtLeast(60L)
                                delay(delayMs)
                            }
                        }
                        // Suspends until the finger is lifted (or the gesture cancels),
                        // then stops the repeat loop immediately.
                        tryAwaitRelease()
                        job.cancel()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = MaterialTheme.colorScheme.primary)
    }
}

/** AM/PM selector shown as two stacked toggle chips, matching the stepper height. */
@Composable
private fun PeriodToggle(
    modifier: Modifier,
    title: String,
    isPm: Boolean,
    amLabel: String,
    pmLabel: String,
    onSelect: (Boolean) -> Unit,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PeriodChip(label = amLabel, selected = !isPm, onClick = { onSelect(false) })
            PeriodChip(label = pmLabel, selected = isPm, onClick = { onSelect(true) })
        }
    }
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
        )
    }
}
