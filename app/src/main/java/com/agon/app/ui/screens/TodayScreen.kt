package com.agon.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.TaskCard
import com.agon.app.viewmodel.OrbitUiState

@Composable
fun TodayScreen(
    state: OrbitUiState,
    onTaskClick: (Long) -> Unit,
    onToggleTask: (Long) -> Unit,
    onStartFocus: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.isLoading -> LoadingScreen()
        state.loadError != null -> ErrorScreen(state.loadError, onRetry)
        else -> TodayContent(state, onTaskClick, onToggleTask, onStartFocus)
    }
}

@Composable
private fun TodayContent(
    state: OrbitUiState,
    onTaskClick: (Long) -> Unit,
    onToggleTask: (Long) -> Unit,
    onStartFocus: () -> Unit,
) {
    val openTasks = state.tasks.filterNot { it.completed }
    val completedCount = state.tasks.count { it.completed }
    val progress = if (state.tasks.isEmpty()) 0f else completedCount / state.tasks.size.toFloat()

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Good morning",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Ready to find your orbit?", style = MaterialTheme.typography.headlineMedium)
                }
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = "Orbit profile",
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item {
            FocusHero(
                sessionsCompleted = state.sessionsCompleted,
                dailyGoal = state.preferences.dailyGoal,
                activeTask = state.tasks.firstOrNull { it.id == state.focusTaskId }?.title,
                onStartFocus = onStartFocus,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "Today",
                    value = "$completedCount/${state.tasks.size}",
                    supporting = "tasks complete",
                    icon = { Icon(Icons.Outlined.Timelapse, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Momentum",
                    value = "6 days",
                    supporting = "personal streak",
                    icon = { Icon(Icons.Outlined.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.tertiary) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Up next", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (openTasks.isEmpty()) {
            item {
                EmptyState(
                    title = "Clear horizon",
                    message = "Everything is complete. Make space for what matters next.",
                )
            }
        } else {
            items(openTasks.take(3), key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onClick = { onTaskClick(task.id) },
                    onToggle = { onToggleTask(task.id) },
                )
            }
        }
    }
}

@Composable
private fun FocusHero(
    sessionsCompleted: Int,
    dailyGoal: Int,
    activeTask: String?,
    onStartFocus: () -> Unit,
) {
    val gradient = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary),
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().background(gradient).padding(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = .15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Focus flow", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(
                        "$sessionsCompleted of $dailyGoal sessions today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = .8f),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = activeTask ?: "A quiet 25 minutes can move the day forward.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = .9f),
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onStartFocus,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Text(if (activeTask == null) "Start a focus session" else "Continue focus")
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    supporting: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 7.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text("Preparing your day…", modifier = Modifier.padding(top = 14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.ErrorOutline, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.error)
            Text("Lost the signal", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 14.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            IconButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Retry")
            }
        }
    }
}
