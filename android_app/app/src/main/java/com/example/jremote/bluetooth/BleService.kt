package com.example.jremote.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.jremote.data.ConnectionStatus
import com.example.jremote.data.ConnectionType
import com.example.jremote.data.DebugLevel
import com.example.jremote.data.DebugManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class BleService(private val context: Context) {

    companion object {
        private const val TAG = "BleService"

        val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val CHARACTERISTIC_UUID_TX: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        val CHARACTERISTIC_UUID_RX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val SCAN_PERIOD = 10000L // 10 seconds
        private const val CONNECT_TIMEOUT = 15000L // 15 seconds
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val RECONNECT_DELAY = 2000L // 2 seconds
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var isConnected = false

    private var currentDevice: BluetoothDevice? = null
    private var currentDeviceAddress: String? = null

    @Volatile
    private var autoReconnectEnabled = false
    private var negotiatedMtu = 20
    private var reconnectAttempts = 0

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var reconnectJob: Job? = null
    private var rssiJob: Job? = null
    private var pingJob: Job? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // 调试管理器
    private val debugManager = DebugManager()
    val debugMessages = debugManager.debugMessages

    private val _connectionStatus = MutableStateFlow(ConnectionStatus())
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _receivedData = MutableStateFlow<ByteArray?>(null)
    val receivedData: StateFlow<ByteArray?> = _receivedData.asStateFlow()

    private val _userDataReceived = MutableStateFlow<ByteArray?>(null)
    val userDataReceived: StateFlow<ByteArray?> = _userDataReceived.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _rssi = MutableStateFlow<Int?>(null)
    val rssi: StateFlow<Int?> = _rssi.asStateFlow()

    private val _latency = MutableStateFlow<Int?>(null)
    val latency: StateFlow<Int?> = _latency.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private var scanCallback: ScanCallback? = null
    private var lastPingTime: Long = 0
    private val scannedDevicesList = CopyOnWriteArrayList<BluetoothDevice>()
    private val isScanningFlag = AtomicBoolean(false)
    private val isBluetoothReady = AtomicBoolean(false)

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            log(DebugLevel.INFO, TAG, "连接状态变化: status=$status, newState=$newState")

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log(DebugLevel.INFO, TAG, "已连接到 GATT 服务器")
                    gatt.requestMtu(517)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log(DebugLevel.INFO, TAG, "已断开连接, status=$status")
                    handleDisconnection(status)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            log(DebugLevel.INFO, TAG, "MTU 已设置: $mtu, status=$status")
            negotiatedMtu = maxOf(mtu - 3, 20)
            log(DebugLevel.INFO, TAG, "实际发送 payload 大小: $negotiatedMtu 字节")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            log(DebugLevel.INFO, TAG, "服务发现完成, status=$status")

            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    log(DebugLevel.INFO, TAG, "找到目标服务")

                    txCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID_TX)
                    rxCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID_RX)

                    if (txCharacteristic != null) {
                        enableNotification(gatt, txCharacteristic!!)
                    }

                    if (rxCharacteristic != null && txCharacteristic != null) {
                        onConnectionEstablished()
                    } else {
                        log(DebugLevel.ERROR, TAG, "未找到所有特征")
                        disconnect()
                    }
                } else {
                    log(DebugLevel.ERROR, TAG, "未找到目标服务")
                    disconnect()
                }
            } else {
                log(DebugLevel.ERROR, TAG, "服务发现失败: $status")
                disconnect()
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _rssi.value = rssi
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value ?: return
            if (data.isEmpty()) return

            when (data[0]) {
                0xAA.toByte() -> {
                    _receivedData.value = data
                    val hexString = data.joinToString(" ") { String.format("%02X", it) }
                    log(DebugLevel.INFO, "MCU", "HEX: $hexString")
                }
                0xBB.toByte() -> {
                    if (data.size >= 2) {
                        val payloadLen = (data[1].toInt() and 0xFF)
                        if (data.size >= 2 + payloadLen) {
                            val payload = data.copyOfRange(2, 2 + payloadLen)
                            _userDataReceived.value = payload
                            val hexString = payload.joinToString(" ") { String.format("%02X", it) }
                            log(DebugLevel.INFO, "Serial", "RX: $hexString")
                        }
                    }
                }
                0x50.toByte() -> {
                    if (data.size == 1) {
                        val latencyMs = (System.currentTimeMillis() - lastPingTime).toInt()
                        _latency.value = latencyMs
                    }
                }
                else -> {
                    _receivedData.value = data
                    val hexString = data.joinToString(" ") { String.format("%02X", it) }
                    log(DebugLevel.INFO, "MCU", "HEX: $hexString")
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log(DebugLevel.ERROR, TAG, "数据发送失败: $status")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log(DebugLevel.INFO, TAG, "通知已启用")
            } else {
                log(DebugLevel.ERROR, TAG, "启用通知失败: $status")
            }
        }
    }

    private fun log(level: DebugLevel, tag: String, message: String) {
        debugManager.log(level, tag, message)
    }

    private fun handleDisconnection(status: Int) {
        isConnected = false
        _isConnecting.value = false

        val wasAutoReconnectEnabled = autoReconnectEnabled
        val lastDeviceAddress = currentDeviceAddress

        // Clean up
        stopRssiUpdates()
        stopPing()

        try {
            bluetoothGatt?.close()
        } catch (e: Exception) {
            log(DebugLevel.ERROR, TAG, "关闭 GATT 失败: ${e.message}")
        }
        bluetoothGatt = null
        txCharacteristic = null
        rxCharacteristic = null
        negotiatedMtu = 20

        _connectionStatus.value = ConnectionStatus()
        _rssi.value = null
        _latency.value = null

        // Auto reconnect logic
        if (wasAutoReconnectEnabled && lastDeviceAddress != null && status != 0) {
            // status != 0 means unexpected disconnection
            log(DebugLevel.INFO, TAG, "检测到意外断开，准备重连...")
            scheduleReconnect(lastDeviceAddress)
        }
    }

    private fun onConnectionEstablished() {
        isConnected = true
        _isConnecting.value = false
        reconnectAttempts = 0

        currentDevice?.let { device ->
            _connectionStatus.value = ConnectionStatus(
                isConnected = true,
                deviceName = device.name ?: "Unknown",
                deviceAddress = device.address,
                connectionType = ConnectionType.BLUETOOTH
            )
        }

        log(DebugLevel.INFO, TAG, "BLE 连接就绪")
        startRssiUpdates()
        startPing()
    }

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDevice> {
        if (!hasBluetoothConnectPermission()) {
            log(DebugLevel.ERROR, TAG, "缺少蓝牙权限")
            return emptyList()
        }
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    fun getDeviceByAddress(address: String): BluetoothDevice? {
        return try {
            bluetoothAdapter?.getRemoteDevice(address)
        } catch (e: Exception) {
            log(DebugLevel.ERROR, TAG, "获取设备失败: ${e.message}")
            null
        }
    }

    @SuppressLint("MissingPermission")
    fun startBleScan() {
        if (!hasBluetoothPermissions()) {
            log(DebugLevel.ERROR, TAG, "缺少蓝牙权限")
            return
        }

        if (!isBluetoothEnabled()) {
            log(DebugLevel.ERROR, TAG, "蓝牙未开启")
            return
        }

        // Stop any existing scan first
        if (isScanningFlag.get()) {
            stopBleScan()
        }

        scannedDevicesList.clear()
        _scannedDevices.value = emptyList()
        isScanningFlag.set(true)
        _isScanning.value = true
        log(DebugLevel.INFO, TAG, "开始扫描 BLE 设备...")

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            log(DebugLevel.ERROR, TAG, "无法获取 BLE 扫描器")
            _isScanning.value = false
            isScanningFlag.set(false)
            return
        }

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                if (!scannedDevicesList.any { it.address == device.address }) {
                    scannedDevicesList.add(device)
                    _scannedDevices.value = scannedDevicesList.toList()
                    log(DebugLevel.INFO, TAG, "发现设备: ${device.name ?: "Unknown"} (${device.address})")
                }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                for (result in results) {
                    val device = result.device
                    if (!scannedDevicesList.any { it.address == device.address }) {
                        scannedDevicesList.add(device)
                    }
                }
                _scannedDevices.value = scannedDevicesList.toList()
            }

            override fun onScanFailed(errorCode: Int) {
                val errorMsg = when (errorCode) {
                    ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "SCAN_FAILED_ALREADY_STARTED"
                    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED"
                    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "SCAN_FAILED_FEATURE_UNSUPPORTED"
                    ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "SCAN_FAILED_INTERNAL_ERROR"
                    ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES"
                    else -> "未知错误 ($errorCode)"
                }
                log(DebugLevel.ERROR, TAG, "扫描失败: $errorMsg")
                stopBleScan()
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
        } catch (e: Exception) {
            log(DebugLevel.ERROR, TAG, "开始扫描失败: ${e.message}")
            _isScanning.value = false
            isScanningFlag.set(false)
            return
        }

        // Auto stop after scan period
        mainHandler.postDelayed({
            stopBleScan()
        }, SCAN_PERIOD)
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        if (!isScanningFlag.getAndSet(false)) return

        _isScanning.value = false
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        scanCallback?.let {
            try {
                scanner?.stopScan(it)
            } catch (e: Exception) {
                log(DebugLevel.ERROR, TAG, "停止扫描失败: ${e.message}")
            }
            scanCallback = null
        }
        log(DebugLevel.INFO, TAG, "扫描停止，发现 ${scannedDevicesList.size} 个设备")
    }

    private fun hasBluetoothPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        return permissions.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    private fun enableNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val result = gatt.setCharacteristicNotification(characteristic, true)
        log(DebugLevel.INFO, TAG, "设置通知: $result")

        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        } else {
            log(DebugLevel.ERROR, TAG, "未找到 CCC 描述符")
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.Main) {
        if (!hasBluetoothConnectPermission()) {
            log(DebugLevel.ERROR, TAG, "缺少蓝牙连接权限")
            return@withContext false
        }

        if (_isConnecting.value) {
            log(DebugLevel.WARNING, TAG, "正在连接中...")
            return@withContext false
        }

        // Stop scanning before connecting
        if (isScanningFlag.get()) {
            log(DebugLevel.INFO, TAG, "停止扫描以进行连接...")
            stopBleScan()
            delay(300) // Wait for scan to stop
        }

        if (isConnected) {
            log(DebugLevel.INFO, TAG, "已经连接，先断开")
            disconnect()
            delay(500) // Wait for disconnect to complete
        }

        try {
            _isConnecting.value = true
            currentDevice = device
            currentDeviceAddress = device.address

            log(DebugLevel.INFO, TAG, "正在连接: ${device.name} (${device.address})...")

            // Close existing GATT
            bluetoothGatt?.close()
            bluetoothGatt = null

            // Always use autoConnect=false for reliable connection
            // The system will handle reconnection if needed
            val autoConnect = false

            bluetoothGatt = device.connectGatt(
                context,
                autoConnect,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )

            // Wait for connection with timeout
            val startTime = System.currentTimeMillis()
            while (!isConnected && (System.currentTimeMillis() - startTime) < CONNECT_TIMEOUT) {
                delay(100)
            }

            if (isConnected) {
                log(DebugLevel.INFO, TAG, "连接成功")
                reconnectAttempts = 0
                _isConnecting.value = false
                true
            } else {
                log(DebugLevel.WARNING, TAG, "连接超时")
                // Clean up GATT resources properly
                cleanupGatt()
                _isConnecting.value = false
                false
            }
        } catch (e: SecurityException) {
            log(DebugLevel.ERROR, TAG, "安全异常: ${e.message}")
            cleanupGatt()
            _isConnecting.value = false
            false
        } catch (e: Exception) {
            log(DebugLevel.ERROR, TAG, "连接异常: ${e.message}")
            cleanupGatt()
            _isConnecting.value = false
            false
        }
    }

    /**
     * Clean up GATT resources without triggering callbacks
     */
    private fun cleanupGatt() {
        try {
            bluetoothGatt?.let { gatt ->
                try {
                    gatt.disconnect()
                } catch (e: Exception) {
                    // Ignore disconnect errors during cleanup
                }
                try {
                    gatt.close()
                } catch (e: Exception) {
                    // Ignore close errors during cleanup
                }
            }
        } catch (e: Exception) {
            log(DebugLevel.WARNING, TAG, "清理 GATT 时出错: ${e.message}")
        }
        bluetoothGatt = null
        txCharacteristic = null
        rxCharacteristic = null
        negotiatedMtu = 20
    }

    fun sendData(data: ByteArray): Boolean {
        if (!isConnected || rxCharacteristic == null || bluetoothGatt == null) {
            log(DebugLevel.ERROR, TAG, "未连接或特征不可用")
            return false
        }

        return try {
            val chunkSize = maxOf(negotiatedMtu, 20)
            if (data.size <= chunkSize) {
                rxCharacteristic?.value = data
                bluetoothGatt?.writeCharacteristic(rxCharacteristic)
            } else {
                for (i in data.indices step chunkSize) {
                    val end = minOf(i + chunkSize, data.size)
                    val chunk = data.copyOfRange(i, end)
                    rxCharacteristic?.value = chunk
                    bluetoothGatt?.writeCharacteristic(rxCharacteristic)
                    Thread.sleep(10)
                }
            }
            true
        } catch (e: Exception) {
            log(DebugLevel.ERROR, TAG, "发送失败: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        autoReconnectEnabled = false
        reconnectJob?.cancel()
        reconnectJob = null

        stopRssiUpdates()
        stopPing()

        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            log(DebugLevel.ERROR, TAG, "断开连接失败: ${e.message}")
        }

        bluetoothGatt = null
        txCharacteristic = null
        rxCharacteristic = null
        isConnected = false
        _isConnecting.value = false
        currentDevice = null
        negotiatedMtu = 20

        _rssi.value = null
        _latency.value = null
        _connectionStatus.value = ConnectionStatus()
    }

    fun setAutoReconnect(enabled: Boolean) {
        autoReconnectEnabled = enabled
        log(DebugLevel.INFO, TAG, "自动重连: $enabled")
    }

    fun isAutoReconnectEnabled(): Boolean = autoReconnectEnabled

    private fun scheduleReconnect(address: String) {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            log(DebugLevel.WARNING, TAG, "重连次数已达上限")
            return
        }

        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            delay(RECONNECT_DELAY)
            reconnectAttempts++

            log(DebugLevel.INFO, TAG, "尝试重连 ($reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)...")

            val device = getDeviceByAddress(address) ?: getBondedDevices().find { it.address == address }
            if (device != null) {
                val success = connect(device)
                if (!success) {
                    scheduleReconnect(address)
                }
            } else {
                log(DebugLevel.WARNING, TAG, "无法获取设备进行重连")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRssiUpdates() {
        rssiJob?.cancel()
        rssiJob = serviceScope.launch {
            while (isConnected) {
                try {
                    bluetoothGatt?.readRemoteRssi()
                } catch (e: Exception) {
                    // Ignore
                }
                delay(2000)
            }
        }
    }

    private fun stopRssiUpdates() {
        rssiJob?.cancel()
        rssiJob = null
    }

    @SuppressLint("MissingPermission")
    private fun startPing() {
        pingJob?.cancel()
        pingJob = serviceScope.launch {
            delay(1000)
            while (isConnected) {
                try {
                    lastPingTime = System.currentTimeMillis()
                    val pingData = byteArrayOf(0x70.toByte())
                    rxCharacteristic?.value = pingData
                    bluetoothGatt?.writeCharacteristic(rxCharacteristic)
                } catch (e: Exception) {
                    // Ignore
                }
                delay(1000)
            }
        }
    }

    private fun stopPing() {
        pingJob?.cancel()
        pingJob = null
    }

    @SuppressLint("MissingPermission")
    fun removeBond(device: BluetoothDevice): Boolean {
        return try {
            log(DebugLevel.INFO, TAG, "正在取消配对: ${device.name}...")
            val method = device.javaClass.getMethod("removeBond")
            val result = method.invoke(device) as Boolean
            if (result) {
                log(DebugLevel.INFO, TAG, "取消配对成功")
            } else {
                log(DebugLevel.ERROR, TAG, "取消配对失败")
            }
            result
        } catch (e: Exception) {
            log(DebugLevel.ERROR, TAG, "取消配对异常: ${e.message}")
            false
        }
    }

    fun clearDebugMessages() {
        debugManager.clear()
    }

    fun isConnected(): Boolean = isConnected
}
