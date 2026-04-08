package com.micplugin

import android.app.Application
import com.micplugin.plugin.PluginPathPrefs
import com.micplugin.service.ShizukuManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MicPluginApp : Application() {

    @Inject lateinit var shizukuManager: ShizukuManager
    @Inject lateinit var pluginPathPrefs: PluginPathPrefs

    override fun onCreate() {
        super.onCreate()
        CrashReporter.init(this)
        shizukuManager.init()
        pluginPathPrefs.init(this)
        listOf(
            "plugins/lv2", "plugins/clap", "plugins/vst3",
            "presets", "logs", "crashes"
        ).forEach { dir ->
            val f = java.io.File(filesDir, dir)
            if (!f.exists()) f.mkdirs()
        }
    }
}
