package com.dallet.app.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

interface SecurityStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun getBoolean(key: String): Boolean
    fun putBoolean(key: String, value: Boolean)
}

class PinLock(private val storage: SecurityStorage) {

    val isEnabled: Boolean
        get() = storage.getString(KEY_HASH) != null && storage.getString(KEY_SALT) != null

    val biometricEnabled: Boolean
        get() = isEnabled && storage.getBoolean(KEY_BIOMETRIC)

    fun setPin(pin: String) {
        require(isValidPin(pin)) { "PIN must be $PIN_LENGTH digits" }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        storage.putString(KEY_SALT, Base64.getEncoder().encodeToString(salt))
        storage.putString(KEY_HASH, Base64.getEncoder().encodeToString(hash(pin, salt)))
    }

    fun verify(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        val salt = storage.getString(KEY_SALT)?.let { Base64.getDecoder().decode(it) }
            ?: return false
        val expected = storage.getString(KEY_HASH)?.let { Base64.getDecoder().decode(it) }
            ?: return false
        return MessageDigest.isEqual(hash(pin, salt), expected)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        storage.putBoolean(KEY_BIOMETRIC, enabled && isEnabled)
    }

    fun disable() {
        storage.putString(KEY_HASH, null)
        storage.putString(KEY_SALT, null)
        storage.putBoolean(KEY_BIOMETRIC, false)
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    companion object {
        const val PIN_LENGTH = 4
        private const val SALT_BYTES = 16
        private const val ITERATIONS = 60_000
        private const val KEY_BITS = 256
        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_BIOMETRIC = "biometric"

        fun isValidPin(pin: String): Boolean =
            pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }
    }
}
