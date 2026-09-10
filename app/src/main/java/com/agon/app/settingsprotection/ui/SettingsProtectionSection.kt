package com.agon.app.settingsprotection.ui

import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.admin.deviceAdminSettingsIntent
import com.agon.app.settingsprotection.data.ProtectionCommitmentStore
import com.agon.app.settingsprotection.domain.AuthMode
import com.agon.app.settingsprotection.domain.FailedAttempt
import com.agon.app.settingsprotection.domain.PinOperationResult
import com.agon.app.settingsprotection.domain.PinPolicy
import com.agon.app.settingsprotection.domain.UsernamePolicy
import com.agon.app.settingsprotection.viewmodel.SettingsProtectionViewModel
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "App Settings Protection" controls, rendered inside the existing Settings tab as a list of
 * items so it matches the surrounding layout without altering it.
 */
@Composable
fun SettingsProtectionSection(
    language: String,
    viewModel: SettingsProtectionViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPinDialog by remember { mutableStateOf(false) }
    var showStrongLockNotice by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var refreshTicker by remember { mutableStateOf(0) }
    val adminSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshEnvironment()
    }
    val permissionGate = com.agon.app.applock.rememberSensitiveActionGate(language)

    val isStrongActive = remember(state, refreshTicker) { ProtectionCommitmentStore.isStrongActive(context) }
    val strongRemaining = remember(state, refreshTicker) { ProtectionCommitmentStore.remainingMillis(context) }
    val commitmentExpired = remember(state, refreshTicker) { ProtectionCommitmentStore.isExpired(context) || ProtectionCommitmentStore.isStopped(context) }

    LaunchedEffect(Unit) { viewModel.refreshEnvironment() }

    LaunchedEffect(viewModel) {
        viewModel.results.collectLatest { result ->
            feedback = resultMessage(result, language)
            if (result is PinOperationResult.Success) {
                showPinDialog = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = sectionTitle(language),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )

        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = CircleShape,
                        color = if (isStrongActive) MaterialTheme.colorScheme.primaryContainer
                        else if (commitmentExpired) MaterialTheme.colorScheme.surfaceContainer
                        else if (state.protecting) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (commitmentExpired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (isStrongActive) strongProtectionActiveTitle(language) else protectionLabel(language),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (isStrongActive) {
                                strongRemainingNotice(strongRemaining, language)
                            } else if (commitmentExpired) {
                                protectionExpired(language)
                            } else if (state.protecting) {
                                protectionActive(language)
                            } else {
                                protectionInactive(language)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (isStrongActive) strongProtectionExplanation(language) else if (commitmentExpired) protectionExpiredExplanation(language) else explanation(language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.credentialCorrupted && !isStrongActive) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = corruptedTitle(language),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = corruptedMessage(language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::resetCorruptedCredential, modifier = Modifier.fillMaxWidth()) {
                        Text(resetPin(language))
                    }
                }
            }
        }

        ProtectionRow(
            icon = Icons.Outlined.Password,
            title = if (isStrongActive) {
                strongProtectionActiveTitle(language)
            } else if (commitmentExpired) {
                protectionExpiredTitle(language)
            } else if (state.pinConfigured) {
                changePin(language)
            } else {
                setupProtectionOption(language)
            },
            subtitle = if (isStrongActive) {
                strongLockedPinSubtitle(strongRemaining, language)
            } else if (commitmentExpired) {
                protectionExpiredSubtitle(language)
            } else if (state.pinConfigured) {
                pinConfiguredHint(state.pinLength, language)
            } else {
                pinMissingHint(language)
            },
            onClick = {
                if (isStrongActive) {
                    showStrongLockNotice = true
                } else {
                    showPinDialog = true
                }
            },
        )

        AnimatedVisibility(visible = state.temporaryAccessSeconds > 0 && !isStrongActive) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timer, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = temporaryAccessLabel(language),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = "${state.temporaryAccessSeconds}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    TextButton(onClick = viewModel::endTemporaryAccess) { Text(lockNow(language)) }
                }
            }
        }

        ProtectionRow(
            icon = Icons.Outlined.AccessibilityNew,
            title = accessibilityLabel(language),
            subtitle = if (state.accessibilityConnected) grantedLabel(language) else requiredLabel(language),
            onClick = {
                if (isStrongActive) {
                    showStrongLockNotice = true
                    return@ProtectionRow
                }
                val openSettings = {
                    viewModel.allowPermissionEnable()
                    runCatching { context.startActivity(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    Unit
                }
                if (state.pinConfigured) {
                    permissionGate.requireCredential(openSettings)
                } else {
                    openSettings()
                }
            },
        )

        ProtectionRow(
            icon = Icons.Outlined.AdminPanelSettings,
            title = adminLabel(language),
            subtitle = if (state.deviceAdminActive) grantedLabel(language) else optionalLabel(language),
            onClick = {
                if (isStrongActive) {
                    showStrongLockNotice = true
                    return@ProtectionRow
                }
                val openSettings = {
                    viewModel.allowPermissionEnable()
                    adminSettingsLauncher.launch(deviceAdminSettingsIntent(context))
                }
                if (state.pinConfigured) {
                    permissionGate.requireCredential(openSettings)
                } else {
                    openSettings()
                }
            },
        )

        ProtectionRow(
            icon = Icons.Outlined.History,
            title = attemptsLabel(language),
            subtitle = attemptsHint(state.failedAttempts.size, language),
            onClick = { showHistory = true },
        )
    }

    if (showPinDialog && !isStrongActive) {
        PinEditorDialog(
            requiresCurrent = state.pinConfigured,
            currentAuthMode = state.authMode,
            currentUsername = state.username,
            language = language,
            onDismiss = { showPinDialog = false },
            onConfirmPin = { current, next, confirm -> viewModel.savePin(next, confirm, current) },
            onConfirmUsernamePassword = { current, username, next, confirm ->
                viewModel.saveUsernamePassword(username, next, confirm, current)
            },
            onActivateStrong = { chosenDays ->
                ProtectionCommitmentStore.setStrongDays(context, chosenDays)
                refreshTicker++
                viewModel.refreshEnvironment()
            },
        )
    }

    if (showStrongLockNotice) {
        AlertDialog(
            onDismissRequest = { showStrongLockNotice = false },
            icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(strongLockDialogTitle(language), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        strongCannotChangePinMessage(language),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${remainingPrefix(language)}${ProtectionCommitmentStore.formatStrongRemaining(strongRemaining, language)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showStrongLockNotice = false }) { Text(okLabel(language)) }
            },
        )
    }

    if (showHistory) {
        AttemptHistoryDialog(
            attempts = state.failedAttempts,
            language = language,
            onClear = viewModel::clearHistory,
            onDismiss = { showHistory = false },
        )
    }

    permissionGate.Host()

    feedback?.let { message ->
        AlertDialog(
            onDismissRequest = { feedback = null },
            icon = { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { feedback = null }) { Text(okLabel(language)) } },
        )
    }
}

