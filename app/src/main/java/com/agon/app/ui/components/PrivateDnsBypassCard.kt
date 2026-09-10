package com.agon.app.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.security.PrivateDnsGuard
import kotlinx.coroutines.delay

/**
 * Guardian-facing alert when system Private DNS is active and would bypass the local VPN DNS filter.
 */
@Composable
fun PrivateDnsBypassCard(language: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var active by remember { mutableStateOf(PrivateDnsGuard.isBypassActive(context)) }
    var hostname by remember { mutableStateOf(PrivateDnsGuard.configuredHostname(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            active = PrivateDnsGuard.isBypassActive(context)
            hostname = PrivateDnsGuard.configuredHostname(context)
            delay(3_000L)
        }
    }

    if (!active) return

    val title = when (language) {
        "en" -> "Private DNS is bypassing protection"
        "ar" -> "DNS الخاص يتجاوز الحماية"
        else -> "DNS تایبەت پاراستن تێدەپەڕێنێت"
    }
    val body = when (language) {
        "en" -> "Turn Private DNS Off in system settings so BlockX can filter websites. " +
            if (hostname.isNotBlank()) "Current server: $hostname" else ""
        "ar" -> "أوقف DNS الخاص من إعدادات النظام حتى يتمكن BlockX من تصفية المواقع. " +
            if (hostname.isNotBlank()) "الخادم الحالي: $hostname" else ""
        else -> "DNS تایبەت لە ڕێکخستنی سیستەم بکوژێنەوە تا BlockX وێبسایتەکان فلتەر بکات. " +
            if (hostname.isNotBlank()) "سێرڤەری ئێستا: $hostname" else ""
    }
    val openLabel = when (language) {
        "en" -> "Open DNS settings"
        "ar" -> "فتح إعدادات DNS"
        else -> "کردنەوەی ڕێکخستنی DNS"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = body.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                ) {
                    Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(openLabel)
                }
                TextButton(onClick = {
                    active = PrivateDnsGuard.isBypassActive(context)
                    hostname = PrivateDnsGuard.configuredHostname(context)
                }) {
                    Text(
                        when (language) {
                            "en" -> "Recheck"
                            "ar" -> "إعادة الفحص"
                            else -> "پشکنینەوە"
                        },
                    )
                }
            }
        }
    }
}
