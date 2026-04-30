package com.micplugin.service

import android.content.Context
import android.media.*
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*

data class OutputDevice(
    val id: Int,
    val name: String,
    val type: Int,
)

object SoftwareLoopback {

    private const val TAG         = "MicPlugin.Loopback"
    private const val SAMPLE_RATE = 48000
    private const val CHANNELS    = AudioFormat.CHANNEL_IN_MONO
    private const val FORMAT      = AudioFormat.ENCODING_PCM_16BIT

    private var record:        AudioRecord? = null
    private var routingTrack:  AudioTrack?  = null  // VOICE_COMMUNICATION — what Discord taps
    private var monitorTrack:  AudioTrack?  = null  // STREAM_MUSIC — what you hear
    private var job:           Job?         = null
    private var audioManager:  AudioManager? = null
    private var selectedDeviceId: Int = -1
    private var monitorEnabled: Boolean = true

    val isRunning: Boolean get() = job?.isActive == true

    // ── Public API ────────────────────────────────────────────────────────────

    fun start(context: Context, processBuffer: ((ShortArray, Int) -> Unit)? = null) {
        if (isRunning) return
        audioManager = context.getSystemService(AudioManager::class.java)

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS, FORMAT).coerceAtLeast(4096)
        record       = buildRecord(minBuf)
        routingTrack = buildRoutingTrack(minBuf)  // routes to Discord/apps — no audio mode changes
        monitorTrack = buildMonitorTrack(minBuf)  // plays to your ears — independent

        if (record == null || routingTrack == null) {
            Log.e(TAG, "Failed to create audio objects"); stop(); return
        }

        record!!.startRecording()
        routingTrack!!.play()
        if (monitorEnabled) monitorTrack?.play()

        applyMonitorDevice()

        Log.i(TAG, "Loopback started buf=$minBuf monitor=$monitorEnabled")

        job = CoroutineScope(Dispatchers.Default).launch {
            val buf = ShortArray(minBuf / 2)
            while (isActive) {
                val read = record?.read(buf, 0, buf.size) ?: break
                if (read <= 0) continue
                processBuffer?.invoke(buf, read)
                // Always write to routing track so Discord always hears processed audio
                routingTrack?.write(buf, 0, read)
                // Only write to monitor if enabled
                if (monitorEnabled) monitorTrack?.write(buf, 0, read)
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        try { record?.stop();       record?.release()       } catch (_: Exception) {}
        try { routingTrack?.stop(); routingTrack?.release() } catch (_: Exception) {}
        try { monitorTrack?.stop(); monitorTrack?.release() } catch (_: Exception) {}
        record = null; routingTrack = null; monitorTrack = null
        Log.i(TAG, "Loopback stopped")
    }

    fun setMonitorEnabled(enabled: Boolean) {
        monitorEnabled = enabled
        if (enabled) {
            monitorTrack?.play()
        } else {
            monitorTrack?.pause()
            monitorTrack?.flush()
        }
        // NEVER touch isMicrophoneMute — that kills Discord input entirely
        Log.i(TAG, "Monitor ${if (enabled) "ON" else "OFF"}")
    }

    // Kept for API compat — does nothing now (we never mute mic)
    fun setMicrophoneSidetone(mute: Boolean) {
        Log.d(TAG, "setMicrophoneSidetone ignored — mic mute removed to fix Discord")
    }

    fun getOutputDevices(context: Context): List<OutputDevice> {
        val am = context.getSystemService(AudioManager::class.java)
        val devices = mutableListOf(OutputDevice(-1, "Auto (best available)", -1))
        if (Build.VERSION.SDK_INT >= 23) {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).forEach { d ->
                val name = when (d.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE   -> "Earpiece"
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER    -> "Speaker"
                    AudioDeviceInfo.TYPE_WIRED_HEADSET      -> "Wired Headset"
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES   -> "Wired Headphones"
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO      -> "Bluetooth SCO"
                    AudioDeviceInfo.TYPE_BLE_HEADSET        -> "BLE Headset"
                    AudioDeviceInfo.TYPE_USB_HEADSET        -> "USB Headset"
                    AudioDeviceInfo.TYPE_USB_DEVICE         -> "USB Audio"
                    else -> d.productName?.toString()?.takeIf { it.isNotBlank() } ?: return@forEach
                }
                devices.add(OutputDevice(d.id, name, d.type))
            }
        }
        return devices
    }

    fun setOutputDevice(context: Context, deviceId: Int) {
        selectedDeviceId = deviceId
        audioManager = audioManager ?: context.getSystemService(AudioManager::class.java)
        applyMonitorDevice()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun applyMonitorDevice() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT < 23) return
        try {
            val track = monitorTrack ?: return
            if (selectedDeviceId == -1) {
                // Auto: prefer wired/BT, then earpiece
                val preferred = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                } ?: am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                preferred?.let { track.preferredDevice = it }
            } else {
                val dev = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .firstOrNull { it.id == selectedDeviceId }
                dev?.let { track.preferredDevice = it }
            }
        } catch (e: Exception) {
            Log.e(TAG, "applyMonitorDevice failed: $e")
        }
    }

    // Routing track — VOICE_COMMUNICATION so Discord/Messenger taps processed audio
    // Does NOT set audio mode — Discord manages its own mode
    private fun buildRoutingTrack(bufSize: Int): AudioTrack? {
        return try {
            if (Build.VERSION.SDK_INT >= 26) {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
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
        } catch (e: Exception) { Log.e(TAG, "routingTrack failed: $e"); null }
    }

    // Monitor track — STREAM_MUSIC so it's independent, never fights Discord's audio session
    private fun buildMonitorTrack(bufSize: Int): AudioTrack? {
        return try {
            if (Build.VERSION.SDK_INT >= 26) {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
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
        } catch (e: Exception) { Log.e(TAG, "monitorTrack failed: $e"); null }
    }

    private fun buildRecord(bufSize: Int): AudioRecord? {
        for (src in listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
        )) {
            try {
                val r = if (Build.VERSION.SDK_INT >= 23) {
                    AudioRecord.Builder()
                        .setAudioSource(src)
                        .setAudioFormat(AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE).setEncoding(FORMAT)
                            .setChannelMask(CHANNELS).build())
                        .setBufferSizeInBytes(bufSize * 2).build()
                } else {
                    AudioRecord(src, SAMPLE_RATE, CHANNELS, FORMAT, bufSize * 2)
                }
                if (r.state == AudioRecord.STATE_INITIALIZED) {
                    Log.i(TAG, "AudioRecord src=$src"); return r
                }
                r.release()
            } catch (_: Exception) {}
        }
        return null
    }
}
