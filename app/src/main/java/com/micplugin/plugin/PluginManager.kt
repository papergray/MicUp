package com.micplugin.plugin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pathPrefs: PluginPathPrefs,
) {
    companion object {
        private const val TAG = "PluginManager"
        const val APK_PLUGIN_ACTION = "com.micplugin.AUDIO_PLUGIN"
    }

    private val _discovered = MutableStateFlow<List<PluginDescriptor>>(emptyList())
    val discovered: StateFlow<List<PluginDescriptor>> = _discovered

    suspend fun scanAll() = withContext(Dispatchers.IO) {
        val results = mutableListOf<PluginDescriptor>()
        results += scanLv2()
        results += scanClap()
        results += scanVst3()
        results += scanApkPlugins()
        _discovered.value = results
        Log.i(TAG, "Scan complete: ${results.size} plugins found")
        Log.i(TAG, "LV2 paths:  ${pathPrefs.allPaths(context, PluginFormat.LV2).map { it.absolutePath }}")
        Log.i(TAG, "CLAP paths: ${pathPrefs.allPaths(context, PluginFormat.CLAP).map { it.absolutePath }}")
        Log.i(TAG, "VST3 paths: ${pathPrefs.allPaths(context, PluginFormat.VST3).map { it.absolutePath }}")
    }

    private fun scanLv2(): List<PluginDescriptor> {
        val found = mutableListOf<PluginDescriptor>()
        for (dir in pathPrefs.allPaths(context, PluginFormat.LV2)) {
            if (!dir.exists()) { Log.d(TAG, "LV2 dir missing: ${dir.absolutePath}"); continue }
            dir.walk()
                .filter { it.extension == "so" || it.extension == "lv2" }
                .forEach { f ->
                    Log.d(TAG, "Found LV2: ${f.absolutePath}")
                    found += PluginDescriptor(
                        id     = "lv2:${f.nameWithoutExtension}",
                        name   = f.nameWithoutExtension.replace("_", " "),
                        format = PluginFormat.LV2,
                        path   = f.absolutePath,
                        params = parseLv2TtlParams(f.absolutePath),
                    )
                }
        }
        return found
    }

    private fun scanClap(): List<PluginDescriptor> {
        val found = mutableListOf<PluginDescriptor>()
        for (dir in pathPrefs.allPaths(context, PluginFormat.CLAP)) {
            if (!dir.exists()) { Log.d(TAG, "CLAP dir missing: ${dir.absolutePath}"); continue }
            dir.walk()
                .filter { it.extension == "clap" || it.extension == "so" }
                .forEach { f ->
                    Log.d(TAG, "Found CLAP: ${f.absolutePath}")
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
        for (dir in pathPrefs.allPaths(context, PluginFormat.VST3)) {
            if (!dir.exists()) { Log.d(TAG, "VST3 dir missing: ${dir.absolutePath}"); continue }
            dir.walk()
                .filter { it.extension == "so" }
                .forEach { f ->
                    Log.d(TAG, "Found VST3: ${f.absolutePath}")
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
        val pm     = context.packageManager
        val intent = Intent(APK_PLUGIN_ACTION)
        val svcs   = pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
        return svcs.map { info ->
            val pkg   = info.serviceInfo.packageName
            val label = pm.getPackageInfo(pkg, 0).applicationInfo
                ?.let { pm.getApplicationLabel(it).toString() } ?: pkg
            PluginDescriptor(id = "apk:$pkg", name = label, format = PluginFormat.APK, path = pkg)
        }
    }

    fun parseApkPluginInfo(json: String): List<PluginParam> {
        return try {
            val obj    = Json.parseToJsonElement(json).jsonObject
            val params = obj["params"]?.jsonArray ?: return emptyList()
            params.map { el ->
                val p = el.jsonObject
                PluginParam(
                    id      = p["id"]?.jsonPrimitive?.int      ?: 0,
                    name    = p["name"]?.jsonPrimitive?.content ?: "Param",
                    min     = p["min"]?.jsonPrimitive?.float    ?: 0f,
                    max     = p["max"]?.jsonPrimitive?.float    ?: 1f,
                    default = p["default"]?.jsonPrimitive?.float ?: 0f,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse plugin info: $e")
            emptyList()
        }
    }

    private fun parseLv2TtlParams(soPath: String): List<PluginParam> {
        val dir = java.io.File(soPath).parent ?: return emptyList()
        val stem = java.io.File(soPath).nameWithoutExtension
        val ttl = listOf("$dir/$stem.ttl", "$dir/manifest.ttl")
            .mapNotNull { p -> java.io.File(p).takeIf { it.exists() }?.readText() }
            .firstOrNull() ?: return emptyList()

        val params = mutableListOf<PluginParam>()
        var inPort = false
        var idx = -1; var nm = ""; var mn = 0f; var mx = 1f; var df = 0f
        var isCtrl = false; var isInput = false

        for (line in ttl.lines()) {
            val t = line.trim()
            if (t == "[") {
                if (inPort && idx >= 0 && isCtrl && isInput)
                    params += PluginParam(idx, nm.ifEmpty { "p$idx" }, mn, mx, df)
                inPort = true; idx = -1; nm = ""; mn = 0f; mx = 1f; df = 0f
                isCtrl = false; isInput = false; continue
            }
            if (t.startsWith("]")) {
                if (inPort && idx >= 0 && isCtrl && isInput)
                    params += PluginParam(idx, nm.ifEmpty { "p$idx" }, mn, mx, df)
                inPort = false; continue
            }
            if (!inPort) continue
            if (t.contains("lv2:ControlPort")) isCtrl = true
            if (t.contains("lv2:InputPort")) isInput = true
            if (t.contains("lv2:AudioPort")) isCtrl = false
            t.split("lv2:index").getOrNull(1)?.trim()?.split(Regex("\\s")
                )?.firstOrNull()?.trimEnd(';',',','.')?.toIntOrNull()?.let { idx = it }
            if (nm.isEmpty() && t.contains("lv2:name")) {
                val q = t.indexOf('"'); val q2 = t.lastIndexOf('"')
                if (q >= 0 && q2 > q) nm = t.substring(q + 1, q2)
            }
            t.split("lv2:minimum").getOrNull(1)?.trim()?.split(Regex("\\s")
                )?.firstOrNull()?.trimEnd(';',',','.')?.toFloatOrNull()?.let { mn = it }
            t.split("lv2:maximum").getOrNull(1)?.trim()?.split(Regex("\\s")
                )?.firstOrNull()?.trimEnd(';',',','.')?.toFloatOrNull()?.let { mx = it }
            t.split("lv2:default").getOrNull(1)?.trim()?.split(Regex("\\s")
                )?.firstOrNull()?.trimEnd(';',',','.')?.toFloatOrNull()?.let { df = it }
        }
        Log.i(TAG, "TTL scan: ${params.size} params from $soPath")
        return params
    }


}
