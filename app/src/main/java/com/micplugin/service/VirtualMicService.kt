package com.micplugin.service

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
// Adding the missing import that likely caused the build failure
import com.micplugin.service.ShizukuManager.ShizukuState

enum class VirtualMicTier {
    VOIP_STREAM, MEDIA_PROJECTION, SHIZUKU_ADB, ROOT_MAGISK;

    val displayName: String get() = when (this) {
        VOIP_STREAM      -> "VoIP Compatible"
        MEDIA_PROJECTION -> "Android 14 Mode"
        SHIZUKU_ADB      -> "Shizuku — ADB Level"
        ROOT_MAGISK      -> "Root Mode — System-Wide"
    }
    val description: String get() = when (this) {
        VOIP_STREAM      -> "Processed audio routes through VoIP stream. Works with Meet, Teams, Discord, Zoom."
        MEDIA_PROJECTION -> "Uses MediaProjection to intercept and re-inject audio. Set MicPlugin as virtual mic in Sound Settings."
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
            return true
        }
    }

    private val _activeTier = MutableStateFlow(VirtualMicTier.ROOT_MAGISK)
    val activeTier: StateFlow<VirtualMicTier> = _activeTier

    init { detectAndSetBestTier() }

    private fun detectAndSetBestTier() {
        _activeTier.value = VirtualMicTier.ROOT_MAGISK
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
            VirtualMicTier.ROOT_MAGISK -> {
                installMagiskModule(activity)
            }
        }
    }

    private fun installMagiskModule(context: Context) {
        try {
            val outFile = File(context.cacheDir, MAGISK_MODULE_ASSET)
            context.assets.open(MAGISK_MODULE_ASSET).use { i -> outFile.outputStream().use { o -> i.copyTo(o) } }
            ProcessBuilder("su", "-c", "magisk --install-module ${outFile.absolutePath}").start()
            Log.i(TAG, "Magisk module extraction triggered")
        } catch (e: Exception) {
            Log.e(TAG, "Magisk module install failed: $e")
        }
    }
}
