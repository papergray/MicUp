package com.micplugin.rvc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class RvcConnectionViewModel @Inject constructor() : ViewModel() {
    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected(null))
    val connectionStatus = _connectionStatus.asStateFlow()
    
    private val _settings = MutableStateFlow(RvcSettings())
    val settings = _settings.asStateFlow()
    
    private var apiClient: RvcApiClient? = null
    
    fun updateSettings(settings: RvcSettings) {
        _settings.value = settings
    }
    
    fun testConnection(endpoint: String) {
        viewModelScope.launch {
            _connectionStatus.value = ConnectionStatus.Connecting
            try {
                apiClient = RvcApiClient(endpoint)
                val startTime = System.currentTimeMillis()
                
                val result = apiClient?.healthCheck()
                val latency = System.currentTimeMillis() - startTime
                
                if (result?.isSuccess == true) {
                    _connectionStatus.value = ConnectionStatus.Connected(latency, System.currentTimeMillis())
                    // Update settings with endpoint
                    _settings.value = _settings.value.copy(kaggleEndpoint = endpoint)
                    // Start periodic health checks
                    startPeriodicHealthCheck(endpoint)
                } else {
                    _connectionStatus.value = ConnectionStatus.Disconnected(result?.exceptionOrNull()?.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.Error(e.message ?: "Connection failed")
            }
        }
    }
    
    private fun startPeriodicHealthCheck(endpoint: String) {
        viewModelScope.launch {
            while (true) {
                delay(30000)  // Check every 30 seconds
                try {
                    val startTime = System.currentTimeMillis()
                    val result = apiClient?.healthCheck()
                    val latency = System.currentTimeMillis() - startTime
                    
                    if (result?.isSuccess == true) {
                        _connectionStatus.value = ConnectionStatus.Connected(latency, System.currentTimeMillis())
                    } else {
                        _connectionStatus.value = ConnectionStatus.Disconnected("Health check failed")
                    }
                } catch (e: Exception) {
                    _connectionStatus.value = ConnectionStatus.Error(e.message ?: "Health check error")
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        apiClient?.close()
    }
}
