package com.example.jremote.data

data class DiscoveredDevice(
    val name: String,
    val address: String,      // BLE: MAC 地址, Wi-Fi: IP 地址
    val ip: String = "",       // Wi-Fi 模式使用
    val port: Int = 1034,      // Wi-Fi 模式使用
    val connectionType: ConnectionType,
    val rssi: Int = 0
)
