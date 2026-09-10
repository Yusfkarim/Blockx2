package com.agon.app.settingsprotection.data

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.agon.app.settingsprotection.domain.FailedAttempt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the PIN as a PBKDF2 digest that is additionally sealed with an Android Keystore key
 * before it is written to the encrypted DataStore. The raw PIN is never persisted.
 */
@Singleton
class PinCredentialStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: KeystoreCipherProvider,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val attempts: Flow<List<FailedAttempt>> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { preferences -> decodeAttempts(preferences[KEY_ATTEMPTS]) }

    /** Persists [pin]; returns false when the keystore refuses to seal the digest. */
    suspend fun store(pin: String): Boolean = withContext(Dispatchers.IO) {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val digest = derive(pin, salt) ?: return@withContext false
        val sealed = cipher.encrypt(digest) ?: return@withContext false
        val material = listOf(
            VERSION.toString(),
            Base64.encodeToString(salt, Base64.NO_WRAP),
            ITERATIONS.toString(),
            sealed,
        ).joinToString(SEPARATOR)
        dataStore.edit { it[KEY_MATERIAL] = material }
        true
    }

    /** Constant-time verification of [pin] against the stored digest. */
    suspend fun matches(pin: String): VerificationOutcome = withContext(Dispatchers.IO) {
        val material = read(KEY_MATERIAL) ?: return@withContext VerificationOutcome.NOT_CONFIGURED
        val parts = material.split(SEPARATOR)
        if (parts.size != 4) return@withContext VerificationOutcome.CORRUPTED
        val salt = runCatching { Base64.decode(parts[1], Base64.NO_WRAP) }.getOrNull()
            ?: return@withContext VerificationOutcome.CORRUPTED
        val iterations = parts[2].toIntOrNull() ?: return@withContext VerificationOutcome.CORRUPTED
        val stored = cipher.decrypt(parts[3]) ?: return@withContext VerificationOutcome.CORRUPTED
        val candidate = derive(pin, salt, iterations) ?: return@withContext VerificationOutcome.CORRUPTED
        if (MessageDigest.isEqual(stored, candidate)) VerificationOutcome.MATCH else VerificationOutcome.MISMATCH
    }

    suspend fun isConfigured(): Boolean = read(KEY_MATERIAL) != null

    suspend fun clear() {
        dataStore.edit { it.remove(KEY_MATERIAL) }
    }

    suspend fun appendAttempt(attempt: FailedAttempt) {
        dataStore.edit { preferences ->
            val history = decodeAttempts(preferences[KEY_ATTEMPTS])
            val updated = (listOf(attempt) + history).take(MAX_ATTEMPTS)
            preferences[KEY_ATTEMPTS] = json.encodeToString(updated)
        }
    }

    suspend fun clearAttempts() {
        dataStore.edit { it[KEY_ATTEMPTS] = json.encodeToString(emptyList<FailedAttempt>()) }
    }

    suspend fun currentAttempts(): List<FailedAttempt> =
        runCatching { attempts.first() }.getOrDefault(emptyList())

    private suspend fun read(key: Preferences.Key<String>): String? = runCatching {
        dataStore.data.catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }.first()[key]
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun decodeAttempts(raw: String?): List<FailedAttempt> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<FailedAttempt>>(raw) }.getOrDefault(emptyList())
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray? = runCatching {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, DIGEST_BITS)
        SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }.getOrNull()

    enum class VerificationOutcome { MATCH, MISMATCH, NOT_CONFIGURED, CORRUPTED }

    private companion object {
        val KEY_MATERIAL = stringPreferencesKey("pin_material")
        val KEY_ATTEMPTS = stringPreferencesKey("failed_attempts")
        const val ALGORITHM = "PBKDF2WithHmacSHA1"
        const val ITERATIONS = 100_000
        const val DIGEST_BITS = 256
        const val SALT_LENGTH = 32
        const val SEPARATOR = "|"
        const val VERSION = 1
        const val MAX_ATTEMPTS = 50
    }
}
