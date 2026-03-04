package com.example.jremote.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleConfigScreen(
    isConnected: Boolean,
    configStatus: String,
    currentWifiSsid: String?,
    scannedDevices: List<String>,
    isScanning: Boolean,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReadCurrentWifi: () -> Unit,
    onStartScan: () -> Unit,
    onConnectToDevice: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var configSent by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // 当 currentWifiSsid 变化时，更新 SSID
    LaunchedEffect(currentWifiSsid) {
        if (currentWifiSsid != null && ssid.isEmpty()) {
            ssid = currentWifiSsid
            onSsidChange(currentWifiSsid)
        }
    }

    // 监听状态变化
    LaunchedEffect(configStatus) {
        if (configStatus.contains("成功") || configStatus.contains("连接成功")) {
            configSent = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi 配网") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Router,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (isConnected) "已连接到 ESP32" else "连接 ESP32 配网模块",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 状态提示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        configStatus.contains("成功") || configStatus.contains("连接成功") ->
                            MaterialTheme.colorScheme.primaryContainer
                        configStatus.contains("失败") || configStatus.contains("错误") || configStatus.contains("失败") ->
                            MaterialTheme.colorScheme.errorContainer
                        else ->
                            MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        configStatus.contains("成功") || configStatus.contains("连接成功") -> {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (configSent) "配网成功！ESP32 将自动连接 WiFi" else configStatus.ifEmpty { "已连接" },
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        configStatus.contains("失败") || configStatus.contains("错误") -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = configStatus,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        else -> {
                            Text(
                                text = if (isConnected) "连接成功，请配置 WiFi" else configStatus.ifEmpty { "请扫描并连接 ESP32" },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 未连接时显示扫描界面
            if (!isConnected) {
                // 扫描按钮
                OutlinedButton(
                    onClick = onStartScan,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isScanning
                ) {
                    Text(if (isScanning) "扫描中..." else "扫描 ESP32 设备")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 显示扫描到的设备
                if (scannedDevices.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "发现设备:",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            scannedDevices.forEach { deviceName ->
                                TextButton(
                                    onClick = { onConnectToDevice(deviceName) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(deviceName)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 一键获取当前 WiFi
                FilledTonalButton(
                    onClick = onReadCurrentWifi,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (currentWifiSsid != null) "当前 WiFi: $currentWifiSsid" else "读取当前 WiFi")
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // 已连接后显示 WiFi 配置
            if (isConnected) {
                // SSID 输入框
                OutlinedTextField(
                    value = ssid,
                    onValueChange = {
                        ssid = it
                        onSsidChange(it)
                    },
                    label = { Text("WiFi 名称 (SSID)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 密码输入框
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onPasswordChange(it)
                    },
                    label = { Text("WiFi 密码（开放网络留空）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 发送配置按钮
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = ssid.isNotBlank() && !configSent
                ) {
                    Text(if (configSent) "已发送" else "发送配置")
                }

                // 配置成功提示
                if (configSent) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "ESP32 正在连接 WiFi...\n连接成功后可在局域网模式发现设备",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 说明
            Text(
                text = "运行时按一下 IO0 按钮可进入配网模式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
