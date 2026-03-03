package com.example.jremote.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jremote.bluetooth.BleService
import com.example.jremote.data.AppSettings
import com.example.jremote.data.ButtonConfig
import com.example.jremote.data.ConnectionMode
import com.example.jremote.data.ConnectionStatus
import com.example.jremote.data.ControlData
import com.example.jremote.data.DebugLevel
import com.example.jremote.data.DebugMessage
import com.example.jremote.data.DiscoveredDevice
import com.example.jremote.data.JoystickState
import com.example.jremote.data.SettingsRepository
import com.example.jremote.wifi.WifiService
import com.example.jremote.wifi.UdpDiscovery
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ControlViewModel(application: Application) : AndroidViewModel(application) {

    private val bleService = BleService(application)
    private val wifiService = WifiService(application)
    private val udpDiscovery = UdpDiscovery()
    private val settingsRepository = SettingsRepository(application)
    
    private val _leftJoystickState = MutableStateFlow(JoystickState())
    val leftJoystickState: StateFlow<JoystickState> = _leftJoystickState.asStateFlow()
    
    private val _rightJoystickState = MutableStateFlow(JoystickState())
    val rightJoystickState: StateFlow<JoystickState> = _rightJoystickState.asStateFlow()
    
    private val _buttonStates = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val buttonStates: StateFlow<Map<Int, Boolean>> = _buttonStates.asStateFlow()
    
    private val defaultButtonConfigs = listOf(
        ButtonConfig(0, "LX", 0x01.toByte()),
        ButtonConfig(1, "LY", 0x02.toByte()),
        ButtonConfig(2, "LZ", 0x04.toByte()),
        ButtonConfig(3, "RX", 0x08.toByte()),
        ButtonConfig(4, "RY", 0x10.toByte()),
        ButtonConfig(5, "RZ", 0x20.toByte()),
        ButtonConfig(6, "L1", 0x40.toByte(), isToggle = true),
        ButtonConfig(7, "L2", 0x80.toByte(), isToggle = true),
        ButtonConfig(8, "L3", 0x00.toByte(), isToggle = true),
        ButtonConfig(9, "L4", 0x00.toByte(), isToggle = true),
        ButtonConfig(10, "R1", 0x00.toByte(), isToggle = true),
        ButtonConfig(11, "R2", 0x00.toByte(), isToggle = true),
        ButtonConfig(12, "R3", 0x00.toByte(), isToggle = true),
        ButtonConfig(13, "R4", 0x00.toByte(), isToggle = true)
    )
    
    private val _buttonConfigs = MutableStateFlow(defaultButtonConfigs)
    val buttonConfigs: StateFlow<List<ButtonConfig>> = _buttonConfigs.asStateFlow()
    
    private val _connectionStatus = MutableStateFlow(ConnectionStatus())
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _debugMessages = MutableStateFlow<List<DebugMessage>>(emptyList())
    val debugMessages: StateFlow<List<DebugMessage>> = _debugMessages.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isInControlMode = MutableStateFlow(false)
    val isInControlMode: StateFlow<Boolean> = _isInControlMode.asStateFlow()

    private val _isEmergencyStopped = MutableStateFlow(false)
    val isEmergencyStopped: StateFlow<Boolean> = _isEmergencyStopped.asStateFlow()

    val rssi: StateFlow<Int?> = bleService.rssi
    val latency: StateFlow<Int?> = bleService.latency

    private var sendJob: Job? = null
    private var reconnectJob: Job? = null
    private var previousConnectionStatus = false

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val bondedDevices = bleService.getBondedDevices()
    val scannedDevices = bleService.scannedDevices
    val isScanning = bleService.isScanning

    private val _currentConnectionMode = MutableStateFlow(ConnectionMode.BLE)
    val currentConnectionMode: StateFlow<ConnectionMode> = _currentConnectionMode.asStateFlow()

    val wifiScannedDevices = udpDiscovery.discoveredDevices
    val isWifiScanning = udpDiscovery.isScanning
    val wifiLatency = wifiService.latency

    init {
        viewModelScope.launch {
            settingsRepository.appSettings.collect { settings ->
                _settings.value = settings
            }
        }
        
        viewModelScope.launch {
            loadButtonConfigs()
        }

        viewModelScope.launch {
            bleService.connectionStatus.collect { status ->
                // 检测连接断开事件
                if (previousConnectionStatus && !status.isConnected) {
                    // 连接断开，触发自动重连
                    onConnectionLost()
                }
                previousConnectionStatus = status.isConnected
                _connectionStatus.value = status
            }
        }

        viewModelScope.launch {
            bleService.debugMessages.collect { messages ->
                _debugMessages.value = messages
            }
        }

        viewModelScope.launch {
            wifiService.connectionStatus.collect { status ->
                // 检测连接断开事件
                if (previousConnectionStatus && !status.isConnected) {
                    onConnectionLost()
                }
                previousConnectionStatus = status.isConnected
                _connectionStatus.value = status
            }
        }

        viewModelScope.launch {
            wifiService.debugMessages.collect { messages ->
                _debugMessages.value = messages
            }
        }
    }
    
    private suspend fun loadButtonConfigs() {
        val configs = defaultButtonConfigs.map { config ->
            val isEnabled = settingsRepository.getButtonEnabled(config.id).first()
            val isToggle = settingsRepository.getButtonToggle(config.id).first()
            config.copy(isEnabled = isEnabled, isToggle = isToggle)
        }
        _buttonConfigs.value = configs
    }
    
    fun startBleScan() {
        bleService.startBleScan()
    }
    
    fun stopBleScan() {
        bleService.stopBleScan()
    }

    fun startWifiDiscovery(mode: ConnectionMode) {
        udpDiscovery.startDiscovery(mode)
    }

    fun stopWifiDiscovery() {
        udpDiscovery.stopDiscovery()
    }

    fun connectToWifiDevice(device: DiscoveredDevice) {
        viewModelScope.launch {
            // 保存最后连接的设备信息
            val newSettings = _settings.value.copy(
                lastConnectionMode = ConnectionMode.LAN,
                lastConnectedDeviceIp = device.ip
            )
            settingsRepository.updateAppSettings(newSettings)

            wifiService.connect(device)
        }
    }

    fun disconnectWifi() {
        stopSending()
        wifiService.disconnect()
    }

    fun setConnectionMode(mode: ConnectionMode) {
        // 如果当前已连接，先断开
        if (_connectionStatus.value.isConnected) {
            when (_currentConnectionMode.value) {
                ConnectionMode.BLE -> bleService.disconnect()
                ConnectionMode.AP, ConnectionMode.LAN -> wifiService.disconnect()
            }
        }
        _currentConnectionMode.value = mode

        // 保存模式到设置
        viewModelScope.launch {
            val newSettings = _settings.value.copy(lastConnectionMode = mode)
            settingsRepository.updateAppSettings(newSettings)
        }
    }
    
    fun updateLeftJoystick(state: JoystickState) {
        _leftJoystickState.value = state
    }
    
    fun updateRightJoystick(state: JoystickState) {
        _rightJoystickState.value = state
    }
    
    fun updateButtonState(buttonId: Int, isPressed: Boolean) {
        val currentStates = _buttonStates.value.toMutableMap()
        currentStates[buttonId] = isPressed
        _buttonStates.value = currentStates
    }
    
    fun startSending() {
        if (_isSending.value) return
        
        _isSending.value = true
        _isInControlMode.value = true
        _isEmergencyStopped.value = false
        sendJob = viewModelScope.launch {
            while (true) {
                sendControlData()
                delay(_settings.value.sendIntervalMs)
            }
        }
    }
    
    fun stopSending() {
        val stopData = ControlData(
            leftJoystick = JoystickState(0f, 0f),
            rightJoystick = JoystickState(0f, 0f),
            buttons = emptyMap()
        )
        val data = byteArrayOf(0xAA.toByte()) + stopData.toByteArray()

        when (_currentConnectionMode.value) {
            ConnectionMode.BLE -> bleService.sendData(data)
            ConnectionMode.AP, ConnectionMode.LAN -> wifiService.sendData(data)
        }

        _isSending.value = false
        _isInControlMode.value = false
        _isEmergencyStopped.value = false
        sendJob?.cancel()
        sendJob = null
    }
    
    fun emergencyStop() {
        val stopData = ControlData(
            leftJoystick = JoystickState(0f, 0f),
            rightJoystick = JoystickState(0f, 0f),
            buttons = emptyMap()
        )
        val data = byteArrayOf(0xEE.toByte()) + stopData.toByteArray()

        when (_currentConnectionMode.value) {
            ConnectionMode.BLE -> bleService.sendData(data)
            ConnectionMode.AP, ConnectionMode.LAN -> wifiService.sendData(data)
        }

        _isSending.value = false
        _isEmergencyStopped.value = true
        sendJob?.cancel()
        sendJob = null
    }
    
    fun exitControlMode() {
        _isInControlMode.value = false
        _isSending.value = false
        _isEmergencyStopped.value = false
        sendJob?.cancel()
        sendJob = null
    }
    
    private fun sendControlData() {
        val controlData = ControlData(
            leftJoystick = _leftJoystickState.value,
            rightJoystick = _rightJoystickState.value,
            buttons = _buttonStates.value
        )

        val data = byteArrayOf(0xAA.toByte()) + controlData.toByteArray()

        if (_connectionStatus.value.isConnected) {
            when (_currentConnectionMode.value) {
                ConnectionMode.BLE -> bleService.sendData(data)
                ConnectionMode.AP, ConnectionMode.LAN -> wifiService.sendData(data)
            }
        }
    }
    
    fun connectToDevice(deviceAddress: String) {
        val device = bondedDevices.find { it.address == deviceAddress }
            ?: scannedDevices.value.find { it.address == deviceAddress }

        device?.let {
            viewModelScope.launch {
                // 保存最后连接的设备地址
                val newSettings = _settings.value.copy(lastConnectedDeviceAddress = deviceAddress)
                settingsRepository.updateAppSettings(newSettings)

                // 如果启用自动重连，设置标志
                if (_settings.value.autoReconnect) {
                    bleService.setAutoReconnect(true)
                }

                bleService.connect(device)
            }
        }
    }
    
    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        stopSending()

        when (_currentConnectionMode.value) {
            ConnectionMode.BLE -> bleService.disconnect()
            ConnectionMode.AP, ConnectionMode.LAN -> wifiService.disconnect()
        }
    }

    private fun onConnectionLost() {
        // 如果正在发送数据，停止发送
        if (_isSending.value) {
            stopSending()
        }

        // 检查是否启用自动重连
        if (_settings.value.autoReconnect) {
            val lastDeviceAddress = _settings.value.lastConnectedDeviceAddress
            if (lastDeviceAddress != null) {
                startReconnect(lastDeviceAddress)
            }
        }
    }

    private fun startReconnect(deviceAddress: String) {
        // 取消之前的重连任务
        reconnectJob?.cancel()

        reconnectJob = viewModelScope.launch {
            // 等待2秒后再尝试重连，避免频繁重连
            delay(2000)

            // 获取已配对设备列表
            val device = bondedDevices.find { it.address == deviceAddress }
                ?: bleService.scannedDevices.value.find { it.address == deviceAddress }

            if (device != null) {
                // 最多重连5次
                var reconnectAttempts = 0
                val maxReconnectAttempts = 5

                while (reconnectAttempts < maxReconnectAttempts && !_connectionStatus.value.isConnected) {
                    reconnectAttempts++
                    addDebugMessage(
                        DebugLevel.INFO,
                        "AutoReconnect",
                        "正在重连 (尝试 $reconnectAttempts/$maxReconnectAttempts)..."
                    )

                    val success = bleService.connect(device)
                    if (success) {
                        addDebugMessage(DebugLevel.INFO, "AutoReconnect", "重连成功!")
                        break
                    }

                    if (reconnectAttempts < maxReconnectAttempts) {
                        // 等待3秒后再试
                        delay(3000)
                    }
                }

                if (!_connectionStatus.value.isConnected) {
                    addDebugMessage(
                        DebugLevel.WARNING,
                        "AutoReconnect",
                        "重连失败，请手动重新连接"
                    )
                }
            } else {
                addDebugMessage(
                    DebugLevel.WARNING,
                    "AutoReconnect",
                    "未找到之前连接的设备: $deviceAddress"
                )
            }
        }
    }

    private fun addDebugMessage(level: DebugLevel, tag: String, message: String) {
        val newMessage = DebugMessage(level = level, tag = tag, message = message)
        _debugMessages.value = (_debugMessages.value + newMessage).takeLast(1000)
    }

    fun removeBond(deviceAddress: String) {
        val device = bondedDevices.find { it.address == deviceAddress }
        device?.let {
            bleService.removeBond(it)
        }
    }

    fun clearDebugMessages() {
        bleService.clearDebugMessages()
    }
    
    fun updateButtonConfig(config: ButtonConfig) {
        val configs = _buttonConfigs.value.toMutableList()
        val index = configs.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            configs[index] = config
            _buttonConfigs.value = configs
            
            viewModelScope.launch {
                settingsRepository.updateButtonConfig(config.id, config.isEnabled, config.isToggle)
            }
        }
    }
    
    fun updateSettings(newSettings: AppSettings) {
        // 如果自动重连设置发生变化，更新 BleService
        if (newSettings.autoReconnect != _settings.value.autoReconnect) {
            bleService.setAutoReconnect(newSettings.autoReconnect)
        }

        _settings.value = newSettings
        viewModelScope.launch {
            settingsRepository.updateAppSettings(newSettings)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopSending()
        udpDiscovery.stopDiscovery()

        when (_currentConnectionMode.value) {
            ConnectionMode.BLE -> bleService.disconnect()
            ConnectionMode.AP, ConnectionMode.LAN -> wifiService.disconnect()
        }
    }
}
