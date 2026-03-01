package com.example.jremote.data

data class DebugMessage(
    val timestamp: Long = System.currentTimeMillis(),
    val level: DebugLevel,
    val tag: String,
    val message: String
)

enum class DebugLevel {
    INFO,
    WARNING,
    ERROR
}
