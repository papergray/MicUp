package com.micplugin

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {

    fun init(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashFile(appContext, thread, throwable)
            } catch (_: Exception) {}
            // Still call original handler (shows system crash dialog)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashFile(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, "crashes")
        dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(dir, "crash_$timestamp.txt")

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        file.writeText(buildString {
            appendLine("=== MicPlugin Crash Report ===")
            appendLine("Time:    $timestamp")
            appendLine("Thread:  ${thread.name}")
            appendLine("Device:  ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABI:     ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")
            appendLine()
            appendLine("=== Stack Trace ===")
            appendLine(sw.toString())
        })
    }

    /** Returns all crash reports sorted newest first */
    fun getCrashReports(context: Context): List<File> {
        val dir = File(context.filesDir, "crashes")
        return dir.listFiles()
            ?.filter { it.extension == "txt" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun clearAll(context: Context) {
        File(context.filesDir, "crashes").listFiles()?.forEach { it.delete() }
    }
}
