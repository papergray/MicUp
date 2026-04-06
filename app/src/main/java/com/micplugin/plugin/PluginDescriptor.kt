package com.micplugin.plugin

import java.util.UUID

enum class PluginFormat {
    LV2, CLAP, VST3, APK;

    val displayName: String get() = name
    val colorHex: Long get() = when (this) {
        LV2  -> 0xFF00BFA5L
        CLAP -> 0xFF69F0AEL
        VST3 -> 0xFFAA00FFL
        APK  -> 0xFFFF6D00L
    }
}

data class PluginParam(
    val id: Int,
    val name: String,
    val min: Float,
    val max: Float,
    val default: Float,
    val type: ParamType = ParamType.FLOAT,
)

enum class ParamType { FLOAT, BOOL, ENUM }

data class PluginDescriptor(
    val id: String,                    // URI for LV2, CLAP ID, UUID for APK
    val name: String,
    val author: String = "",
    val version: String = "1.0",
    val format: PluginFormat,
    val path: String,                  // .so / .clap / bundle dir / package name
    val params: List<PluginParam> = emptyList(),
    val latencySamples: Int = 0,
    val description: String = "",
)

data class PluginSlot(
    val id: UUID = UUID.randomUUID(),
    val descriptor: PluginDescriptor,
    val nativeHandle: Long = 0L,       // handle from OboeEngine, 0 for APK
    var enabled: Boolean = true,
    var position: Int = 0,
    var paramValues: Map<Int, Float> = emptyMap(),
)

/** Runtime plugin instance abstraction — bridges Kotlin ↔ C++ or AIDL */
abstract class ExternalPlugin(val descriptor: PluginDescriptor) {
    abstract fun setParam(id: Int, value: Float)
    abstract fun getParam(id: Int): Float
    abstract fun release()
}
