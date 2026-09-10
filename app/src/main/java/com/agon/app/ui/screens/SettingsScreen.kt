package com.agon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.agon.app.data.OrbitPreferences
import com.agon.app.data.ThemeMode

@Composable
fun SettingsScreen(
    preferences: OrbitPreferences,
    onThemeChange: (ThemeMode) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onDailyGoalChange: (Int) -> Unit,
    onReset: () -> Unit,
) {
    var showResetDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = 18.dp,
            end = 20.dp,
            bottom = 104.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Your space", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Tune Orbit to match how you work best.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item { SectionLabel("Appearance") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    SettingHeader(Icons.Outlined.DarkMode, "Theme", "Choose the light that feels right")
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = preferences.themeMode == mode,
                                onClick = { onThemeChange(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                }
            }
        }
        item { SectionLabel("Focus cues") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SwitchSetting(
                        Icons.Outlined.Notifications,
                        "Session reminders",
                        "A gentle prompt when time is up",
                        preferences.notificationsEnabled,
                        onNotificationsChange,
                    )
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = .16f))
                    SwitchSetting(
                        Icons.Outlined.VolumeUp,
                        "Focus sounds",
                        "Play subtle start and finish cues",
                        preferences.soundEnabled,
                        onSoundChange,
                    )
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = .16f))
                    SwitchSetting(
                        Icons.Outlined.Vibration,
                        "Haptic feedback",
                        "Feel key timer actions",
                        preferences.hapticsEnabled,
                        onHapticsChange,
                    )
                }
            }
        }
        item { SectionLabel("Daily rhythm") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Session goal", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "A motivating target, not a limit",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { onDailyGoalChange(preferences.dailyGoal - 1) },
                        enabled = preferences.dailyGoal > 1,
                    ) { Icon(Icons.Outlined.Remove, contentDescription = "Decrease goal") }
                    Text("${preferences.dailyGoal}", style = MaterialTheme.typography.titleLarge)
                    IconButton(
                        onClick = { onDailyGoalChange(preferences.dailyGoal + 1) },
                        enabled = preferences.dailyGoal < 8,
                    ) { Icon(Icons.Outlined.Add, contentDescription = "Increase goal") }
                }
            }
        }
        item { SectionLabel("Data") }
        item {
            Card(onClick = { showResetDialog = true }, modifier = Modifier.fillMaxWidth()) {
                SettingHeader(
                    Icons.Outlined.RestartAlt,
                    "Reset Orbit",
                    "Restore sample tasks and preferences",
                    modifier = Modifier.padding(18.dp),
                )
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Orbit Focus", style = MaterialTheme.typography.labelLarge)
                Text("Version 1.0 • Made for calmer days", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset your space?") },
            text = { Text("Your tasks and preferences will return to their original state. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showResetDialog = false; onReset() }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun SettingHeader(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchSetting(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
