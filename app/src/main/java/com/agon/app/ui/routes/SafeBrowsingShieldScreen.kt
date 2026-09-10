package com.agon.app.ui.routes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.blocklist.domain.BuiltInAdultDomains
import com.agon.app.data.ShieldState
import com.agon.app.ui.components.SafeBrowsingVpnCard

/**
 * Dedicated Safe Browsing tab (درع التصفح الآمن):
 * independent VPN toggle + lock timer, block analytics, educational guide.
 * Not shown on the Home tab — only via bottom navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeBrowsingShieldScreen(state: ShieldState) {
    val lang = state.language
    val domainIndexSize = remember {
        val n = BuiltInAdultDomains.size
        if (n <= 0) 2_000_000 else n
    }
    val totalBlocked = state.blockedDomains
    val totalFiltered = state.filteredRequests

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when (lang) {
                                "en" -> "Safe Browsing Shield"
                                "ar" -> "درع التصفح الآمن"
                                else -> "درع التصفح الآمن"
                            },
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            when (lang) {
                                "en" -> "Independent DNS filter for the whole device"
                                "ar" -> "تصفية DNS مستقلة على مستوى الجهاز"
                                else -> "فلتەری DNS سەربەخۆ لەسەر ئاستی ئامێر"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }

        // 1) Independent enable + commitment lock
        item { SafeBrowsingVpnCard(state = state) }

        // 2) Analytics / counters
        item {
            Text(
                when (lang) {
                    "en" -> "Filter statistics"
                    "ar" -> "إحصائيات التصفية"
                    else -> "ئاماری فلتەر"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatGlassCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Block,
                    value = formatCompact(totalBlocked),
                    label = when (lang) {
                        "en" -> "Blocked requests"
                        "ar" -> "الطلبات المحظورة"
                        else -> "داواکاری بلۆککراو"
                    },
                    accent = MaterialTheme.colorScheme.error,
                )
                StatGlassCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Dns,
                    value = formatCompact(totalFiltered),
                    label = when (lang) {
                        "en" -> "DNS lookups"
                        "ar" -> "استعلامات DNS"
                        else -> "پرسیاری DNS"
                    },
                    accent = MaterialTheme.colorScheme.primary,
                )
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (lang) {
                                "en" -> "Active filtered domains"
                                "ar" -> "نطاقات DNS الفعّالة في القاعدة"
                                else -> "دۆمەینی فلتەرکراوی چالاک"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            formatDomainCount(domainIndexSize, lang),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // 3) Educational glassmorphism guide
        item {
            EducationalGuideCard(language = lang)
        }
    }
}

@Composable
private fun StatGlassCard(
    modifier: Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    accent: Color,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EducationalGuideCard(language: String) {
    val brush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        ),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(brush, RoundedCornerShape(24.dp))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                ) {
                    Icon(
                        Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp).size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    when (language) {
                        "en" -> "How does Safe Browsing protect you?"
                        "ar" -> "كيف يحميك درع التصفح؟"
                        else -> "چۆن درع التصفح دەتپارێزێت؟"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when (language) {
                    "en" ->
                        "The shield filters DNS lookups for the whole device to block adult sites, malware, " +
                            "and malicious tracking domains — without slowing your connection or draining the battery."
                    "ar" ->
                        "يقوم الدرع بتصفية اتصالات DNS على مستوى الجهاز كلياً لمنع المواقع الإباحية والفيروسات، " +
                            "ونطاقات التتبع الخبيثة دون التأثير على سرعة الإنترنت أو استهلاك البطارية."
                    else ->
                        "درع لەسەر ئاستی ئامێر داواکارییەکانی DNS فلتەر دەکات بۆ ڕێگری لە ماڵپەڕی نەخوازراو، " +
                            "ڤایرۆس و دۆمەینی شوێنکەوتن — بەبێ خاوبوونەوەی ئینتەرنێت یان زۆر بەکارهێنانی باتری."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
            )
        }
    }
}

private fun formatCompact(value: Long): String = when {
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format("%.1fK", value / 1_000.0)
    else -> value.toString()
}

private fun formatDomainCount(count: Int, language: String): String {
    val compact = when {
        count >= 1_000_000 -> String.format("~%,d+", count).replace(',', ',')
        count >= 1_000 -> "%,d+".format(count)
        else -> count.toString()
    }
    // Prefer friendly ~2M+ when bundled index missing/empty placeholder path used.
    val display = if (count >= 1_000_000) {
        when (language) {
            "en" -> "~${count / 1_000_000}M+ domains"
            "ar" -> "~${count / 1_000_000} مليون+ نطاق"
            else -> "~${count / 1_000_000}M+ دۆمەین"
        }
    } else {
        when (language) {
            "en" -> "$compact domains"
            "ar" -> "$compact نطاق"
            else -> "$compact دۆمەین"
        }
    }
    return display
}
