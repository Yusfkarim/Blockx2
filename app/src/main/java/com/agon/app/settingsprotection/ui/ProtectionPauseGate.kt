package com.agon.app.settingsprotection.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Autorenew
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
import com.agon.app.settingsprotection.domain.AuthMode
import com.agon.app.settingsprotection.domain.PinPolicy
import com.agon.app.settingsprotection.domain.UsernamePolicy
import com.agon.app.settingsprotection.viewmodel.ProtectionPauseViewModel
import kotlinx.coroutines.launch

/**
 * Credential gate shown before protection is temporarily paused for three minutes.
 */
@Composable
fun ProtectionPauseGate(
    language: String,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
    viewModel: ProtectionPauseViewModel = viewModel(),
) {
    val context = LocalContext.current
    val configured by viewModel.pinConfigured.collectAsStateWithLifecycle()
    val authMode by viewModel.authMode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
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
                            "en" -> "Protection cannot be paused during the Strong Protection duration."
                            "ar" -> "لا يمكن إيقاف الحماية مؤقتاً أثناء فترة الحماية القوية."
                            else -> "ناتوانرێت پاراستن بوەستێنرێت لە ماوەی پاراستنی بەهێزدا."
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
        onVerified()
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Autorenew, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(title(language, authMode), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(message(language, authMode), style = MaterialTheme.typography.bodyMedium)
                if (authMode == AuthMode.USERNAME_PASSWORD) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { input ->
                            username = input.filter { !it.isWhitespace() }.take(UsernamePolicy.MAX_LENGTH)
                            error = false
                        },
                        label = { Text(usernameLabel(language)) },
                        singleLine = true,
                        isError = error,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = secret,
                    onValueChange = { input ->
                        secret = if (authMode == AuthMode.PIN) {
                            PinPolicy.sanitize(input)
                        } else {
                            input.filter { !it.isWhitespace() }.take(PinPolicy.MAX_LENGTH)
                        }
                        error = false
                    },
                    label = { Text(secretLabel(language, authMode)) },
                    singleLine = true,
                    isError = error,
                    supportingText = if (error) {
                        { Text(wrongCredential(language, authMode), color = MaterialTheme.colorScheme.error) }
                    } else {
                        null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (authMode == AuthMode.PIN) KeyboardType.NumberPassword else KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !checking && secret.length >= PinPolicy.MIN_LENGTH &&
                    (authMode == AuthMode.PIN || username.length >= UsernamePolicy.MIN_LENGTH),
                onClick = {
                    checking = true
                    scope.launch {
                        val granted = viewModel.verify(username, secret)
                        checking = false
                        if (granted) {
                            onVerified()
                        } else {
                            error = true
                            secret = ""
                        }
                    }
                },
            ) { Text(confirm(language)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancel(language)) } },
    )
}

private fun title(language: String, authMode: AuthMode) = when (authMode) {
    AuthMode.PIN -> when (language) {
        "en" -> "Confirm with PIN"
        "ar" -> "التأكيد بالرمز"
        else -> "بە PIN پشتڕاست بکەوە"
    }
    AuthMode.USERNAME_PASSWORD -> when (language) {
        "en" -> "Confirm your credentials"
        "ar" -> "تأكيد بيانات الدخول"
        else -> "زانیارییەکانی چوونەژوورەوە پشتڕاست بکەوە"
    }
}

private fun message(language: String, authMode: AuthMode) = when (authMode) {
    AuthMode.PIN -> when (language) {
        "en" -> "Protection will be paused for 3 minutes and resumes automatically. Enter the configured PIN to continue."
        "ar" -> "سيتم إيقاف الحماية مؤقتاً لمدة ٣ دقائق ثم تعود تلقائياً. أدخل رمز الحماية للمتابعة."
        else -> "پاراستن بۆ ماوەی ٣ خولەک دەوەستێت و خۆکارانە دەگەڕێتەوە. PIN ـی پاراستن بنووسە."
    }
    AuthMode.USERNAME_PASSWORD -> when (language) {
        "en" -> "Protection will be paused for 3 minutes and resumes automatically. Enter your username and password to continue."
        "ar" -> "سيتم إيقاف الحماية مؤقتاً لمدة ٣ دقائق ثم تعود تلقائياً. أدخل اسم المستخدم وكلمة المرور للمتابعة."
        else -> "پاراستن بۆ ماوەی ٣ خولەک دەوەستێت و خۆکارانە دەگەڕێتەوە. ناوی بەکارهێنەر و وشەی نهێنی بنووسە."
    }
}

private fun usernameLabel(language: String) = when (language) {
    "en" -> "Username"
    "ar" -> "اسم المستخدم"
    else -> "ناوی بەکارهێنەر"
}

private fun secretLabel(language: String, authMode: AuthMode) = when (authMode) {
    AuthMode.PIN -> when (language) {
        "en" -> "Protection PIN"
        "ar" -> "رمز الحماية"
        else -> "PIN ـی پاراستن"
    }
    AuthMode.USERNAME_PASSWORD -> when (language) {
        "en" -> "Password"
        "ar" -> "كلمة المرور"
        else -> "وشەی نهێنی"
    }
}

private fun wrongCredential(language: String, authMode: AuthMode) = when (authMode) {
    AuthMode.PIN -> when (language) {
        "en" -> "Incorrect PIN. Protection stays enabled."
        "ar" -> "الرمز غير صحيح. تبقى الحماية مفعلة."
        else -> "PIN هەڵەیە. پاراستن چالاک دەمێنێتەوە."
    }
    AuthMode.USERNAME_PASSWORD -> when (language) {
        "en" -> "Incorrect username or password. Protection stays enabled."
        "ar" -> "اسم المستخدم أو كلمة المرور غير صحيحة. تبقى الحماية مفعلة."
        else -> "ناوی بەکارهێنەر یان وشەی نهێنی هەڵەیە. پاراستن چالاک دەمێنێتەوە."
    }
}

private fun confirm(language: String) = when (language) {
    "en" -> "Pause 3 min"
    "ar" -> "إيقاف ٣ دقائق"
    else -> "وەستان ٣ خولەک"
}

private fun cancel(language: String) = when (language) {
    "en" -> "Cancel"
    "ar" -> "إلغاء"
    else -> "پاشگەزبوونەوە"
}
