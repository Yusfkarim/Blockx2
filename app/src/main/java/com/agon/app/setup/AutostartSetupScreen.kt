package com.agon.app.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agon.app.health.AutostartManager
import com.agon.app.health.OemKnowledgeBase
import com.agon.app.localization.tr

/**
 * Dedicated Autostart setup step. Optional: Continue is enabled once Autostart is satisfied
 * (OEM: user opens settings then confirms; stock Android auto-satisfies), or the user may
 * skip it and enable Autostart later from in-app Settings.
 */
@Composable
fun AutostartSetupPane(
    language: String,
    active: Boolean,
    onOpenSettings: () -> Unit,
    onConfirmConfigured: () -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val manager = remember(context) { AutostartManager(context) }
    val steps = remember(language) { manager.guideSteps(language) }
    var openedSettings by remember { mutableStateOf(false) }

    val canContinue = active || !manager.requiresUserAction()

    if (canContinue) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            Text(
                manager.guideTitle(language),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Text(
                tr("wizard_enabled", language),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Button(
                onClick = onNext,
                enabled = true,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(tr("wizard_next", language), fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        Text(
            manager.guideTitle(language),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            tr("step_autostart_desc", language),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            OemKnowledgeBase.skinName(manager.vendor()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEachIndexed { index, line ->
                    Text(
                        "${index + 1}. $line",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                openedSettings = true
                onOpenSettings()
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(tr("wizard_enable", language), fontWeight = FontWeight.Bold)
        }
        // Confirm only after user opened OEM settings; then mark + allow Next.
        OutlinedButton(
            onClick = {
                onConfirmConfigured()
                onNext()
            },
            enabled = openedSettings,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(tr("step_autostart_confirm", language), fontWeight = FontWeight.Bold)
        }
        // Optional step: skip lets the user continue without Autostart and enable it later.
        OutlinedButton(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(tr("wizard_skip", language), fontWeight = FontWeight.SemiBold)
        }
    }
}
