package com.example.jremote.data

/**
 * 帧格式类型
 */
enum class FrameFormat {
    MIN,      // 最简帧（6字节，8个按钮）
    STANDARD, // 标准帧（9字节，32个按钮）
    BIT16     // 16位帧（17字节，32个按钮，高精度摇杆）
}

/**
 * 控制数据
 * 支持最简/标准/16位三种帧格式
 */
data class ControlData(
    val frameFormat: FrameFormat = FrameFormat.STANDARD,
    val leftJoystick: JoystickState = JoystickState(),
    val rightJoystick: JoystickState = JoystickState(),
    val buttons: Map<Int, Boolean> = emptyMap(),
    val routeId: Byte? = null,  // 多节点路由ID，为空表示不使用多节点
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 转换为字节数组（带帧头）
     */
    fun toByteArray(): ByteArray {
        val header = getHeader()
        val joystickData = when (frameFormat) {
            FrameFormat.MIN -> {
                leftJoystick.toByteArray8() + rightJoystick.toByteArray8()
            }
            FrameFormat.STANDARD, FrameFormat.BIT16 -> {
                if (frameFormat == FrameFormat.BIT16) {
                    leftJoystick.toByteArray16() + rightJoystick.toByteArray16()
                } else {
                    leftJoystick.toByteArray8() + rightJoystick.toByteArray8()
                }
            }
        }
        val buttonData = when (frameFormat) {
            FrameFormat.MIN -> {
                val bytes = ByteArray(1)
                buttons.entries.forEachIndexed { index, entry ->
                    if (entry.value && index < 8) {
                        bytes[0] = (bytes[0].toInt() or (1 shl index)).toByte()
                    }
                }
                bytes
            }
            else -> {
                val bytes = ByteArray(4)
                buttons.entries.forEachIndexed { index, entry ->
                    if (entry.value && index < 32) {
                        bytes[index / 8] = (bytes[index / 8].toInt() or (1 shl (index % 8))).toByte()
                    }
                }
                bytes
            }
        }

        return if (routeId != null) {
            // 多节点帧：帧头 + 路由ID + 数据
            byteArrayOf(header) + byteArrayOf(routeId) + joystickData + buttonData
        } else {
            // 标准帧：帧头 + 数据
            byteArrayOf(header) + joystickData + buttonData
        }
    }

    /**
     * 获取帧头
     */
    private fun getHeader(): Byte {
        val isMulti = routeId != null
        return when (frameFormat) {
            FrameFormat.MIN -> if (isMulti) FrameType.CONTROL_DATA_MIN_MULTI else FrameType.CONTROL_DATA_MIN
            FrameFormat.STANDARD -> if (isMulti) FrameType.CONTROL_DATA_MULTI else FrameType.CONTROL_DATA
            FrameFormat.BIT16 -> if (isMulti) FrameType.CONTROL_DATA_16_MULTI else FrameType.CONTROL_DATA_16
        }
    }

    companion object {
        /**
         * 从字节数组解析（自动检测帧格式）
         */
        fun fromByteArray(data: ByteArray): ControlData? {
            if (data.isEmpty()) return null

            val header = data[0]
            val frameFormat: FrameFormat
            val hasRoute: Boolean
            val dataOffset: Int

            when (header.toInt() and 0xFF) {
                FrameType.CONTROL_DATA_MIN.toInt() -> {
                    frameFormat = FrameFormat.MIN
                    hasRoute = false
                    dataOffset = 1
                }
                FrameType.CONTROL_DATA.toInt() -> {
                    frameFormat = FrameFormat.STANDARD
                    hasRoute = false
                    dataOffset = 1
                }
                FrameType.CONTROL_DATA_16.toInt() -> {
                    frameFormat = FrameFormat.BIT16
                    hasRoute = false
                    dataOffset = 1
                }
                FrameType.CONTROL_DATA_MIN_MULTI.toInt() -> {
                    frameFormat = FrameFormat.MIN
                    hasRoute = true
                    dataOffset = 2
                }
                FrameType.CONTROL_DATA_MULTI.toInt() -> {
                    frameFormat = FrameFormat.STANDARD
                    hasRoute = true
                    dataOffset = 2
                }
                FrameType.CONTROL_DATA_16_MULTI.toInt() -> {
                    frameFormat = FrameFormat.BIT16
                    hasRoute = true
                    dataOffset = 2
                }
                else -> return null
            }

            val joystickByteCount = if (frameFormat == FrameFormat.BIT16) 8 else 4
            val buttonByteCount = if (frameFormat == FrameFormat.MIN) 1 else 4
            val requiredLength = dataOffset + joystickByteCount + buttonByteCount

            if (data.size < requiredLength) return null

            val routeId: Byte? = if (hasRoute) data[1] else null
            val joystickData = data.copyOfRange(dataOffset, dataOffset + joystickByteCount)
            val buttonData = data.copyOfRange(dataOffset + joystickByteCount, dataOffset + joystickByteCount + buttonByteCount)

            val leftJoystick = if (frameFormat == FrameFormat.BIT16) {
                JoystickState.fromByteArray16(joystickData)
            } else {
                JoystickState.fromByteArray8(joystickData)
            }

            val rightJoystick = if (frameFormat == FrameFormat.BIT16) {
                JoystickState.fromByteArray16(joystickData.copyOfRange(4, 8))
            } else {
                JoystickState.fromByteArray8(joystickData.copyOfRange(2, 4))
            }

            val buttons = mutableMapOf<Int, Boolean>()
            val buttonCount = if (frameFormat == FrameFormat.MIN) 8 else 32
            for (i in 0 until buttonCount) {
                val byteIndex = i / 8
                val bitIndex = i % 8
                val isPressed = (buttonData[byteIndex].toInt() and (1 shl bitIndex)) != 0
                buttons[i] = isPressed
            }

            return ControlData(
                frameFormat = frameFormat,
                leftJoystick = leftJoystick,
                rightJoystick = rightJoystick,
                buttons = buttons,
                routeId = routeId
            )
        }

        /**
         * 创建急停帧
         */
        fun emergency(): ByteArray {
            return byteArrayOf(FrameType.EMERGENCY)
        }
    }
}
