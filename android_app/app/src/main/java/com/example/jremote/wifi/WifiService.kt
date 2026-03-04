package com.example.jremote.wifi

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.jremote.data.ConnectionStatus
import com.example.jremote.data.ConnectionType
import com.example.jremote.data.DebugLevel
import com.example.jremote.data.DebugManager
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
    private val wifiManager: WifiManager = application.getSystemService(Context.WIFI_SERVICE) as WifiManager

    // 调试管理器
    private val debugManager = DebugManager()
    val debugMessages = debugManager.debugMessages

    private val _connectionStatus = MutableStateFlow(ConnectionStatus())
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _latency = MutableStateFlow<Int?>(null)
    val latency: StateFlow<Int?> = _latency.asStateFlow()

    private val _wifiRssi = MutableStateFlow<Int?>(null)
    val wifiRssi: StateFlow<Int?> = _wifiRssi.asStateFlow()

    private var socket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null
    private var targetPort: Int = DATA_PORT
    private var receiveJob: Job? = null
    private var pingJob: Job? = null

    private var isConnected = false
    private var lastPingTime = 0L  // 上次发送 ping 的时间（仅用于 ping 延迟计算）

    fun connect(device: DiscoveredDevice, onComplete: (Boolean) -> Unit = {}) {
        serviceScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    addDebugMessage(DebugLevel.INFO, TAG, "正在连接 ${device.name}...")
                    addDebugMessage(DebugLevel.INFO, TAG, "目标地址: ${device.ip}:${device.port}")

                    targetAddress = InetAddress.getByName(device.ip)
                    targetPort = device.port
                    addDebugMessage(DebugLevel.INFO, TAG, "解析地址成功: $targetAddress")

                    // 创建socket，绑定到数据端口
                    socket = DatagramSocket(DATA_PORT).apply {
                        soTimeout = 5000
                        reuseAddress = true
                    }
                    addDebugMessage(DebugLevel.INFO, TAG, "Socket 创建成功，本地端口: ${socket?.localPort}")

                    // 启动接收线程
                    startReceiving()
                    addDebugMessage(DebugLevel.INFO, TAG, "接收线程已启动")

                    // 尝试发送测试数据
                    val testData = byteArrayOf(0xAA.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)
                    addDebugMessage(DebugLevel.INFO, TAG, "发送测试数据...")
                    sendData(testData)
                    addDebugMessage(DebugLevel.INFO, TAG, "测试数据已发送")

                    // 短暂等待
                    Thread.sleep(500)
                }

                // 直接标记为已连接（UDP是无连接的，只要能发送数据就认为连接成功）
                isConnected = true
                _connectionStatus.value = ConnectionStatus(
                    isConnected = true,
                    deviceName = device.name,
                    deviceAddress = device.ip,
                    connectionType = device.connectionType,
                    signalStrength = getWifiRssi() ?: 0
                )
                startPingLoop()
                startWifiRssiMonitor()
                addDebugMessage(DebugLevel.INFO, TAG, "连接成功: ${device.ip}:${device.port}")
                onComplete(true)
            } catch (e: Exception) {
                addDebugMessage(DebugLevel.ERROR, TAG, "连接失败: ${e.message}")
                e.printStackTrace()
                disconnect()
                onComplete(false)
            }
        }
    }

    fun disconnect() {
        isConnected = false
        receiveJob?.cancel()
        pingJob?.cancel()

        socket?.close()
        socket = null
        targetAddress = null
        lastPingTime = 0L

        _connectionStatus.value = ConnectionStatus()
        _latency.value = null
        _wifiRssi.value = null

        addDebugMessage(DebugLevel.INFO, TAG, "已断开连接")
    }

    fun sendData(data: ByteArray) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                if (!isConnected || socket == null || targetAddress == null) {
                    withContext(Dispatchers.Main) {
                        addDebugMessage(DebugLevel.WARNING, TAG, "发送失败: 未连接或socket无效 isConnected=$isConnected socket=${socket != null} target=${targetAddress != null}")
                    }
                    return@launch
                }

                val packet = DatagramPacket(data, data.size, targetAddress, targetPort)
                socket?.send(packet)
                // 只有 ping 请求才记录发送时间，用于延迟计算
                if (data.size == 1 && data[0] == PING_REQUEST) {
                    lastPingTime = System.currentTimeMillis()
                    withContext(Dispatchers.Main) {
                        addDebugMessage(DebugLevel.INFO, TAG, "Ping 发送成功")
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: e.toString()
                withContext(Dispatchers.Main) {
                    addDebugMessage(DebugLevel.ERROR, TAG, "发送失败: $errorMsg")
                }
            }
        }
    }

    private fun testConnection(): Boolean {
        val startTime = System.currentTimeMillis()
        addDebugMessage(DebugLevel.INFO, TAG, "发送 Ping 请求到 ${targetAddress}:${targetPort}")
        sendData(byteArrayOf(PING_REQUEST))

        // 等待响应
        try {
            socket?.soTimeout = 3000
            val buffer = ByteArray(64)
            val response = DatagramPacket(buffer, buffer.size)
            addDebugMessage(DebugLevel.INFO, TAG, "等待响应...")
            socket?.receive(response)

            val receivedData = buffer.copyOf(response.length)
            addDebugMessage(DebugLevel.INFO, TAG, "收到响应: ${receivedData.contentToString()}, 长度: ${response.length}, 来自: ${response.address}")

            if (receivedData.isNotEmpty() && receivedData[0] == PING_RESPONSE) {
                _latency.value = (System.currentTimeMillis() - startTime).toInt()
                addDebugMessage(DebugLevel.INFO, TAG, "Ping 成功! 延迟: ${_latency.value}ms")
                return true
            } else {
                addDebugMessage(DebugLevel.WARNING, TAG, "收到无效响应: ${receivedData.contentToString()}")
            }
        } catch (e: Exception) {
            addDebugMessage(DebugLevel.ERROR, TAG, "Ping 失败: ${e.message}")
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
                    val currentTime = System.currentTimeMillis()

                    // 检查是否是响应 (0x50)
                    if (data.isNotEmpty() && data[0] == PING_RESPONSE) {
                        // 根据上次 ping 发送时间计算延迟
                        if (lastPingTime > 0) {
                            _latency.value = (currentTime - lastPingTime).toInt()
                            lastPingTime = 0  // 重置，避免重复计算
                        }
                    } else if (data.isNotEmpty()) {
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
                    sendData(byteArrayOf(PING_REQUEST))
                }
            }
        }
    }

    private fun startWifiRssiMonitor() {
        serviceScope.launch {
            while (isActive && isConnected) {
                try {
                    val rssi = wifiManager.connectionInfo?.rssi
                    if (rssi != null && rssi != -127) {  // -127 表示无效
                        _wifiRssi.value = rssi
                    }
                } catch (e: Exception) {
                    // 忽略错误
                }
                delay(1000)
            }
        }
    }

    fun getWifiRssi(): Int? {
        return try {
            val rssi = wifiManager.connectionInfo?.rssi
            if (rssi != null && rssi != -127) rssi else null
        } catch (e: Exception) {
            null
        }
    }

    private fun handleReceivedData(data: ByteArray) {
        // 显示收到的数据，标记为 MUC（来自 MCU/ESP32）
        try {
            val message = String(data, Charsets.UTF_8).trimEnd('\u0000')
            addDebugMessage(DebugLevel.INFO, "MUC", "$message")
        } catch (e: Exception) {
            addDebugMessage(DebugLevel.INFO, "MUC", "收到数据: ${data.contentToString()}")
        }
    }

    private fun addDebugMessage(level: DebugLevel, tag: String, message: String) {
        debugManager.log(level, tag, message)
    }

    fun clearDebugMessages() {
        debugManager.clear()
    }
}
