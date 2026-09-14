package com.micplugin.rvc

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class RvcAudioRecorder {
    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNELS = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS, AUDIO_FORMAT)
    private val audioBuffer = ShortArray(bufferSize)
    private val audioBufferFloat = FloatArray(bufferSize)

    private val _recordingState = MutableStateFlow(false)
    val recordingState = _recordingState.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)  // 0-1 range
    val audioLevel = _audioLevel.asStateFlow()

    private val audioChunkListeners = mutableListOf<(FloatArray) -> Unit>()

    fun start(): Boolean {
        if (isRecording) return false
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNELS,
                AUDIO_FORMAT,
                bufferSize * 2
            )
            audioRecord?.startRecording()
            isRecording = true
            _recordingState.value = true
            return true
        } catch (e: Exception) {
            isRecording = false
            _recordingState.value = false
            return false
        }
    }

    fun stop() {
        isRecording = false
        _recordingState.value = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    suspend fun captureChunk(chunkSize: Int): FloatArray? {
        if (!isRecording || audioRecord == null) return null

        val chunk = FloatArray(chunkSize)
        val readSize = audioRecord!!.read(audioBuffer, 0, minOf(chunkSize, audioBuffer.size))
        
        if (readSize > 0) {
            // Convert short to float and normalize to -1.0 to 1.0
            for (i in 0 until readSize) {
                audioBufferFloat[i] = audioBuffer[i] / 32768f
            }
            
            // Copy to chunk
            audioBufferFloat.copyInto(chunk, 0, 0, minOf(readSize, chunkSize))
            
            // Calculate level
            val rms = sqrt(chunk.map { it * it }.sum() / chunk.size)
            _audioLevel.value = minOf(1f, rms * 2f)
            
            return chunk
        }
        
        return null
    }

    fun onAudioChunk(listener: (FloatArray) -> Unit) {
        audioChunkListeners.add(listener)
    }

    private fun emitChunk(chunk: FloatArray) {
        audioChunkListeners.forEach { it(chunk) }
    }
}
