package com.example.jremote.screen

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.jremote.data.ConnectionType
import com.example.jremote.data.DiscoveredDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    bondedDevices: List<BluetoothDevice>,
    scannedDevices: List<BluetoothDevice>,
    isScanning: Boolean,
    isConnected: Boolean,
    connectedDeviceName: String,
    currentConnectionMode: ConnectionType,
    wifiScannedDevices: List<DiscoveredDevice>,
    isWifiScanning: Boolean,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRemoveBond: (String) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onStartWifiScan: (ConnectionType) -> Unit,
    onStopWifiScan: () -> Unit,
    onConnectWifiDevice: (DiscoveredDevice) -> Unit,
    onSetConnectionMode: (ConnectionType) -> Unit,
    onConfigWifi: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermissions by remember { mutableStateOf(false) }
    var locationEnabled by remember { mutableStateOf(false) }
    var connectingAddress by remember { mutableStateOf<String?>(null) }

    // 检查位置服务是否开启
    fun checkLocationEnabled() {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    // 监听生命周期，在 resume 时检查位置服务
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkLocationEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 连接成功或失败后清除连接状态
    LaunchedEffect(isConnected) {
        if (isConnected) {
            connectingAddress = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
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

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!allGranted) {
            permissionLauncher.launch(permissions)
        } else {
            hasPermissions = true
        }

        // 检查位置服务
        checkLocationEnabled()
    }

    // 进入页面根据模式自动扫描
    LaunchedEffect(hasPermissions, locationEnabled, currentConnectionMode, isConnected) {
        if (hasPermissions && !isConnected) {
            when (currentConnectionMode) {
                ConnectionType.BLUETOOTH -> {
                    if (locationEnabled) {
                        onStartScan()
                    }
                }
                ConnectionType.WIFI_AP, ConnectionType.WIFI_LAN -> onStartWifiScan(currentConnectionMode)
                ConnectionType.USB -> { /* USB 模式暂不支持扫描 */ }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isConnected) connectedDeviceName else "扫描设备",
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isConnected) {
                            Text(
                                text = when (currentConnectionMode) {
                                    ConnectionType.BLUETOOTH -> "蓝牙"
                                    ConnectionType.WIFI_AP -> "Wi-Fi AP"
                                    ConnectionType.WIFI_LAN -> "Wi-Fi 局域网"
                                    ConnectionType.USB -> "USB"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (isConnected) {
                        FilledTonalButton(
                            onClick = onDisconnect,
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text("断开")
                        }
                    } else if (currentConnectionMode == ConnectionType.WIFI_AP || currentConnectionMode == ConnectionType.WIFI_LAN) {
                        // WiFi 模式下显示配网按钮
                        FilledTonalButton(onClick = onConfigWifi) {
                            Icon(
                                imageVector = Icons.Default.Router,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("配网")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (currentConnectionMode == ConnectionType.BLUETOOTH)
                                Icons.Filled.Bluetooth else Icons.Outlined.Bluetooth,
                            contentDescription = null
                        )
                    },
                    label = { Text("蓝牙") },
                    selected = currentConnectionMode == ConnectionType.BLUETOOTH,
                    onClick = {
                        if (currentConnectionMode != ConnectionType.BLUETOOTH) {
                            onSetConnectionMode(ConnectionType.BLUETOOTH)
                        }
                    }
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (currentConnectionMode == ConnectionType.WIFI_AP)
                                Icons.Filled.Router else Icons.Outlined.Router,
                            contentDescription = null
                        )
                    },
                    label = { Text("AP") },
                    selected = currentConnectionMode == ConnectionType.WIFI_AP,
                    onClick = {
                        if (currentConnectionMode != ConnectionType.WIFI_AP) {
                            onSetConnectionMode(ConnectionType.WIFI_AP)
                        }
                    }
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (currentConnectionMode == ConnectionType.WIFI_LAN)
                                Icons.Filled.Wifi else Icons.Outlined.Wifi,
                            contentDescription = null
                        )
                    },
                    label = { Text("局域网") },
                    selected = currentConnectionMode == ConnectionType.WIFI_LAN,
                    onClick = {
                        if (currentConnectionMode != ConnectionType.WIFI_LAN) {
                            onSetConnectionMode(ConnectionType.WIFI_LAN)
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (!hasPermissions) {
                // 权限请求界面
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "需要蓝牙权限",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "请授予蓝牙权限以扫描设备",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (currentConnectionMode == ConnectionType.BLUETOOTH && !locationEnabled) {
                // 位置服务未开启
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "需要开启位置服务",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "BLE 扫描需要位置服务才能工作",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(
                            onClick = {
                                // 打开位置设置
                                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                context.startActivity(intent)
                            }
                        ) {
                            Text("打开设置")
                        }
                    }
                }
            } else {
                // 根据模式显示不同的设备列表
                when (currentConnectionMode) {
                    ConnectionType.BLUETOOTH -> {
                        BleDeviceList(
                            scannedDevices = scannedDevices,
                            isScanning = isScanning,
                            isConnected = isConnected,
                            connectedDeviceName = connectedDeviceName,
                            connectingAddress = connectingAddress,
                            onConnect = { address ->
                                connectingAddress = address
                                onConnect(address)
                            }
                        )
                    }
                    ConnectionType.WIFI_AP, ConnectionType.WIFI_LAN -> {
                        WifiDeviceList(
                            devices = wifiScannedDevices,
                            isScanning = isWifiScanning,
                            isConnected = isConnected,
                            connectedDeviceName = connectedDeviceName,
                            connectingAddress = connectingAddress,
                            onConnect = { device ->
                                connectingAddress = device.ip
                                onConnectWifiDevice(device)
                            }
                        )
                    }
                    ConnectionType.USB -> {
                        // USB 模式暂不支持
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "USB 模式暂不支持",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BleDeviceList(
    scannedDevices: List<BluetoothDevice>,
    isScanning: Boolean,
    isConnected: Boolean,
    connectedDeviceName: String,
    connectingAddress: String?,
    onConnect: (String) -> Unit
) {
    if (scannedDevices.isEmpty()) {
        EmptyDeviceList(
            isScanning = isScanning,
            isConnected = isConnected
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = scannedDevices,
                key = { it.address }
            ) { device ->
                val isThisDeviceConnecting = connectingAddress == device.address
                val isThisDeviceConnected = isConnected && connectedDeviceName == (device.name ?: "Unknown")
                val isBonded = device.bondState == BluetoothDevice.BOND_BONDED

                DeviceCard(
                    deviceName = device.name ?: "未知设备",
                    deviceAddress = device.address,
                    isConnected = isThisDeviceConnected,
                    isConnecting = isThisDeviceConnecting,
                    isBonded = isBonded,
                    onClick = {
                        if (!isConnected && connectingAddress == null) {
                            onConnect(device.address)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun WifiDeviceList(
    devices: List<DiscoveredDevice>,
    isScanning: Boolean,
    isConnected: Boolean,
    connectedDeviceName: String,
    connectingAddress: String?,
    onConnect: (DiscoveredDevice) -> Unit
) {
    if (devices.isEmpty()) {
        EmptyDeviceList(
            isScanning = isScanning,
            isConnected = isConnected
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = devices,
                key = { it.ip }
            ) { device ->
                val isThisDeviceConnecting = connectingAddress == device.ip
                val isThisDeviceConnected = isConnected && connectedDeviceName == device.name

                WifiDeviceCard(
                    device = device,
                    isConnected = isThisDeviceConnected,
                    isConnecting = isThisDeviceConnecting,
                    onClick = {
                        if (!isConnected && connectingAddress == null) {
                            onConnect(device)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyDeviceList(
    isScanning: Boolean,
    isConnected: Boolean
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "正在扫描设备...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "未发现设备",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "切换模式重新扫描",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    deviceName: String,
    deviceAddress: String,
    isConnected: Boolean,
    isConnecting: Boolean,
    isBonded: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态指示图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isConnected -> MaterialTheme.colorScheme.primary
                            isBonded -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = if (isConnected || isBonded)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isConnected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isBonded) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "已配对",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Text(
                    text = deviceAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // 连接状态
            when {
                isConnecting -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                isConnected -> {
                    Text(
                        text = "已连接",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun WifiDeviceCard(
    device: DiscoveredDevice,
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态指示图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = if (isConnected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isConnected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${device.ip}:${device.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // 连接状态
            when {
                isConnecting -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                isConnected -> {
                    Text(
                        text = "已连接",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
