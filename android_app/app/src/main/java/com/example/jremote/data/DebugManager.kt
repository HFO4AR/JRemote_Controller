package com.example.jremote.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 调试消息管理器
 * 统一管理所有调试信息的输出
 */
class DebugManager {

    private val _debugMessages = MutableStateFlow<List<DebugMessage>>(emptyList())
    val debugMessages: StateFlow<List<DebugMessage>> = _debugMessages.asStateFlow()

    private val maxMessages = 1000

    /**
     * 添加调试消息
     */
    fun log(level: DebugLevel, tag: String, message: String) {
        val newMessage = DebugMessage(
            level = level,
            tag = tag,
            message = message
        )
        _debugMessages.value = (_debugMessages.value + newMessage).takeLast(maxMessages)
    }

    /**
     * 添加 info 级别消息
     */
    fun info(tag: String, message: String) {
        log(DebugLevel.INFO, tag, message)
    }

    /**
     * 添加 warning 级别消息
     */
    fun warning(tag: String, message: String) {
        log(DebugLevel.WARNING, tag, message)
    }

    /**
     * 添加 error 级别消息
     */
    fun error(tag: String, message: String) {
        log(DebugLevel.ERROR, tag, message)
    }

    /**
     * 清空所有消息
     */
    fun clear() {
        _debugMessages.value = emptyList()
    }
}
