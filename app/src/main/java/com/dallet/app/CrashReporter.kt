package com.dallet.app

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Saves the stack trace of an uncaught exception so the next launch can show it. */
object CrashReporter {
    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_CHARS = 4000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val writer = StringWriter()
                error.printStackTrace(PrintWriter(writer))
                File(appContext.filesDir, FILE_NAME)
                    .writeText("Поток: ${thread.name}\n$writer")
            } catch (_: Throwable) {
                // Never let the reporter hide the original crash.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        return if (file.exists()) file.readText().take(MAX_CHARS) else null
    }

    fun clear(context: Context) {
        File(context.applicationContext.filesDir, FILE_NAME).delete()
    }
}
