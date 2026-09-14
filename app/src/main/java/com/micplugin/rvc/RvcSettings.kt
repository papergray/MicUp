package com.micplugin.rvc

import kotlinx.serialization.Serializable

enum class F0Method {
    CREPE, HARVEST, PARSELMOUTH, RMVPE
}

enum class InputSource {
    MICROPHONE, FILE
}

@Serializable
data class RvcSettings(
    // Model
    val modelName: String = "Okada Kazuchika",
    val modelUrl: String = "",
    
    // Input
    val inputSource: String = "MICROPHONE",
    
    // Voice parameters
    val speakerId: Int = 0,
    val pitch: Int = 0,  // -12 to +12 semitones
    val formant: Float = 0.0f,  // -3.0 to +3.0
    val indexRate: Float = 0.5f,  // 0.0 to 1.0 (search mix)
    val protectConsonants: Boolean = true,
    
    // Processing
    val chunkSize: Int = 1024,  // 1 to 1000
    val hopLength: Int = 256,  // 1 to 512
    val f0Method: String = "RMVPE",  // CREPE, HARVEST, PARSELMOUTH, RMVPE
    
    // Kaggle endpoint
    val kaggleEndpoint: String = "",  // User provides this
)
