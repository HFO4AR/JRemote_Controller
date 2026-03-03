# Wi-Fi UDP 控制功能实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 JRemote Controller 添加 Wi-Fi UDP 控制功能，支持 AP 模式和局域网模式，与 BLE 模式并存。

**Architecture:**
- 新增 WifiService 处理 UDP 连接
- 新增 UdpDiscovery 处理设备发现
- ConnectionScreen 添加模式切换下拉菜单
- ControlViewModel 统一管理三种连接模式

**Tech Stack:** Kotlin, Jetpack Compose, Android Coroutines, UDP Socket

---

## 阶段一：数据模型

### Task 1: 创建 ConnectionMode 枚举

**Files:**
- Create: `android_app/app/src/main/java/com/example/jremote/data/ConnectionMode.kt`

**Step 1: 创建文件**

```kotlin
package com.example.jremote.data

enum class ConnectionMode {
    BLE,      // 蓝牙模式
    AP,       // AP 模式（Wi-Fi 直连）
    LAN       // 局域网模式
}
```

**Step 2: Commit**
```bash
git add android_app/app/src/main/java/com/example/jremote/data/ConnectionMode.kt
git commit -m "feat: 添加 ConnectionMode 枚举"
```

---

### Task 2: 创建 DiscoveredDevice 数据类

**Files:**
- Create: `android_app/app/src/main/java/com/example/jremote/data/DiscoveredDevice.kt`

**Step 1: 创建文件**

```kotlin
package com.example.jremote.data

data class DiscoveredDevice(
    val name: String,
    val address: String,      // BLE: MAC 地址, Wi-Fi: IP 地址
    val ip: String = "",       // Wi-Fi 模式使用
    val port: Int = 1034,      // Wi-Fi 模式使用
    val mode: ConnectionMode,
    val rssi: Int = 0
)
```

**Step 2: Commit**
```bash
git add android_app/app/src/main/java/com/example/jremote/data/DiscoveredDevice.kt
git commit -m "feat: 添加 DiscoveredDevice 数据类"
```

---

## 阶段二：Wi-Fi 服务

### Task 3: 创建 WifiService

**Files:**
- Create: `android_app/app/src/main/java/com/example/jremote/wifi/WifiService.kt`

**Step 1: 创建 WifiService 类**

```kotlin
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
import java.nio.ByteBuffer

class WifiService(private val application: Application) {

    companion object {
        private const val TAG = "WifiService"
        const val DATA_PORT = 1034
        const val DISCOVERY_PORT = 1035
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
```

**Step 2: Commit**
```bash
git add android_app/app/src/main/java/com/example/jremote/wifi/WifiService.kt
git commit -m "feat: 添加 WifiService UDP 连接服务"
```

---

### Task 4: 创建 UdpDiscovery

**Files:**
- Create: `android_app/app/src/main/java/com/example/jremote/wifi/UdpDiscovery.kt`

**Step 1: 创建 UdpDiscovery 类**

```kotlin
package com.example.jremote.wifi

import android.util.Log
import com.example.jremote.data.ConnectionMode
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

class UdpDiscovery {

    companion object {
        private const val TAG = "UdpDiscovery"
        const val DISCOVERY_PORT = 1035
        const val DISCOVERY_MESSAGE = "JREMOTE_DISCOVER"
        const val DEVICE_PREFIX = "JREMOTE:"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _debugMessages = MutableStateFlow<List<DebugMessage>>(emptyList())
    val debugMessages: StateFlow<List<DebugMessage>> = _debugMessages.asStateFlow()

    private var socket: DatagramSocket? = null
    private var scanJob: Job? = null
    private var isScanning = false

    // 响应回调
    private var onDeviceFound: ((DiscoveredDevice) -> Unit)? = null

    fun setOnDeviceFound(callback: (DiscoveredDevice) -> Unit) {
        onDeviceFound = callback
    }

    fun startDiscovery(mode: ConnectionMode) {
        if (_isScanning.value) return

        isScanning = true
        _isScanning.value = true
        _discoveredDevices.value = emptyList()

        scanJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    socket = DatagramSocket(DISCOVERY_PORT).apply {
                        reuseAddress = true
                    }

                    val buffer = ByteArray(1024)

                    while (isActive && isScanning) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket?.receive(packet)

                            val data = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                            val address = packet.address.hostAddress ?: ""

                            addDebugMessage(DebugLevel.DEBUG, TAG, "收到响应: $data from $address")

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

                val message = DISCOVERY_MESSAGE.toByteArray(StandardCharsets.UTF_8)

                // 发送广播到当前网段的所有地址
                val broadcastAddresses = listOf(
                    "255.255.255.255",
                    "192.168.255.255",
                    "10.255.255.255"
                )

                for (broadcastAddress in broadcastAddresses) {
                    try {
                        val address = InetAddress.getByName(broadcastAddress)
                        val packet = DatagramPacket(message, message.size, address, DISCOVERY_PORT)
                        socket.send(packet)
                    } catch (e: Exception) {
                        // 忽略单个广播失败
                    }
                }

                socket.close()

                // 持续发送广播 5 秒
                var count = 0
                while (isScanning && count < 10) {
                    delay(500)
                    count++
                }

            } catch (e: Exception) {
                addDebugMessage(DebugLevel.ERROR, TAG, "广播失败: ${e.message}")
            }
        }
    }

    private fun parseDeviceResponse(data: String, address: String, mode: ConnectionMode) {
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
                    mode = mode,
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
        isScanning = false
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
```

