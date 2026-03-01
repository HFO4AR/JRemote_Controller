package com.example.jremote.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.example.jremote.data.ConnectionStatus
import com.example.jremote.data.ConnectionType
import com.example.jremote.data.DebugLevel
import com.example.jremote.data.DebugMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothService(private val context: Context) {
    
    companion object {
        private const val TAG = "BluetoothService"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    
    private var isConnected = false
    private var receiveThread: Thread? = null
    
    private val _connectionStatus = MutableStateFlow(ConnectionStatus())
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    private val _debugMessages = MutableStateFlow<List<DebugMessage>>(emptyList())
    val debugMessages: StateFlow<List<DebugMessage>> = _debugMessages.asStateFlow()
    
    private val _receivedData = MutableStateFlow<ByteArray?>(null)
    val receivedData: StateFlow<ByteArray?> = _receivedData.asStateFlow()
    
    private fun addDebugMessage(level: DebugLevel, tag: String, message: String) {
        val newMessage = DebugMessage(level = level, tag = tag, message = message)
        _debugMessages.value = (_debugMessages.value + newMessage).takeLast(1000)
    }
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermissions()) {
            addDebugMessage(DebugLevel.ERROR, TAG, "Missing Bluetooth permissions")
            return emptyList()
        }
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }
    
    private fun hasBluetoothPermissions(): Boolean {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
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
    
    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions()) {
            addDebugMessage(DebugLevel.ERROR, TAG, "Missing Bluetooth permissions")
            return@withContext false
        }
        
        try {
            addDebugMessage(DebugLevel.INFO, TAG, "Connecting to ${device.name}...")
            
            bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothSocket?.connect()
            
            outputStream = bluetoothSocket?.outputStream
            inputStream = bluetoothSocket?.inputStream
            
            isConnected = true
            _connectionStatus.value = ConnectionStatus(
                isConnected = true,
                deviceName = device.name ?: "Unknown",
                deviceAddress = device.address,
                connectionType = ConnectionType.BLUETOOTH
            )
            
            startReceiveThread()
            
            addDebugMessage(DebugLevel.INFO, TAG, "Connected to ${device.name}")
            true
        } catch (e: IOException) {
            addDebugMessage(DebugLevel.ERROR, TAG, "Connection failed: ${e.message}")
            disconnect()
            false
        } catch (e: SecurityException) {
            addDebugMessage(DebugLevel.ERROR, TAG, "Security exception: ${e.message}")
            disconnect()
            false
        }
    }
    
    private fun startReceiveThread() {
        receiveThread = Thread {
            val buffer = ByteArray(1024)
            while (isConnected && !Thread.currentThread().isInterrupted) {
                try {
                    val bytes = inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        val data = buffer.copyOf(bytes)
                        _receivedData.value = data
                        addDebugMessage(DebugLevel.INFO, TAG, "Received ${bytes} bytes")
                    }
                } catch (e: IOException) {
                    if (isConnected) {
                        addDebugMessage(DebugLevel.ERROR, TAG, "Receive error: ${e.message}")
                        disconnect()
                    }
                    break
                }
            }
        }.apply { start() }
    }
    
    fun sendData(data: ByteArray): Boolean {
        return try {
            outputStream?.write(data)
            outputStream?.flush()
            addDebugMessage(DebugLevel.INFO, TAG, "Sent ${data.size} bytes")
            true
        } catch (e: IOException) {
            addDebugMessage(DebugLevel.ERROR, TAG, "Send error: ${e.message}")
            false
        }
    }
    
    fun disconnect() {
        isConnected = false
        receiveThread?.interrupt()
        receiveThread = null
        
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            addDebugMessage(DebugLevel.ERROR, TAG, "Disconnect error: ${e.message}")
        }
        
        inputStream = null
        outputStream = null
        bluetoothSocket = null
        
        _connectionStatus.value = ConnectionStatus()
        addDebugMessage(DebugLevel.INFO, TAG, "Disconnected")
    }
    
    fun clearDebugMessages() {
        _debugMessages.value = emptyList()
    }
}
