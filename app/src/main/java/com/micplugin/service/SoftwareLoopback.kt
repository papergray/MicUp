package com.micplugin.service

import android.content.Context
import android.media.*
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*

object SoftwareLoopback {

    private const val TAG         = "MicPlugin.Loopback"
    private const val SAMPLE_RATE = 48000
    private const val CHANNELS    = AudioFormat.CHANNEL_IN_MONO
    private const val FORMAT      = AudioFormat.ENCODING_PCM_16BIT

    private var record: AudioRecord? = null
    private var track:  AudioTrack?  = null
    private var job:    Job?         = null
    private var audioManager: AudioManager? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun start(context: Context, processBuffer: ((ShortArray, Int) -> Unit)? = null) {
        if (isRunning) return

        audioManager = context.getSystemService(AudioManager::class.java)

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS, FORMAT)
            .coerceAtLeast(4096)

        record = buildRecord(minBuf)
        track  = buildTrack(minBuf)

        if (record == null || track == null) {
            Log.e(TAG, "Failed to create audio objects")
            stop(); return
        }

        setCommunicationMode(true)
        // FIX #2: force off speakerphone, route to earpiece/headset
        routeToEarpieceOrHeadset()

        record!!.startRecording()
        track!!.play()
        Log.i(TAG, "Software loopback started — buffer=$minBuf")

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
        try { record?.stop(); record?.release() } catch (_: Exception) {}
        try { track?.stop();  track?.release()  } catch (_: Exception) {}
        record = null; track = null
        setCommunicationMode(false)
        restoreAudioRouting()
        Log.i(TAG, "Software loopback stopped")
    }

    // FIX #3: mute raw sidetone so only processed audio heard in monitor
    fun setMicrophoneSidetone(mute: Boolean) {
        try {
            audioManager?.isMicrophoneMute = mute
        } catch (_: Exception) {}
    }

    // ── FIX #2: Speaker lock ──────────────────────────────────────────────────

    private fun routeToEarpieceOrHeadset() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                // Prefer wired headset, then bluetooth, then earpiece — NOT speaker
                val preferred = am.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                } ?: am.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                } ?: am.availableCommunicationDevices.firstOrNull()

                preferred?.let {
                    am.setCommunicationDevice(it)
                    Log.i(TAG, "Communication device set: ${it.productName} type=${it.type}")
                }
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "routeToEarpieceOrHeadset failed: $e")
        }
    }

    private fun restoreAudioRouting() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice()
            else { @Suppress("DEPRECATION") am.isSpeakerphoneOn = false }
        } catch (_: Exception) {}
    }

    // ── Communication mode ────────────────────────────────────────────────────

    private fun setCommunicationMode(enable: Boolean) {
        val am = audioManager ?: return
        try {
            @Suppress("DEPRECATION")
            am.mode = if (enable) AudioManager.MODE_IN_COMMUNICATION
                      else        AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.e(TAG, "setCommunicationMode failed: $e")
        }
    }

    // ── Build AudioRecord ─────────────────────────────────────────────────────

    private fun buildRecord(bufSize: Int): AudioRecord? {
        // FIX #4: MIC first so voice messages work, VOICE_COMMUNICATION second
        val sources = listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
        )
        for (src in sources) {
            try {
                val r = if (Build.VERSION.SDK_INT >= 23) {
                    AudioRecord.Builder()
                        .setAudioSource(src)
                        .setAudioFormat(AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(FORMAT)
                            .setChannelMask(CHANNELS)
                            .build())
                        .setBufferSizeInBytes(bufSize * 2)
                        .build()
                } else {
                    AudioRecord(src, SAMPLE_RATE, CHANNELS, FORMAT, bufSize * 2)
                }
                if (r.state == AudioRecord.STATE_INITIALIZED) {
                    Log.i(TAG, "AudioRecord source=$src")
                    return r
                }
                r.release()
            } catch (_: Exception) {}
        }
        return null
    }

    // ── Build AudioTrack ──────────────────────────────────────────────────────

    private fun buildTrack(bufSize: Int): AudioTrack? {
        return try {
            if (Build.VERSION.SDK_INT >= 26) {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(FORMAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                    .setBufferSizeInBytes(bufSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_VOICE_CALL,
                    SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                    FORMAT, bufSize * 2, AudioTrack.MODE_STREAM
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack build failed: $e"); null
        }
    }
}
