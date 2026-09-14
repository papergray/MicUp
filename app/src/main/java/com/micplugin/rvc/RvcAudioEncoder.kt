package com.micplugin.rvc

import android.media.AudioFormat
import android.media.AudioTrack
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.use
import java.util.Base64

object RvcAudioEncoder {
    
    /**
     * Convert FloatArray audio to base64 WAV string
     */
    fun encodeToBase64(audioData: FloatArray, sampleRate: Int = 44100): String {
        val wavBytes = floatArrayToWav(audioData, sampleRate)
        return Base64.getEncoder().encodeToString(wavBytes)
    }
    
    /**
     * Convert base64 WAV string back to FloatArray
     */
    fun decodeFromBase64(base64: String): FloatArray {
        val wavBytes = Base64.getDecoder().decode(base64)
        return wavToFloatArray(wavBytes)
    }
    
    /**
     * Convert FloatArray to WAV bytes
     */
    private fun floatArrayToWav(audioData: FloatArray, sampleRate: Int): ByteArray {
        return ByteArrayOutputStream().use { baos ->
            // Convert float to 16-bit PCM
            val shortArray = FloatArray(audioData.size) { i ->
                (audioData[i] * 32767f).toInt().toShort().toInt().toFloat()
            }
            val pcmData = ShortArray(audioData.size) { i ->
                (audioData[i] * 32767f).toInt().toShort()
            }
            
            val pcmBytes = ByteArray(pcmData.size * 2)
            val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in pcmData) {
                buffer.putShort(sample)
            }
            
            // WAV header
            val channels = 1
            val bitsPerSample = 16
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            val subchunk2Size = pcmBytes.size
            val chunkSize = 36 + subchunk2Size
            
            // RIFF header
            baos.write("RIFF".toByteArray())
            baos.write(intToBytes(chunkSize))
            baos.write("WAVE".toByteArray())
            
            // fmt subchunk
            baos.write("fmt ".toByteArray())
            baos.write(intToBytes(16))  // Subchunk1Size
            baos.write(shortToBytes(1.toShort()))  // AudioFormat (PCM)
            baos.write(shortToBytes(channels.toShort()))
            baos.write(intToBytes(sampleRate))
            baos.write(intToBytes(byteRate))
            baos.write(shortToBytes(blockAlign.toShort()))
            baos.write(shortToBytes(bitsPerSample.toShort()))
            
            // data subchunk
            baos.write("data".toByteArray())
            baos.write(intToBytes(subchunk2Size))
            baos.write(pcmBytes)
            
            baos.toByteArray()
        }
    }
    
    /**
     * Convert WAV bytes back to FloatArray
     */
    private fun wavToFloatArray(wavBytes: ByteArray): FloatArray {
        // Skip WAV header (typically 44 bytes)
        val headerSize = 44
        if (wavBytes.size < headerSize) return FloatArray(0)
        
        val pcmSize = wavBytes.size - headerSize
        val shortArray = ShortArray(pcmSize / 2)
        
        val buffer = ByteBuffer.wrap(wavBytes, headerSize, pcmSize).order(ByteOrder.LITTLE_ENDIAN)
        for (i in shortArray.indices) {
            shortArray[i] = buffer.short
        }
        
        // Convert 16-bit PCM to float (-1.0 to 1.0)
        return FloatArray(shortArray.size) { i ->
            shortArray[i] / 32768f
        }
    }
    
    private fun intToBytes(value: Int): ByteArray {
        return ByteArray(4).also { bytes ->
            bytes[0] = (value and 0xFF).toByte()
            bytes[1] = ((value shr 8) and 0xFF).toByte()
            bytes[2] = ((value shr 16) and 0xFF).toByte()
            bytes[3] = ((value shr 24) and 0xFF).toByte()
        }
    }
    
    private fun shortToBytes(value: Short): ByteArray {
        return ByteArray(2).also { bytes ->
            bytes[0] = (value.toInt() and 0xFF).toByte()
            bytes[1] = ((value.toInt() shr 8) and 0xFF).toByte()
        }
    }
}
