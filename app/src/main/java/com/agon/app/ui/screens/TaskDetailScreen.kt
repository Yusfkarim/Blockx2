package com.agon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.agon.app.data.FocusTask

@Composable
fun TaskDetailScreen(
    task: FocusTask?,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onStartFocus: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
            Text("Task details", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 6.dp))
            if (task != null) {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete task", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (task == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("This task is no longer available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onBack) { Text("Go back") }
            }
        } else {
            Spacer(Modifier.height(22.dp))
            Text(
                task.title,
                style = MaterialTheme.typography.headlineLarge,
                textDecoration = if (task.completed) TextDecoration.LineThrough else null,
            )
            Text(
                if (task.completed) "Complete — nicely done." else "Make a little room, then begin.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(26.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    DetailRow(Icons.Outlined.Category, "Category", task.category)
                    DetailRow(Icons.Outlined.AccessTime, "Focus estimate", "${task.estimatedMinutes} minutes")
                    DetailRow(Icons.Outlined.Flag, "Priority", task.priority.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onStartFocus,
                enabled = !task.completed,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, null)
                Text("Start focused work", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth().height(54.dp).padding(top = 8.dp),
            ) {
                Text(if (task.completed) "Mark as open" else "Mark complete")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDeleteDialog && task != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this task?") },
            text = { Text("“${task.title}” will be removed from your plan.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
