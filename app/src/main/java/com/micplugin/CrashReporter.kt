package com.micplugin

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
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
        File(context.applicationContext.filesDir, "crashes").mkdirs()

        val default = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val file = writeCrashFile(thread, throwable)
            if (file != null) shareCrashFile(context.applicationContext, file)
            Thread.sleep(2500) // let share sheet appear before process dies
            default?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashFile(thread: Thread, throwable: Throwable): File? {
        val ctx = appContext ?: return null
        return try {
            val dir = File(ctx.filesDir, "crashes").also { it.mkdirs() }
            val ts  = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val file = File(dir, "crash_$ts.txt")

            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))

            val text = buildString {
                appendLine("=== MicUp Crash Report ===")
                appendLine("Time:    $ts")
                appendLine("Thread:  ${thread.name}")
                appendLine("Device:  ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("ABI:     ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")
                appendLine()
                appendLine(sw.toString())
            }

            FileOutputStream(file).use { fos ->
                fos.write(text.toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync()
            }
            file
        } catch (_: Throwable) { null }
    }

    private fun shareCrashFile(ctx: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "MicUp crash — ${file.nameWithoutExtension}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(Intent.createChooser(share, "Send crash log").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Throwable) {}
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
