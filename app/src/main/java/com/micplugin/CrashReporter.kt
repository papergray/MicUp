package com.micplugin

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val dir = File(context.applicationContext.filesDir, "crashes")
        dir.mkdirs()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Write FIRST — process still alive here
            writeCrashFile(thread, throwable)
            // Then let system handle (shows crash dialog / kills process)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashFile(thread: Thread, throwable: Throwable) {
        val ctx = appContext ?: return
        try {
            val dir = File(ctx.filesDir, "crashes")
            dir.mkdirs()

            val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val file = File(dir, "crash_$ts.txt")

            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))

            val text = buildString {
                appendLine("=== MicPlugin Crash Report ===")
                appendLine("Time:    $ts")
                appendLine("Thread:  ${thread.name}")
                appendLine("Device:  ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("ABI:     ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")
                appendLine()
                appendLine("=== Stack Trace ===")
                appendLine(sw.toString())
            }

            // Use FileOutputStream + flush + sync for guaranteed write before process dies
            FileOutputStream(file).use { fos ->
                fos.write(text.toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync()  // Force OS to flush to disk NOW
            }
        } catch (_: Throwable) {
            // Never throw from crash handler
        }
    }

    fun getCrashReports(context: Context): List<File> =
        File(context.filesDir, "crashes")
            .listFiles()
            ?.filter { it.extension == "txt" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun clearAll(context: Context) {
        File(context.filesDir, "crashes").listFiles()?.forEach { it.delete() }
    }
}
