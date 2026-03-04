package com.example.jremote.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jremote.bluetooth.BleConfigService
import com.example.jremote.wifi.WifiUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BleConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val bleConfigService = BleConfigService(application)
    private val wifiUtils = WifiUtils(application)

    val isConnected = bleConfigService.isConnected
    val configStatus = bleConfigService.configStatus
    val debugMessages = bleConfigService.debugMessages

    // 扫描到的设备名称列表
    val scannedDeviceNames: StateFlow<List<String>> = bleConfigService.scannedDevices.map { devices ->
        devices.map { it.name ?: it.address }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    val isScanning = bleConfigService.isScanning

    private val _ssid = MutableStateFlow("")
    val ssid: StateFlow<String> = _ssid.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _currentWifiSsid = MutableStateFlow<String?>(null)
    val currentWifiSsid: StateFlow<String?> = _currentWifiSsid.asStateFlow()

    fun isBluetoothEnabled(): Boolean = bleConfigService.isBluetoothEnabled()

    fun readCurrentWifi() {
        val currentSsid = wifiUtils.getCurrentSsid()
        if (currentSsid != null) {
            _currentWifiSsid.value = currentSsid
            _ssid.value = currentSsid
        }
    }

    fun startScan() {
        bleConfigService.startScan()
    }

    fun stopScan() {
        bleConfigService.stopScan()
    }

    fun connectToDevice(deviceAddress: String) {
        // 从扫描列表中找到对应的设备
        val device = bleConfigService.scannedDevices.value.find {
            (it.name ?: it.address) == deviceAddress
        }
        device?.let { bleConfigService.connect(it) }
    }

    fun disconnect() {
        bleConfigService.disconnect()
    }

    fun updateSsid(ssid: String) {
        _ssid.value = ssid
    }

    fun updatePassword(password: String) {
        _password.value = password
    }

    fun sendConfig() {
        viewModelScope.launch {
            bleConfigService.sendSsid(_ssid.value)
            kotlinx.coroutines.delay(500)
            bleConfigService.sendPassword(_password.value)
        }
    }
}
