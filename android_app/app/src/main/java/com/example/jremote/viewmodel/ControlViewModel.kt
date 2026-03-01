package com.example.jremote.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jremote.bluetooth.BleService
import com.example.jremote.data.AppSettings
import com.example.jremote.data.ButtonConfig
import com.example.jremote.data.ConnectionStatus
import com.example.jremote.data.ControlData
import com.example.jremote.data.DebugLevel
import com.example.jremote.data.DebugMessage
import com.example.jremote.data.JoystickState
import com.example.jremote.data.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ControlViewModel(application: Application) : AndroidViewModel(application) {
    
    private val bleService = BleService(application)
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

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val bondedDevices = bleService.getBondedDevices()
    val scannedDevices = bleService.scannedDevices
    val isScanning = bleService.isScanning

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
                _connectionStatus.value = status
            }
        }

        viewModelScope.launch {
            bleService.debugMessages.collect { messages ->
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
        bleService.sendData(data)
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
        bleService.sendData(data)
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
            bleService.sendData(data)
        }
    }
    
    fun connectToDevice(deviceAddress: String) {
        val device = bondedDevices.find { it.address == deviceAddress }
            ?: scannedDevices.value.find { it.address == deviceAddress }
        
        device?.let {
            viewModelScope.launch {
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
            
            viewModelScope.launch {
                settingsRepository.updateButtonConfig(config.id, config.isEnabled, config.isToggle)
            }
        }
    }
    
    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        viewModelScope.launch {
            settingsRepository.updateAppSettings(newSettings)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopSending()
        bleService.disconnect()
    }
}
