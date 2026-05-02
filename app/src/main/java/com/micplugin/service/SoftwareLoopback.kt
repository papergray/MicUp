package com.micplugin.service

import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*

data class OutputDevice(val id: Int, val name: String, val type: Int)

object SoftwareLoopback {

    private const val TAG         = "MicPlugin.Loopback"
    private const val SAMPLE_RATE = 48000
    private const val CHANNELS    = AudioFormat.CHANNEL_IN_MONO
    private const val FORMAT      = AudioFormat.ENCODING_PCM_16BIT

    private var record:       AudioRecord?  = null
    private var track:        AudioTrack?   = null  // ONE track only
    private var aec:          AcousticEchoCanceler? = null
    private var ns:           NoiseSuppressor?      = null
    private var job:          Job?          = null
    private var audioManager: AudioManager? = null
    private var selectedDeviceId: Int       = -1
    private var monitorEnabled:   Boolean   = true
    private var focusRequest: AudioFocusRequest? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun start(context: Context, processBuffer: ((ShortArray, Int) -> Unit)? = null) {
        if (isRunning) return
        audioManager = context.getSharedPreferences("micup_prefs", Context.MODE_PRIVATE)
            .let { audioManager ?: context.getSystemService(AudioManager::class.java) }

        // Use 2x minimum for low latency — coerceAtLeast(2048) balances latency vs underruns
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS, FORMAT).coerceAtLeast(2048)
        record = buildRecord(minBuf)
        track  = buildTrack(minBuf)

        if (record == null || track == null) { Log.e(TAG, "Init failed"); stop(); return }

        // AEC cancels hardware sidetone from mic capture
        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(record!!.audioSessionId)
            aec?.enabled = true
            Log.i(TAG, "AEC enabled")
        }
        if (NoiseSuppressor.isAvailable()) {
            ns = NoiseSuppressor.create(record!!.audioSessionId)
            ns?.enabled = true
        }

        record!!.startRecording()
        track!!.play()
        // Set volume based on monitor state — this is the ONLY track
        track!!.setVolume(if (monitorEnabled) 1f else 0f)

        applyOutputDevice()
        requestAudioFocus()
        try {
            @Suppress("DEPRECATION")
            audioManager?.mode = if (monitorEnabled) AudioManager.MODE_IN_COMMUNICATION
                                  else                AudioManager.MODE_NORMAL
        } catch (_: Exception) {}

        Log.i(TAG, "Loopback started monitor=$monitorEnabled")

        job = CoroutineScope(Dispatchers.Default).launch {
            val buf = ShortArray(minBuf / 2)
            while (isActive) {
                val read = record?.read(buf, 0, buf.size) ?: break
                if (read <= 0) continue
                processBuffer?.invoke(buf, read)
                track?.write(buf, 0, read)
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        try { aec?.release(); ns?.release() } catch (_: Exception) {}
        try { record?.stop(); record?.release() } catch (_: Exception) {}
        try { track?.stop();  track?.release()  } catch (_: Exception) {}
        aec = null; ns = null; record = null; track = null
        abandonAudioFocus()
        try { @Suppress("DEPRECATION") audioManager?.mode = AudioManager.MODE_NORMAL } catch (_: Exception) {}
        Log.i(TAG, "Loopback stopped")
    }

    fun setMonitorEnabled(enabled: Boolean) {
        monitorEnabled = enabled
        track?.setVolume(if (enabled) 1f else 0f)
        // MODE_IN_COMMUNICATION tells Android to route VOICE_COMMUNICATION capture to apps
        // Only set when running — don't set if stopped
        if (isRunning) {
            try {
                @Suppress("DEPRECATION")
                audioManager?.mode = if (enabled) AudioManager.MODE_IN_COMMUNICATION
                                     else          AudioManager.MODE_NORMAL
            } catch (_: Exception) {}
        }
        Log.i(TAG, "Monitor ${if (enabled) "ON" else "OFF"}")
    }

    fun setMicrophoneSidetone(mute: Boolean) { /* no-op */ }
    fun onVoipCallStateChanged(active: Boolean) { Log.d(TAG, "VoIP: $active") }

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

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun applyOutputDevice() {
        if (Build.VERSION.SDK_INT < 23) return
        val am = audioManager ?: return
        try {
            val t = track ?: return
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val dev = if (selectedDeviceId == -1) {
                outputs.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                } ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            } else outputs.firstOrNull { it.id == selectedDeviceId }
            dev?.let { t.preferredDevice = it }
        } catch (e: Exception) { Log.e(TAG, "applyOutputDevice: $e") }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT    -> track?.setVolume(0f)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> track?.setVolume(0.2f)
                AudioManager.AUDIOFOCUS_GAIN              -> {
                    track?.setVolume(if (monitorEnabled) 1f else 0f)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(listener)
                .setWillPauseWhenDucked(false).build()
            am.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(listener, AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            else @Suppress("DEPRECATION") audioManager?.abandonAudioFocus(null)
        } catch (_: Exception) {}
    }

    private fun buildTrack(bufSize: Int): AudioTrack? = try {
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
            AudioTrack(AudioManager.STREAM_VOICE_CALL, SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, FORMAT, bufSize * 2, AudioTrack.MODE_STREAM)
        }
    } catch (e: Exception) { Log.e(TAG, "buildTrack: $e"); null }

    private fun buildRecord(bufSize: Int): AudioRecord? {
        for (src in listOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.MIC)) {
            try {
                val r = if (Build.VERSION.SDK_INT >= 23) {
                    AudioRecord.Builder()
                        .setAudioSource(src)
                        .setAudioFormat(AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE).setEncoding(FORMAT)
                            .setChannelMask(CHANNELS).build())
                        .setBufferSizeInBytes(bufSize * 2).build()
                } else AudioRecord(src, SAMPLE_RATE, CHANNELS, FORMAT, bufSize * 2)
                if (r.state == AudioRecord.STATE_INITIALIZED) { Log.i(TAG, "Record src=$src"); return r }
                r.release()
            } catch (_: Exception) {}
        }
        return null
    }
}
