package com.micplugin.plugin

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File

data class ImportResult(
    val success: Boolean,
    val pluginDescriptor: PluginDescriptor? = null,
    val error: String = "",
)

object PluginImporter {

    private const val TAG = "PluginImporter"

    /**
     * Called when the user opens a plugin file from a file manager.
     * Copies it into the correct internal plugins dir and returns a descriptor.
     */
    fun importFromUri(context: Context, uri: Uri): ImportResult {
        return try {
            val fileName = resolveFileName(context, uri)
                ?: return ImportResult(false, error = "Could not read file name")

            val format = detectFormat(fileName)
                ?: return ImportResult(false, error = "Unknown plugin format: $fileName\nSupported: .so .clap .lv2")

            val destDir = destDir(context, format).also { it.mkdirs() }
            val destFile = File(destDir, fileName)

            // Copy bytes
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return ImportResult(false, error = "Could not open file")

            Log.i(TAG, "Imported $fileName → ${destFile.absolutePath}")

            val descriptor = PluginDescriptor(
                id     = "${format.name.lowercase()}:${destFile.nameWithoutExtension}",
                name   = destFile.nameWithoutExtension.replace("_", " ").replace("-", " "),
                format = format,
                path   = destFile.absolutePath,
            )
            ImportResult(true, descriptor)

        } catch (e: Exception) {
            Log.e(TAG, "Import failed: $e")
            ImportResult(false, error = e.message ?: "Unknown error")
        }
    }

    private fun resolveFileName(context: Context, uri: Uri): String? {
        // Try content resolver display name first
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (col >= 0 && cursor.moveToFirst()) return cursor.getString(col)
            }
        }
        // Fall back to path segment
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun detectFormat(fileName: String): PluginFormat? = when {
        fileName.endsWith(".clap") -> PluginFormat.CLAP
        fileName.endsWith(".lv2")  -> PluginFormat.LV2
        fileName.endsWith(".so")   -> guessFormatFromSoName(fileName)
        else -> null
    }

    /** Best-effort guess from .so filename conventions */
    private fun guessFormatFromSoName(name: String): PluginFormat = when {
        name.contains("vst3", ignoreCase = true) -> PluginFormat.VST3
        name.contains("lv2",  ignoreCase = true) -> PluginFormat.LV2
        name.contains("clap", ignoreCase = true) -> PluginFormat.CLAP
        else -> PluginFormat.VST3  // default to VST3 for generic .so
    }

    private fun destDir(context: Context, format: PluginFormat): File {
        val sub = when (format) {
            PluginFormat.LV2  -> "plugins/lv2"
            PluginFormat.CLAP -> "plugins/clap"
            PluginFormat.VST3 -> "plugins/vst3"
            else              -> "plugins"
        }
        return File(context.filesDir, sub)
    }
}
