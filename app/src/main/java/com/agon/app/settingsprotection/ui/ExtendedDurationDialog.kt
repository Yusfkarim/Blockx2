package com.agon.app.settingsprotection.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.localization.tr

/** Minutes/hours scale helpers for the extended duration list. */
private const val MINUTE_MILLIS = 60L * 1000L
private const val HOUR_MILLIS = 60L * MINUTE_MILLIS
private const val DAY_MILLIS = 24L * HOUR_MILLIS

/** Sentinel matching the "boo هەتاهەتایە / إلى الأبد / Forever" row. */
const val DURATION_FOREVER = -1L

private data class ExtendedDurationOption(val millis: Long, val key: String)

/**
 * The extended duration picker shared by the notification-shade lock and the pause-protection
 * flow. Option list and order are a product contract (12h … 365d, then Forever) and every row
 * resolves through [tr] in Kurdish / Arabic / English.
 */
@Composable
fun ExtendedDurationDialog(
    language: String,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val options = remember {
        listOf(
            // Short 3-minute test option first, then 12 hours and the longer commitments.
            ExtendedDurationOption(3L * MINUTE_MILLIS, "duration_3m"),
            ExtendedDurationOption(12L * HOUR_MILLIS, "duration_12h"),
            ExtendedDurationOption(1L * DAY_MILLIS, "duration_1d"),
            ExtendedDurationOption(2L * DAY_MILLIS, "duration_2d"),
            ExtendedDurationOption(3L * DAY_MILLIS, "duration_3d"),
            ExtendedDurationOption(7L * DAY_MILLIS, "duration_1w"),
            ExtendedDurationOption(14L * DAY_MILLIS, "duration_14d"),
            ExtendedDurationOption(20L * DAY_MILLIS, "duration_20d"),
            ExtendedDurationOption(25L * DAY_MILLIS, "duration_25d"),
            ExtendedDurationOption(30L * DAY_MILLIS, "duration_30d"),
            ExtendedDurationOption(40L * DAY_MILLIS, "duration_40d"),
            ExtendedDurationOption(60L * DAY_MILLIS, "duration_60d"),
            ExtendedDurationOption(90L * DAY_MILLIS, "duration_90d"),
            ExtendedDurationOption(120L * DAY_MILLIS, "duration_120d"),
            ExtendedDurationOption(150L * DAY_MILLIS, "duration_150d"),
            ExtendedDurationOption(190L * DAY_MILLIS, "duration_190d"),
            ExtendedDurationOption(220L * DAY_MILLIS, "duration_220d"),
            ExtendedDurationOption(300L * DAY_MILLIS, "duration_300d"),
            ExtendedDurationOption(365L * DAY_MILLIS, "duration_365d"),
            ExtendedDurationOption(DURATION_FOREVER, "duration_forever"),
        )
    }
    var selected by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = tr("duration_dialog_title", language),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = selected == index,
                                onClick = { selected = index },
                                role = Role.RadioButton,
                            )
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == index, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = tr(option.key, language),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onPick(options[selected].millis) }) {
                Text(tr("ok", language))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("cancel", language)) }
        },
    )
}
