package com.example.jremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.jremote.data.ConnectionType
import com.example.jremote.navigation.Screen
import com.example.jremote.screen.BleConfigScreen
import com.example.jremote.screen.ConnectionScreen
import com.example.jremote.screen.ControlScreen
import com.example.jremote.screen.SerialTerminalScreen
import com.example.jremote.screen.SettingsScreen
import com.example.jremote.ui.theme.JRemoteTheme
import com.example.jremote.viewmodel.BleConfigViewModel
import com.example.jremote.viewmodel.ControlViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ControlViewModel = viewModel()
            val settings by viewModel.settings.collectAsState()

            JRemoteTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor
            ) {
                val navController = rememberNavController()

                AppNavigation(
                    navController = navController,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModel: ControlViewModel,
    modifier: Modifier = Modifier
) {
    val leftJoystickState by viewModel.leftJoystickState.collectAsState()
    val rightJoystickState by viewModel.rightJoystickState.collectAsState()
    val buttonConfigs by viewModel.buttonConfigs.collectAsState()
    val buttonStates by viewModel.buttonStates.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val debugMessages by viewModel.debugMessages.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val isInControlMode by viewModel.isInControlMode.collectAsState()
    val isEmergencyStopped by viewModel.isEmergencyStopped.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val rssi by viewModel.rssi.collectAsState()
    val latency by viewModel.latency.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Screen.Control.route,
        modifier = modifier.fillMaxSize()
    ) {
        composable(Screen.Control.route) {
            ControlScreen(
                leftJoystickState = leftJoystickState,
                rightJoystickState = rightJoystickState,
                buttonConfigs = buttonConfigs,
                buttonStates = buttonStates,
                connectionStatus = connectionStatus,
                debugMessages = debugMessages,
                isSending = isSending,
                isInControlMode = isInControlMode,
                isEmergencyStopped = isEmergencyStopped,
                showDebugPanel = settings.showDebugPanel,
                toggleButtonLayout = settings.toggleButtonLayout,
                hapticFeedback = settings.hapticFeedback,
                rssi = rssi,
                latency = latency,
                onLeftJoystickChange = { viewModel.updateLeftJoystick(it) },
                onRightJoystickChange = { viewModel.updateRightJoystick(it) },
                onButtonPressed = { id, pressed -> viewModel.updateButtonState(id, pressed) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onConnectionClick = { navController.navigate(Screen.Connection.route) },
                onStartSending = { viewModel.startSending() },
                onStopSending = { viewModel.stopSending() },
                onEmergencyStop = { viewModel.emergencyStop() },
                onExitControlMode = { viewModel.exitControlMode() }
            )
        }

        composable(Screen.Connection.route) {
            val scannedDevices by viewModel.scannedDevices.collectAsState()
            val isScanning by viewModel.isScanning.collectAsState()
            val currentConnectionMode by viewModel.currentConnectionMode.collectAsState()
            val wifiScannedDevices by viewModel.wifiScannedDevices.collectAsState()
            val isWifiScanning by viewModel.isWifiScanning.collectAsState()

            ConnectionScreen(
                bondedDevices = viewModel.bondedDevices,
                scannedDevices = scannedDevices,
                isScanning = isScanning,
                isConnected = connectionStatus.isConnected,
                connectedDeviceName = connectionStatus.deviceName,
                currentConnectionMode = currentConnectionMode,
                wifiScannedDevices = wifiScannedDevices,
                isWifiScanning = isWifiScanning,
                onConnect = { address -> viewModel.connectToDevice(address) },
                onDisconnect = { viewModel.disconnect() },
                onRemoveBond = { address -> viewModel.removeBond(address) },
                onStartScan = { viewModel.startBleScan() },
                onStartWifiScan = { mode -> viewModel.startWifiDiscovery(mode) },
                onStopWifiScan = { viewModel.stopWifiDiscovery() },
                onConnectWifiDevice = { device -> viewModel.connectToWifiDevice(device) },
                onSetConnectionMode = { mode -> viewModel.setConnectionMode(mode) },
                onConfigWifi = { navController.navigate(Screen.BleConfig.route) },
                onNavigateBack = { navController.popBackStack() },
                onRefresh = {
                    when (currentConnectionMode) {
                        ConnectionType.BLUETOOTH -> viewModel.startBleScan()
                        ConnectionType.WIFI_AP, ConnectionType.WIFI_LAN -> viewModel.startWifiDiscovery(currentConnectionMode)
                        ConnectionType.USB -> { /* USB 不支持 */ }
                    }
                },
                onStopScan = {
                    when (currentConnectionMode) {
                        ConnectionType.BLUETOOTH -> viewModel.stopBleScan()
                        ConnectionType.WIFI_AP, ConnectionType.WIFI_LAN -> viewModel.stopWifiDiscovery()
                        ConnectionType.USB -> { /* USB 不支持 */ }
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                buttonConfigs = buttonConfigs,
                settings = settings,
                onUpdateButtonConfig = { viewModel.updateButtonConfig(it) },
                onUpdateSettings = { viewModel.updateSettings(it) },
                onNavigateBack = { navController.popBackStack() },
                onSerialTerminalClick = { navController.navigate(Screen.SerialTerminal.route) }
            )
        }

        composable(Screen.BleConfig.route) {
            val configViewModel: BleConfigViewModel = viewModel()
            val isConnected by configViewModel.isConnected.collectAsState()
            val configStatus by configViewModel.configStatus.collectAsState()
            val currentWifiSsid by configViewModel.currentWifiSsid.collectAsState()
            val scannedDevices by configViewModel.scannedDeviceNames.collectAsState()
            val isScanning by configViewModel.isScanning.collectAsState()

            BleConfigScreen(
                isConnected = isConnected,
                configStatus = configStatus,
                currentWifiSsid = currentWifiSsid,
                scannedDevices = scannedDevices,
                isScanning = isScanning,
                onSsidChange = { configViewModel.updateSsid(it) },
                onPasswordChange = { configViewModel.updatePassword(it) },
                onConnect = { configViewModel.sendConfig() },
                onDisconnect = { configViewModel.disconnect() },
                onReadCurrentWifi = { configViewModel.readCurrentWifi() },
                onStartScan = { configViewModel.startScan() },
                onConnectToDevice = { address -> configViewModel.connectToDevice(address) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SerialTerminal.route) {
            SerialTerminalScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
