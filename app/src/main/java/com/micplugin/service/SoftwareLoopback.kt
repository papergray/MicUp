package com.micplugin.service

import android.content.Context
import android.media.*
import android.os.Build
import android.util.Log

data class OutputDevice(val id: Int, val name: String, val type: Int)

object SoftwareLoopback {

    private const val TAG         = "MicPlugin.Loopback"
    private const val SAMPLE_RATE = 48000
    private const val FORMAT      = AudioFormat.ENCODING_PCM_16BIT

    // monitorTrack: STREAM_MUSIC — what YOU hear (volume controlled)
    private var monitorTrack: AudioTrack?   = null
    // routingTrack: VOICE_COMMUNICATION, volume=0 — what Discord/apps tap (always silent to you)
    private var routingTrack: AudioTrack?   = null
    private var audioManager: AudioManager? = null
    private var selectedDeviceId: Int       = -1
    private var monitorEnabled:   Boolean   = true
    private var focusRequest:     AudioFocusRequest? = null

    val isRunning: Boolean get() = monitorTrack != null

    fun start(context: Context) {
        if (isRunning) return
        audioManager = context.getSystemService(AudioManager::class.java)
        val minBuf = 4096  // fixed buffer — no AudioRecord needed
        monitorTrack = buildMonitorTrack(minBuf)
        routingTrack = buildRoutingTrack(minBuf)


        monitorTrack?.play()
        monitorTrack?.setVolume(if (monitorEnabled) 1f else 0f)
        routingTrack?.play()
        routingTrack?.setVolume(0f) // ALWAYS silent to user — Discord reads session internally

        applyOutputDevice()
        requestAudioFocus()
        Log.i(TAG, "Loopback started monitor=$monitorEnabled")

        // No self-capture — OboeEngine feeds us via writeProcessed()
    }

    fun stop() {
        try { monitorTrack?.stop(); monitorTrack?.release() } catch (_: Exception) {}
        try { routingTrack?.stop(); routingTrack?.release() } catch (_: Exception) {}
        monitorTrack = null; routingTrack = null
        abandonAudioFocus()
        try { @Suppress("DEPRECATION") audioManager?.mode = AudioManager.MODE_NORMAL } catch (_: Exception) {}
        Log.i(TAG, "Loopback stopped")
    }


    /** Called by OboeEngine with already-processed audio — the ONLY audio source */
    fun writeProcessed(buf: ShortArray, size: Int) {
        if (!isRunning) return
        if (monitorEnabled) monitorTrack?.write(buf, 0, size)
        routingTrack?.write(buf, 0, size)
    }

    fun setMonitorEnabled(enabled: Boolean) {
        monitorEnabled = enabled
        monitorTrack?.setVolume(if (enabled) 1f else 0f)
        // Pause writes to monitor when off to save CPU
        Log.i(TAG, "Monitor ${if (enabled) "ON" else "OFF"}")
    }

    fun setMicrophoneSidetone(mute: Boolean) {}
    fun onVoipCallStateChanged(active: Boolean) {}

    fun getOutputDevices(context: Context): List<OutputDevice> {
        val am = context.getSystemService(AudioManager::class.java)
        val list = mutableListOf(OutputDevice(-1, "Auto (best available)", -1))
        if (Build.VERSION.SDK_INT >= 23) {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).forEach { d ->
                val name = when (d.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE  -> "Earpiece"
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER   -> "Speaker"
                    AudioDeviceInfo.TYPE_WIRED_HEADSET     -> "Wired Headset"
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES  -> "Wired Headphones"
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO     -> "Bluetooth SCO"
                    AudioDeviceInfo.TYPE_BLE_HEADSET       -> "BLE Headset"
                    AudioDeviceInfo.TYPE_USB_HEADSET       -> "USB Headset"
                    else -> d.productName?.toString()?.takeIf { it.isNotBlank() } ?: return@forEach
                }
                list.add(OutputDevice(d.id, name, d.type))
            }
        }
        return list
    }

    fun setOutputDevice(context: Context, deviceId: Int) {
        selectedDeviceId = deviceId
        audioManager = audioManager ?: context.getSystemService(AudioManager::class.java)
        applyOutputDevice()
    }

    private fun applyOutputDevice() {
        if (Build.VERSION.SDK_INT < 23) return
        val am = audioManager ?: return
        try {
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val dev = if (selectedDeviceId == -1) {
                outputs.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                } ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            } else outputs.firstOrNull { it.id == selectedDeviceId }
            dev?.let { monitorTrack?.preferredDevice = it }
        } catch (e: Exception) { Log.e(TAG, "applyOutputDevice: $e") }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT         -> monitorTrack?.setVolume(0f)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK-> monitorTrack?.setVolume(0.2f)
                AudioManager.AUDIOFOCUS_GAIN                   -> monitorTrack?.setVolume(if (monitorEnabled) 1f else 0f)
            }
        }
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(listener)
                .setWillPauseWhenDucked(false).build()
            am.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            else @Suppress("DEPRECATION") audioManager?.abandonAudioFocus(null)
        } catch (_: Exception) {}
    }

    // Monitor track — STREAM_MUSIC, fully volume-controlled, routes to your ears
    private fun buildMonitorTrack(bufSize: Int): AudioTrack? = try {
        if (Build.VERSION.SDK_INT >= 26) {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE).setEncoding(FORMAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(bufSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM).build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, FORMAT, bufSize * 2, AudioTrack.MODE_STREAM)
        }
    } catch (e: Exception) { Log.e(TAG, "buildMonitorTrack: $e"); null }

    // Routing track — VOICE_COMMUNICATION, always volume=0 — Discord reads its session
    private fun buildRoutingTrack(bufSize: Int): AudioTrack? = try {
        if (Build.VERSION.SDK_INT >= 26) {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE).setEncoding(FORMAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(bufSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM).build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(AudioManager.STREAM_VOICE_CALL, SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, FORMAT, bufSize * 2, AudioTrack.MODE_STREAM)
        }
    } catch (e: Exception) { Log.e(TAG, "buildRoutingTrack: $e"); null }

}
