package com.agon.app.settingsprotection.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps AES-256/GCM keys that never leave the hardware backed Android Keystore.
 * Cipher text layout is `iv || payload`, Base64 encoded for preference storage.
 */
@Singleton
class KeystoreCipherProvider @Inject constructor() {

    private val lock = Any()

    private fun secretKey(): SecretKey = synchronized(lock) {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        generator.generateKey()
    }

    /** Encrypts [payload], returning null when the platform keystore is unavailable. */
    fun encrypt(payload: ByteArray): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(payload)
        val iv = cipher.iv
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        Base64.encodeToString(combined, Base64.NO_WRAP)
    }.getOrNull()

    /** Decrypts [encoded], returning null when the key was invalidated or data is corrupt. */
    fun decrypt(encoded: String): ByteArray? = runCatching {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        if (combined.size <= IV_LENGTH) return null
        val iv = combined.copyOfRange(0, IV_LENGTH)
        val payload = combined.copyOfRange(IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        cipher.doFinal(payload)
    }.getOrNull()

    /** Drops the key, invalidating every value that was encrypted with it. */
    fun reset() = synchronized(lock) {
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }
        Unit
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "family_shield_settings_protection"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE = 256
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
