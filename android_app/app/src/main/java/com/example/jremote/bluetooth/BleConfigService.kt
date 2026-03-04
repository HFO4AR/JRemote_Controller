package com.example.jremote.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.example.jremote.data.DebugLevel
import com.example.jremote.data.DebugManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BLE 配网服务
 * 用于连接到 ESP32 配网模式并发送 WiFi 凭据
 */
class BleConfigService(private val context: Context) {

    companion object {
        private const val TAG = "BleConfigService"

        // BLE 配网服务 UUID
        const val CONFIG_SERVICE_UUID = "0000ffff-0000-1000-8000-00805f9b34fb"
        const val WIFI_SSID_UUID = "0000ff01-0000-1000-8000-00805f9b34fb"
        const val WIFI_PASSWORD_UUID = "0000ff02-0000-1000-8000-00805f9b34fb"
        const val STATUS_UUID = "0000ff03-0000-1000-8000-00805f9b34fb"
        const val COMMAND_UUID = "0000ff04-0000-1000-8000-00805f9b34fb"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    // 调试管理器
    private val debugManager = DebugManager()
    val debugMessages = debugManager.debugMessages

    private var bluetoothGatt: BluetoothGatt? = null
    private var ssidCharacteristic: BluetoothGattCharacteristic? = null
    private var passwordCharacteristic: BluetoothGattCharacteristic? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _configStatus = MutableStateFlow("")
    val configStatus: StateFlow<String> = _configStatus.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    private var scanCallback: android.bluetooth.le.ScanCallback? = null

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return

        _isScanning.value = true
        _scannedDevices.value = emptyList()

        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        scanCallback = object : android.bluetooth.le.ScanCallback() {
            override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                val device = result.device
                val name = device.name ?: return
                if (name.contains("ESP32") || name.contains("Config")) {
                    val currentList = _scannedDevices.value.toMutableList()
                    if (currentList.none { it.address == device.address }) {
                        currentList.add(device)
                        _scannedDevices.value = currentList
                        debugManager.info(TAG, "发现设备: ${device.name}")
                    }
                }
            }
        }

        scanner.startScan(scanCallback)

        // 扫描 10 秒后停止
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            stopScan()
        }, 10000)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanCallback?.let {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(it)
        }
        scanCallback = null
        _isScanning.value = false
        debugManager.info(TAG, "扫描停止")
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        debugManager.info(TAG, "正在连接到 ${device.name}...")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _isConnected.value = false
        _configStatus.value = ""
        ssidCharacteristic = null
        passwordCharacteristic = null
        statusCharacteristic = null
        commandCharacteristic = null
    }

    fun sendSsid(ssid: String) {
        ssidCharacteristic?.let {
            it.value = ssid.toByteArray()
            bluetoothGatt?.writeCharacteristic(it)
            debugManager.info(TAG, "发送 SSID: $ssid")
        }
    }

    fun sendPassword(password: String) {
        passwordCharacteristic?.let {
            it.value = password.toByteArray()
            bluetoothGatt?.writeCharacteristic(it)
            debugManager.info(TAG, "发送密码")
        }
    }

    fun sendCommand(command: String) {
        commandCharacteristic?.let {
            it.value = command.toByteArray()
            bluetoothGatt?.writeCharacteristic(it)
            debugManager.info(TAG, "发送命令: $command")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _isConnected.value = true
                    debugManager.info(TAG, "连接成功")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _isConnected.value = false
                    debugManager.info(TAG, "断开连接")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(java.util.UUID.fromString(CONFIG_SERVICE_UUID))
                if (service != null) {
                    ssidCharacteristic = service.getCharacteristic(java.util.UUID.fromString(WIFI_SSID_UUID))
                    passwordCharacteristic = service.getCharacteristic(java.util.UUID.fromString(WIFI_PASSWORD_UUID))
                    statusCharacteristic = service.getCharacteristic(java.util.UUID.fromString(STATUS_UUID))
                    commandCharacteristic = service.getCharacteristic(java.util.UUID.fromString(COMMAND_UUID))

                    // 启用状态通知
                    statusCharacteristic?.let {
                        gatt.setCharacteristicNotification(it, true)
                    }

                    debugManager.info(TAG, "服务发现成功")
                } else {
                    debugManager.error(TAG, "未找到配网服务")
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                debugManager.info(TAG, "写入成功: ${characteristic.uuid}")
            } else {
                debugManager.error(TAG, "写入失败: $status")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value?.let { String(it) } ?: ""
            _configStatus.value = value
            debugManager.info(TAG, "状态更新: $value")
        }
    }
}
