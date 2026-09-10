package com.agon.app.blocklist.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PlaylistRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.material3.FilterChip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.blocklist.data.TimedBlockingStore
import com.agon.app.blocklist.domain.BlockRule
import com.agon.app.blocklist.domain.BlocklistUiState
import com.agon.app.blocklist.domain.InstalledApp
import com.agon.app.blocklist.domain.ListMode
import com.agon.app.blocklist.domain.RuleSort
import com.agon.app.blocklist.domain.RuleType
import com.agon.app.blocklist.domain.TimedBlockingIds
import com.agon.app.blocklist.domain.formatTimedRemaining
import com.agon.app.blocklist.viewmodel.BlocklistViewModel
import com.agon.app.applock.rememberSensitiveActionGate
import com.agon.app.localization.LocalAppLanguage
import com.agon.app.localization.appText
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import com.agon.app.localization.tr
import kotlinx.coroutines.launch
import java.net.IDN

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocklistRoute(viewModel: BlocklistViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = LocalAppLanguage.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showTimedLock by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BlockRule?>(null) }
    val protectionGate = rememberSensitiveActionGate(language)

    // Holds (label, remainingMillis) of a delete attempt that is still inside its block period, so
    // a dialog can explain that the item cannot be removed until its time is up.
    var lockedDeleteInfo by remember { mutableStateOf<Pair<String, Long>?>(null) }

    // Whitelist UI is removed; the blocklist screen is always BLOCK mode.
    LaunchedEffect(Unit) { viewModel.setMode(ListMode.BLOCK) }

    fun deleteNow(items: List<BlockRule>) {
        if (items.isEmpty()) return
        viewModel.delete(items) { deleted ->
            scope.launch {
                val message = if (deleted.size == 1) {
                    appText("Item deleted", language)
                } else {
                    appText("%d items deleted", language).replace("%d", deleted.size.toString())
                }
                val result = snackbar.showSnackbar(message, appText("Undo", language), withDismissAction = true)
                if (result == SnackbarResult.ActionPerformed) viewModel.restore(deleted)
            }
        }
    }

    fun deleteWithUndo(items: List<BlockRule>) {
        if (items.isEmpty()) return
        // A rule with an active block duration cannot be removed until its time is fully up. If any
        // selected item is still within its period, refuse the delete and show the longest
        // remaining time. Rules with no duration (blockUntil == 0) are never affected.
        val stillLocked = items.filter {
            TimedBlockingStore.isLocked(context, TimedBlockingIds.blocklist(it.id))
        }
        if (stillLocked.isNotEmpty()) {
            val longest = stillLocked.maxByOrNull {
                TimedBlockingStore.remainingMillis(context, TimedBlockingIds.blocklist(it.id))
            }!!
            val remaining = TimedBlockingStore.remainingMillis(context, TimedBlockingIds.blocklist(longest.id))
            val label = if (items.size == 1) longest.label else stillLocked.size.toString()
            lockedDeleteInfo = label to remaining
            return
        }
        deleteNow(items)
    }

    fun requestEdit(rule: BlockRule) {
        if (rule.type != RuleType.KEYWORD) return
        protectionGate.requireCredential { editing = rule }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (state.selectedIds.isNotEmpty()) {
                TopAppBar(
                    title = { Text(appText("%d selected", language).replace("%d", state.selectedIds.size.toString())) },
                    navigationIcon = { IconButton(onClick = viewModel::clearSelection) { Icon(Icons.Default.Close, appText("Clear selection", language)) } },
                    actions = { IconButton(onClick = { deleteWithUndo(state.rules.filter { it.id in state.selectedIds }) }) { Icon(Icons.Default.Delete, appText("Delete selected", language)) } },
                )
            } else TopAppBar(
                title = { Column { Text(appText("Blocklist", language), fontWeight = FontWeight.Bold); Text(appText("Control what is allowed", language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, appText("Add sites", language)) } },
    ) { padding ->
        BlocklistContent(state, Modifier.padding(padding), viewModel::setType, viewModel::setQuery, viewModel::setSort, viewModel::toggleSelection, ::requestEdit, ::deleteWithUndo, { showAdd = true }, { showTimedLock = true })
    }
    if (showAdd) AddRulesSheet(ListMode.BLOCK, state.type, viewModel::installedApps, { values, type, days -> viewModel.add(values, type, days) }) { showAdd = false }
    if (showTimedLock) {
        TimedAppLockDialog(
            language = language,
            appsLoader = viewModel::installedApps,
            onLock = { pkg, label, durationMs -> viewModel.addTimedAppLock(pkg, label, durationMs) },
            onDismiss = { showTimedLock = false },
        )
    }
    editing?.let { rule ->
        EditKeywordDialog(
            rule,
            onDismiss = { editing = null },
            onSave = { value ->
                viewModel.update(rule, value)
                editing = null
            },
        )
    }
    lockedDeleteInfo?.let { (label, remaining) ->
        val lang = language
        AlertDialog(
            onDismissRequest = { lockedDeleteInfo = null },
            icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
            title = {
                Text(
                    when (lang) {
                        "en" -> "Cannot remove yet"
                        "ar" -> "\u0644\u0627 \u064a\u0645\u0643\u0646 \u0627\u0644\u062d\u0630\u0641 \u0627\u0644\u0622\u0646"
                        else -> "\u0646\u0627\u062a\u0648\u0627\u0646\u0631\u06ce\u062a \u0628\u0633\u0631\u062f\u0631\u06ce\u062a\u06d5\u0648\u06d5"
                    },
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    Text(
                        when (lang) {
                            "en" -> "This block stays active until its chosen time is over. You cannot remove it before then."
                            "ar" -> "\u064a\u0628\u0642\u0649 \u0647\u0630\u0627 \u0627\u0644\u062d\u0638\u0631 \u0641\u0639\u0627\u0644\u0627\u064b \u062d\u062a\u0649 \u0627\u0646\u062a\u0647\u0627\u0621 \u0627\u0644\u0645\u062f\u0629 \u0627\u0644\u0645\u062d\u062f\u062f\u0629\u060c \u0648\u0644\u0627 \u064a\u0645\u0643\u0646\u0643 \u062d\u0630\u0641\u0647 \u0642\u0628\u0644 \u0630\u0644\u0643."
                            else -> "\u0626\u06d5\u0645 \u0628\u0644\u06c6\u06a9\u06d5 \u0686\u0627\u0644\u0627\u06a9 \u062f\u06d5\u0645\u06ce\u0646\u06ce\u062a \u0647\u06d5\u062a\u0627 \u06a9\u0627\u062a\u06d5 \u062f\u06cc\u0627\u0631\u06cc\u06a9\u0631\u0627\u0648\u06d5\u06a9\u06d5\u06cc \u062a\u06d5\u0648\u0627\u0648 \u0628\u06ce\u062a\u061b \u067e\u06ce\u0634 \u0626\u06d5\u0648\u06d5 \u0646\u0627\u062a\u0648\u0627\u0646\u06cc \u0628\u06cc\u0633\u0695\u06ce\u062a\u06d5\u0648\u06d5."
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(
                            formatTimedRemaining(remaining, lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { lockedDeleteInfo = null }) { Text(appText("Done", lang)) }
            },
        )
    }
    protectionGate.Host()
}

/**
 * Remaining 48h delete cool-down to the second, e.g. "1d 05:30:12" or "05:30:12".
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlocklistContent(
    state: BlocklistUiState,
    modifier: Modifier,
    onType: (RuleType?) -> Unit,
    onQuery: (String) -> Unit,
    onSort: (RuleSort) -> Unit,
    onSelect: (Long) -> Unit,
    onEdit: (BlockRule) -> Unit,
    onDelete: (List<BlockRule>) -> Unit,
    onAdd: () -> Unit,
    onTimedAppLock: () -> Unit,
) {
    var sortExpanded by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val filters = listOf(null to "All", RuleType.WEBSITE to "Websites", RuleType.APP to "Apps", RuleType.KEYWORD to "Keywords")
                items(filters) { (type, label) ->
                    AssistChip(onClick = { onType(type) }, label = { Text(appText(label, LocalAppLanguage.current)) }, leadingIcon = if (state.type == type) {{ Icon(Icons.Default.Check, null, Modifier.size(17.dp)) }} else null, colors = AssistChipDefaults.assistChipColors(containerColor = if (state.type == type) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = state.query, onValueChange = onQuery, modifier = Modifier.weight(1f), placeholder = { Text(appText("Search blocklist", LocalAppLanguage.current)) }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { AnimatedVisibility(state.query.isNotBlank()) { IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, appText("Clear search", LocalAppLanguage.current)) } } }, singleLine = true, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
                Spacer(Modifier.width(8.dp))
                Box {
                    IconButton(onClick = { sortExpanded = true }) { Icon(Icons.Default.Sort, appText("Sort", LocalAppLanguage.current)) }
                    DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                        listOf(RuleSort.RECENT to "Recent", RuleSort.OLDEST to "Oldest", RuleSort.AZ to "A–Z", RuleSort.ZA to "Z–A").forEach { (sort, title) -> DropdownMenuItem(text = { Text(appText(title, LocalAppLanguage.current)) }, leadingIcon = { if (state.sort == sort) Icon(Icons.Default.Check, null) }, onClick = { onSort(sort); sortExpanded = false }) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (state.mode == ListMode.BLOCK) {
            TimedAppLockBanner(language = LocalAppLanguage.current, onClick = onTimedAppLock)
            Spacer(Modifier.height(8.dp))
        }
        AnimatedContent(targetState = state.loading to state.rules.isEmpty(), label = "list state", modifier = Modifier.fillMaxSize()) { (loading, empty) ->
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                empty -> EmptyState(state, onAdd)
                else -> LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.rules, key = { it.id }) { rule -> RuleRow(rule, rule.id in state.selectedIds, state.selectedIds.isNotEmpty(), onSelect, onEdit, onDelete) }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(state: BlocklistUiState, onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(Modifier.size(88.dp), CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.PlaylistRemove, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary) } }
        Spacer(Modifier.height(18.dp))
        Text(appText(if (state.query.isBlank()) "No items yet" else "No matching items", LocalAppLanguage.current), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(appText(if (state.query.isBlank()) "Add items to your blocklist." else "Try another search or filter.", LocalAppLanguage.current), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.query.isBlank()) { Spacer(Modifier.height(20.dp)); Button(onClick = onAdd) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(appText("Add Sites", LocalAppLanguage.current)) } }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RuleRow(rule: BlockRule, selected: Boolean, selectionMode: Boolean, onSelect: (Long) -> Unit, onEdit: (BlockRule) -> Unit, onDelete: (List<BlockRule>) -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.Settled) return@rememberSwipeToDismissBoxState false
            onDelete(listOf(rule))
            // Keep the row visible until the protection credential is verified and the
            // repository actually removes the item (keywords, websites and apps).
            false
        },
    )
    SwipeToDismissBox(state = dismissState, backgroundContent = {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 22.dp), contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd) { Icon(Icons.Outlined.DeleteOutline, appText("Delete", LocalAppLanguage.current), tint = MaterialTheme.colorScheme.error) }
    }) {
        Card(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { if (selectionMode) onSelect(rule.id) else onEdit(rule) }, onLongClick = { onSelect(rule.id) }).animateContentSize(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(44.dp), RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(ruleIcon(rule.type), null, tint = MaterialTheme.colorScheme.primary) } }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(rule.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(ruleSubtitle(rule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val rowContext = LocalContext.current
                    val lockId = TimedBlockingIds.blocklist(rule.id)
                    var remainingMs by remember(rule.id) {
                        mutableStateOf(TimedBlockingStore.remainingMillis(rowContext, lockId))
                    }
                    LaunchedEffect(rule.id) {
                        while (true) {
                            remainingMs = TimedBlockingStore.remainingMillis(rowContext, lockId)
                            kotlinx.coroutines.delay(1000L)
                        }
                    }
                    if (remainingMs > 0L || TimedBlockingStore.isLocked(rowContext, lockId)) {
                        Text(
                            text = formatTimedRemaining(remainingMs, LocalAppLanguage.current),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (selected) Icon(Icons.Default.Check, appText("Selected", LocalAppLanguage.current), tint = MaterialTheme.colorScheme.primary)
                else if (rule.type == RuleType.KEYWORD) IconButton(onClick = { onEdit(rule) }) { Icon(Icons.Default.Edit, appText("Edit keyword", LocalAppLanguage.current)) }
                else IconButton(onClick = { onDelete(listOf(rule)) }) { Icon(Icons.Default.MoreVert, appText("Options", LocalAppLanguage.current)) }
            }
        }
    }
}

private fun ruleIcon(type: RuleType): ImageVector = when (type) { RuleType.WEBSITE -> Icons.Default.Language; RuleType.APP -> Icons.Default.Android; RuleType.KEYWORD -> Icons.Default.TextFields }
@Composable
private fun ruleSubtitle(rule: BlockRule): String = when (rule.type) {
    RuleType.WEBSITE -> appText("Website", LocalAppLanguage.current)
    RuleType.APP -> rule.value
    RuleType.KEYWORD -> appText("Keyword", LocalAppLanguage.current)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRulesSheet(initialMode: ListMode, initialType: RuleType?, loadApps: suspend () -> List<InstalledApp>, onAdd: (List<Pair<String, String>>, RuleType, Int) -> Unit, onDismiss: () -> Unit) {
    val language = LocalAppLanguage.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by rememberSaveable { mutableStateOf(initialType ?: RuleType.WEBSITE) }
    // Chosen block duration in days for BLOCK rules (0 = not yet chosen). Required before saving.
    var blockDays by rememberSaveable { mutableStateOf(0) }
    var input by rememberSaveable { mutableStateOf("") }
    var appQuery by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loadingApps by remember { mutableStateOf(true) }
    val selectedApps = remember { mutableStateListOf<InstalledApp>() }
    LaunchedEffect(Unit) { apps = loadApps(); loadingApps = false }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(appText("Add to Blocklist", language), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                RuleType.entries.forEachIndexed { index, item -> SegmentedButton(selected = type == item, onClick = { type = item; error = null }, shape = SegmentedButtonDefaults.itemShape(index, 3), icon = { Icon(ruleIcon(item), null, Modifier.size(17.dp)) }) { Text(appText(when (item) { RuleType.WEBSITE -> "Sites"; RuleType.APP -> "Apps"; RuleType.KEYWORD -> "Words" }, language)) } }
            }
            Spacer(Modifier.height(18.dp))

            // The scrollable middle region. For Sites/Keywords it holds a text field + the duration
            // picker and scrolls as one; for Apps the app list lives in its own bounded, scrollable
            // box so the sheet itself never becomes a long scroll.
            val middleScroll = rememberScrollState()
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(middleScroll),
            ) {
                when (type) {
                    RuleType.WEBSITE -> {
                        Text(appText("Enter one or more domains", language), fontWeight = FontWeight.SemiBold)
                        Text(appText("Separate domains with a new line, comma, or space.", language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = input, onValueChange = { input = it; error = null }, modifier = Modifier.fillMaxWidth().height(140.dp), placeholder = { Text("example.com\nfamily-safe.org") }, isError = error != null, supportingText = { error?.let { Text(it) } }, shape = RoundedCornerShape(16.dp))
                    }
                    RuleType.KEYWORD -> {
                        Text(appText("Add keywords", language), fontWeight = FontWeight.SemiBold)
                        Text(appText("Separate multiple keywords with a new line or comma.", language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = input, onValueChange = { input = it; error = null }, modifier = Modifier.fillMaxWidth().height(130.dp), placeholder = { Text(appText("Enter keywords", language)) }, isError = error != null, supportingText = { error?.let { Text(it) } }, shape = RoundedCornerShape(16.dp))
                    }
                    RuleType.APP -> {
                        OutlinedTextField(value = appQuery, onValueChange = { appQuery = it }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text(appText("Search installed apps", language)) }, singleLine = true, shape = RoundedCornerShape(16.dp))
                        Spacer(Modifier.height(10.dp))
                        // Bounded box: the installed-app list scrolls INSIDE this card only, so the
                        // sheet stays compact like the Sites/Keywords tabs.
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        ) {
                            if (loadingApps) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            } else {
                                val filtered = apps.filter { appQuery.isBlank() || it.name.contains(appQuery, true) || it.packageName.contains(appQuery, true) }
                                if (filtered.isEmpty()) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(appText("No matching items", language), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    LazyColumn(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        items(filtered, key = { it.packageName }) { app ->
                                            val selected = selectedApps.any { it.packageName == app.packageName }
                                            ListItem(headlineContent = { Text(app.name, maxLines = 1) }, supportingContent = { Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingContent = { DrawableIcon(app.icon, Modifier.size(42.dp)) }, trailingContent = { if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { if (selected) selectedApps.removeAll { it.packageName == app.packageName } else selectedApps.add(app) }, colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                // Duration picker: choose how many days the block lasts (min 3 days) before saving.
                BlockDurationPicker(language = language, selectedDays = blockDays, onSelect = { blockDays = it })
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                if (blockDays <= 0) { error = appText("Choose a block duration first", language); return@Button }
                when (type) {
                    RuleType.WEBSITE -> {
                        val values = input.split(Regex("[\\s,;]+" )).filter { it.isNotBlank() }
                        val invalid = values.filterNot(::isValidDomain)
                        if (values.isEmpty()) error = appText("Enter at least one domain", language) else if (invalid.isNotEmpty()) error = appText("Invalid domain: %s", language).replace("%s", invalid.first()) else { onAdd(values.map { normalizeDomain(it) to normalizeDomain(it) }, type, blockDays); onDismiss() }
                    }
                    RuleType.KEYWORD -> { val values = input.split(Regex("[\\n,;]+" )).map { it.trim() }.filter { it.isNotBlank() }; if (values.isEmpty()) error = appText("Enter at least one keyword", language) else { onAdd(values.map { it to it }, type, blockDays); onDismiss() } }
                    RuleType.APP -> if (selectedApps.isNotEmpty()) { onAdd(selectedApps.map { it.packageName to it.name }, type, blockDays); onDismiss() }
                }
            }, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = (type != RuleType.APP || selectedApps.isNotEmpty()) && blockDays > 0) {
                val label = if (type == RuleType.APP && selectedApps.isNotEmpty()) {
                    appText("Add %d apps", language).replace("%d", selectedApps.size.toString())
                } else {
                    appText(when (type) { RuleType.WEBSITE -> "Add Sites"; RuleType.APP -> "Add Apps"; RuleType.KEYWORD -> "Add Keywords" }, language)
                }
                Text(label)
            }
        }
    }
}

@Composable
private fun DrawableIcon(
    drawable: android.graphics.drawable.Drawable,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            android.widget.ImageView(context).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        },
        update = { imageView ->
            if (imageView.drawable !== drawable) imageView.setImageDrawable(drawable)
        },
    )
}

@Composable
private fun EditKeywordDialog(rule: BlockRule, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable(rule.id) { mutableStateOf(rule.value) }
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Default.Edit, null) }, title = { Text(appText("Edit keyword", LocalAppLanguage.current)) }, text = { OutlinedTextField(value, { value = it }, label = { Text(appText("Keyword", LocalAppLanguage.current)) }, singleLine = true) }, dismissButton = { TextButton(onClick = onDismiss) { Text(appText("Cancel", LocalAppLanguage.current)) } }, confirmButton = { TextButton(onClick = { onSave(value.trim()) }, enabled = value.isNotBlank()) { Text(appText("Save", LocalAppLanguage.current)) } })
}

private fun normalizeDomain(raw: String): String = raw.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/').trimEnd('.')
private fun isValidDomain(raw: String): Boolean {
    val normalized = normalizeDomain(raw)
    val domain = normalized.removePrefix("*.")
    val ipv4 = domain.split('.').takeIf { it.size == 4 }?.all { it.toIntOrNull() in 0..255 } == true
    if (ipv4) return !normalized.startsWith("*.")
    return try {
        val ascii = IDN.toASCII(domain)
        ascii.length in 4..253 && ascii.contains('.') && ascii.split('.').all { label -> label.length in 1..63 && label.first().isLetterOrDigit() && label.last().isLetterOrDigit() && label.all { it.isLetterOrDigit() || it == '-' } }
    } catch (_: IllegalArgumentException) { false }
}


/**
 * Duration picker shown before a block is applied. The user must choose how many days the block
 * lasts; the minimum is 3 days (no 1- or 2-day option). Rendered as rows of chips so it fits on
 * small and large screens, RTL-aware.
 */
@Composable
private fun BlockDurationPicker(language: String, selectedDays: Int, onSelect: (Int) -> Unit) {
    val heading = when (language) {
        "en" -> "For how many days do you want to block this?"
        "ar" -> "\u0644\u0643\u0645 \u064a\u0648\u0645\u064b\u0627 \u062a\u0631\u064a\u062f \u062d\u0638\u0631 \u0647\u0630\u0627\u061f"
        else -> "\u0628\u06c6 \u0686\u06d5\u0646\u062f \u0695\u06c6\u0698 \u062f\u06d5\u062a\u06d5\u0648\u06ce\u062a \u0626\u06d5\u0645\u06d5 \u0628\u0644\u06c6\u06a9 \u0628\u06a9\u0631\u06ce\u062a\u061f"
    }
    fun dayLabel(d: Int): String = when (language) {
        "en" -> "$d days"
        "ar" -> "$d \u064a\u0648\u0645"
        else -> "$d \u0695\u06c6\u0698"
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(heading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        BLOCK_DAY_OPTIONS.chunked(3).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { d ->
                    FilterChip(
                        selected = selectedDays == d,
                        onClick = { onSelect(d) },
                        label = { Text(dayLabel(d), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Selectable block durations in days. Minimum is 3 days as required. */
private val BLOCK_DAY_OPTIONS = listOf(3, 9, 15, 30, 40, 60, 90, 120, 150, 200, 220, 260, 300)

// -------------------------------------------------------------------------------------------
// Timed App Lock (الحظر الزمني للتطبيقات) — strict, auto-expiring, no early unlock.
// -------------------------------------------------------------------------------------------

/** Banner entry shown at the top of the BLOCK list; opens the timed-lock flow. */
@Composable
private fun TimedAppLockBanner(language: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.LockClock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tr("timed_lock_section", language),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    tr("timed_lock_section_sub", language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/**
 * Two-step flow: pick the target app + duration (hour presets), then the strict warning
 * confirmation. Once confirmed there is no way back — the store owns the window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimedAppLockDialog(
    language: String,
    appsLoader: suspend () -> List<InstalledApp>,
    onLock: (String, String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<InstalledApp?>(null) }
    var minutes by rememberSaveable { mutableIntStateOf(120) }
    var showWarn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = runCatching { appsLoader() }.getOrDefault(emptyList())
        loading = false
    }

    val filtered = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    if (showWarn) {
        val target = selected
        AlertDialog(
            onDismissRequest = { showWarn = false },
            icon = { Icon(Icons.Default.LockClock, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(tr("timed_lock_confirm_title", language), fontWeight = FontWeight.Bold) },
            text = { Text(tr("timed_lock_confirm_body", language)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWarn = false
                        if (target != null) onLock(target.packageName, target.name, minutes * 60_000L)
                        // Close the whole flow right after the lock is saved — previously the
                        // picker dialog stayed open underneath after a successful apply.
                        onDismiss()
                    },
                ) {
                    Text(
                        tr("timed_lock_confirm_cta", language),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarn = false }) { Text(tr("cancel", language)) }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("timed_lock_title", language), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    tr("timed_lock_choose_app", language),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text(tr("timed_lock_search", language)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(8.dp))
                if (loading) {
                    Box(
                        Modifier.fillMaxWidth().height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth().height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            val isSelected = selected?.packageName == app.packageName
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent,
                                    )
                                    .clickable { selected = app }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                DrawableIcon(app.icon, Modifier.size(36.dp))
                                Spacer(Modifier.width(10.dp))
                                Icon(
                                    if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    app.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    tr("timed_lock_duration", language),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(30, 60, 120, 240, 480, 720, 1440).forEach { mins ->
                        val label = if (mins % 60 == 0) "${mins / 60} ${tr("timed_lock_hours", language)}" else "$mins ${tr("timed_lock_minutes", language)}"
                        FilterChip(
                            selected = minutes == mins,
                            onClick = { minutes = mins },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (selected != null) showWarn = true },
                enabled = selected != null,
            ) {
                Text(tr("timed_lock_apply", language), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("cancel", language)) } },
    )
}
