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
    val lastConnectionMode: ConnectionType = ConnectionType.BLUETOOTH,
    val lastConnectedDeviceIp: String? = null,  // Wi-Fi 模式使用
    val frameFormat: FrameFormat = FrameFormat.STANDARD,  // 帧格式
    val joystickAutoReturn: JoystickAutoReturn = JoystickAutoReturn()  // 摇杆自动回中设置
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

// 摇杆自动回中设置
data class JoystickAutoReturn(
    val leftX: Boolean = true,  // 左摇杆 X 轴自动回中
    val leftY: Boolean = true,  // 左摇杆 Y 轴自动回中
    val rightX: Boolean = true, // 右摇杆 X 轴自动回中
    val rightY: Boolean = true  // 右摇杆 Y 轴自动回中
)
