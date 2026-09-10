package com.agon.app.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun tr(language: String, ku: String, ar: String, en: String) = when (language) {
    "ar" -> ar; "en" -> en; else -> ku
}

private fun messageFor(manifest: UpdateManifest, language: String): String {
    manifest.message[language]?.takeIf { it.isNotBlank() }?.let { return it }
    return tr(
        language,
        "وەشانێکی نوێی ئەپەکە بەردەستە. تکایە نوێی بکەرەوە بۆ بەردەوامبوون.",
        "يتوفر إصدار جديد من التطبيق. يرجى التحديث للمتابعة.",
        "A new version of the app is available. Please update to continue.",
    )
}

private fun openStore(context: android.content.Context, manifest: UpdateManifest) {
    val url = manifest.storeUrl.ifBlank { UpdateChecker.DEFAULT_STORE_URL }
    runCatching {
        // Prefer the Play Store app, fall back to a browser.
        val marketUri = Uri.parse("market://details?id=${context.packageName}")
        context.startActivity(
            Intent(Intent.ACTION_VIEW, marketUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/**
 * Full-screen, non-dismissible gate shown when a MANDATORY update is required. The user cannot
 * reach the app until they open the store. Back press is intentionally not handled here so the
 * caller keeps showing the gate.
 */
@Composable
fun MandatoryUpdateGate(manifest: UpdateManifest, language: String) {
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(Modifier.size(112.dp), CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        null,
                        Modifier.size(58.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                tr(language, "نوێکردنەوە پێویستە", "التحديث مطلوب", "Update required"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                messageFor(manifest, language),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (manifest.latestVersionName.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    tr(language, "وەشانی نوێ:", "الإصدار الجديد:", "New version:") +
                        " ${manifest.latestVersionName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(30.dp))
            Button(
                onClick = { openStore(context, manifest) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.SystemUpdate, null, Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    tr(language, "نوێکردنەوە لە گوگڵ پلەی", "التحديث من Google Play", "Update on Google Play"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

/**
 * Dismissible dialog for an OPTIONAL update. The user can update now or continue using the app.
 */
@Composable
fun OptionalUpdateDialog(manifest: UpdateManifest, language: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                tr(language, "نوێکردنەوەیەک بەردەستە", "يتوفر تحديث", "Update available"),
                fontWeight = FontWeight.Bold,
            )
        },
        text = { Text(messageFor(manifest, language)) },
        confirmButton = {
            Button(
                onClick = { openStore(context, manifest); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(tr(language, "نوێبکەرەوە", "تحديث", "Update"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr(language, "دواتر", "لاحقاً", "Later"))
            }
        },
    )
}
