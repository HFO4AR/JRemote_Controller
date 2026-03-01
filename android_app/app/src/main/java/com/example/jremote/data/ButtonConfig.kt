package com.example.jremote.data

data class ButtonConfig(
    val id: Int,
    val name: String,
    val keyCode: Byte,
    val isToggle: Boolean = false,
    val isEnabled: Boolean = true
)
