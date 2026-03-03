package com.example.jremote.data

data class AppSettings(
    val sendIntervalMs: Long = 20L,
    val showDebugPanel: Boolean = true,
    val hapticFeedback: Boolean = true,
    val autoReconnect: Boolean = false,
    val toggleButtonLayout: ToggleButtonLayout = ToggleButtonLayout.HORIZONTAL,
    val lastConnectedDeviceAddress: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val lastConnectionMode: ConnectionMode = ConnectionMode.BLE,
    val lastConnectedDeviceIp: String? = null  // Wi-Fi 模式使用
)

enum class ToggleButtonLayout {
    HORIZONTAL,
    GRID_2X2
}

enum class ThemeMode {
    SYSTEM,  // 跟随系统
    DARK,    // 深色
    LIGHT    // 浅色
}
