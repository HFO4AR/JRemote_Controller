package com.example.jremote.data

data class ControlData(
    val leftJoystick: JoystickState = JoystickState(),
    val rightJoystick: JoystickState = JoystickState(),
    val buttons: Map<Int, Boolean> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toByteArray(): ByteArray {
        val joystickData = leftJoystick.toByteArray() + rightJoystick.toByteArray()
        val buttonData = ByteArray(4)
        buttons.entries.forEachIndexed { index, entry ->
            if (entry.value && index < 32) {
                buttonData[index / 8] = (buttonData[index / 8].toInt() or (1 shl (index % 8))).toByte()
            }
        }
        return joystickData + buttonData
    }
}
