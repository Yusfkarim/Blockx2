package com.agon.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agon.app.data.FocusTask
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.TaskCard

enum class TaskFilter(val label: String) { ALL("All"), OPEN("Open"), DONE("Done") }

@Composable
fun TasksScreen(
    tasks: List<FocusTask>,
    onTaskClick: (Long) -> Unit,
    onToggleTask: (Long) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(TaskFilter.ALL) }
    val filtered = remember(tasks, query, filter) {
        tasks.filter { task ->
            val matchesQuery = task.title.contains(query, ignoreCase = true) ||
                task.category.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                TaskFilter.ALL -> true
                TaskFilter.OPEN -> !task.completed
                TaskFilter.DONE -> task.completed
            }
            matchesQuery && matchesFilter
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp)) {
            Text("Your tasks", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Turn intention into a calm, workable plan.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Search tasks or categories") },
                shape = MaterialTheme.shapes.large,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                TaskFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        }

        AnimatedContent(targetState = filtered.isEmpty(), label = "task results") { empty ->
            if (empty) {
                Box(Modifier.fillMaxSize()) {
                    EmptyState(
                        title = if (query.isBlank()) "Nothing here yet" else "No close matches",
                        message = if (query.isBlank()) "Add a task and give your day a clear direction."
                        else "Try a different word or change the filter.",
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp,
                        top = 14.dp,
                        end = 20.dp,
                        bottom = 104.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            "${filtered.size} ${if (filtered.size == 1) "task" else "tasks"}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    items(filtered, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onClick = { onTaskClick(task.id) },
                            onToggle = { onToggleTask(task.id) },
                        )
                    }
                }
            }
        }
    }
}
