package com.micplugin.rvc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RvcMonitorViewModel @Inject constructor() : ViewModel() {
    
    private val _isMonitorEnabled = MutableStateFlow(false)
    val isMonitorEnabled = _isMonitorEnabled.asStateFlow()
    
    private val _volume = MutableStateFlow(0.8f)  // 0.0 to 1.0
    val volume = _volume.asStateFlow()
    
    private val _isPlayingOutput = MutableStateFlow(false)
    val isPlayingOutput = _isPlayingOutput.asStateFlow()
    
    private var audioPlayer: RvcAudioPlayer? = null
    
    fun enableMonitor(enable: Boolean) {
        _isMonitorEnabled.value = enable
        
        if (enable) {
            if (audioPlayer == null) {
                audioPlayer = RvcAudioPlayer()
            }
        } else {
            audioPlayer?.stop()
            _isPlayingOutput.value = false
        }
    }
    
    fun setVolume(volume: Float) {
        _volume.value = volume.coerceIn(0f, 1.0f)
        audioPlayer?.setVolume(_volume.value)
    }
    
    fun playAudio(audioData: FloatArray) {
        if (!_isMonitorEnabled.value) return
        
        viewModelScope.launch {
            _isPlayingOutput.value = true
            try {
                audioPlayer?.play(audioData.map { it * _volume.value }.toFloatArray())
            } finally {
                _isPlayingOutput.value = false
            }
        }
    }
    
    fun stopPlayback() {
        audioPlayer?.stop()
        _isPlayingOutput.value = false
    }
    
    override fun onCleared() {
        super.onCleared()
        audioPlayer?.stop()
        audioPlayer = null
    }
}
