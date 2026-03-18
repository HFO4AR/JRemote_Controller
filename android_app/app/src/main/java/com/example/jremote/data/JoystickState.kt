package com.example.jremote.data

data class JoystickState(
    val x: Float = 0f,
    val y: Float = 0f,
    val angle: Float = 0f,
    val distance: Float = 0f
) {
    /**
     * 转换为 8 位字节数组（0 ~ 255，无符号）
     * 原值范围 -127~127 映射到 0~255（中心点 128）
     */
    fun toByteArray8(): ByteArray {
        // 有符号值 (-127~127) 转换为无符号 (0~255)
        // x = -127 -> 0, x = 0 -> 128, x = 127 -> 255
        val xUnsigned = ((x * 127).toInt() + 128).coerceIn(0, 255)
        val yUnsigned = ((y * 127).toInt() + 128).coerceIn(0, 255)
        return byteArrayOf(xUnsigned.toByte(), yUnsigned.toByte())
    }

    /**
     * 转换为 16 位字节数组（大端序，0 ~ 65535，无符号）
     * 原值范围 -32767~32767 映射到 0~65535（中心点 32768）
     */
    fun toByteArray16(): ByteArray {
        // 有符号值 (-32767~32767) 转换为无符号 (0~65535)
        // x = -1.0 -> 0, x = 0.0 -> 32768, x = 1.0 -> 65535
        val xUnsigned = ((x * 32767).toInt() + 32768).coerceIn(0, 65535)
        val yUnsigned = ((y * 32767).toInt() + 32768).coerceIn(0, 65535)
        return byteArrayOf(
            (xUnsigned shr 8).toByte(),
            xUnsigned.toByte(),
            (yUnsigned shr 8).toByte(),
            yUnsigned.toByte()
        )
    }

    companion object {
        /**
         * 从 8 位字节数组解析（无符号 0~255）
         */
        fun fromByteArray8(data: ByteArray): JoystickState {
            if (data.size < 2) return JoystickState()
            // 无符号值 (0~255) 转换回有符号 (-127~127)
            val xSigned = (data[0].toInt() and 0xFF) - 128
            val ySigned = (data[1].toInt() and 0xFF) - 128
            val x = xSigned.toFloat() / 127f
            val y = ySigned.toFloat() / 127f
            return fromNormalized(x, y)
        }

        /**
         * 从 16 位字节数组解析（大端序，无符号 0~65535）
         */
        fun fromByteArray16(data: ByteArray): JoystickState {
            if (data.size < 4) return JoystickState()
            // 无符号值 (0~65535) 转换回有符号 (-32767~32767)
            val xUnsigned = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            val yUnsigned = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
            val xSigned = xUnsigned - 32768
            val ySigned = yUnsigned - 32768
            val x = xSigned.toFloat() / 32767f
            val y = ySigned.toFloat() / 32767f
            return fromNormalized(x, y)
        }

        /**
         * 从归一化值创建
         */
        private fun fromNormalized(x: Float, y: Float): JoystickState {
            val distance = kotlin.math.min(1f, kotlin.math.sqrt(x * x + y * y))
            val angle = kotlin.math.atan2(y.toDouble(), x.toDouble()).toFloat()
            return JoystickState(
                x = x.coerceIn(-1f, 1f),
                y = y.coerceIn(-1f, 1f),
                angle = angle,
                distance = distance
            )
        }
    }
}
