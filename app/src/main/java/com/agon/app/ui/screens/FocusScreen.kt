package com.agon.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.ui.components.OrbitProgressRing
import com.agon.app.viewmodel.OrbitUiState

@Composable
fun FocusScreen(
    state: OrbitUiState,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onSelectTask: (Long) -> Unit,
) {
    val activeTask = state.tasks.firstOrNull { it.id == state.focusTaskId }
    val elapsed = state.focusTotalSeconds - state.focusRemainingSeconds
    val progress = if (state.focusTotalSeconds == 0) 0f else elapsed / state.focusTotalSeconds.toFloat()
    val minutes = state.focusRemainingSeconds / 60
    val seconds = state.focusRemainingSeconds % 60

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Text("Focus space", style = MaterialTheme.typography.headlineMedium)
            Text(
                "One thing at a time. Everything else can wait.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(34.dp))
        OrbitProgressRing(
            progress = progress,
            modifier = Modifier.size(238.dp),
            strokeWidth = 12.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Spa, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "%02d:%02d".format(minutes, seconds),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (state.isTimerRunning) "Stay with it" else if (elapsed > 0) "Paused" else "Ready when you are",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        AnimatedContent(targetState = activeTask?.title, label = "active focus task") { title ->
            Text(
                text = title ?: "Free focus session",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onResetTimer) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Reset timer")
            }
            FilledIconButton(onClick = onToggleTimer, modifier = Modifier.size(62.dp)) {
                Icon(
                    if (state.isTimerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isTimerRunning) "Pause" else "Start",
                    modifier = Modifier.size(30.dp),
                )
            }
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large) {
                Text(
                    "${state.sessionsCompleted}/${state.preferences.dailyGoal}",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("Choose a task", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            items(state.tasks.filterNot { it.completed }, key = { it.id }) { task ->
                Card(
                    onClick = { onSelectTask(task.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (task.id == state.focusTaskId) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(task.title, style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${task.estimatedMinutes} min • ${task.category}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
    }
}
