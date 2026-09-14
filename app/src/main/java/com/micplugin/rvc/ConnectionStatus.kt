package com.micplugin.rvc

sealed class ConnectionStatus {
    object Connecting : ConnectionStatus()
    data class Connected(val latencyMs: Long, val timestamp: Long) : ConnectionStatus()
    data class Disconnected(val reason: String?) : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}
