package com.micplugin.service

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class VirtualMicTier {
    VOIP_STREAM, MEDIA_PROJECTION, SHIZUKU_ADB, ROOT_MAGISK;

    val displayName: String get() = when (this) {
        VOIP_STREAM      -> "VoIP Compatible"
        MEDIA_PROJECTION -> "Software Loopback"
        SHIZUKU_ADB      -> "Shizuku — ADB Level"
        ROOT_MAGISK      -> "Root Mode — System-Wide"
    }
    val description: String get() = when (this) {
        VOIP_STREAM      -> "Processed audio routes through VoIP stream. Works with Meet, Teams, Discord, Zoom."
        MEDIA_PROJECTION -> "Uses software audio loopback via VOICE_COMMUNICATION stream. Works on all devices without root or Shizuku."
        SHIZUKU_ADB      -> "Uses Shizuku (ADB-level) to load ALSA loopback and route processed audio. Apps see 'MicPlugin Virtual Mic' without full root."
        ROOT_MAGISK      -> "Magisk module creates /dev/snd/virtual_mic via ALSA loopback. All apps see MicPlugin as a separate hardware device."
    }
    val badgeColorHex: Long get() = when (this) {
        VOIP_STREAM      -> 0xFF3DFCACL
        MEDIA_PROJECTION -> 0xFF7C5CFCL
        SHIZUKU_ADB      -> 0xFF00B4D8L
        ROOT_MAGISK      -> 0xFFFFD700L
    }
}

