package com.micplugin

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MicPluginApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.init(this)
        listOf(
            "plugins/lv2", "plugins/clap", "plugins/vst3",
            "presets", "logs", "crashes"
        ).forEach { dir ->
            val f = java.io.File(filesDir, dir)
            if (!f.exists()) f.mkdirs()
        }
    }
}
