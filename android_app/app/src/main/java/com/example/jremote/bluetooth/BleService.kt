package com.example.jremote.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.example.jremote.data.ConnectionStatus
import com.example.jremote.data.ConnectionType
import com.example.jremote.data.DebugLevel
import com.example.jremote.data.DebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class BleService(private val context: Context) {

    companion object {
        private const val TAG = "BleService"

        val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        // TX: 设备发送 (Notify) - App 订阅此特征接收数据
        val CHARACTERISTIC_UUID_TX: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        // RX: 设备接收 (Write) - App 向此特征写入数据
        val CHARACTERISTIC_UUID_RX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    private var isConnected = false
    private var currentDevice: BluetoothDevice? = null
    private var autoReconnectEnabled = false

    // 调试管理器
    private val debugManager = DebugManager()
    val debugMessages = debugManager.debugMessages

    private val _connectionStatus = MutableStateFlow(ConnectionStatus())
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _receivedData = MutableStateFlow<ByteArray?>(null)
    val receivedData: StateFlow<ByteArray?> = _receivedData.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _rssi = MutableStateFlow<Int?>(null)
    val rssi: StateFlow<Int?> = _rssi.asStateFlow()

    private val _latency = MutableStateFlow<Int?>(null)
    val latency: StateFlow<Int?> = _latency.asStateFlow()

    private var scanCallback: ScanCallback? = null
    private var rssiUpdateJob: kotlinx.coroutines.Job? = null
    private var lastPingTime: Long = 0
    private var pingJob: kotlinx.coroutines.Job? = null
    
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            addDebugMessage(DebugLevel.INFO, TAG, "连接状态变化: status=$status, newState=$newState")
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    addDebugMessage(DebugLevel.INFO, TAG, "已连接到 GATT 服务器")
                    addDebugMessage(DebugLevel.INFO, TAG, "请求 MTU...")
                    gatt.requestMtu(517)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    addDebugMessage(DebugLevel.INFO, TAG, "已断开连接")
                    isConnected = false
                    _connectionStatus.value = ConnectionStatus()
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                }
            }
        }
        
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            addDebugMessage(DebugLevel.INFO, TAG, "MTU 已设置: $mtu")
            addDebugMessage(DebugLevel.INFO, TAG, "开始发现服务...")
            gatt.discoverServices()
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addDebugMessage(DebugLevel.INFO, TAG, "服务发现完成")

                val services = gatt.services
                addDebugMessage(DebugLevel.INFO, TAG, "发现 ${services.size} 个服务")

                for (service in services) {
                    addDebugMessage(DebugLevel.INFO, TAG, "服务: ${service.uuid}")
                    for (char in service.characteristics) {
                        addDebugMessage(DebugLevel.INFO, TAG, "  特征: ${char.uuid}, 属性: ${char.properties}")
                    }
                }

                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    addDebugMessage(DebugLevel.INFO, TAG, "找到目标服务")

                    for (char in service.characteristics) {
                        when (char.uuid) {
                            CHARACTERISTIC_UUID_TX -> {
                                txCharacteristic = char
                                addDebugMessage(DebugLevel.INFO, TAG, "找到 TX 特征")
                            }
                            CHARACTERISTIC_UUID_RX -> {
                                rxCharacteristic = char
                                addDebugMessage(DebugLevel.INFO, TAG, "找到 RX 特征")
                            }
                        }
                    }

                    if (txCharacteristic != null) {
                        enableNotification(gatt, txCharacteristic!!)
                    }

                    if (rxCharacteristic != null && txCharacteristic != null) {
                        isConnected = true
                        currentDevice?.let { device ->
                            _connectionStatus.value = ConnectionStatus(
                                isConnected = true,
                                deviceName = device.name ?: "Unknown",
                                deviceAddress = device.address,
                                connectionType = ConnectionType.BLUETOOTH
                            )
                        }
                        addDebugMessage(DebugLevel.INFO, TAG, "BLE 连接就绪")
                        // 开始定期读取 RSSI 和 ping
                        startRssiUpdates()
                        startPing()
                    } else {
                        addDebugMessage(DebugLevel.ERROR, TAG, "未找到所有特征")
                    }
                } else {
                    addDebugMessage(DebugLevel.ERROR, TAG, "未找到目标服务: $SERVICE_UUID")
                }
            } else {
                addDebugMessage(DebugLevel.ERROR, TAG, "服务发现失败: $status")
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _rssi.value = rssi
            }
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value
            _receivedData.value = data

            // 检查是否是 ping 响应
            if (data.size == 1 && data[0] == 0x50.toByte()) { // 'P'
                val currentTime = System.currentTimeMillis()
                val latency = (currentTime - lastPingTime).toInt()
                _latency.value = latency
                return
            }

            // 尝试将数据解析为字符串显示
            val hexString = data.joinToString(" ") { String.format("%02X", it) }
            val textString = try {
                String(data, Charsets.UTF_8).filter { it.code in 32..126 }
            } catch (e: Exception) {
                ""
            }

            if (textString.isNotEmpty()) {
                addDebugMessage(DebugLevel.INFO, "MCU", textString)
            } else {
                addDebugMessage(DebugLevel.INFO, "MCU", "HEX: $hexString")
            }
        }
        
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            // 不显示发送成功，只显示失败
            if (status != BluetoothGatt.GATT_SUCCESS) {
                addDebugMessage(DebugLevel.ERROR, TAG, "数据发送失败: $status")
            }
        }
        
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addDebugMessage(DebugLevel.INFO, TAG, "通知已启用")
            } else {
                addDebugMessage(DebugLevel.ERROR, TAG, "启用通知失败: $status")
            }
        }
    }
    
    private fun addDebugMessage(level: com.example.jremote.data.DebugLevel, tag: String, message: String) {
        debugManager.log(level, tag, message)
    }
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    @Suppress("DEPRECATION")
    fun getBondedDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermissions()) {
            addDebugMessage(DebugLevel.ERROR, TAG, "缺少蓝牙权限")
            return emptyList()
        }
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }
    
    @Suppress("MissingPermission")
    fun startBleScan() {
        if (!hasBluetoothPermissions()) {
            addDebugMessage(DebugLevel.ERROR, TAG, "缺少蓝牙权限")
            return
        }
        
        if (_isScanning.value) {
            stopBleScan()
        }
        
        _scannedDevices.value = emptyList()
        _isScanning.value = true
        addDebugMessage(DebugLevel.INFO, TAG, "开始扫描 BLE 设备...")
        
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            addDebugMessage(DebugLevel.ERROR, TAG, "无法获取 BLE 扫描器")
            _isScanning.value = false
            return
        }
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val currentList = _scannedDevices.value
                if (!currentList.any { it.address == device.address }) {
                    _scannedDevices.value = currentList + device
                    addDebugMessage(DebugLevel.INFO, TAG, "发现设备: ${device.name ?: "Unknown"} (${device.address})")
                }
            }
            
            override fun onBatchScanResults(results: List<ScanResult>) {
                for (result in results) {
                    val device = result.device
                    val currentList = _scannedDevices.value
                    if (!currentList.any { it.address == device.address }) {
                        _scannedDevices.value = currentList + device
                    }
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                addDebugMessage(DebugLevel.ERROR, TAG, "扫描失败: $errorCode")
                _isScanning.value = false
            }
        }
        
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        scanner.startScan(null, settings, scanCallback)
        
        // 10秒后自动停止扫描
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            stopBleScan()
        }, 10000)
    }
    
    @Suppress("MissingPermission")
    fun stopBleScan() {
        if (!_isScanning.value) return
        
        _isScanning.value = false
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        scanCallback?.let {
            scanner?.stopScan(it)
            scanCallback = null
        }
        addDebugMessage(DebugLevel.INFO, TAG, "停止扫描")
    }
    
    private fun hasBluetoothPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
        return permissions.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun enableNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val result = gatt.setCharacteristicNotification(characteristic, true)
        addDebugMessage(DebugLevel.INFO, TAG, "设置通知: $result")
        
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        } else {
            addDebugMessage(DebugLevel.ERROR, TAG, "未找到 CCC 描述符")
        }
    }
    
    @Suppress("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions()) {
            addDebugMessage(DebugLevel.ERROR, TAG, "缺少蓝牙权限")
            return@withContext false
        }

        try {
            addDebugMessage(DebugLevel.INFO, TAG, "正在连接到 ${device.name} (${device.address})...")

            disconnect()

            // 如果设备未配对，尝试创建配对（不等待，让系统在后台处理）
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                addDebugMessage(DebugLevel.INFO, TAG, "设备未配对，发起配对请求...")
                device.createBond()
            }

            currentDevice = device
            bluetoothGatt = device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )

            // 等待连接结果（最多15秒）
            var waitCount = 0
            while (!isConnected && waitCount < 150) {
                delay(100)
                waitCount++
            }

            if (isConnected) {
                addDebugMessage(DebugLevel.INFO, TAG, "连接成功")
                true
            } else {
                addDebugMessage(DebugLevel.ERROR, TAG, "连接超时")
                disconnect()
                false
            }
        } catch (e: SecurityException) {
            addDebugMessage(DebugLevel.ERROR, TAG, "安全异常: ${e.message}")
            false
        } catch (e: Exception) {
            addDebugMessage(DebugLevel.ERROR, TAG, "连接异常: ${e.message}")
            false
        }
    }
    
    fun sendData(data: ByteArray): Boolean {
        if (!isConnected || rxCharacteristic == null || bluetoothGatt == null) {
            addDebugMessage(DebugLevel.ERROR, TAG, "未连接或特征不可用")
            return false
        }
        
        return try {
            val chunkSize = 20
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
        } catch (e: SecurityException) {
            addDebugMessage(DebugLevel.ERROR, TAG, "发送失败: ${e.message}")
            false
        } catch (e: Exception) {
            addDebugMessage(DebugLevel.ERROR, TAG, "发送异常: ${e.message}")
            false
        }
    }
    
    fun disconnect() {
        autoReconnectEnabled = false
        stopRssiUpdates()
        stopPing()
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: SecurityException) {
            addDebugMessage(DebugLevel.ERROR, TAG, "断开连接失败: ${e.message}")
        } catch (e: Exception) {
            addDebugMessage(DebugLevel.ERROR, TAG, "断开连接异常: ${e.message}")
        }

        bluetoothGatt = null
        txCharacteristic = null
        rxCharacteristic = null
        isConnected = false
        currentDevice = null
        _rssi.value = null
        _latency.value = null
        _connectionStatus.value = ConnectionStatus()
    }

    fun setAutoReconnect(enabled: Boolean) {
        autoReconnectEnabled = enabled
    }

    fun isAutoReconnectEnabled(): Boolean = autoReconnectEnabled

    @Suppress("MissingPermission")
    private fun startRssiUpdates() {
        rssiUpdateJob = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (isConnected) {
                try {
                    bluetoothGatt?.readRemoteRssi()
                } catch (e: Exception) {
                    // 忽略错误
                }
                delay(2000) // 每2秒读取一次
            }
        }
    }

    private fun stopRssiUpdates() {
        rssiUpdateJob?.cancel()
        rssiUpdateJob = null
    }

    @Suppress("MissingPermission")
    private fun startPing() {
        pingJob = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            delay(1000) // 等待1秒后开始
            while (isConnected) {
                try {
                    lastPingTime = System.currentTimeMillis()
                    val pingData = byteArrayOf(0x70.toByte()) // 'p'
                    rxCharacteristic?.value = pingData
                    bluetoothGatt?.writeCharacteristic(rxCharacteristic)
                } catch (e: Exception) {
                    // 忽略错误
                }
                delay(1000) // 每秒ping一次
            }
        }
    }

    private fun stopPing() {
        pingJob?.cancel()
        pingJob = null
    }

    @Suppress("MissingPermission")
    fun removeBond(device: BluetoothDevice): Boolean {
        return try {
            addDebugMessage(DebugLevel.INFO, TAG, "正在取消配对: ${device.name}...")
            val method = device.javaClass.getMethod("removeBond")
            val result = method.invoke(device) as Boolean
            if (result) {
                addDebugMessage(DebugLevel.INFO, TAG, "取消配对成功")
            } else {
                addDebugMessage(DebugLevel.ERROR, TAG, "取消配对失败")
            }
            result
        } catch (e: Exception) {
            addDebugMessage(DebugLevel.ERROR, TAG, "取消配对异常: ${e.message}")
            false
        }
    }

    fun clearDebugMessages() {
        debugManager.clear()
    }
}
