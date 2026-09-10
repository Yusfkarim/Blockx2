package com.agon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.services.CallOverlayGate
import com.agon.app.ui.theme.AgonAppTheme

/**
 * Calm, faith-centred block screen. The actual matched keyword travels here directly from the
 * block engine (EXTRA_DOMAIN carries the keyword for keyword rules), so what the user sees is
 * always the real detection result. All intent extras are kept unchanged for compatibility.
 */
class BlockedWebsiteActivity : ComponentActivity() {

    /** Listener that finishes this screen when a phone call starts (see CallOverlayGate). */
    private var suppressReceiver: android.content.BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val value = intent.getStringExtra(EXTRA_DOMAIN).orEmpty()
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()
        val language = getSharedPreferences("family_shield", MODE_PRIVATE).getString("language", "ku") ?: "ku"
        val isKeywordBlock = reason.startsWith("Blocked keyword:", ignoreCase = true)
        val advice = BlockedAdvice.next(this)
        // Call safety: hide for the duration of the call; CallOverlayGate keeps a restoration
        // snapshot so the same exact screen returns at 0ms once the phone idles (IDLE). The
        // restore side re-verifies that a browser is still in the foreground (the subject of a
        // website block), otherwise the stale snapshot is dropped.
        suppressReceiver = CallOverlayGate.attach(this) {
            CallOverlayGate.SuppressedOverlay(
                kindApp = false,
                packageName = "",
                domain = value,
                reason = reason,
                category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty(),
                confidence = intent.getIntExtra(EXTRA_CONFIDENCE, 100),
                time = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis()),
            )
        }
        setContent {
            AgonAppTheme(darkTheme = true) {
                BlockedPage(
                    value = value,
                    isKeywordBlock = isKeywordBlock,
                    advice = advice,
                    language = language,
                    onBack = ::returnToBrowser,
                )
            }
        }
    }

    private fun returnToBrowser() {
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        CallOverlayGate.detach(this, suppressReceiver)
        suppressReceiver = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_REASON = "reason"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_CONFIDENCE = "confidence"
        const val EXTRA_TIME = "time"
    }
}

// ---------------------------------------------------------------- fixed palette
private val NightTop = Color(0xFF04120C)
private val NightMid = Color(0xFF0A2018)
private val NightBottom = Color(0xFF0E2A20)
private val WordCardFill = Color(0xFF14231C)
private val AdviceCardFill = Color(0xFF10241B)
private val CardStroke = Color(0x338DDBB2)
private val SoftText = Color(0xFFECF5EF)
private val MutedText = Color(0xFF9CBBAD)
private val AccentGreen = Color(0xFF8DDBB2)
private val AccentGold = Color(0xFFE3C778)
private val WordRed = Color(0xFFFFB4A6)

private fun titleFor(isKeyword: Boolean, language: String): String = if (isKeyword) {
    when (language) { "ar" -> "تم حظر هذه الكلمة"; "en" -> "This word was blocked"; else -> "ئەم وشەیە بلۆک کرا" }
} else {
    when (language) { "ar" -> "تم حظر هذا المحتوى"; "en" -> "This content was blocked"; else -> "ئەم ناوەڕۆکە بلۆک کرا" }
}

private fun subtitleFor(language: String): String =
    when (language) {
        "ar" -> "حماية درع لاٲبرح"
        "en" -> "Protected by BlockX LaAbrah"
        else -> "پاراستنی درع لاأبرح"
    }

private fun valueLabelFor(isKeyword: Boolean, language: String): String = if (isKeyword) {
    when (language) { "ar" -> "الكلمة المحظورة"; "en" -> "Blocked word"; else -> "وشەی بلۆککراو" }
} else {
    when (language) { "ar" -> "المحتوى المحظور"; "en" -> "Blocked content"; else -> "ناوەڕۆکی بلۆککراو" }
}

private fun adviceLabelFor(language: String): String =
    when (language) { "ar" -> "توقف وتأمل"; "en" -> "Pause and reflect"; else -> "بوەستە و بیربکەرەوە" }

private fun backLabelFor(language: String): String =
    when (language) { "ar" -> "رجوع"; "en" -> "Back"; else -> "گەڕانەوە" }

private fun unspecifiedFor(language: String): String =
    when (language) { "ar" -> "غير محدد"; "en" -> "Unspecified"; else -> "دیارینەکراو" }

@Composable
private fun BlockedPage(
    value: String,
    isKeywordBlock: Boolean,
    advice: Advice,
    language: String,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val direction = if (language == "en") LayoutDirection.Ltr else LayoutDirection.Rtl
    val adviceAlign = if (language == "en") TextAlign.Start else TextAlign.Right
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(NightTop, NightMid, NightBottom))),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Scrollable region: keeps every part of a long advice fully reachable
                // on any screen size without cutting content.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AnimatedVisibility(
                        visible = shown,
                        enter = fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 8 },
                    ) {
                        Column(
                            modifier = Modifier.widthIn(max = 560.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.height(28.dp))

                            // Header shield badge
                            Surface(
                                modifier = Modifier.size(96.dp),
                                shape = CircleShape,
                                color = AccentGreen.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.35f)),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.Shield,
                                        contentDescription = null,
                                        modifier = Modifier.size(46.dp),
                                        tint = AccentGreen,
                                    )
                                }
                            }

                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = titleFor(isKeywordBlock, language),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = SoftText,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = subtitleFor(language),
                                style = MaterialTheme.typography.labelLarge,
                                color = AccentGold.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center,
                            )

                            Spacer(Modifier.height(26.dp))

                            // ---- Card 1: the actually detected blocked word ----
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(26.dp),
                                colors = CardDefaults.cardColors(containerColor = WordCardFill),
                                border = BorderStroke(1.dp, CardStroke),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp, horizontal = 22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Outlined.Block,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = WordRed.copy(alpha = 0.9f),
                                        )
                                        Spacer(Modifier.size(8.dp))
                                        Text(
                                            text = valueLabelFor(isKeywordBlock, language),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MutedText,
                                        )
                                    }
                                    Spacer(Modifier.height(14.dp))
                                    Text(
                                        text = "\u201C" + value.ifBlank { unspecifiedFor(language) } + "\u201D",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 26.sp,
                                        lineHeight = 38.sp,
                                        color = WordRed,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }

                            Spacer(Modifier.height(18.dp))

                            // ---- Card 2: a single admonition, fully scrollable via the outer scroll ----
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(26.dp),
                                colors = CardDefaults.cardColors(containerColor = AdviceCardFill),
                                border = BorderStroke(1.dp, CardStroke.copy(alpha = 0.55f)),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp, horizontal = 22.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(40.dp),
                                            shape = CircleShape,
                                            color = AccentGold.copy(alpha = 0.14f),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Outlined.VolunteerActivism,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(22.dp),
                                                    tint = AccentGold,
                                                )
                                            }
                                        }
                                        Spacer(Modifier.size(12.dp))
                                        Text(
                                            text = adviceLabelFor(language),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = SoftText,
                                        )
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    HorizontalDivider(color = CardStroke.copy(alpha = 0.4f))
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = advice.text(language),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontSize = 18.sp,
                                        lineHeight = 32.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SoftText,
                                        textAlign = adviceAlign,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }

                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }

                // Pinned action button
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        contentColor = Color(0xFF04120C),
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(backLabelFor(language), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
