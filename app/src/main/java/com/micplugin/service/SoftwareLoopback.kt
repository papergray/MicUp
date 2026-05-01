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

    private var record:        AudioRecord?  = null
    private var routingTrack:  AudioTrack?   = null
    private var monitorTrack:  AudioTrack?   = null
    private var job:           Job?          = null
    private var audioManager:  AudioManager? = null
    private var selectedDeviceId: Int        = -1
    private var monitorEnabled:   Boolean    = true
    private var focusRequest: AudioFocusRequest? = null
    private var inVoipCall = false  // true when Discord/Teams/etc has a live call

    val isRunning: Boolean get() = job?.isActive == true

    // ── Public API ────────────────────────────────────────────────────────────

    fun start(context: Context, processBuffer: ((ShortArray, Int) -> Unit)? = null) {
        if (isRunning) return
        audioManager = context.getSystemService(AudioManager::class.java)

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS, FORMAT).coerceAtLeast(4096)
        record       = buildRecord(minBuf)
        routingTrack = buildRoutingTrack(minBuf)
        monitorTrack = buildMonitorTrack(minBuf)

        if (record == null || routingTrack == null) {
            Log.e(TAG, "Failed to create audio objects"); stop(); return
        }

        record!!.startRecording()
        routingTrack!!.play()
        routingTrack!!.setVolume(0f)  // silent to user — Discord reads via shared voice session
        if (monitorEnabled) monitorTrack?.play()

        // Request audio focus for monitor — yield immediately to VoIP apps
        requestAudioFocus()

        applyMonitorDevice()
        Log.i(TAG, "Loopback started monitor=$monitorEnabled")

        job = CoroutineScope(Dispatchers.Default).launch {
            val buf = ShortArray(minBuf / 2)
            while (isActive) {
                val read = record?.read(buf, 0, buf.size) ?: break
                if (read <= 0) continue
                processBuffer?.invoke(buf, read)
                routingTrack?.write(buf, 0, read)
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
        abandonAudioFocus()
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
        Log.i(TAG, "Monitor ${if (enabled) "ON" else "OFF"}")
    }

    // Called by AudioProcessingService when it detects a VoIP call started/ended
    // VoIP call active → don't set MODE_IN_COMMUNICATION (Discord owns it)
    // VoIP call ended  → set MODE_IN_COMMUNICATION (recorder apps hear processed audio)
    fun onVoipCallStateChanged(active: Boolean) {
        inVoipCall = active
        Log.i(TAG, "VoIP call state: $active")
        // Audio mode left at MODE_NORMAL — hardware sidetone stays off
    }

    fun setMicrophoneSidetone(mute: Boolean) {
        // No-op — we never mute mic, it kills all recording
    }

    fun getOutputDevices(context: Context): List<OutputDevice> {
        val am = context.getSystemService(AudioManager::class.java)
        val devices = mutableListOf(OutputDevice(-1, "Auto (best available)", -1))
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
                    AudioDeviceInfo.TYPE_USB_DEVICE        -> "USB Audio"
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

    // ── Audio Focus ───────────────────────────────────────────────────────────

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    // Another app (Discord voice msg, recorder) needs exclusive mic access
                    // Pause monitor so we don't conflict — keep routing track running
                    Log.i(TAG, "Audio focus lost ($change) — pausing monitor")
                    monitorTrack?.pause()
                    monitorTrack?.flush()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    // Just duck — reduce monitor volume but keep running
                    monitorTrack?.setVolume(0.2f)
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    Log.i(TAG, "Audio focus gained — restoring monitor")
                    routingTrack?.setVolume(0f)   // always keep routing track silent
                    monitorTrack?.setVolume(1.0f)
                    if (monitorEnabled) monitorTrack?.play()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setOnAudioFocusChangeListener(listener)
                .setWillPauseWhenDucked(false)
                .setAcceptsDelayedFocusGain(true)
                .build()
            am.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    // ── Audio Mode ────────────────────────────────────────────────────────────

    private fun setAudioMode(communication: Boolean) {
        val am = audioManager ?: return
        try {
            @Suppress("DEPRECATION")
            am.mode = if (communication) AudioManager.MODE_IN_COMMUNICATION
                      else               AudioManager.MODE_NORMAL
            Log.i(TAG, "Audio mode: ${if (communication) "IN_COMMUNICATION" else "NORMAL"}")
        } catch (e: Exception) {
            Log.e(TAG, "setAudioMode failed: $e")
        }
    }

    // ── Monitor device routing ────────────────────────────────────────────────

    private fun applyMonitorDevice() {
        if (Build.VERSION.SDK_INT < 23) return
        val am = audioManager ?: return
        try {
            val track = monitorTrack ?: return
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val dev = if (selectedDeviceId == -1) {
                outputs.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET    ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET      ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                } ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            } else {
                outputs.firstOrNull { it.id == selectedDeviceId }
            }
            dev?.let { track.preferredDevice = it }
        } catch (e: Exception) {
            Log.e(TAG, "applyMonitorDevice failed: $e")
        }
    }

    // ── Audio tracks ──────────────────────────────────────────────────────────

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
    } catch (e: Exception) { Log.e(TAG, "routingTrack failed: $e"); null }

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
    } catch (e: Exception) { Log.e(TAG, "monitorTrack failed: $e"); null }

    private fun buildRecord(bufSize: Int): AudioRecord? {
        for (src in listOf(
            MediaRecorder.AudioSource.MIC,
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