**Step 2: Commit**
```bash
git add android_app/app/src/main/java/com/example/jremote/wifi/UdpDiscovery.kt
git commit -m "feat: 添加 UdpDiscovery 设备发现服务"
```

---

## 阶段三：AppSettings 更新

### Task 5: 更新 AppSettings 添加连接模式字段

**Files:**
- Modify: `android_app/app/src/main/java/com/example/jremote/data/AppSettings.kt`

**Step 1: 添加新字段**

在 AppSettings 中添加：
```kotlin
val lastConnectionMode: ConnectionMode = ConnectionMode.BLE,
val lastConnectedDeviceIp: String? = null,  // Wi-Fi 模式使用
```

**Step 2: Commit**
```bash
git add android_app/app/src/main/java/com/example/jremote/data/AppSettings.kt
git commit -m "feat: AppSettings 添加连接模式字段"
```

---

### Task 6: 更新 SettingsRepository 添加持久化

**Files:**
- Modify: `android_app/app/src/main/java/com/example/jremote/data/SettingsRepository.kt`

**Step 1: 添加新的 Preferences Key**

```kotlin
val LAST_CONNECTION_MODE = stringPreferencesKey("last_connection_mode")
val LAST_CONNECTED_DEVICE_IP = stringPreferencesKey("last_connected_device_ip")
```

**Step 2: 更新 loadSettings 和 updateAppSettings**

在 loadSettings 中读取新字段，在 updateAppSettings 中保存新字段。

**Step 3: Commit**
```bash
git add android_app/app/src/main/java/com/example/jremote/data/SettingsRepository.kt
git commit -m "feat: SettingsRepository 添加连接模式持久化"
```

---

## 阶段四：ControlViewModel 更新

### Task 7: 更新 ControlViewModel 支持 Wi-Fi

**Files:**
- Modify: `android_app/app/src/main/java/com/example/jremote/viewmodel/ControlViewModel.kt`

**Step 1: 添加 WifiService 和 UdpDiscovery**

```kotlin
import com.example.jremote.wifi.WifiService
import com.example.jremote.wifi.UdpDiscovery
import com.example.jremote.data.ConnectionMode
import com.example.jremote.data.DiscoveredDevice
```

在类中添加：
```kotlin
private val wifiService = WifiService(application)
private val udpDiscovery = UdpDiscovery()

private val _currentConnectionMode = MutableStateFlow(ConnectionMode.BLE)
val currentConnectionMode: StateFlow<ConnectionMode> = _currentConnectionMode.asStateFlow()

val wifiScannedDevices = udpDiscovery.discoveredDevices
val isWifiScanning = udpDiscovery.isScanning

val wifiLatency = wifiService.latency
```

**Step 2: 更新数据发送逻辑**

修改 sendControlData() 和其他发送相关方法，根据 _currentConnectionMode 选择使用 bleService 或 wifiService。

**Step 3: 添加 Wi-Fi 连接方法**

添加 connectToWifiDevice(), startWifiDiscovery(), stopWifiDiscovery(), disconnectWifi() 等方法。

**Step 4: 添加 Wi-Fi 状态收集**

在 init 中添加 wifiService 和 udpDiscovery 的状态收集。

**Step 5: Commit**
```bash
git add android_app/app/src/main/java/com/example/jremote/viewmodel/ControlViewModel.kt
git commit -m "feat: ControlViewModel 添加 Wi-Fi 支持"
```

---

## 阶段五：UI 更新

### Task 8: 更新 ConnectionScreen 添加模式切换

**Files:**
- Modify: `android_app/app/src/main/java/com/example/jremote/screen/ConnectionScreen.kt`

**Step 1: 添加模式切换下拉菜单**

在界面顶部添加 ExposedDropdownMenuBox，包含 BLE/AP/局域网 三个选项。

**Step 2: 根据模式显示不同的设备列表**

- BLE 模式：显示 BLE 扫描结果
- AP/局域网模式：显示 Wi-Fi 发现结果

**Step 3: 添加 Wi-Fi 发现控制按钮**

添加"开始扫描"/"停止扫描"按钮。

**Step 4: Commit**
```bash
git add android_app/app/src/main/java/com/example/jremote/screen/ConnectionScreen.kt
git commit -m "feat: ConnectionScreen 添加模式切换 UI"
```

---

## 阶段六：ESP32 示例代码

### Task 9: 创建 ESP32 Wi-Fi UDP 接收端示例

**Files:**
- Create: `sample/esp32_udp_receiver/esp32_udp_receiver.ino`

**Step 1: 创建完整的 ESP32 UDP 接收端代码**

实现：
- AP 模式（作为热点）
- Station 模式（连接 Wi-Fi）
- 监听 1034 端口接收控制数据
- 监听 1035 端口响应设备发现
- 数据包解析（与 BLE 相同）

**Step 2: Commit**
```bash
git add sample/esp32_udp_receiver/
git commit -m "feat: 添加 ESP32 Wi-Fi UDP 接收端示例"
```

---

## 执行方式选择

**Plan complete and saved to `docs/plans/2026-03-03-wifi-udp-implementation.md`.**

**Two execution options:**

1. **Subagent-Driven (this session)** - I dispatch fresh subagent per task, review between tasks, fast iteration

2. **Parallel Session (separate)** - Open new session with executing-plans, batch execution with checkpoints

Which approach?
