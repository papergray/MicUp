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

    private var libraryLoaded = false

    init {
        try {
            System.loadLibrary("micplugin_native")
            libraryLoaded = true
            engineHandle = nativeCreate()
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("OboeEngine", "Failed to load native library: $e")
            engineHandle = 0L
        } catch (e: Exception) {
            android.util.Log.e("OboeEngine", "Native init error: $e")
            engineHandle = 0L
        }
    }

    fun isNativeLoaded() = libraryLoaded && engineHandle != 0L

    fun start(): Boolean = if (engineHandle != 0L) nativeStart(engineHandle) else false
    fun stop() { if (engineHandle != 0L) nativeStop(engineHandle) }
    fun isValid() = engineHandle != 0L

    fun setParam(effectId: Int, paramId: Int, value: Float) {
        if (engineHandle != 0L) nativeSetParam(engineHandle, effectId, paramId, value)
    }

    fun getLevels(): FloatArray =
        if (engineHandle != 0L) nativeGetLevels(engineHandle) else floatArrayOf(-100f, -100f, 0f)

    fun loadPlugin(soPath: String, formatId: Int): Long =
        if (engineHandle != 0L) nativeLoadPlugin(engineHandle, soPath, formatId) else 0L

    fun unloadPlugin(pluginHandle: Long) {
        if (engineHandle != 0L) nativeUnloadPlugin(engineHandle, pluginHandle)
    }

    fun getPluginParams(pluginHandle: Long): String =
        if (engineHandle != 0L) nativeGetPluginParams(engineHandle, pluginHandle) else "[]"

    fun setPluginParam(pluginHandle: Long, paramId: Int, value: Float) {
        if (engineHandle != 0L) nativeSetPluginParam(engineHandle, pluginHandle, paramId, value)
    }

    fun getSampleRate(): Int = if (engineHandle != 0L) nativeGetSampleRate(engineHandle) else 48000
    fun getFramesPerBurst(): Int = if (engineHandle != 0L) nativeGetFramesPerBurst(engineHandle) else 128

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
    private external fun nativeGetPluginParams(handle: Long, pluginHandle: Long): String
    private external fun nativeGetSampleRate(handle: Long): Int
    private external fun nativeGetFramesPerBurst(handle: Long): Int
}
