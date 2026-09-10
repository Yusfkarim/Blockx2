package com.agon.app.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.admin.UninstallProtectionManager
import com.agon.app.localization.tr

/**
 * Shows the current uninstall-protection strength (Basic / Admin / Device Owner).
 * Purely informational — it never changes admin state itself, so it cannot weaken protection.
 */
@Composable
fun UninstallProtectionCard(language: String, level: UninstallProtectionManager.Level) {
    val (icon, titleKey, descKey) = when (level) {
        UninstallProtectionManager.Level.DEVICE_OWNER -> Triple(Icons.Default.GppGood, "level_owner", "level_owner_desc")
        UninstallProtectionManager.Level.DEVICE_ADMIN -> Triple(Icons.Default.Shield, "level_admin", "level_admin_desc")
        UninstallProtectionManager.Level.ACCESSIBILITY_ONLY -> Triple(Icons.Default.GppMaybe, "level_basic", "level_basic_desc")
    }
    val accent = when (level) {
        UninstallProtectionManager.Level.DEVICE_OWNER -> MaterialTheme.colorScheme.primary
        UninstallProtectionManager.Level.DEVICE_ADMIN -> MaterialTheme.colorScheme.tertiary
        UninstallProtectionManager.Level.ACCESSIBILITY_ONLY -> MaterialTheme.colorScheme.error
    }

    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.15f), modifier = Modifier.size(46.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon as ImageVector, null, tint = accent) }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("protection_level", language), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(tr(titleKey, language), fontWeight = FontWeight.Bold, color = accent)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(tr(descKey, language), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
