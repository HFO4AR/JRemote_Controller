package com.example.jremote.data

data class AppSettings(
    val sendIntervalMs: Long = 20L,
    val showDebugPanel: Boolean = true,
    val hapticFeedback: Boolean = true,
    val autoReconnect: Boolean = false
)
