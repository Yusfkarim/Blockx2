package com.agon.app.ui.routes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.agon.app.applock.rememberSensitiveActionGate
import com.agon.app.data.ShieldState
import com.agon.app.localization.appText
import com.agon.app.support.SupportPromptPolicy

private data class Dest(val title: String, val icon: ImageVector)

/**
 * Tab order: Home first (includes independent Safe Browsing VPN card), then Safe Browsing tab.
 * Standard/Strong protection never auto-starts VPN (see [ProtectionSettingsRoute]).
 */
private val destinations = listOf(
    Dest("سەرەکی", Icons.Default.Home),
    Dest("درع التصفح", Icons.Default.Shield),
    Dest("بلۆکلیست", Icons.Default.Block),
    Dest("کات", Icons.Default.HourglassTop),
    Dest("ڕیلز", Icons.Default.Movie),
    Dest("ڕێکخستن", Icons.Default.Settings),
)

/** Default start destination index: Home (الرئیسیة) in the corner. */
const val START_DESTINATION_HOME = 0

/** Safe Browsing Shield tab index (swapped with Home). */
const val DEST_SAFE_BROWSING = 1

/**
 * Bottom-nav shell. Corner tab is Home; Safe Browsing sits where Home used to be.
 */
@Composable
fun FamilyShieldScaffold(
    state: ShieldState,
    safeBrowsing: @Composable () -> Unit,
    dashboard: @Composable () -> Unit,
    blocklist: @Composable () -> Unit,
    timeLimits: @Composable () -> Unit,
    history: @Composable () -> Unit,
    settings: @Composable () -> Unit,
    supportOverlay: @Composable (visible: Boolean, onDismiss: () -> Unit) -> Unit,
) {
    var destination by rememberSaveable { mutableIntStateOf(START_DESTINATION_HOME) }
    val tabGate = rememberSensitiveActionGate(state.language)
    val context = LocalContext.current
    var showSupport by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (SupportPromptPolicy.shouldShow(context)) {
            showSupport = true
            SupportPromptPolicy.markShown(context)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            PremiumBottomBar(
                currentIndex = destination,
                onSelect = { idx ->
                    if (idx == START_DESTINATION_HOME) destination = idx
                    else tabGate.run { destination = idx }
                },
                language = state.language,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
                0 -> dashboard()
                1 -> safeBrowsing()
                2 -> blocklist()
                3 -> timeLimits()
                4 -> history()
                else -> settings()
            }
        }
    }
    tabGate.Host()
    supportOverlay(showSupport) { showSupport = false }
}

@Composable
private fun PremiumBottomBar(currentIndex: Int, onSelect: (Int) -> Unit, language: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
            ) {
                destinations.forEachIndexed { index, item ->
                    val selected = currentIndex == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onSelect(index) },
                        icon = { Icon(item.icon, item.title) },
                        label = {
                            Text(
                                appText(item.title, language),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}
