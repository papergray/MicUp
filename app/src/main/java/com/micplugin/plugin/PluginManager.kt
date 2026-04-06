package com.micplugin.plugin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.micplugin.IAudioPlugin
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "PluginManager"
        const val APK_PLUGIN_ACTION = "com.micplugin.AUDIO_PLUGIN"

        private val LV2_DIRS  = listOf("plugins/lv2")
        private val CLAP_DIRS = listOf("plugins/clap")
        private val VST3_DIRS = listOf("plugins/vst3")
    }

    private val _discovered = MutableStateFlow<List<PluginDescriptor>>(emptyList())
    val discovered: StateFlow<List<PluginDescriptor>> = _discovered

    /** Scan all plugin directories + installed APKs. Call from a coroutine. */
    suspend fun scanAll() = withContext(Dispatchers.IO) {
        val results = mutableListOf<PluginDescriptor>()
        results += scanLv2()
        results += scanClap()
        results += scanVst3()
        results += scanApkPlugins()
        _discovered.value = results
        Log.i(TAG, "Scan complete: ${results.size} plugins found")
    }

    private fun scanLv2(): List<PluginDescriptor> {
        val found = mutableListOf<PluginDescriptor>()
        for (dir in LV2_DIRS) {
            val base = File(context.filesDir, dir)
            if (!base.exists()) continue
            base.walk()
                .filter { it.extension == "so" || it.extension == "lv2" }
                .forEach { f ->
                    found += PluginDescriptor(
                        id     = "lv2:${f.nameWithoutExtension}",
                        name   = f.nameWithoutExtension.replace("_", " "),
                        format = PluginFormat.LV2,
                        path   = f.absolutePath,
                        params = emptyList(),
                    )
                }
        }
        return found
    }

    private fun scanClap(): List<PluginDescriptor> {
        val found = mutableListOf<PluginDescriptor>()
        for (dir in CLAP_DIRS) {
            val base = File(context.filesDir, dir)
            if (!base.exists()) continue
            base.walk()
                .filter { it.extension == "clap" || it.extension == "so" }
                .forEach { f ->
                    found += PluginDescriptor(
                        id     = "clap:${f.nameWithoutExtension}",
                        name   = f.nameWithoutExtension.replace("_", " "),
                        format = PluginFormat.CLAP,
                        path   = f.absolutePath,
                        params = emptyList(),
                    )
                }
        }
        return found
    }

    private fun scanVst3(): List<PluginDescriptor> {
        val found = mutableListOf<PluginDescriptor>()
        for (dir in VST3_DIRS) {
            val base = File(context.filesDir, dir)
            if (!base.exists()) continue
            base.walk()
                .filter { it.extension == "so" }
                .forEach { f ->
                    found += PluginDescriptor(
                        id          = "vst3:${f.nameWithoutExtension}",
                        name        = f.nameWithoutExtension.replace("_", " "),
                        format      = PluginFormat.VST3,
                        path        = f.absolutePath,
                        description = "VST3 (experimental)",
                    )
                }
        }
        return found
    }

    fun scanApkPlugins(): List<PluginDescriptor> {
        val pm   = context.packageManager
        val intent = Intent(APK_PLUGIN_ACTION)
        val svcs = pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
        return svcs.map { info ->
            val pkg  = info.serviceInfo.packageName
            val label = pm.getPackageInfo(pkg, 0).applicationInfo
                ?.let { pm.getApplicationLabel(it).toString() } ?: pkg
            PluginDescriptor(
                id     = "apk:$pkg",
                name   = label,
                format = PluginFormat.APK,
                path   = pkg, // package name used as path for APK plugins
            )
        }
    }

    /** Parse plugin params from JSON returned by APK IAudioPlugin.getPluginInfo() */
    fun parseApkPluginInfo(json: String): List<PluginParam> {
        return try {
            val obj    = Json.parseToJsonElement(json).jsonObject
            val params = obj["params"]?.jsonArray ?: return emptyList()
            params.map { el ->
                val p = el.jsonObject
                PluginParam(
                    id      = p["id"]?.jsonPrimitive?.int     ?: 0,
                    name    = p["name"]?.jsonPrimitive?.content ?: "Param",
                    min     = p["min"]?.jsonPrimitive?.float   ?: 0f,
                    max     = p["max"]?.jsonPrimitive?.float   ?: 1f,
                    default = p["default"]?.jsonPrimitive?.float ?: 0f,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse plugin info: $e")
            emptyList()
        }
    }
}
