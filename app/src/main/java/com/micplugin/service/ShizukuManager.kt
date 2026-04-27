package com.micplugin.service

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

enum class ShizukuState {
    UNAVAILABLE,   // Shizuku not installed / not running
    NEED_GRANT,    // Installed but permission not granted yet
    READY,         // Permission granted, good to go
}

@Singleton
class ShizukuManager @Inject constructor() {

    companion object {
        private const val TAG = "ShizukuManager"
        private const val REQUEST_CODE = 42
    }

    private val _state = MutableStateFlow(ShizukuState.UNAVAILABLE)
    val state: StateFlow<ShizukuState> = _state

    // Called once from App.onCreate
    fun init() {
        try {
            if (!Shizuku.pingBinder()) {
                _state.value = ShizukuState.UNAVAILABLE
                Log.i(TAG, "Shizuku binder not available")
                return
            }
            refreshState()

            Shizuku.addBinderReceivedListener {
                Log.i(TAG, "Shizuku binder received")
                refreshState()
            }
            Shizuku.addBinderDeadListener {
                Log.w(TAG, "Shizuku binder died")
                _state.value = ShizukuState.UNAVAILABLE
            }
            Shizuku.addRequestPermissionResultListener { _, result ->
                _state.value = if (result == PackageManager.PERMISSION_GRANTED)
                    ShizukuState.READY else ShizukuState.NEED_GRANT
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku init failed: $e")
            _state.value = ShizukuState.UNAVAILABLE
        }
    }

    private fun refreshState() {
        _state.value = try {
            when {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.READY
                else -> ShizukuState.NEED_GRANT
            }
        } catch (e: Exception) {
            ShizukuState.UNAVAILABLE
        }
    }

    /** Call from Activity to trigger the Shizuku permission dialog */
    fun requestPermission() {
        try {
            if (_state.value == ShizukuState.NEED_GRANT)
                Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "requestPermission failed: $e")
        }
    }

    /**
     * Run a shell command via Shizuku (adb-level).
     * Returns stdout, or null on failure.
     */
    fun exec(cmd: String): String? {
        if (_state.value != ShizukuState.READY) return null
        return execInternal(cmd)
    }

    /** Exec with auto su-fallback — tries Shizuku first, then root su paths */
    fun execWithFallback(cmd: String): String? {
        if (_state.value == ShizukuState.READY) {
            execInternal(cmd)?.let { return it }
        }
        // Fallback: try known su binary paths
        val suPaths = listOf(
            "/data/adb/magisk/busybox",
            "/data/adb/ksu/bin/su",
            "/system/xbin/su",
            "/system/bin/su",
            "su",
        )
        for (su in suPaths) {
            try {
                val args = if (su.contains("busybox")) arrayOf(su, "sh", "-c", cmd)
                           else arrayOf(su, "-c", cmd)
                val p = Runtime.getRuntime().exec(args)
                val out = p.inputStream.bufferedReader().readText()
                val err = p.errorStream.bufferedReader().readText()
                p.waitFor()
                Log.d(TAG, "execWithFallback[$su][$cmd] => ${out.take(80)}")
                if (out.isNotBlank()) return out
            } catch (_: Exception) {}
        }
        Log.e(TAG, "execWithFallback: all su paths failed for: $cmd")
        return null
    }

    private fun execInternal(cmd: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val out = process.inputStream.bufferedReader().readText()
            process.waitFor()
            Log.d(TAG, "exec[$cmd] => ${out.take(120)}")
            out
        } catch (e: Exception) {
            Log.e(TAG, "exec failed: $e")
            null
        }
    }

    // ── Helpers used by VirtualMicService ────────────────────────────────────

    /** Load ALSA loopback module so processed audio appears as a mic device */
    fun loadAlsaLoopback(): Boolean {
        val out = execWithFallback("modprobe snd-aloop pcm_substreams=2 2>&1 || insmod /system/lib/modules/snd-aloop.ko pcm_substreams=2 2>&1")
        return out != null && !out.contains("error", ignoreCase = true)
    }

    /** Route our audio output to the loopback capture side via tinyalsa */
    fun routeToLoopback(sampleRate: Int = 48000): Boolean {
        val tinymix = execWithFallback("which tinymix") ?: return false
        if (tinymix.isBlank()) return false
        execWithFallback("tinymix 'Loopback Mixer' 1")
        return true
    }

    /** Set microphone as default input (adb-level appops) */
    fun setAppOpsMicDefault(packageName: String): Boolean {
        val out = execWithFallback("appops set $packageName RECORD_AUDIO allow")
        return out != null
    }

    val isReady: Boolean get() = _state.value == ShizukuState.READY
}
