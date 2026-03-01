package com.example.jremote.data

data class ConnectionStatus(
    val isConnected: Boolean = false,
    val deviceName: String = "",
    val deviceAddress: String = "",
    val connectionType: ConnectionType = ConnectionType.BLUETOOTH,
    val signalStrength: Int = 0,
    val latency: Long = 0
)

enum class ConnectionType {
    BLUETOOTH,
    WIFI,
    USB
}
