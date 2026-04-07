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

enum class VirtualMicTier {
    VOIP_STREAM, MEDIA_PROJECTION, ROOT_MAGISK;

    val displayName: String get() = when (this) {
        VOIP_STREAM      -> "VoIP Compatible"
        MEDIA_PROJECTION -> "Android 14 Mode"
        ROOT_MAGISK      -> "Root Mode — System-Wide"
    }
    val description: String get() = when (this) {
        VOIP_STREAM      -> "Processed audio routes through the VoIP stream. All conferencing apps (Meet, Teams, Discord, Zoom, WhatsApp) will capture from this stream automatically."
        MEDIA_PROJECTION -> "Uses MediaProjection to intercept and re-inject audio. Set MicPlugin as virtual mic in Sound Settings."
        ROOT_MAGISK      -> "Magisk module creates /dev/snd/virtual_mic via ALSA loopback. All apps see 'MicPlugin Virtual Mic' as a separate hardware device."
    }
    val badgeColorHex: Long get() = when (this) {
        VOIP_STREAM      -> 0xFF3DFCACL
        MEDIA_PROJECTION -> 0xFF7C5CFCL
        ROOT_MAGISK      -> 0xFFFFD700L
    }
}

@Singleton
class VirtualMicService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "VirtualMicService"
        private const val MAGISK_MODULE_ASSET = "micplugin_routing.zip"

        /** Lightweight root check — file-only, no blocking exec (safe for main thread). */
        fun isRooted(): Boolean {
            val suPaths = listOf(
                "/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/su/bin/su", "/magisk/.core/bin/su", "/data/local/xbin/su",
            )
            return suPaths.any { java.io.File(it).exists() }
        }
    }

    private val _activeTier = MutableStateFlow(VirtualMicTier.VOIP_STREAM)
    val activeTier: StateFlow<VirtualMicTier> = _activeTier

    init { detectAndSetBestTier() }

    private fun detectAndSetBestTier() {
        _activeTier.value = when {
            isRooted()                  -> VirtualMicTier.ROOT_MAGISK
            Build.VERSION.SDK_INT >= 34 -> VirtualMicTier.MEDIA_PROJECTION
            else                        -> VirtualMicTier.VOIP_STREAM
        }
        Log.i(TAG, "Virtual mic tier: ${_activeTier.value}")
    }

    fun getActiveTier(): VirtualMicTier = _activeTier.value
    fun getStatusDescription(): String = _activeTier.value.description

    fun requestUpgrade(activity: Activity) {
        when (_activeTier.value) {
            VirtualMicTier.VOIP_STREAM -> {
                if (Build.VERSION.SDK_INT >= 34)
                    _activeTier.value = VirtualMicTier.MEDIA_PROJECTION
            }
            VirtualMicTier.MEDIA_PROJECTION -> {
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
            Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --install-module ${outFile.absolutePath}"))
            Log.i(TAG, "Magisk module extraction triggered")
        } catch (e: Exception) {
            Log.e(TAG, "Magisk module install failed: $e")
        }
    }
}
