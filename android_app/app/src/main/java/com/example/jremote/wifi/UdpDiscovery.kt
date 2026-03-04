package com.example.jremote.wifi

import android.content.Context
import android.util.Log
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets

class UdpDiscovery(private val context: Context) {

    companion object {
        private const val TAG = "UdpDiscovery"
        const val DISCOVERY_PORT = 1035
        const val DISCOVERY_MESSAGE = "JREMOTE_DISCOVER"
        const val DEVICE_PREFIX = "JREMOTE:"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wifiUtils = WifiUtils(context)

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _debugMessages = MutableStateFlow<List<DebugMessage>>(emptyList())
    val debugMessages: StateFlow<List<DebugMessage>> = _debugMessages.asStateFlow()

    private var socket: DatagramSocket? = null
    private var scanJob: Job? = null

    // 响应回调
    private var onDeviceFound: ((DiscoveredDevice) -> Unit)? = null

    fun setOnDeviceFound(callback: (DiscoveredDevice) -> Unit) {
        onDeviceFound = callback
    }

    fun startDiscovery(mode: ConnectionType) {
        if (_isScanning.value) return

        _isScanning.value = true
        _isScanning.value = true
        _discoveredDevices.value = emptyList()

        scanJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    socket = DatagramSocket(DISCOVERY_PORT).apply {
                        reuseAddress = true
                    }

                    val buffer = ByteArray(1024)

                    while (isActive && _isScanning.value) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket?.receive(packet)

                            val data = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                            val address = packet.address.hostAddress ?: ""

                            addDebugMessage(DebugLevel.INFO, TAG, "收到响应: $data from $address")

                            // 解析设备信息
                            if (data.startsWith(DEVICE_PREFIX)) {
                                parseDeviceResponse(data, address, mode)
                            }
                        } catch (e: Exception) {
                            // 超时或其他错误，继续扫描
                        }
                    }
                }
            } catch (e: Exception) {
                addDebugMessage(DebugLevel.ERROR, TAG, "扫描出错: ${e.message}")
            } finally {
                _isScanning.value = false
            }
        }

        // 发送广播
        scope.launch {
            sendBroadcast()
        }
    }

    private suspend fun sendBroadcast() {
        withContext(Dispatchers.IO) {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                socket.soTimeout = 5000

                val message = DISCOVERY_MESSAGE.toByteArray(StandardCharsets.UTF_8)

                // 获取手机 IP 并计算广播地址
                try {
                    val wifiAddr = wifiUtils.getCurrentWifiIp()
                    if (wifiAddr != null) {
                        // 根据手机 IP 计算广播地址
                        val parts = wifiAddr.split(".")
                        if (parts.size == 4) {
                            val broadcastAddr = "${parts[0]}.${parts[1]}.${parts[2]}.255"
                            addDebugMessage(DebugLevel.INFO, TAG, "发送广播到: $broadcastAddr")
                            val address = InetAddress.getByName(broadcastAddr)
                            val packet = DatagramPacket(message, message.size, address, DISCOVERY_PORT)
                            socket.send(packet)
                        }
                    }
                } catch (e: Exception) {
                    addDebugMessage(DebugLevel.WARNING, TAG, "获取广播地址失败: ${e.message}")
                }

                // 也尝试发送到通用广播地址
                try {
                    val broadcastAddress = "255.255.255.255"
                    val address = InetAddress.getByName(broadcastAddress)
                    val packet = DatagramPacket(message, message.size, address, DISCOVERY_PORT)
                    socket.send(packet)
                    addDebugMessage(DebugLevel.INFO, TAG, "发送广播到: $broadcastAddress")
                } catch (e: Exception) {
                    addDebugMessage(DebugLevel.WARNING, TAG, "通用广播失败: ${e.message}")
                }

                socket.close()

                // 持续发送广播 5 秒
                var count = 0
                while (_isScanning.value && count < 10) {
                    delay(500)
                    count++
                }

            } catch (e: Exception) {
                addDebugMessage(DebugLevel.ERROR, TAG, "广播失败: ${e.message}")
            }
        }
    }

    private fun parseDeviceResponse(data: String, address: String, mode: ConnectionType) {
        try {
            // 格式: JREMOTE:{设备名称}:{IP}:{端口}
            val parts = data.split(":")
            if (parts.size >= 4) {
                val deviceName = parts[1]
                val ip = parts[2]
                val port = parts[3].toIntOrNull() ?: 1034

                val device = DiscoveredDevice(
                    name = deviceName,
                    address = ip,
                    ip = ip,
                    port = port,
                    connectionType = mode,
                    rssi = 0
                )

                val currentList = _discoveredDevices.value.toMutableList()
                val existingIndex = currentList.indexOfFirst { it.ip == ip }

                if (existingIndex >= 0) {
                    currentList[existingIndex] = device
                } else {
                    currentList.add(device)
                }

                _discoveredDevices.value = currentList
                onDeviceFound?.invoke(device)

                addDebugMessage(DebugLevel.INFO, TAG, "发现设备: $deviceName ($ip:$port)")
            }
        } catch (e: Exception) {
            addDebugMessage(DebugLevel.WARNING, TAG, "解析设备响应失败: ${e.message}")
        }
    }

    fun stopDiscovery() {
        _isScanning.value = false
        scanJob?.cancel()
        socket?.close()
        socket = null
        _isScanning.value = false
    }

    fun clearDevices() {
        _discoveredDevices.value = emptyList()
    }

    private fun addDebugMessage(level: DebugLevel, tag: String, message: String) {
        val newMessage = DebugMessage(level = level, tag = tag, message = message)
        _debugMessages.value = (_debugMessages.value + newMessage).takeLast(100)
    }
}
