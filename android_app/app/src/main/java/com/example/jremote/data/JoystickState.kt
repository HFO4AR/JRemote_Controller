package com.example.jremote.data

data class JoystickState(
    val x: Float = 0f,
    val y: Float = 0f,
    val angle: Float = 0f,
    val distance: Float = 0f
) {
    /**
     * 转换为 8 位字节数组（-127 ~ 127）
     */
    fun toByteArray8(): ByteArray {
        return byteArrayOf(
            (x * 127).toInt().toByte(),
            (y * 127).toInt().toByte()
        )
    }

    /**
     * 转换为 16 位字节数组（大端序，-32767 ~ 32767）
     */
    fun toByteArray16(): ByteArray {
        val x16 = (x * 32767).toInt().toShort()
        val y16 = (y * 32767).toInt().toShort()
        return byteArrayOf(
            (x16.toInt() shr 8).toByte(),
            x16.toByte(),
            (y16.toInt() shr 8).toByte(),
            y16.toByte()
        )
    }

    companion object {
        /**
         * 从 8 位字节数组解析
         */
        fun fromByteArray8(data: ByteArray): JoystickState {
            if (data.size < 2) return JoystickState()
            val x = data[0].toInt().toFloat() / 127f
            val y = data[1].toInt().toFloat() / 127f
            return fromNormalized(x, y)
        }

        /**
         * 从 16 位字节数组解析（大端序）
         */
        fun fromByteArray16(data: ByteArray): JoystickState {
            if (data.size < 4) return JoystickState()
            val x = ((data[0].toInt() shl 8) or (data[1].toInt() and 0xFF)).toShort().toFloat() / 32767f
            val y = ((data[2].toInt() shl 8) or (data[3].toInt() and 0xFF)).toShort().toFloat() / 32767f
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
