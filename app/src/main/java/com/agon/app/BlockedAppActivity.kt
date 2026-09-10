package com.agon.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.BroadcastReceiver
import com.agon.app.localization.tr
import com.agon.app.services.CallOverlayGate
import com.agon.app.timelimit.BlockScheduleStore
import com.agon.app.ui.theme.AgonAppTheme
import kotlinx.coroutines.delay

class BlockedAppActivity : ComponentActivity() {

    /** Listener that finishes this screen when a phone call starts (see CallOverlayGate). */
    private var suppressReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Idempotent: needed so the live countdown can read the real, persisted block deadline
        // even if this activity is the first component to touch the store in this process.
        BlockScheduleStore.initialize(this)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val timeLimit = intent.getBooleanExtra(EXTRA_TIME_LIMIT, false)
        val scheduleName = intent.getStringExtra(EXTRA_SCHEDULE_NAME)
        val quarantine = intent.getBooleanExtra(EXTRA_QUARANTINE, false)
        val appName = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString() } catch (_: Exception) { packageName }
        val prefs = getSharedPreferences("family_shield", MODE_PRIVATE)
        // Call safety: a ringing/started call hides this screen instantly (the call UI must own
        // the display); CallOverlayGate keeps a restoration snapshot so IDLE restores it at 0ms.
        suppressReceiver = CallOverlayGate.attach(this) {
            CallOverlayGate.SuppressedOverlay(
                kindApp = true,
                packageName = packageName,
                timeLimit = timeLimit,
                scheduleName = scheduleName,
                quarantine = quarantine,
            )
        }
        setContent { AgonAppTheme { BlockedAppScreen(appName, prefs.getString("language", "ku") ?: "ku", timeLimit, scheduleName, quarantine) { goHome() } } }
    }

    override fun onDestroy() {
        CallOverlayGate.detach(this, suppressReceiver)
        suppressReceiver = null
        super.onDestroy()
    }
    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }
    companion object {
        const val EXTRA_PACKAGE = "blocked_package"
        const val EXTRA_TIME_LIMIT = "blocked_time_limit"
        const val EXTRA_SCHEDULE_NAME = "blocked_schedule_name"
        const val EXTRA_QUARANTINE = "blocked_quarantine"
    }
}

/** Live countdown as HH:MM:SS, e.g. 00:02:37. */
private fun formatBlockCountdown(millis: Long): String {
    val totalSeconds = ((millis + 999) / 1000).coerceAtLeast(0L)
    return "%02d:%02d:%02d".format(totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
}

@Composable
private fun BlockedAppScreen(appName: String, language: String, timeLimit: Boolean, scheduleName: String?, quarantine: Boolean, onHome: () -> Unit) {
    BackHandler(onBack = onHome)
    val title = when {
        quarantine -> tr("block_quarantine_title", language)
        scheduleName != null -> tr("block_schedule_title", language)
        timeLimit && language == "en" -> "Daily time is up"
        timeLimit && language == "ar" -> "انتهى الوقت اليومي"
        timeLimit -> "کاتی ڕۆژانە تەواو بوو"
        language == "en" -> "This app is blocked"
        language == "ar" -> "هذا التطبيق محظور"
        else -> "ئەم ئەپە بلۆککراوە"
    }
    val message = when {
        quarantine -> tr("block_quarantine_message", language).format(appName)
        scheduleName != null -> tr("block_schedule_message", language).format(appName, scheduleName)
        timeLimit && language == "en" -> "You've reached today's time limit for $appName. It will be available again tomorrow, or can be extended with the protection credential."
        timeLimit && language == "ar" -> "لقد وصلت إلى الحد الزمني اليومي لتطبيق $appName. سيتوفر مجدداً غداً، أو يمكن تمديده باستخدام بيانات الحماية."
        timeLimit -> "گەیشتیتە سنووری کاتی ئەمڕۆ بۆ $appName. سبەینێ دووبارە بەردەست دەبێت، یان بە زانیارییەکانی پاراستن کاتەکە زیاد دەکرێت."
        language == "en" -> "$appName was blocked by BlockX LaAbrah. Protection credentials are required to change this setting."
        language == "ar" -> "تم حظر $appName بواسطة BlockX LaAbrah. يلزم إدخال بيانات الحماية لتغيير هذا الإعداد."
        else -> "$appName لەلایەن BlockX LaAbrah ـەوە بلۆککراوە. بۆ گۆڕینی ئەم ڕێکخستنە زانیارییەکانی پاراستن پێویستە."
    }
    val button = when (language) { "en" -> "Go to Home"; "ar" -> "العودة للرئيسية"; else -> "گەڕانەوە بۆ سەرەکی" }

    // The countdown + admonition only make sense for a timed scheduled / urge block, which is
    // exactly the case that carries a schedule name. Everything else keeps the original design.
    val showCountdown = scheduleName != null

    // Live 1-second ticker. State updates recompose only the countdown text; no screen refresh.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(showCountdown) {
        if (showCountdown) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }

    // Real, persisted deadline of the active window (survives app close / background). The urge
    // block reports its own end via BlockScheduleStore.activeEndMillis.
    val activeSchedule = if (showCountdown) BlockScheduleStore.activeScheduleNow() else null
    val windowEndMillis = remember(activeSchedule?.id) {
        if (activeSchedule != null) BlockScheduleStore.activeEndMillis(activeSchedule, System.currentTimeMillis()) else 0L
    }
    val remaining = (windowEndMillis - nowMillis).coerceAtLeast(0L)
    // The block ends automatically the moment the window elapses (engine-driven); reflect it here.
    val blockEnded = showCountdown && (activeSchedule == null || remaining == 0L)

    // One admonition, chosen once and kept stable for this screen (never mixed).
    val context = LocalContext.current
    val advice = remember { if (showCountdown) BlockedAdvice.next(context) else null }

    val direction = if (language == "en") LayoutDirection.Ltr else LayoutDirection.Rtl

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Surface(Modifier.fillMaxSize().systemBarsPadding(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val podColor = when {
                    quarantine -> MaterialTheme.colorScheme.errorContainer
                    scheduleName != null || timeLimit -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                }
                val podIcon = when {
                    quarantine -> Icons.Default.NewReleases
                    scheduleName != null -> Icons.Default.Bedtime
                    timeLimit -> Icons.Default.HourglassTop
                    else -> Icons.Default.Shield
                }
                val podTint = when {
                    quarantine -> MaterialTheme.colorScheme.onErrorContainer
                    scheduleName != null || timeLimit -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                }
                Surface(
                    Modifier.size(112.dp),
                    CircleShape,
                    color = podColor,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            podIcon,
                            null,
                            Modifier.size(58.dp),
                            tint = podTint,
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)

                // ---- Countdown + advice (top of the card region) ----
                if (showCountdown) {
                    Spacer(Modifier.height(18.dp))
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (blockEnded) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    Modifier.size(26.dp),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    tr("block_ended", language),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    textAlign = TextAlign.Center,
                                )
                            } else {
                                Text(
                                    tr("schedule_time_left", language),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    formatBlockCountdown(remaining),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }

                            if (advice != null) {
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.height(16.dp))
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.TipsAndUpdates,
                                        null,
                                        Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        tr("schedule_advice_title", language),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    advice.text(language),
                                    style = MaterialTheme.typography.bodyLarge,
                                    lineHeight = 26.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Text(message, modifier = Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(30.dp))
                Button(onClick = onHome, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(button, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
