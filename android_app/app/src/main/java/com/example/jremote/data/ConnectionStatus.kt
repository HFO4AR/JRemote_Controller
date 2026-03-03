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
    WIFI_AP,     // Wi-Fi AP 模式（直连）
    WIFI_LAN,    // Wi-Fi 局域网模式
    USB
}
