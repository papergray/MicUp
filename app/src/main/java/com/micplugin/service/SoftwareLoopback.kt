package com.micplugin.service

import android.content.Context
import android.media.*
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*

/**
 * Pure software loopback — works on ALL Android devices, no kernel module needed.
 *
 * Pipeline:
 *   AudioRecord (MIC) → DSP buffer → AudioTrack (VOICE_COMMUNICATION stream)
 *
 * VOICE_COMMUNICATION stream is what Discord/Messenger/Zoom tap as mic input
 * when the earpiece/speaker is the output. Combined with setCommunicationDevice
 * (Android 12+) or MODE_IN_COMMUNICATION (older), other apps hear processed audio.
 */
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

        // Set device into communication mode so apps tap this stream as mic
        setCommunicationMode(true)

        record!!.startRecording()
        track!!.play()
        Log.i(TAG, "Software loopback started — buffer=$minBuf sampleRate=$SAMPLE_RATE")

        job = CoroutineScope(Dispatchers.Default).launch {
            val buf = ShortArray(minBuf / 2)
            while (isActive) {
                val read = record?.read(buf, 0, buf.size) ?: break
                if (read <= 0) continue

                // Apply any DSP processing passed in from AudioEngine
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
        Log.i(TAG, "Software loopback stopped")
    }

    // ── Build AudioRecord ─────────────────────────────────────────────────────

    private fun buildRecord(bufSize: Int): AudioRecord? {
        // Try sources in order of quality
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
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
                    Log.i(TAG, "AudioRecord created with source=$src")
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
                AudioTrack(
                    AudioManager.STREAM_VOICE_CALL,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    FORMAT,
                    bufSize * 2,
                    AudioTrack.MODE_STREAM
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack build failed: $e"); null
        }
    }

    // ── Communication mode (makes other apps use this stream as mic input) ────

    private fun setCommunicationMode(enable: Boolean) {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                // Android 12+ — set communication device explicitly
                if (enable) {
                    val earpiece = am.availableCommunicationDevices
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                        ?: am.availableCommunicationDevices.firstOrNull()
                    earpiece?.let { am.setCommunicationDevice(it) }
                } else {
                    am.clearCommunicationDevice()
                }
            } else {
                // Older Android — use audio mode
                @Suppress("DEPRECATION")
                am.mode = if (enable) AudioManager.MODE_IN_COMMUNICATION
                           else       AudioManager.MODE_NORMAL
            }
        } catch (e: Exception) {
            Log.e(TAG, "setCommunicationMode failed: $e")
        }
    }
}
