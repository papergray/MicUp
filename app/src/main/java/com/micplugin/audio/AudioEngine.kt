package com.micplugin.audio

import android.util.Log
import com.micplugin.plugin.PluginChain
import com.micplugin.plugin.PluginFormat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AudioLevels(
    val inputDb: Float = -100f,
    val outputDb: Float = -100f,
    val gainReductionDb: Float = 0f,
)

data class EngineStatus(
    val isRunning: Boolean = false,
    val sampleRate: Int = 48000,
    val framesPerBurst: Int = 128,
    val latencyMs: Float = 0f,
)

@Singleton
class AudioEngine @Inject constructor(
    private val oboe: OboeEngine,
    val pluginChain: PluginChain,
) {
    companion object { private const val TAG = "AudioEngine" }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _levels = MutableStateFlow(AudioLevels())
    val levels: StateFlow<AudioLevels> = _levels

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status

    private var meterJob: Job? = null

    fun start(): Boolean {
        val ok = oboe.start()
        if (ok) {
            _status.value = EngineStatus(
                isRunning      = true,
                sampleRate     = oboe.getSampleRate(),
                framesPerBurst = oboe.getFramesPerBurst(),
                latencyMs      = oboe.getFramesPerBurst() * 1000f / oboe.getSampleRate() * 2f,
            )
            startMeterPolling()
            Log.i(TAG, "Engine started at ${oboe.getSampleRate()}Hz / ${oboe.getFramesPerBurst()} frames")
        }
        return ok
    }

    fun stop() {
        meterJob?.cancel()
        oboe.stop()
        _status.value = EngineStatus(isRunning = false)
        _levels.value = AudioLevels()
    }

    fun setMasterBypass(bypass: Boolean) = oboe.setParam(99, 0, if (bypass) 1f else 0f)

    // ── Built-in effect param setters (all RT-safe via atomic stores) ──────────
    fun setGateThreshold(db: Float)   = oboe.setParam(0, 0, db)
    fun setGateAttack(ms: Float)      = oboe.setParam(0, 1, ms)
    fun setGateRelease(ms: Float)     = oboe.setParam(0, 2, ms)
    fun setGateEnabled(on: Boolean)   = oboe.setParam(0, 3, if (on) 1f else 0f)

    fun setEqBand(band: Int, db: Float) = oboe.setParam(1, band, db)
    fun setEqEnabled(on: Boolean)       = oboe.setParam(1, 10, if (on) 1f else 0f)

    fun setCompThreshold(db: Float)   = oboe.setParam(2, 0, db)
    fun setCompRatio(ratio: Float)    = oboe.setParam(2, 1, ratio)
    fun setCompAttack(ms: Float)      = oboe.setParam(2, 2, ms)
    fun setCompRelease(ms: Float)     = oboe.setParam(2, 3, ms)
    fun setCompMakeup(db: Float)      = oboe.setParam(2, 4, db)
    fun setCompEnabled(on: Boolean)   = oboe.setParam(2, 5, if (on) 1f else 0f)

    fun setReverbMix(mix: Float)      = oboe.setParam(3, 0, mix)
    fun setReverbRoom(size: Float)    = oboe.setParam(3, 1, size)
    fun setReverbDamp(damp: Float)    = oboe.setParam(3, 2, damp)
    fun setReverbEnabled(on: Boolean) = oboe.setParam(3, 3, if (on) 1f else 0f)

    fun setPitchSemitones(st: Float)  = oboe.setParam(4, 0, st)
    fun setPitchEnabled(on: Boolean)  = oboe.setParam(4, 1, if (on) 1f else 0f)

    // ── Plugin loading ────────────────────────────────────────────────────────
    fun loadNativePlugin(soPath: String, format: PluginFormat): Long {
        val formatId = when (format) {
            PluginFormat.CLAP -> 0
            PluginFormat.LV2  -> 1
            PluginFormat.VST3 -> 2
            else              -> return 0L
        }
        return oboe.loadPlugin(soPath, formatId)
    }

    fun unloadNativePlugin(handle: Long) = oboe.unloadPlugin(handle)

    fun setNativePluginParam(handle: Long, paramId: Int, value: Float) =
        oboe.setPluginParam(handle, paramId, value)

    // ── Meter polling (50ms cadence, off audio thread) ────────────────────────
    private fun startMeterPolling() {
        meterJob = scope.launch {
            while (isActive) {
                val raw = oboe.getLevels()
                _levels.value = AudioLevels(
                    inputDb        = raw.getOrElse(0) { -100f },
                    outputDb       = raw.getOrElse(1) { -100f },
                    gainReductionDb = raw.getOrElse(2) { 0f },
                )
                delay(50)
            }
        }
    }
}
