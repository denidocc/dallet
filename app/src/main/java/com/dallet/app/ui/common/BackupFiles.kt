package com.dallet.app.ui.common

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BackupFiles {
    suspend fun write(context: Context, uri: Uri, text: String) {
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: error("cannot open file for writing")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        }
    }

    suspend fun writeBytes(context: Context, uri: Uri, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: error("cannot open file for writing")
            stream.use { it.write(bytes) }
        }
    }

    suspend fun read(context: Context, uri: Uri): String =
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(uri)
                ?: error("cannot open file for reading")
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
}
