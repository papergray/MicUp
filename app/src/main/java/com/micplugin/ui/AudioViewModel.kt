package com.micplugin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micplugin.audio.AudioEngine
import com.micplugin.audio.AudioLevels
import com.micplugin.plugin.*
import com.micplugin.preset.EffectState
import com.micplugin.preset.Preset
import com.micplugin.preset.PresetManager
import com.micplugin.plugin.PluginPathPrefs
import com.micplugin.service.ShizukuManager
import com.micplugin.service.SoftwareLoopback
import com.micplugin.service.ShizukuState
import com.micplugin.service.VirtualMicService
import com.micplugin.service.VirtualMicTier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class GateState(
    val enabled: Boolean = true,
    val thresholdDb: Float = -50f,
    val attackMs: Float = 5f,
    val releaseMs: Float = 150f,
)

data class EqState(
    val enabled: Boolean = true,
    val bands: List<Float> = List(10) { 0f },
)

data class CompState(
    val enabled: Boolean = true,
    val thresholdDb: Float = -18f,
    val ratio: Float = 4f,
    val attackMs: Float = 10f,
    val releaseMs: Float = 100f,
    val makeupDb: Float = 0f,
)

data class ReverbState(
    val enabled: Boolean = false,
    val mix: Float = 0f,
    val roomSize: Float = 0.5f,
    val damping: Float = 0.5f,
)

data class PitchState(
    val enabled: Boolean = false,
    val semitones: Float = 0f,
)

