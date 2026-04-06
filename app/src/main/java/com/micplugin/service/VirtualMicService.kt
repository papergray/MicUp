package com.micplugin.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.scottyab.rootbeer.RootBeer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class VirtualMicTier {
    /** VoIP stream routing — works without any special permissions */
    VOIP_STREAM,
    /** Android 14+ AudioPlaybackCapture / MediaProjection */
    MEDIA_PROJECTION,
    /** Rooted device with Magisk ALSA loopback module */
    ROOT_MAGISK;

    val displayName: String get() = when (this) {
        VOIP_STREAM      -> "VoIP Compatible"
        MEDIA_PROJECTION -> "Android 14 Mode"
        ROOT_MAGISK      -> "Root Mode — System-Wide"
    }
    val description: String get() = when (this) {
        VOIP_STREAM ->
            "Processed audio routes through the VoIP stream. " +
            "All conferencing apps (Meet, Teams, Discord, Zoom, WhatsApp) " +
            "will capture from this stream automatically."
        MEDIA_PROJECTION ->
            "Uses MediaProjection to intercept and re-inject audio. " +
            "Set MicPlugin as virtual mic in Sound Settings."
        ROOT_MAGISK ->
            "Magisk module creates /dev/snd/virtual_mic via ALSA loopback. " +
            "All apps see 'MicPlugin Virtual Mic' as a separate hardware device."
    }
    val badgeColorHex: Long get() = when (this) {
        VOIP_STREAM      -> 0xFF3DFCAC
        MEDIA_PROJECTION -> 0xFF7C5CFC
        ROOT_MAGISK      -> 0xFFFFD700
    }
}

@Singleton
class VirtualMicService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "VirtualMicService"
        private const val MAGISK_MODULE_ASSET = "micplugin_routing.zip"
    }

    private val _activeTier = MutableStateFlow(VirtualMicTier.VOIP_STREAM)
    val activeTier: StateFlow<VirtualMicTier> = _activeTier

    init { detectAndSetBestTier() }

    private fun detectAndSetBestTier() {
        val rootBeer = RootBeer(context)
        _activeTier.value = when {
            rootBeer.isRooted              -> VirtualMicTier.ROOT_MAGISK
            Build.VERSION.SDK_INT >= 34    -> VirtualMicTier.MEDIA_PROJECTION
            else                           -> VirtualMicTier.VOIP_STREAM
        }
        Log.i(TAG, "Virtual mic tier: ${_activeTier.value}")
    }

    fun getActiveTier(): VirtualMicTier = _activeTier.value

    fun getStatusDescription(): String = _activeTier.value.description

    /**
     * Walk user through upgrading to a higher tier.
     * For MEDIA_PROJECTION: launches system permission dialog.
     * For ROOT_MAGISK: extracts and installs Magisk module.
     */
    fun requestUpgrade(activity: Activity) {
        when (_activeTier.value) {
            VirtualMicTier.VOIP_STREAM -> {
                if (Build.VERSION.SDK_INT >= 34) {
                    _activeTier.value = VirtualMicTier.MEDIA_PROJECTION
                }
            }
            VirtualMicTier.MEDIA_PROJECTION -> {
                val rootBeer = RootBeer(activity)
                if (rootBeer.isRooted) {
                    installMagiskModule(activity)
                    _activeTier.value = VirtualMicTier.ROOT_MAGISK
                }
            }
            VirtualMicTier.ROOT_MAGISK -> { /* already best tier */ }
        }
    }

    private fun installMagiskModule(context: Context) {
        try {
            val outFile = java.io.File(context.cacheDir, MAGISK_MODULE_ASSET)
            context.assets.open(MAGISK_MODULE_ASSET).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            // Trigger Magisk install via root shell
            Runtime.getRuntime().exec(
                arrayOf("su", "-c", "magisk --install-module ${outFile.absolutePath}")
            )
            Log.i(TAG, "Magisk module extraction triggered")
        } catch (e: Exception) {
            Log.e(TAG, "Magisk module install failed: $e")
        }
    }
}
