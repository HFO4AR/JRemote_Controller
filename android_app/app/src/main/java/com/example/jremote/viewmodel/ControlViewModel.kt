package com.example.jremote.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jremote.bluetooth.BleService
import com.example.jremote.data.ButtonConfig
import com.example.jremote.data.ConnectionStatus
import com.example.jremote.data.ControlData
import com.example.jremote.data.DebugLevel
import com.example.jremote.data.DebugMessage
import com.example.jremote.data.JoystickState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ControlViewModel(application: Application) : AndroidViewModel(application) {
    
    private val bleService = BleService(application)
    
    private val _leftJoystickState = MutableStateFlow(JoystickState())
    val leftJoystickState: StateFlow<JoystickState> = _leftJoystickState.asStateFlow()
    
    private val _rightJoystickState = MutableStateFlow(JoystickState())
    val rightJoystickState: StateFlow<JoystickState> = _rightJoystickState.asStateFlow()
    
    private val _buttonStates = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val buttonStates: StateFlow<Map<Int, Boolean>> = _buttonStates.asStateFlow()
    
    private val _buttonConfigs = MutableStateFlow<List<ButtonConfig>>(
        listOf(
            ButtonConfig(0, "A", 0x01.toByte()),
            ButtonConfig(1, "B", 0x02.toByte()),
            ButtonConfig(2, "X", 0x04.toByte()),
            ButtonConfig(3, "Y", 0x08.toByte()),
            ButtonConfig(4, "LB", 0x10.toByte(), isToggle = true),
            ButtonConfig(5, "RB", 0x20.toByte(), isToggle = true),
            ButtonConfig(6, "LT", 0x40.toByte()),
            ButtonConfig(7, "RT", 0x80.toByte()),
            ButtonConfig(8, "START", 0x00.toByte()),
            ButtonConfig(9, "SELECT", 0x00.toByte())
        )
    )
    val buttonConfigs: StateFlow<List<ButtonConfig>> = _buttonConfigs.asStateFlow()
    
    private val _connectionStatus = MutableStateFlow(ConnectionStatus())
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _debugMessages = MutableStateFlow<List<DebugMessage>>(emptyList())
    val debugMessages: StateFlow<List<DebugMessage>> = _debugMessages.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    val rssi: StateFlow<Int?> = bleService.rssi
    val latency: StateFlow<Int?> = bleService.latency

    private var sendJob: Job? = null

    private val sendIntervalMs = 20L

    val bondedDevices = bleService.getBondedDevices()
    val scannedDevices = bleService.scannedDevices
    val isScanning = bleService.isScanning

    init {
        viewModelScope.launch {
            bleService.connectionStatus.collect { status ->
                _connectionStatus.value = status
            }
        }

        viewModelScope.launch {
            bleService.debugMessages.collect { messages ->
                _debugMessages.value = messages
            }
        }
    }
    
    fun startBleScan() {
        bleService.startBleScan()
    }
    
    fun stopBleScan() {
        bleService.stopBleScan()
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
        sendJob = viewModelScope.launch {
            while (true) {
                sendControlData()
                delay(sendIntervalMs)
            }
        }
    }
    
    fun stopSending() {
        _isSending.value = false
        sendJob?.cancel()
        sendJob = null
    }
    
    private fun sendControlData() {
        if (!_connectionStatus.value.isConnected) return
        
        val controlData = ControlData(
            leftJoystick = _leftJoystickState.value,
            rightJoystick = _rightJoystickState.value,
            buttons = _buttonStates.value
        )
        
        val data = byteArrayOf(0xAA.toByte()) + controlData.toByteArray()
        bleService.sendData(data)
    }
    
    fun connectToDevice(deviceAddress: String) {
        viewModelScope.launch {
            // 先在已配对设备中查找
            var device = bondedDevices.find { it.address == deviceAddress }
            // 再在扫描到的设备中查找
            if (device == null) {
                device = scannedDevices.value.find { it.address == deviceAddress }
            }
            if (device != null) {
                bleService.connect(device)
            }
        }
    }
    
    fun disconnect() {
        stopSending()
        bleService.disconnect()
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
        }
    }
    
    fun addButtonConfig(config: ButtonConfig) {
        val configs = _buttonConfigs.value.toMutableList()
        configs.add(config)
        _buttonConfigs.value = configs
    }
    
    fun removeButtonConfig(buttonId: Int) {
        val configs = _buttonConfigs.value.filter { it.id != buttonId }
        _buttonConfigs.value = configs
    }
    
    override fun onCleared() {
        super.onCleared()
        stopSending()
        bleService.disconnect()
    }
}
