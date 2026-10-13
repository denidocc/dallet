package com.dallet.app.backup

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dallet.app.DalletApplication
import com.dallet.app.ledger.BackupCrypto
import com.dallet.app.ledger.Ledger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

data class AutoBackupState(
    val folderUri: String? = null,
    val folderName: String? = null,
    val enabled: Boolean = false,
    val hasPassword: Boolean = false,
    val lastAtMillis: Long = 0L,
    val lastError: Boolean = false
)

/** Keeps the auto-backup password encrypted with a non-exportable Android Keystore key. */
internal object PasswordVault {
    private const val ALIAS = "dallet_autobackup_password"
    private const val PROVIDER = "AndroidKeyStore"

    private fun key() = KeyStore.getInstance(PROVIDER).run {
        load(null)
        (getKey(ALIAS, null) as? javax.crypto.SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }

    fun seal(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(data, Base64.NO_WRAP)
    }

    fun open(blob: String): String? = try {
        val (iv, data) = blob.split(":")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}

object AutoBackup {
    private const val PREFS = "dallet_autobackup"
    private const val WORK_NAME = "auto_backup"
    private const val FILE_PREFIX = "Dallet-auto-"
    private const val KEEP_FILES = 7

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun state(context: Context): AutoBackupState {
        val prefs = prefs(context)
        val uri = prefs.getString("folder", null)
        return AutoBackupState(
            folderUri = uri,
            folderName = uri?.let { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name },
            enabled = prefs.getBoolean("enabled", false) && uri != null,
            hasPassword = prefs.getString("password", null) != null,
            lastAtMillis = prefs.getLong("lastAt", 0L),
            lastError = prefs.getBoolean("lastError", false)
        )
    }

    fun setFolder(context: Context, uri: Uri) {
        prefs(context).edit().putString("folder", uri.toString()).putBoolean("lastError", false).apply()
    }

    /** An empty password means the copies are written unencrypted. */
    fun setPassword(context: Context, password: String) {
        val edit = prefs(context).edit()
        if (password.isEmpty()) edit.remove("password") else edit.putString("password", PasswordVault.seal(password))
        edit.apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("enabled", enabled).apply()
        if (enabled) schedule(context) else WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun schedule(context: Context) {
        if (!state(context).enabled) return
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Writes one copy into the chosen folder and prunes old ones. Returns true on success. */
    suspend fun runNow(context: Context, ledger: Ledger): Boolean {
        val prefs = prefs(context)
        val ok = try {
            val uri = prefs.getString("folder", null) ?: error("no folder")
            val password = prefs.getString("password", null)?.let {
                PasswordVault.open(it) ?: error("password lost")
            }
            val json = ledger.exportBackup()
            val text = if (password != null) BackupCrypto.encrypt(json, password) else json
            withContext(Dispatchers.IO) {
                val dir = DocumentFile.fromTreeUri(context, Uri.parse(uri))
                if (dir == null || !dir.canWrite()) error("folder unavailable")
                val name = FILE_PREFIX + LocalDate.now()
                dir.findFile("$name.json")?.delete()
                val file = dir.createFile("application/json", name) ?: error("cannot create file")
                context.contentResolver.openOutputStream(file.uri, "wt")?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("cannot open file")
                dir.listFiles()
                    .filter { it.name?.startsWith(FILE_PREFIX) == true }
                    .sortedByDescending { it.name }
                    .drop(KEEP_FILES)
                    .forEach { it.delete() }
            }
            true
        } catch (_: Exception) {
            false
        }
        val edit = prefs.edit().putBoolean("lastError", !ok)
        if (ok) edit.putLong("lastAt", System.currentTimeMillis())
        edit.apply()
        return ok
    }
}

class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? DalletApplication ?: return Result.success()
        if (!AutoBackup.state(applicationContext).enabled) return Result.success()
        // A failed run is recorded for the settings screen; the next day's run tries again.
        AutoBackup.runNow(applicationContext, app.ledger)
        return Result.success()
    }
}
