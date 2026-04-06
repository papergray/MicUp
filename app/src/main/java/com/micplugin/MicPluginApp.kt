package com.micplugin

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MicPluginApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Create plugin scan directories on first launch
        listOf(
            "plugins/lv2", "plugins/clap", "plugins/vst3",
            "presets", "logs"
        ).forEach { dir ->
            val f = java.io.File(filesDir, dir)
            if (!f.exists()) f.mkdirs()
        }
    }
}
