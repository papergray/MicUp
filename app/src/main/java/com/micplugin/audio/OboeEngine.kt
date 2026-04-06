package com.micplugin.audio

import javax.inject.Inject
import javax.inject.Singleton

/**
 * JNI bridge to the C++ AudioEngine / PluginHost.
 * All native methods map 1-to-1 with the extern "C" functions in AudioEngine.cpp.
 */
@Singleton
class OboeEngine @Inject constructor() {

    private var engineHandle: Long = 0L

    init {
        System.loadLibrary("micplugin_native")
        engineHandle = nativeCreate()
    }

    fun start(): Boolean = nativeStart(engineHandle)
    fun stop() = nativeStop(engineHandle)
    fun isValid() = engineHandle != 0L

    /** effectId: 0=Gate 1=EQ 2=Comp 3=Reverb 4=Pitch 99=MasterBypass */
    fun setParam(effectId: Int, paramId: Int, value: Float) =
        nativeSetParam(engineHandle, effectId, paramId, value)

    /** Returns [inputDb, outputDb, gainReductionDb] */
    fun getLevels(): FloatArray = nativeGetLevels(engineHandle)

    /**
     * Load a native plugin (.so/.clap/.lv2-so).
     * @param formatId 0=CLAP, 1=LV2, 2=VST3
     * @return plugin handle, or 0 on failure
     */
    fun loadPlugin(soPath: String, formatId: Int): Long =
        nativeLoadPlugin(engineHandle, soPath, formatId)

    fun unloadPlugin(pluginHandle: Long) =
        nativeUnloadPlugin(engineHandle, pluginHandle)

    fun setPluginParam(pluginHandle: Long, paramId: Int, value: Float) =
        nativeSetPluginParam(engineHandle, pluginHandle, paramId, value)

    fun getSampleRate(): Int = nativeGetSampleRate(engineHandle)
    fun getFramesPerBurst(): Int = nativeGetFramesPerBurst(engineHandle)

    fun destroy() {
        if (engineHandle != 0L) {
            nativeDestroy(engineHandle)
            engineHandle = 0L
        }
    }

    // ── JNI declarations ──────────────────────────────────────────────────────
    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeStart(handle: Long): Boolean
    private external fun nativeStop(handle: Long)
    private external fun nativeSetParam(handle: Long, effectId: Int, paramId: Int, value: Float)
    private external fun nativeGetLevels(handle: Long): FloatArray
    private external fun nativeLoadPlugin(handle: Long, soPath: String, formatId: Int): Long
    private external fun nativeUnloadPlugin(handle: Long, pluginHandle: Long)
    private external fun nativeSetPluginParam(handle: Long, pluginHandle: Long, paramId: Int, value: Float)
    private external fun nativeGetSampleRate(handle: Long): Int
    private external fun nativeGetFramesPerBurst(handle: Long): Int
}
