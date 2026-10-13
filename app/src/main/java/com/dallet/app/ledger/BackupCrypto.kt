package com.dallet.app.ledger

import org.json.JSONException
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : Exception("Неверный пароль или файл повреждён")

/**
 * Password protection for a backup file. The plain backup JSON is wrapped into an envelope:
 * AES-256-GCM with a key derived from the password by PBKDF2-HMAC-SHA256 (random salt and IV).
 */
object BackupCrypto {
    private const val APP_MARKER = "dallet"
    private const val ITERATIONS = 200_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    fun isEncrypted(text: String): Boolean = try {
        val root = JSONObject(text)
        root.optString("app") == APP_MARKER && root.optBoolean("encrypted", false)
    } catch (_: JSONException) {
        false
    }

    fun encrypt(plain: String, password: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, ITERATIONS), GCMParameterSpec(TAG_BITS, iv))
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getEncoder()
        return JSONObject()
            .put("app", APP_MARKER)
            .put("encrypted", true)
            .put("version", 1)
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", ITERATIONS)
            .put("salt", encoder.encodeToString(salt))
            .put("iv", encoder.encodeToString(iv))
            .put("data", encoder.encodeToString(data))
            .toString()
    }

    /** Returns the plain backup JSON; throws [WrongPasswordException] for a bad password or a damaged file. */
    fun decrypt(envelope: String, password: String): String {
        try {
            val root = JSONObject(envelope)
            val decoder = Base64.getDecoder()
            val iterations = root.getInt("iterations")
            if (iterations !in 1_000..2_000_000) throw InvalidBackupException("Файл не похож на резервную копию Dallet")
            val salt = decoder.decode(root.getString("salt"))
            val iv = decoder.decode(root.getString("iv"))
            val data = decoder.decode(root.getString("data"))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            return String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (error: GeneralSecurityException) {
            throw WrongPasswordException()
        } catch (error: JSONException) {
            throw InvalidBackupException("Файл не похож на резервную копию Dallet")
        } catch (error: IllegalArgumentException) {
            throw InvalidBackupException("Файл не похож на резервную копию Dallet")
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }
}