@Composable
private fun ProtectionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing?.invoke()
        }
    }
}

private enum class SelectedProtectionType {
    STRONG,
    PIN,
    USERNAME_PASSWORD,
}

@Composable
private fun PinEditorDialog(
    requiresCurrent: Boolean,
    currentAuthMode: AuthMode,
    currentUsername: String,
    language: String,
    onDismiss: () -> Unit,
    onConfirmPin: (String, String, String) -> Unit,
    onConfirmUsernamePassword: (String, String, String, String) -> Unit,
    onActivateStrong: (Int) -> Unit,
) {
    val commitmentContext = LocalContext.current
    var protectionType by remember {
        mutableStateOf(
            if (currentAuthMode == AuthMode.USERNAME_PASSWORD) SelectedProtectionType.USERNAME_PASSWORD
            else SelectedProtectionType.STRONG
        )
    }
    var current by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(if (currentAuthMode == AuthMode.USERNAME_PASSWORD) currentUsername else "") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var commitmentDays by remember { mutableStateOf<Int?>(ProtectionCommitmentStore.TEST_5_MINUTES) }

    val isFormValid = when (protectionType) {
        SelectedProtectionType.STRONG -> commitmentDays != null
        SelectedProtectionType.PIN -> PinPolicy.isValid(next) && next == confirm &&
            (!requiresCurrent || current.length >= PinPolicy.MIN_LENGTH) && commitmentDays != null
        SelectedProtectionType.USERNAME_PASSWORD -> UsernamePolicy.isValid(username) &&
            PinPolicy.isValid(next) && next == confirm &&
            (!requiresCurrent || current.length >= PinPolicy.MIN_LENGTH) && commitmentDays != null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Password, null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                when (protectionType) {
                    SelectedProtectionType.STRONG -> strongProtectionLabel(language)
                    SelectedProtectionType.PIN -> if (requiresCurrent) changePin(language) else createPin(language)
                    SelectedProtectionType.USERNAME_PASSWORD -> methodUsernameLabel(language)
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Top-Level Protection Mode Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = protectionType == SelectedProtectionType.STRONG,
                        onClick = {
                            protectionType = SelectedProtectionType.STRONG
                            commitmentDays = ProtectionCommitmentStore.TEST_5_MINUTES
                        },
                        label = {
                            Text(
                                when (language) {
                                    "en" -> "🔒 Strong"
                                    "ar" -> "🔒 قوية"
                                    else -> "🔒 بەهێز"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = protectionType == SelectedProtectionType.PIN,
                        onClick = {
                            protectionType = SelectedProtectionType.PIN
                            commitmentDays = ProtectionCommitmentStore.TEST_5_MINUTES
                        },
                        label = {
                            Text(
                                when (language) {
                                    "en" -> "🔑 PIN"
                                    "ar" -> "🔑 رمز PIN"
                                    else -> "🔑 ڕەمزی PIN"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = protectionType == SelectedProtectionType.USERNAME_PASSWORD,
                        onClick = {
                            protectionType = SelectedProtectionType.USERNAME_PASSWORD
                            commitmentDays = ProtectionCommitmentStore.TEST_5_MINUTES
                        },
                        label = {
                            Text(
                                when (language) {
                                    "en" -> "👤 User"
                                    "ar" -> "👤 مستخدم"
                                    else -> "👤 بەکارهێنەر"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (protectionType == SelectedProtectionType.STRONG) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    ) {
                        Text(
                            text = strongProtectionWarningNotice(language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp),
                        )
                    }

                    CommitmentPicker(
                        language = language,
                        isStrong = true,
                        selectedDays = commitmentDays,
                        onSelect = { commitmentDays = it },
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = normalProtectionExplanation(language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp),
                        )
                    }

                    if (requiresCurrent) {
                        PinField(current, currentPinLabel(language), ImeAction.Next) { current = it }
                    }
                    if (protectionType == SelectedProtectionType.USERNAME_PASSWORD) {
                        UsernameField(username, usernameFieldLabel(language)) { username = it }
                        PinField(next, newPasswordLabel(language), ImeAction.Next) { next = it }
                        PinField(confirm, confirmPasswordLabel(language), ImeAction.Done) { confirm = it }
                    } else {
                        PinField(next, newPinLabel(language), ImeAction.Next) { next = it }
                        PinField(confirm, confirmPinLabel(language), ImeAction.Done) { confirm = it }
                    }
                    Text(
                        text = if (protectionType == SelectedProtectionType.USERNAME_PASSWORD) usernameRuleHint(language) else pinRuleHint(language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    CommitmentPicker(
                        language = language,
                        isStrong = false,
                        selectedDays = commitmentDays,
                        onSelect = { commitmentDays = it },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = commitmentDays ?: return@Button
                    if (protectionType == SelectedProtectionType.STRONG) {
                        onActivateStrong(chosen)
                        onDismiss()
                    } else {
                        if (chosen == ProtectionCommitmentStore.FOREVER.toInt()) {
                            ProtectionCommitmentStore.setNormalForever(commitmentContext)
                        } else {
                            ProtectionCommitmentStore.setNormalDays(commitmentContext, chosen)
                        }
                        if (protectionType == SelectedProtectionType.USERNAME_PASSWORD) {
                            onConfirmUsernamePassword(current, username.trim(), next, confirm)
                        } else {
                            onConfirmPin(current, next, confirm)
                        }
                    }
                },
                enabled = isFormValid,
            ) {
                Text(
                    when (protectionType) {
                        SelectedProtectionType.STRONG -> when (language) {
                            "en" -> "Activate Strong Protection"
                            "ar" -> "تفعيل الحماية القوية"
                            else -> "چالاککردنی پاراستنی بەهێز"
                        }
                        else -> saveLabel(language)
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelLabel(language)) } },
    )
}

@Composable
private fun UsernameField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { !it.isWhitespace() }.take(UsernamePolicy.MAX_LENGTH)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AttemptHistoryDialog(
    attempts: List<FailedAttempt>,
    language: String,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(attemptsLabel(language), fontWeight = FontWeight.Bold) },
        text = {
            if (attempts.isEmpty()) {
                Text(noAttempts(language), style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    attempts.reversed().forEach { attempt ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = format.format(Date(attempt.timestamp)),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = attempt.settingsPackage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (attempts.isNotEmpty()) {
                Button(onClick = onClear) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(clearLabel(language))
                }
            } else {
                Button(onClick = onDismiss) { Text(okLabel(language)) }
            }
        },
        dismissButton = {
            if (attempts.isNotEmpty()) {
                TextButton(onClick = onDismiss) { Text(okLabel(language)) }
            }
        },
    )
}

@Composable
private fun PinField(
    value: String,
    label: String,
    imeAction: ImeAction,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(PinPolicy.sanitize(input)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = imeAction,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun sectionTitle(language: String) = when (language) {
    "en" -> "Settings Protection"
    "ar" -> "حماية الإعدادات"
    else -> "پاراستنی ڕێکخستنەکان"
}

private fun protectionLabel(language: String) = when (language) {
    "en" -> "App settings & removal protection"
    "ar" -> "حماية الإعدادات وإلغاء التثبيت"
    else -> "پاراستنی ڕێکخستن و سڕینەوە"
}

private fun strongProtectionActiveTitle(language: String) = when (language) {
    "en" -> "🔒 Strong Protection Active"
    "ar" -> "🔒 الحماية القوية مفعلة"
    else -> "🔒 پاراستنی بەهێز چالاکە"
}

private fun strongRemainingNotice(remainingMillis: Long, language: String): String {
    val formatted = ProtectionCommitmentStore.formatStrongRemaining(remainingMillis, language)
    return when (language) {
        "en" -> "Active — $formatted remaining (Strict Lock)"
        "ar" -> "نشط — متبقي $formatted (قفل صارم)"
        else -> "چالاکە — $formatted ماوە (قوفڵی توند)"
    }
}

private fun strongLockedPinSubtitle(remainingMillis: Long, language: String): String {
    val formatted = ProtectionCommitmentStore.formatStrongRemaining(remainingMillis, language)
    return when (language) {
        "en" -> "Locked ($formatted remaining). Cannot change or disable."
        "ar" -> "مغلق (متبقي $formatted). لا يمكن التعديل أو التعطيل."
        else -> "داخراوە ($formatted ماوە). ناتوانرێت دەستکاری یان ناچالاک بکرێت."
    }
}

private fun strongProtectionExplanation(language: String) = when (language) {
    "en" -> "Strict timed protection is currently in progress. Protection cannot be stopped, the app cannot be uninstalled, and protected settings cannot be opened until the timer completes."
    "ar" -> "الحماية القوية الصارمة قيد التشغيل حالياً. لا يمكن إيقاف الحماية أو إلغاء تثبيت التطبيق أو الدخول للإعدادات المحمية حتى انتهاء الوقت المحدد."
    else -> "پاراستنی بەهێزی توند لە ئێستادا کارایە. ناتوانرێت پاراستن بوەستێنرێت، ئەپەکە بسڕدرێتەوە، یان بچیتە ڕێکخستنە پارێزراوەکان تا کاتەکە تەواو دەبێت."
}

private fun strongLockDialogTitle(language: String) = when (language) {
    "en" -> "Strong Protection Active"
    "ar" -> "الحماية القوية مفعلة"
    else -> "پاراستنی بەهێز چالاکە"
}

private fun strongCannotChangePinMessage(language: String) = when (language) {
    "en" -> "Strong Protection is strictly locked. You cannot modify protection, change PIN, or uninstall the app until the countdown finishes."
    "ar" -> "الحماية القوية مقفلة بشكل صارم. لا يمكنك تعديل الحماية أو تغيير الرمز أو إلغاء تثبيت التطبيق حتى انتهاء العد التنازلي."
    else -> "پاراستنی بەهێز بە توندی داخراوە. ناتوانیت دەستکاری پاراستن بکەیت یان ڕەمز بگۆڕیت یان ئەپ بسڕیتەوە تا کاتەکە تەواو دەبێت."
}

private fun remainingPrefix(language: String) = when (language) {
    "en" -> "Remaining: "
    "ar" -> "الوقت المتبقي: "
    else -> "کاتی ماوە: "
}

private fun protectionActive(language: String) = when (language) {
    "en" -> "Active and guarding settings"
    "ar" -> "نشطة وتحمي الإعدادات"
    else -> "چالاکە و ڕێکخستن دەپارێزێت"
}

private fun protectionInactive(language: String) = when (language) {
    "en" -> "Inactive"
    "ar" -> "غير نشطة"
    else -> "ناچالاکە"
}

private fun explanation(language: String) = when (language) {
    "en" -> "Prevents unauthorized opening of device settings or clearing Family Shield app data."
    "ar" -> "يمنع فتح إعدادات الجهاز غير المصرح به أو مسح بيانات تطبيق Family Shield."
    else -> "ڕێگری دەکات لە کردنەوەی ڕێکخستنەکانی ئامێر بەبێ مۆڵەت یان سڕینەوەی داتای Family Shield."
}

private fun setupProtectionOption(language: String) = when (language) {
    "en" -> "Setup Protection (Strong or PIN)"
    "ar" -> "إعداد الحماية (قوية أو PIN)"
    else -> "دیاریکردنی پاراستن (بەهێز یان PIN)"
}

private fun normalProtectionLabel(language: String) = when (language) {
    "en" -> "Standard PIN Protection"
    "ar" -> "حماية رمز PIN"
    else -> "پاراستن بە PIN"
}

private fun strongProtectionLabel(language: String) = when (language) {
    "en" -> "Strong Protection 🔒"
    "ar" -> "الحماية القوية 🔒"
    else -> "پاراستنی بەهێز 🔒"
}

private fun normalProtectionExplanation(language: String) = when (language) {
    "en" -> "Standard PIN: PIN is required to modify protection or access protected settings."
    "ar" -> "الحماية العادية: يتطلب إدخال رمز PIN لإلغاء الحماية أو تغيير الإعدادات."
    else -> "پاراستنی ئاسایی: پێویستی بە داخڵکردنی PIN دەبێت بۆ دەستکاریکردن یان چوونە ڕێکخستنەکان."
}

private fun strongProtectionWarningNotice(language: String) = when (language) {
    "en" -> "🔒 Strong Protection: Non-bypassable strict lock without PIN. Settings, protection toggles, and uninstall will be locked until the selected duration ends completely."
    "ar" -> "🔒 الحماية القوية: قفل صارم ومباشر بدون رمز PIN. لا يمكن إيقاف الحماية أو إلغاء تثبيت التطبيق أو الدخول للإعدادات المحمية حتى انتهاء المدة المحددة بالكامل."
    else -> "🔒 پاراستنی بەهێز: قوفڵی توند و کاتی بەبێ پێویستی بە PIN. ناتوانرێت پاراستن بوەستێنرێت یان ئەپەکە بسڕدرێتەوە یان دەستکاری ڕێکخستنەکان بکرێت تا تەواوبوونی کاتی دیاریکراو."
}

private fun createPin(language: String) = when (language) {
    "en" -> "Create PIN"
    "ar" -> "إنشاء رمز"
    else -> "دروستکردنی PIN"
}

private fun changePin(language: String) = when (language) {
    "en" -> "Change PIN"
    "ar" -> "تغيير الرمز"
    else -> "گۆڕینی PIN"
}

private fun pinConfiguredHint(length: Int, language: String) = when (language) {
    "en" -> "Passcode is active ($length characters)"
    "ar" -> "الرمز مُفعّل ($length أحرف)"
    else -> "ڕەمز چالاکە ($length پیت)"
}

private fun pinMissingHint(language: String) = when (language) {
    "en" -> "No PIN configured yet"
    "ar" -> "لم يتم إعداد رمز بعد"
    else -> "هێشتا PIN دانەنراوە"
}

private fun temporaryAccessLabel(language: String) = when (language) {
    "en" -> "Temporary access active"
    "ar" -> "وصول مؤقت نشط"
    else -> "دەستپێگەیشتنی کاتی چالاکە"
}

private fun lockNow(language: String) = when (language) {
    "en" -> "Lock now"
    "ar" -> "أقفل الآن"
    else -> "ئێستا داخە"
}

private fun accessibilityLabel(language: String) = when (language) {
    "en" -> "Accessibility service"
    "ar" -> "خدمة إمكانية الوصول"
    else -> "خزمەتگوزاری Accessibility"
}

private fun adminLabel(language: String) = when (language) {
    "en" -> "Device administrator"
    "ar" -> "مسؤول الجهاز"
    else -> "بەڕێوەبەری ئامێر"
}

private fun grantedLabel(language: String) = when (language) {
    "en" -> "Granted"
    "ar" -> "ممنوح"
    else -> "دراوە"
}

private fun requiredLabel(language: String) = when (language) {
    "en" -> "Required for detection"
    "ar" -> "مطلوب للكشف"
    else -> "پێویستە بۆ دۆزینەوە"
}

private fun optionalLabel(language: String) = when (language) {
    "en" -> "Optional, strengthens protection"
    "ar" -> "اختياري، يعزز الحماية"
    else -> "ئارەزوومەندانە، پاراستن بەهێزتر دەکات"
}

private fun attemptsLabel(language: String) = when (language) {
    "en" -> "Blocked attempts"
    "ar" -> "المحاولات المحظورة"
    else -> "هەوڵەکانی بلۆککراو"
}

private fun attemptsHint(count: Int, language: String) = when (language) {
    "en" -> "$count recorded"
    "ar" -> "$count مُسجّلة"
    else -> "$count تۆمارکراو"
}

private fun noAttempts(language: String) = when (language) {
    "en" -> "No blocked attempts recorded."
    "ar" -> "لا توجد محاولات محظورة مسجلة."
    else -> "هیچ هەوڵێکی بلۆککراو تۆمار نەکراوە."
}

private fun corruptedTitle(language: String) = when (language) {
    "en" -> "PIN needs to be reset"
    "ar" -> "يجب إعادة تعيين الرمز"
    else -> "PIN پێویستە ڕێست بکرێتەوە"
}

private fun corruptedMessage(language: String) = when (language) {
    "en" -> "The secure key protecting your PIN was invalidated by the system. Reset and create a new PIN."
    "ar" -> "تم إبطال المفتاح الآمن الذي يحمي رمزك من قبل النظام. أعد التعيين وأنشئ رمزاً جديداً."
    else -> "کلیلی پارێزراوی PIN لەلایەن سیستەمەوە بەتاڵ کراوە. ڕێستی بکەوە و PIN ـێکی نوێ دروست بکە."
}

private fun resetPin(language: String) = when (language) {
    "en" -> "Reset PIN"
    "ar" -> "إعادة تعيين الرمز"
    else -> "ڕێستکردنەوەی PIN"
}

private fun currentPinLabel(language: String) = when (language) {
    "en" -> "Current PIN"
    "ar" -> "الرمز الحالي"
    else -> "PIN ـی ئێستا"
}

private fun newPinLabel(language: String) = when (language) {
    "en" -> "New PIN"
    "ar" -> "الرمز الجديد"
    else -> "PIN ـی نوێ"
}

private fun confirmPinLabel(language: String) = when (language) {
    "en" -> "Confirm PIN"
    "ar" -> "تأكيد الرمز"
    else -> "دڵنیاکردنی PIN"
}

private fun pinRuleHint(language: String) = when (language) {
    "en" -> "4 to 8 digits. Digits only. Stored with Android Keystore encryption."
    "ar" -> "من 4 إلى 8 أرقام. أرقام فقط. مخزّن بتشفير Android Keystore."
    else -> "٤ بۆ ٨ ژمارە. تەنها ژمارە. بە شفرەکردنی Android Keystore هەڵدەگیرێت."
}

private fun methodUsernameLabel(language: String) = when (language) {
    "en" -> "Username & Password"
    "ar" -> "اسم المستخدم وكلمة المرور"
    else -> "ناو و وشەی نهێنی"
}

private fun usernameFieldLabel(language: String) = when (language) {
    "en" -> "Username"
    "ar" -> "اسم المستخدم"
    else -> "ناوی بەکارهێنەر"
}

private fun newPasswordLabel(language: String) = when (language) {
    "en" -> "New password"
    "ar" -> "كلمة المرور الجديدة"
    else -> "وشەی نهێنی نوێ"
}

private fun confirmPasswordLabel(language: String) = when (language) {
    "en" -> "Confirm password"
    "ar" -> "تأكيد كلمة المرور"
    else -> "دڵنیاکردنی وشەی نهێنی"
}

private fun usernameRuleHint(language: String) = when (language) {
    "en" -> "Username: 3 to 20 characters. Password: 4 to 20 characters. Letters, digits and symbols allowed."
    "ar" -> "اسم المستخدم: 3 إلى 20 حرفاً. كلمة المرور: 4 إلى 20 حرفاً. يُسمح بالأحرف والأرقام والرموز."
    else -> "ناوی بەکارهێنەر: ٣ بۆ ٢٠ پیت. وشەی نهێنی: ٤ بۆ ٢٠ پیت. پیت، ژمارە و هێما ڕێگەپێدراون."
}

private fun saveLabel(language: String) = when (language) {
    "en" -> "Save"
    "ar" -> "حفظ"
    else -> "پاشەکەوت"
}

private fun cancelLabel(language: String) = when (language) {
    "en" -> "Cancel"
    "ar" -> "إلغاء"
    else -> "پاشگەزبوونەوە"
}

private fun clearLabel(language: String) = when (language) {
    "en" -> "Clear"
    "ar" -> "مسح"
    else -> "سڕینەوە"
}

private fun okLabel(language: String) = when (language) {
    "en" -> "OK"
    "ar" -> "حسناً"
    else -> "باشە"
}

private fun protectionExpired(language: String) = when (language) {
    "en" -> "Period ended — settings are open"
    "ar" -> "انتهت المدة — الإعدادات مفتوحة"
    else -> "ماوە تەواو بووە — ڕێکخستنەکان کراوەن"
}

private fun protectionExpiredExplanation(language: String) = when (language) {
    "en" -> "The protection period has ended. Protected settings are accessible without PIN. Lock reactivates when you set a new protection period."
    "ar" -> "انتهت مدة الحماية. الإعدادات المحمية متاحة بدون رمز PIN. يعاد تفعيل القفل عند تعيين مدة حماية جديدة."
    else -> "ماوەی پاراستن تەواو بووە. ڕێکخستنە پارێزراوەکان بەبێ PIN دەستپێگەیشتن. قوفڵ دەگەڕێتەوە کاتێک ماوەیەکی نوێ دیاری بکەیت."
}

private fun protectionExpiredTitle(language: String) = when (language) {
    "en" -> "Protection expired"
    "ar" -> "انتهت الحماية"
    else -> "پاراستن تەواو بووە"
}

private fun protectionExpiredSubtitle(language: String) = when (language) {
    "en" -> "Set a new period to reactivate lock"
    "ar" -> "عيّن مدة جديدة لإعادة تفعيل القفل"
    else -> "ماوەیەکی نوێ دیاری بکە بۆ گەڕاندنەوەی قوفڵ"
}

private fun resultMessage(result: PinOperationResult, language: String): String = when (result) {
    PinOperationResult.Success -> when (language) {
        "en" -> "Changes saved successfully."
        "ar" -> "تم حفظ التغييرات بنجاح."
        else -> "گۆڕانکارییەکان بەسەرکەوتوویی پاشەکەوت کران."
    }
    PinOperationResult.InvalidLength -> when (language) {
        "en" -> "Invalid length. Please check requirements."
        "ar" -> "طول غير صالح. يرجى مراجعة الشروط."
        else -> "درێژی نادروستە. تکایە مەرجەکان بپشکنە."
    }
    PinOperationResult.Mismatch -> when (language) {
        "en" -> "Confirmation does not match."
        "ar" -> "تأكيد الرمز غير متطابق."
        else -> "دڵنیاکردنەوە یەکناگرێتەوە."
    }
    PinOperationResult.WrongPin -> when (language) {
        "en" -> "Incorrect current PIN."
        "ar" -> "الرمز الحالي غير صحيح."
        else -> "PIN ـی ئێستا هەڵەیە."
    }
    PinOperationResult.SameAsCurrent -> when (language) {
        "en" -> "New PIN must be different from current PIN."
        "ar" -> "يجب أن يكون الرمز الجديد مختلفاً عن الرمز الحالي."
        else -> "پێویستە PIN ـی نوێ جیاواز بێت لە PIN ـی ئێستا."
    }
    PinOperationResult.Locked -> when (language) {
        "en" -> "Protection is currently locked."
        "ar" -> "الحماية مقفلة حالياً."
        else -> "پاراستن لە ئێستادا داخراوە."
    }
    PinOperationResult.StorageFailure -> when (language) {
        "en" -> "Storage error occurred. Please try again."
        "ar" -> "حدث خطأ في التخزين. يرجى المحاولة مرة أخرى."
        else -> "هەڵەیەک لە هەڵگرتن ڕوویدا. تکایە دووبارە هەوڵ بدەرەوە."
    }
}

@Composable
private fun CommitmentPicker(
    language: String,
    isStrong: Boolean,
    selectedDays: Int?,
    onSelect: (Int) -> Unit,
) {
    val heading = when (language) {
        "en" -> if (isStrong) "Choose Strong Protection Duration:" else "Commitment duration:"
        "ar" -> if (isStrong) "اختر مدة الحماية القوية:" else "مدة الالتزام بالحماية:"
        else -> if (isStrong) "ماوەی پاراستنی بەهێز هەڵبژێرە:" else "ماوەی پابەندبوون بە پاراستن:"
    }
    val foreverLabel = when (language) {
        "en" -> "Forever"
        "ar" -> "إلى الأبد"
        else -> "هەتاهەتایی"
    }
    fun dayLabel(option: Int): String =
        ProtectionCommitmentStore.formatOptionLabel(option, language)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            heading,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        val options = if (isStrong) ProtectionCommitmentStore.STRONG_DAY_OPTIONS else ProtectionCommitmentStore.NORMAL_DAY_OPTIONS
        options.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { days ->
                    FilterChip(
                        selected = selectedDays == days,
                        onClick = { onSelect(days) },
                        label = {
                            Text(
                                dayLabel(days),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (!isStrong) {
            FilterChip(
                selected = selectedDays == ProtectionCommitmentStore.FOREVER.toInt(),
                onClick = { onSelect(ProtectionCommitmentStore.FOREVER.toInt()) },
                label = {
                    Text(
                        foreverLabel,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
