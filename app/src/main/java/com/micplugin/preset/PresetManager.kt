package com.micplugin.preset

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class EffectState(
    val gateEnabled:   Boolean = true,
    val gateThreshold: Float   = -50f,
    val gateAttack:    Float   = 5f,
    val gateRelease:   Float   = 150f,

    val eqEnabled: Boolean     = true,
    val eqBands:   List<Float> = List(10) { 0f },

    val compEnabled:   Boolean = true,
    val compThreshold: Float   = -18f,
    val compRatio:     Float   = 4f,
    val compAttack:    Float   = 10f,
    val compRelease:   Float   = 100f,
    val compMakeup:    Float   = 0f,

    val reverbEnabled: Boolean = false,
    val reverbMix:     Float   = 0f,
    val reverbRoom:    Float   = 0.5f,
    val reverbDamp:    Float   = 0.5f,

    val pitchEnabled:  Boolean = false,
    val pitchSemitones: Float  = 0f,
)

@Serializable
data class Preset(
    val name:        String,
    val isBuiltIn:   Boolean     = false,
    val effects:     EffectState = EffectState(),
    val pluginPaths: List<String> = emptyList(),
)

@Singleton
class PresetManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "PresetManager"
        val BUILT_IN: List<Preset> = listOf(
            Preset(
                name = "Clean Pass-Through",
                isBuiltIn = true,
                effects = EffectState(
                    gateEnabled  = false,
                    eqEnabled    = false,
                    compEnabled  = false,
                    reverbEnabled = false,
                    pitchEnabled = false,
                ),
            ),
            Preset(
                name = "Podcast Voice",
                isBuiltIn = true,
                effects = EffectState(
                    gateThreshold = -45f,
                    eqEnabled     = true,
                    eqBands       = List(10) { i -> if (i == 6 || i == 7) 3f else 0f }, // +3dB at 2k+4k
                    compEnabled   = true,
                    compThreshold = -18f,
                    compRatio     = 4f,
                ),
            ),
            Preset(
                name = "Deep Radio Voice",
                isBuiltIn = true,
                effects = EffectState(
                    pitchEnabled   = true,
                    pitchSemitones = -2f,
                    eqBands        = List(10) { i -> when(i) { 0 -> -6f; 4 -> 3f; else -> 0f } },
                    reverbEnabled  = true,
                    reverbMix      = 0.15f,
                ),
            ),
            Preset(
                name = "Telephone Effect",
                isBuiltIn = true,
                effects = EffectState(
                    // Bandpass 300Hz–3.4kHz: cut lows + highs
                    eqBands = List(10) { i -> when(i) { 0 -> -12f; 1 -> -8f; 8 -> -10f; 9 -> -15f; else -> 0f } },
                    reverbEnabled = false,
                    compEnabled   = true,
                    compRatio     = 8f,
                    compThreshold = -24f,
                ),
            ),
            Preset(
                name = "Studio Vocal",
                isBuiltIn = true,
                effects = EffectState(
                    gateThreshold = -40f,
                    eqBands       = List(10) { i -> when(i) { 0 -> -4f; 6 -> 2f; 7 -> 1f; else -> 0f } },
                    compEnabled   = true,
                    compThreshold = -20f,
                    compRatio     = 3f,
                    compMakeup    = 2f,
                    reverbEnabled = true,
                    reverbMix     = 0.12f,
                    reverbRoom    = 0.3f,
                ),
            ),
        )
    }

    private val presetsDir = File(context.filesDir, "presets")
    private val json       = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val _presets   = MutableStateFlow(BUILT_IN)
    val presets: StateFlow<List<Preset>> = _presets

    init { presetsDir.mkdirs() }

    suspend fun loadAll() = withContext(Dispatchers.IO) {
        val user = presetsDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { f ->
                try { json.decodeFromString<Preset>(f.readText()) }
                catch (e: Exception) { Log.e(TAG, "Failed to load preset ${f.name}: $e"); null }
            } ?: emptyList()
        _presets.value = BUILT_IN + user
    }

    suspend fun save(preset: Preset) = withContext(Dispatchers.IO) {
        val file = File(presetsDir, "${preset.name.replace(" ", "_")}.json")
        file.writeText(json.encodeToString(preset))
        loadAll()
    }

    suspend fun delete(preset: Preset) = withContext(Dispatchers.IO) {
        if (preset.isBuiltIn) return@withContext
        File(presetsDir, "${preset.name.replace(" ", "_")}.json").delete()
        loadAll()
    }
}
