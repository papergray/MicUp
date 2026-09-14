package com.micplugin.rvc

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.minOf

class RvcAudioPlayer {
    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNELS = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val TAG = "RvcAudioPlayer"
    }

    private var audioTrack: AudioTrack? = null
    private val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNELS, AUDIO_FORMAT)
    private var currentVolume = 1.0f

    init {
        try {
            audioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNELS)
                    .setEncoding(AUDIO_FORMAT)
                    .build(),
                bufferSize * 2,
                AudioTrack.MODE_STREAM
            )
            audioTrack?.play()
            Log.d(TAG, "AudioTrack initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
        }
    }

    fun play(audioData: FloatArray) {
        if (audioTrack == null) return
        
        try {
            // Convert float to 16-bit PCM with volume scaling
            val pcmData = ShortArray(audioData.size) { i ->
                (audioData[i] * currentVolume * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            }

            val written = audioTrack?.write(
                pcmData, 
                0, 
                minOf(pcmData.size, bufferSize / 2),
                AudioTrack.WRITE_BLOCKING
            ) ?: 0
            
            Log.d(TAG, "Wrote $written samples to AudioTrack")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
        }
    }

    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1.0f)
        // AudioTrack volume is 0-1 range
        audioTrack?.setVolume(currentVolume)
        Log.d(TAG, "Volume set to $currentVolume")
    }

    fun stop() {
        try {
            audioTrack?.stop()
            Log.d(TAG, "AudioTrack stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioTrack", e)
        }
    }

    fun release() {
        try {
            audioTrack?.release()
            audioTrack = null
            Log.d(TAG, "AudioTrack released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
    }
}