@Singleton
class VirtualMicService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager,
) {
    companion object {
        private const val TAG = "VirtualMicService"
        private const val MAGISK_MODULE_ASSET = "micplugin_routing.zip"

        fun isRooted(): Boolean {
            val suPaths = listOf(
                "/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/su/bin/su", "/magisk/.core/bin/su", "/data/local/xbin/su",
                "/data/adb/magisk/busybox", "/data/adb/ksu/bin/su",
            )
            if (suPaths.any { File(it).exists() }) return true
            // Try executing su — catches cases where file exists but path is hidden
            return try {
                val p = findSuBinary()?.let {
                    Runtime.getRuntime().exec(arrayOf(it, "-c", "id"))
                } ?: Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor()
                out.contains("uid=0")
            } catch (_: Exception) { false }
        }

        /** Returns first working su binary path, tries Magisk/KernelSU/standard paths */
        fun findSuBinary(): String? {
            val candidates = listOf(
                "/data/adb/magisk/busybox",
                "/data/adb/ksu/bin/su",
                "/data/adb/magisk/magisk",
                "/sbin/.magisk/bin/busybox",
                "/system/xbin/su",
                "/system/bin/su",
                "su",
            )
            return candidates.firstOrNull { path ->
                try {
                    if (path == "su") {
                        Runtime.getRuntime().exec(arrayOf("su", "-c", "id")).let { p ->
                            val ok = p.inputStream.bufferedReader().readText().contains("uid=0")
                            p.destroy(); ok
                        }
                    } else File(path).exists()
                } catch (_: Exception) { false }
            }
        }

        fun execRoot(cmd: String): String? {
            val su = findSuBinary() ?: return null
            return try {
                val args = if (su.contains("busybox")) arrayOf(su, "sh", "-c", cmd)
                           else arrayOf(su, "-c", cmd)
                val p = Runtime.getRuntime().exec(args)
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor()
                out
            } catch (_: Exception) { null }
        }
    }

    private val _activeTier = MutableStateFlow(VirtualMicTier.VOIP_STREAM)
    val activeTier: StateFlow<VirtualMicTier> = _activeTier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Initial best-effort detection (Shizuku may not be ready yet — see collector below)
        detectAndSetBestTier()
        // React whenever Shizuku state changes (fixes race: init runs before shizukuManager.init())
        scope.launch {
            shizukuManager.state.collect { state ->
                if (state == ShizukuState.READY &&
                    _activeTier.value != VirtualMicTier.ROOT_MAGISK &&
                    _activeTier.value != VirtualMicTier.SHIZUKU_ADB
                ) {
                    Log.i(TAG, "Shizuku became READY — upgrading tier to SHIZUKU_ADB")
                    onShizukuReady()
                }
            }
        }
    }

    private fun detectAndSetBestTier() {
        _activeTier.value = when {
            isRooted()                       -> VirtualMicTier.ROOT_MAGISK
            shizukuManager.isReady           -> VirtualMicTier.SHIZUKU_ADB
            Build.VERSION.SDK_INT >= 34      -> VirtualMicTier.MEDIA_PROJECTION
            else                             -> VirtualMicTier.VOIP_STREAM
        }
        Log.i(TAG, "Virtual mic tier: ${_activeTier.value}")
    }

    /** Called after Shizuku permission is granted so we re-evaluate tier */
    fun onShizukuReady() {
        if (_activeTier.value == VirtualMicTier.VOIP_STREAM ||
            _activeTier.value == VirtualMicTier.MEDIA_PROJECTION) {
            _activeTier.value = VirtualMicTier.SHIZUKU_ADB
            activateShizukuRouting()
        }
    }

    private fun activateShizukuRouting() {
        val loaded = shizukuManager.loadAlsaLoopback()
        Log.i(TAG, "ALSA loopback loaded: $loaded")
        shizukuManager.routeToLoopback()
        shizukuManager.setAppOpsMicDefault(context.packageName)
    }

    fun getActiveTier(): VirtualMicTier = _activeTier.value
    fun getStatusDescription(): String = _activeTier.value.description

    /**
     * Returns null if the tier is available, or a human-readable reason string if not.
     */
    fun checkTierAvailability(tier: VirtualMicTier): String? = when (tier) {
        VirtualMicTier.VOIP_STREAM      -> null
        VirtualMicTier.MEDIA_PROJECTION -> if (Build.VERSION.SDK_INT >= 29) null
                                           else "Requires Android 10+"
        VirtualMicTier.SHIZUKU_ADB      -> when (shizukuManager.state.value) {
            ShizukuState.READY      -> null
            ShizukuState.NEED_GRANT -> "Shizuku is running but permission hasn't been granted — tap Grant in Settings"
            ShizukuState.UNAVAILABLE -> "Shizuku is not running — start it via Wireless Debugging or ADB"
        }
        VirtualMicTier.ROOT_MAGISK      -> if (isRooted()) null else "Device is not rooted"
    }

    /**
     * Manually select a tier. No-ops silently if the tier isn't available.
     * Returns the unavailability reason if blocked, null on success.
     */
    fun setTier(tier: VirtualMicTier): String? {
        val reason = checkTierAvailability(tier)
        if (reason != null) return reason
        _activeTier.value = tier
        if (tier == VirtualMicTier.SHIZUKU_ADB) activateShizukuRouting()
        Log.i(TAG, "Tier manually set to $tier")
        return null
    }

    fun requestUpgrade(activity: Activity) {
        when (_activeTier.value) {
            VirtualMicTier.VOIP_STREAM -> {
                when {
                    shizukuManager.state.value == ShizukuState.NEED_GRANT -> {
                        shizukuManager.requestPermission()
                    }
                    shizukuManager.isReady -> {
                        _activeTier.value = VirtualMicTier.SHIZUKU_ADB
                        activateShizukuRouting()
                    }
                    Build.VERSION.SDK_INT >= 34 -> {
                        _activeTier.value = VirtualMicTier.MEDIA_PROJECTION
                    }
                }
            }
            VirtualMicTier.MEDIA_PROJECTION -> {
                if (shizukuManager.isReady) {
                    _activeTier.value = VirtualMicTier.SHIZUKU_ADB
                    activateShizukuRouting()
                } else if (shizukuManager.state.value == ShizukuState.NEED_GRANT) {
                    shizukuManager.requestPermission()
                }
            }
            VirtualMicTier.SHIZUKU_ADB -> {
                if (isRooted()) {
                    installMagiskModule(activity)
                    _activeTier.value = VirtualMicTier.ROOT_MAGISK
                }
            }
            VirtualMicTier.ROOT_MAGISK -> {}
        }
    }

    private fun installMagiskModule(context: Context) {
        try {
            val outFile = File(context.cacheDir, MAGISK_MODULE_ASSET)
            context.assets.open(MAGISK_MODULE_ASSET).use { i -> outFile.outputStream().use { o -> i.copyTo(o) } }
            execRoot("magisk --install-module ${outFile.absolutePath}") ?: execRoot("/data/adb/magisk/magisk --install-module ${outFile.absolutePath}")
            Log.i(TAG, "Magisk module extraction triggered")
        } catch (e: Exception) {
            Log.e(TAG, "Magisk module install failed: $e")
        }
    }
}
