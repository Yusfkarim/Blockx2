package com.agon.app.settingsprotection.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.settingsprotection.data.ProtectionCommitmentStore
import com.agon.app.settingsprotection.domain.PinPolicy
import com.agon.app.settingsprotection.viewmodel.AdminDisableViewModel
import kotlinx.coroutines.launch

/**
 * PIN gate shown before device administrator rights are given up from inside the app.
 */
@Composable
fun AdminDisableGate(
    language: String,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
    viewModel: AdminDisableViewModel = viewModel(),
) {
    val context = LocalContext.current
    val configured by viewModel.pinConfigured.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    val isStrongActive = remember { ProtectionCommitmentStore.isStrongActive(context) }
    val remaining = remember { ProtectionCommitmentStore.remainingMillis(context) }

    if (isStrongActive) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
            title = {
                Text(
                    text = when (language) {
                        "en" -> "🔒 Strong Protection Active"
                        "ar" -> "🔒 الحماية القوية مفعلة"
                        else -> "🔒 پاراستنی بەهێز چالاکە"
                    },
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = when (language) {
                            "en" -> "Device administrator cannot be deactivated during Strong Protection."
                            "ar" -> "لا يمكن إلغاء مسؤول الجهاز أثناء فترة الحماية القوية."
                            else -> "ناتوانرێت بەرپرسی ئامێر لاببرێت لە ماوەی پاراستنی بەهێزدا."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${when (language) {
                            "en" -> "Remaining time: "
                            "ar" -> "الوقت المتبقي: "
                            else -> "کاتی ماوە: "
                        }}${ProtectionCommitmentStore.formatStrongRemaining(remaining, language)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text(when (language) {
                        "en" -> "OK"
                        "ar" -> "حسناً"
                        else -> "باشە"
                    })
                }
            },
        )
        return
    }

    if (!configured) {
        // No PIN is configured, so there is nothing available to verify.
        onVerified()
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(title(language), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(message(language), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { input ->
                        pin = PinPolicy.sanitize(input)
                        error = false
                    },
                    label = { Text(pinLabel(language)) },
                    singleLine = true,
                    isError = error,
                    supportingText = if (error) {
                        { Text(wrongPin(language), color = MaterialTheme.colorScheme.error) }
                    } else {
                        null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !checking && pin.length >= PinPolicy.MIN_LENGTH,
                onClick = {
                    checking = true
                    scope.launch {
                        val granted = viewModel.verify(pin)
                        checking = false
                        if (granted) {
                            onVerified()
                        } else {
                            error = true
                            pin = ""
                        }
                    }
                },
            ) { Text(confirm(language)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancel(language)) } },
    )
}

private fun title(language: String) = when (language) {
    "en" -> "Confirm with PIN"
    "ar" -> "التأكيد بالرمز"
    else -> "بە PIN پشتڕاست بکەوە"
}

private fun message(language: String) = when (language) {
    "en" -> "Turning off device administrator makes BlockX LaAbrah removable. Enter the protection PIN to continue."
    "ar" -> "إيقاف مسؤول الجهاز يجعل BlockX LaAbrah قابلاً للإزالة. أدخل رمز الحماية للمتابعة."
    else -> "کوژاندنەوەی بەڕێوەبەری ئامێر وا دەکات BlockX LaAbrah بسڕدرێتەوە. PIN ـی پاراستن بنووسە."
}

private fun pinLabel(language: String) = when (language) {
    "en" -> "Protection PIN"
    "ar" -> "رمز الحماية"
    else -> "PIN ـی پاراستن"
}

private fun wrongPin(language: String) = when (language) {
    "en" -> "Incorrect PIN. Protection stays enabled."
    "ar" -> "الرمز غير صحيح. تبقى الحماية مفعلة."
    else -> "PIN هەڵەیە. پاراستن چالاک دەمێنێتەوە."
}

private fun confirm(language: String) = when (language) {
    "en" -> "Turn off"
    "ar" -> "إيقاف"
    else -> "کوژاندنەوە"
}

private fun cancel(language: String) = when (language) {
    "en" -> "Cancel"
    "ar" -> "إلغاء"
    else -> "پاشگەزبوونەوە"
}
