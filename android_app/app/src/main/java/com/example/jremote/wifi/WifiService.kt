package com.example.jremote.wifi

import android.app.Application
import android.util.Log
import com.example.jremote.data.ConnectionStatus
import com.example.jremote.data.ConnectionType
import com.example.jremote.data.DebugLevel
import com.example.jremote.data.DebugMessage
import com.example.jremote.data.DiscoveredDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class WifiService(private val application: Application) {

    companion object {
        private const val TAG = "WifiService"
        const val DATA_PORT = 1034
        const val PING_REQUEST: Byte = 0x70
        const val PING_RESPONSE: Byte = 0x50
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionStatus = MutableStateFlow(ConnectionStatus())
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _latency = MutableStateFlow<Int?>(null)
    val latency: StateFlow<Int?> = _latency.asStateFlow()

    private val _debugMessages = MutableStateFlow<List<DebugMessage>>(emptyList())
    val debugMessages: StateFlow<List<DebugMessage>> = _debugMessages.asStateFlow()

    private var socket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null
    private var targetPort: Int = DATA_PORT
    private var receiveJob: Job? = null
    private var pingJob: Job? = null

    private var isConnected = false
    private var lastPingTime = 0L

    fun connect(device: DiscoveredDevice): Boolean {
        return try {
            addDebugMessage(DebugLevel.INFO, TAG, "正在连接 ${device.name}...")

            targetAddress = InetAddress.getByName(device.ip)
            targetPort = device.port

            socket = DatagramSocket(DATA_PORT).apply {
                soTimeout = 5000
                reuseAddress = true
            }

            // 启动接收线程
            startReceiving()

            // 尝试 ping 测试连接
            val pingSuccess = testConnection()

            if (pingSuccess) {
                isConnected = true
                _connectionStatus.value = ConnectionStatus(
                    isConnected = true,
                    deviceName = device.name,
                    deviceAddress = device.ip,
                    connectionType = ConnectionType.WIFI,
                    signalStrength = 0
                )
                startPingLoop()
                addDebugMessage(DebugLevel.INFO, TAG, "连接成功: ${device.ip}:${device.port}")
                true
            } else {
                disconnect()
                addDebugMessage(DebugLevel.ERROR, TAG, "连接测试失败")
                false
            }
        } catch (e: Exception) {
            addDebugMessage(DebugLevel.ERROR, TAG, "连接失败: ${e.message}")
            disconnect()
            false
        }
    }

    fun disconnect() {
        isConnected = false
        receiveJob?.cancel()
        pingJob?.cancel()

        socket?.close()
        socket = null
        targetAddress = null

        _connectionStatus.value = ConnectionStatus()
        _latency.value = null

        addDebugMessage(DebugLevel.INFO, TAG, "已断开连接")
    }

    fun sendData(data: ByteArray) {
        if (!isConnected || socket == null || targetAddress == null) {
            return
        }

        try {
            val packet = DatagramPacket(data, data.size, targetAddress, targetPort)
            socket?.send(packet)
        } catch (e: Exception) {
            addDebugMessage(DebugLevel.ERROR, TAG, "发送失败: ${e.message}")
        }
    }

    private fun testConnection(): Boolean {
        val startTime = System.currentTimeMillis()
        sendData(byteArrayOf(PING_REQUEST))

        // 等待响应
        try {
            socket?.soTimeout = 3000
            val buffer = ByteArray(1)
            val response = DatagramPacket(buffer, buffer.size)
            socket?.receive(response)

            if (buffer[0] == PING_RESPONSE) {
                _latency.value = (System.currentTimeMillis() - startTime).toInt()
                return true
            }
        } catch (e: Exception) {
            addDebugMessage(DebugLevel.WARNING, TAG, "Ping 超时")
        }
        return false
    }

    private fun startReceiving() {
        receiveJob = serviceScope.launch {
            val buffer = ByteArray(64)

            while (isActive && socket != null) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)

                    val data = packet.data.copyOf(packet.length)

                    // 检查是否是 ping 响应
                    if (data.isNotEmpty() && data[0] == PING_RESPONSE) {
                        _latency.value = (System.currentTimeMillis() - lastPingTime).toInt()
                    } else {
                        // 处理其他数据（设备响应）
                        handleReceivedData(data)
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        delay(100)
                    }
                }
            }
        }
    }

    private fun startPingLoop() {
        pingJob = serviceScope.launch {
            while (isActive && isConnected) {
                delay(2000)
                if (isConnected) {
                    lastPingTime = System.currentTimeMillis()
                    sendData(byteArrayOf(PING_REQUEST))
                }
            }
        }
    }

    private fun handleReceivedData(data: ByteArray) {
        // 可以在这里处理设备返回的数据
        // 目前仅用于调试显示
    }

    private fun addDebugMessage(level: DebugLevel, tag: String, message: String) {
        val newMessage = DebugMessage(level = level, tag = tag, message = message)
        _debugMessages.value = (_debugMessages.value + newMessage).takeLast(100)
    }

    fun clearDebugMessages() {
        _debugMessages.value = emptyList()
    }
}
