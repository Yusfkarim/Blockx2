package com.agon.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.agon.app.data.TaskPriority

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskSheet(
    onDismiss: () -> Unit,
    onSave: (String, String, Int, TaskPriority) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Deep work") }
    var minutes by remember { mutableIntStateOf(25) }
    var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }
    val focusManager = LocalFocusManager.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp),
        ) {
            Text("Add to your orbit", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Keep it specific, small, and easy to begin.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 80) title = it },
                label = { Text("What needs your attention?") },
                supportingText = { Text("${title.length}/80") },
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
            Text("Category", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Deep work", "Planning", "Admin").forEach { option ->
                    FilterChip(
                        selected = category == option,
                        onClick = { category = option },
                        label = { Text(option) },
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text("Focus estimate", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("$minutes min", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
            Slider(
                value = minutes.toFloat(),
                onValueChange = { minutes = (it / 5).toInt() * 5 },
                valueRange = 5f..60f,
                steps = 10,
            )
            Text("Priority", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.entries.forEach { option ->
                    FilterChip(
                        selected = priority == option,
                        onClick = { priority = option },
                        label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onSave(title, category, minutes, priority) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text("Add task")
            }
        }
    }
}
