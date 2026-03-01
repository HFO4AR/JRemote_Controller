package com.example.jremote.data

data class ButtonConfig(
    val id: Int,
    val name: String,
    val keyCode: Byte,
    val isToggle: Boolean = false,
    val isPressed: Boolean = false
)

data class ButtonState(
    val config: ButtonConfig,
    val isPressed: Boolean = false,
    val isToggled: Boolean = false
)