@HiltViewModel
class AudioViewModel @Inject constructor(
    private val audioEngine: AudioEngine,
    private val pluginManager: PluginManager,
    private val presetManager: PresetManager,
    private val virtualMicService: VirtualMicService,
    private val shizukuManager: ShizukuManager,
    private val pluginPathPrefs: PluginPathPrefs,
) : ViewModel() {

    val levels:      StateFlow<AudioLevels>    = audioEngine.levels
    val engineStatus = audioEngine.status
    val pluginSlots: StateFlow<List<PluginSlot>> = audioEngine.pluginChain.slots
    val discovered:  StateFlow<List<PluginDescriptor>> = pluginManager.discovered
    val presets:     StateFlow<List<Preset>>   = presetManager.presets
    val virtualMicTier: StateFlow<VirtualMicTier> = virtualMicService.activeTier
    val shizukuState: StateFlow<ShizukuState> = shizukuManager.state

    private val _gateState   = MutableStateFlow(GateState())
    private val _eqState     = MutableStateFlow(EqState())
    private val _compState   = MutableStateFlow(CompState())
    private val _reverbState = MutableStateFlow(ReverbState())
    private val _pitchState  = MutableStateFlow(PitchState())
    private val _masterBypass = MutableStateFlow(false)

    val gateState:    StateFlow<GateState>    = _gateState
    val eqState:      StateFlow<EqState>      = _eqState
    val compState:    StateFlow<CompState>    = _compState
    val reverbState:  StateFlow<ReverbState>  = _reverbState
    val pitchState:   StateFlow<PitchState>   = _pitchState
    val masterBypass: StateFlow<Boolean>      = _masterBypass

    private val _selectedPreset = MutableStateFlow<Preset?>(null)
    val selectedPreset: StateFlow<Preset?> = _selectedPreset

    init {
        viewModelScope.launch { presetManager.loadAll() }
        viewModelScope.launch { pluginManager.scanAll() }
    }

    // ── Engine control ─────────────────────────────────────────────────────────
    fun toggleEngine() {
        if (engineStatus.value.isRunning) audioEngine.stop()
        else audioEngine.start()
    }

    fun setMasterBypass(bypass: Boolean) {
        _masterBypass.value = bypass
        audioEngine.setMasterBypass(bypass)
    }

    // ── Gate ──────────────────────────────────────────────────────────────────
    fun setGateEnabled(on: Boolean) {
        _gateState.value = _gateState.value.copy(enabled = on)
        audioEngine.setGateEnabled(on)
    }
    fun setGateThreshold(db: Float) {
        _gateState.value = _gateState.value.copy(thresholdDb = db)
        audioEngine.setGateThreshold(db)
    }
    fun setGateAttack(ms: Float) {
        _gateState.value = _gateState.value.copy(attackMs = ms)
        audioEngine.setGateAttack(ms)
    }
    fun setGateRelease(ms: Float) {
        _gateState.value = _gateState.value.copy(releaseMs = ms)
        audioEngine.setGateRelease(ms)
    }

    // ── EQ ────────────────────────────────────────────────────────────────────
    fun setEqEnabled(on: Boolean) {
        _eqState.value = _eqState.value.copy(enabled = on)
        audioEngine.setEqEnabled(on)
    }
    fun setEqBand(band: Int, db: Float) {
        val bands = _eqState.value.bands.toMutableList()
        bands[band] = db
        _eqState.value = _eqState.value.copy(bands = bands)
        audioEngine.setEqBand(band, db)
    }

    // ── Compressor ────────────────────────────────────────────────────────────
    fun setCompEnabled(on: Boolean) {
        _compState.value = _compState.value.copy(enabled = on)
        audioEngine.setCompEnabled(on)
    }
    fun setCompThreshold(db: Float) {
        _compState.value = _compState.value.copy(thresholdDb = db)
        audioEngine.setCompThreshold(db)
    }
    fun setCompRatio(ratio: Float) {
        _compState.value = _compState.value.copy(ratio = ratio)
        audioEngine.setCompRatio(ratio)
    }
    fun setCompAttack(ms: Float) {
        _compState.value = _compState.value.copy(attackMs = ms)
        audioEngine.setCompAttack(ms)
    }
    fun setCompRelease(ms: Float) {
        _compState.value = _compState.value.copy(releaseMs = ms)
        audioEngine.setCompRelease(ms)
    }
    fun setCompMakeup(db: Float) {
        _compState.value = _compState.value.copy(makeupDb = db)
        audioEngine.setCompMakeup(db)
    }

    // ── Reverb ────────────────────────────────────────────────────────────────
    fun setReverbEnabled(on: Boolean) {
        _reverbState.value = _reverbState.value.copy(enabled = on)
        audioEngine.setReverbEnabled(on)
    }
    fun setReverbMix(mix: Float) {
        _reverbState.value = _reverbState.value.copy(mix = mix)
        audioEngine.setReverbMix(mix)
    }
    fun setReverbRoom(size: Float) {
        _reverbState.value = _reverbState.value.copy(roomSize = size)
        audioEngine.setReverbRoom(size)
    }
    fun setReverbDamp(d: Float) {
        _reverbState.value = _reverbState.value.copy(damping = d)
        audioEngine.setReverbDamp(d)
    }

    // ── Pitch ─────────────────────────────────────────────────────────────────
    fun setPitchEnabled(on: Boolean) {
        _pitchState.value = _pitchState.value.copy(enabled = on)
        audioEngine.setPitchEnabled(on)
    }
    fun setPitchSemitones(st: Float) {
        _pitchState.value = _pitchState.value.copy(semitones = st)
        audioEngine.setPitchSemitones(st)
    }

    // ── Plugin chain ──────────────────────────────────────────────────────────
    fun addPlugin(descriptor: PluginDescriptor) {
        viewModelScope.launch {
            val handle = if (descriptor.format != PluginFormat.APK)
                audioEngine.loadNativePlugin(descriptor.path, descriptor.format) else 0L
            val slot = PluginSlot(descriptor = descriptor, nativeHandle = handle)
            audioEngine.pluginChain.addPlugin(slot)
        }
    }

    fun removePlugin(id: UUID) {
        val slot = audioEngine.pluginChain.getSlot(id) ?: return
        if (slot.nativeHandle != 0L) audioEngine.unloadNativePlugin(slot.nativeHandle)
        audioEngine.pluginChain.removePlugin(id)
    }

    fun togglePlugin(id: UUID) = audioEngine.pluginChain.toggleEnabled(id)

    fun reorderPlugin(from: Int, to: Int) = audioEngine.pluginChain.reorder(from, to)

    fun setPluginParam(slotId: UUID, paramId: Int, value: Float) {
        val slot = audioEngine.pluginChain.getSlot(slotId) ?: return
        if (slot.nativeHandle != 0L)
            audioEngine.setNativePluginParam(slot.nativeHandle, paramId, value)
        audioEngine.pluginChain.updateParam(slotId, paramId, value)
    }

    // ── Presets ───────────────────────────────────────────────────────────────
    fun applyPreset(preset: Preset) {
        _selectedPreset.value = preset
        val e = preset.effects
        setGateEnabled(e.gateEnabled);     setGateThreshold(e.gateThreshold)
        setGateAttack(e.gateAttack);       setGateRelease(e.gateRelease)
        setEqEnabled(e.eqEnabled);         e.eqBands.forEachIndexed { i, db -> setEqBand(i, db) }
        setCompEnabled(e.compEnabled);     setCompThreshold(e.compThreshold)
        setCompRatio(e.compRatio);         setCompAttack(e.compAttack)
        setCompRelease(e.compRelease);     setCompMakeup(e.compMakeup)
        setReverbEnabled(e.reverbEnabled); setReverbMix(e.reverbMix)
        setReverbRoom(e.reverbRoom);       setReverbDamp(e.reverbDamp)
        setPitchEnabled(e.pitchEnabled);   setPitchSemitones(e.pitchSemitones)
        _gateState.value   = GateState(e.gateEnabled, e.gateThreshold, e.gateAttack, e.gateRelease)
        _eqState.value     = EqState(e.eqEnabled, e.eqBands)
        _compState.value   = CompState(e.compEnabled, e.compThreshold, e.compRatio, e.compAttack, e.compRelease, e.compMakeup)
        _reverbState.value = ReverbState(e.reverbEnabled, e.reverbMix, e.reverbRoom, e.reverbDamp)
        _pitchState.value  = PitchState(e.pitchEnabled, e.pitchSemitones)
    }

    fun savePreset(name: String) {
        val preset = Preset(
            name    = name,
            effects = EffectState(
                gateEnabled   = _gateState.value.enabled,
                gateThreshold = _gateState.value.thresholdDb,
                gateAttack    = _gateState.value.attackMs,
                gateRelease   = _gateState.value.releaseMs,
                eqEnabled     = _eqState.value.enabled,
                eqBands       = _eqState.value.bands,
                compEnabled   = _compState.value.enabled,
                compThreshold = _compState.value.thresholdDb,
                compRatio     = _compState.value.ratio,
                compAttack    = _compState.value.attackMs,
                compRelease   = _compState.value.releaseMs,
                compMakeup    = _compState.value.makeupDb,
                reverbEnabled = _reverbState.value.enabled,
                reverbMix     = _reverbState.value.mix,
                reverbRoom    = _reverbState.value.roomSize,
                reverbDamp    = _reverbState.value.damping,
                pitchEnabled  = _pitchState.value.enabled,
                pitchSemitones = _pitchState.value.semitones,
            ),
        )
        viewModelScope.launch { presetManager.save(preset) }
    }

    fun rescan() { viewModelScope.launch { pluginManager.scanAll() } }

    fun requestShizukuPermission() { shizukuManager.requestPermission() }

    val lv2Paths  = pluginPathPrefs.lv2Paths
    val clapPaths = pluginPathPrefs.clapPaths
    val vst3Paths = pluginPathPrefs.vst3Paths

    fun addPluginPath(context: android.content.Context, format: com.micplugin.plugin.PluginFormat, path: String) {
        pluginPathPrefs.addPath(context, format, path)
    }
    fun removePluginPath(context: android.content.Context, format: com.micplugin.plugin.PluginFormat, path: String) {
        pluginPathPrefs.removePath(context, format, path)
    }
    fun allPluginPaths(context: android.content.Context, format: com.micplugin.plugin.PluginFormat) =
        pluginPathPrefs.allPaths(context, format)


    private val _monitoringEnabled = MutableStateFlow(true)
    val monitoringEnabled: kotlinx.coroutines.flow.StateFlow<Boolean> = _monitoringEnabled

    fun setMonitoring(enabled: Boolean) {
        _monitoringEnabled.value = enabled
        audioEngine.setMonitoring(enabled)
        // FIX #3: mute raw mic sidetone when monitoring ON so no dual-voice
        SoftwareLoopback.setMicrophoneSidetone(mute = enabled)
    }


    fun getPluginParamsJson(nativeHandle: Long): String =
        audioEngine.getPluginParamsJson(nativeHandle)

}
