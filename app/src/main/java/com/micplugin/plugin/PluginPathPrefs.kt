package com.micplugin.plugin

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginPathPrefs @Inject constructor() {

    companion object {
        private const val PREFS_NAME = "plugin_paths"
        private const val KEY_LV2    = "lv2_paths"
        private const val KEY_CLAP   = "clap_paths"
        private const val KEY_VST3   = "vst3_paths"
        private const val SEP        = "|||"

        /** Suggested paths users can pick from */
        fun suggestedPaths(): List<String> {
            val sdcard = Environment.getExternalStorageDirectory().absolutePath
            return listOf(
                "$sdcard/MicUp/plugins",
                "$sdcard/MicUp/plugins/lv2",
                "$sdcard/MicUp/plugins/clap",
                "$sdcard/MicUp/plugins/vst3",
                "$sdcard/Android/data/com.micplugin/files/plugins",
                "/sdcard/MicUp/plugins",
                "/data/local/tmp/micplugin_plugins",
            )
        }
    }

    private val _lv2Paths  = MutableStateFlow<List<String>>(emptyList())
    private val _clapPaths = MutableStateFlow<List<String>>(emptyList())
    private val _vst3Paths = MutableStateFlow<List<String>>(emptyList())

    val lv2Paths:  StateFlow<List<String>> = _lv2Paths
    val clapPaths: StateFlow<List<String>> = _clapPaths
    val vst3Paths: StateFlow<List<String>> = _vst3Paths

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _lv2Paths.value  = prefs.getString(KEY_LV2,  "")!!.split(SEP).filter { it.isNotBlank() }
        _clapPaths.value = prefs.getString(KEY_CLAP, "")!!.split(SEP).filter { it.isNotBlank() }
        _vst3Paths.value = prefs.getString(KEY_VST3, "")!!.split(SEP).filter { it.isNotBlank() }
    }

    fun addPath(context: Context, format: PluginFormat, path: String) {
        val trimmed = path.trim().trimEnd('/')
        when (format) {
            PluginFormat.LV2  -> _lv2Paths.value  = (_lv2Paths.value  + trimmed).distinct()
            PluginFormat.CLAP -> _clapPaths.value = (_clapPaths.value + trimmed).distinct()
            PluginFormat.VST3 -> _vst3Paths.value = (_vst3Paths.value + trimmed).distinct()
            else -> return
        }
        save(context)
    }

    fun removePath(context: Context, format: PluginFormat, path: String) {
        when (format) {
            PluginFormat.LV2  -> _lv2Paths.value  = _lv2Paths.value.filter  { it != path }
            PluginFormat.CLAP -> _clapPaths.value = _clapPaths.value.filter { it != path }
            PluginFormat.VST3 -> _vst3Paths.value = _vst3Paths.value.filter { it != path }
            else -> return
        }
        save(context)
    }

    /** All paths for a format — user-added + internal defaults merged */
    fun allPaths(context: Context, format: PluginFormat): List<File> {
        val internalBase = context.filesDir
        val defaultSubs = when (format) {
            PluginFormat.LV2  -> listOf("plugins/lv2",  "plugins")
            PluginFormat.CLAP -> listOf("plugins/clap", "plugins")
            PluginFormat.VST3 -> listOf("plugins/vst3", "plugins")
            else -> emptyList()
        }
        val defaults = defaultSubs.map { File(internalBase, it) }
        val userPaths = when (format) {
            PluginFormat.LV2  -> _lv2Paths.value
            PluginFormat.CLAP -> _clapPaths.value
            PluginFormat.VST3 -> _vst3Paths.value
            else -> emptyList()
        }.map { File(it) }

        return (defaults + userPaths).distinctBy { it.absolutePath }
    }

    private fun save(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_LV2,  _lv2Paths.value.joinToString(SEP))
            putString(KEY_CLAP, _clapPaths.value.joinToString(SEP))
            putString(KEY_VST3, _vst3Paths.value.joinToString(SEP))
            apply()
        }
    }
}
