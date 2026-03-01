package com.example.jremote.data

data class JoystickState(
    val x: Float = 0f,
    val y: Float = 0f,
    val angle: Float = 0f,
    val distance: Float = 0f
) {
    fun toByteArray(): ByteArray {
        val buffer = ByteArray(2)
        buffer[0] = (x * 127).toInt().toByte()
        buffer[1] = (y * 127).toInt().toByte()
        return buffer
    }
}
