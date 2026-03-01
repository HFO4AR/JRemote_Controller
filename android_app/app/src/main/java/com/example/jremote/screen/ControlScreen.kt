package com.example.jremote.screen

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.jremote.components.ControlButton
import com.example.jremote.components.Joystick
import com.example.jremote.data.ButtonConfig
import com.example.jremote.data.ConnectionStatus
import com.example.jremote.data.DebugLevel
import com.example.jremote.data.DebugMessage
import com.example.jremote.data.JoystickState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ControlScreen(
    leftJoystickState: JoystickState,
    rightJoystickState: JoystickState,
    buttonConfigs: List<ButtonConfig>,
    buttonStates: Map<Int, Boolean>,
    connectionStatus: ConnectionStatus,
    debugMessages: List<DebugMessage>,
    isSending: Boolean,
    isInControlMode: Boolean,
    isEmergencyStopped: Boolean,
    showDebugPanel: Boolean,
    rssi: Int?,
    latency: Int?,
    onLeftJoystickChange: (JoystickState) -> Unit,
    onRightJoystickChange: (JoystickState) -> Unit,
    onButtonPressed: (Int, Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onConnectionClick: () -> Unit,
    onStartSending: () -> Unit,
    onStopSending: () -> Unit,
    onEmergencyStop: () -> Unit,
    onExitControlMode: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(isInControlMode) {
        activity?.let {
            val window = it.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)

            if (isInControlMode) {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                WindowCompat.setDecorFitsSystemWindows(window, false)
                insetsController.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                WindowCompat.setDecorFitsSystemWindows(window, true)
                insetsController.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }
        }

        onDispose {
            activity?.let {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val window = it.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                WindowCompat.setDecorFitsSystemWindows(window, true)
                insetsController.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    if (!isInControlMode) {
        PortraitModeScreen(
            connectionStatus = connectionStatus,
            onConnectionClick = onConnectionClick,
            onStartControl = onStartSending,
            onSettingsClick = onSettingsClick
        )
    } else {
        LandscapeControlScreen(
            leftJoystickState = leftJoystickState,
            rightJoystickState = rightJoystickState,
            buttonConfigs = buttonConfigs,
            buttonStates = buttonStates,
            connectionStatus = connectionStatus,
            debugMessages = debugMessages,
            isSending = isSending,
            isEmergencyStopped = isEmergencyStopped,
            showDebugPanel = showDebugPanel,
            rssi = rssi,
            latency = latency,
            onLeftJoystickChange = onLeftJoystickChange,
            onRightJoystickChange = onRightJoystickChange,
            onButtonPressed = onButtonPressed,
            onSettingsClick = onSettingsClick,
            onConnectionClick = onConnectionClick,
            onStartSending = onStartSending,
            onStopSending = onExitControlMode,
            onEmergencyStop = onEmergencyStop
        )
    }
}

@Composable
private fun PortraitModeScreen(
    connectionStatus: ConnectionStatus,
    onConnectionClick: () -> Unit,
    onStartControl: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF12121A))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "JRemote Controller",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/HFO4AR"))
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Design by 1034Robotics HFO4AR",
            color = Color(0xFF888888),
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        if (connectionStatus.isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onConnectionClick() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(8.dp)
                            .background(Color.White, shape = RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = if (connectionStatus.isConnected) "已连接: ${connectionStatus.deviceName}" else "未连接 - 点击连接",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStartControl,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4A90D9)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "启动控制",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onSettingsClick,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3A3A4A)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "设置",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (connectionStatus.isConnected) "点击启动进入横屏控制模式" else "未连接设备",
            color = if (connectionStatus.isConnected) Color(0xFF4CAF50) else Color(0xFFFF9800),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun JoystickInfoPanel(
    label: String,
    state: JoystickState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A2A).copy(alpha = 0.8f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Color(0xFF888888),
            fontSize = 10.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = String.format("X:%+.2f", state.x),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = String.format("Y:%+.2f", state.y),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SignalStrengthIcon(rssi: Int) {
    // RSSI 范围通常在 -100 到 -30 dBm
    // -30 到 -50: 优秀 (4格)
    // -50 到 -65: 良好 (3格)
    // -65 到 -80: 一般 (2格)
    // -80 以下: 差 (1格)
    val signalLevel = when {
        rssi >= -50 -> 4
        rssi >= -65 -> 3
        rssi >= -80 -> 2
        else -> 1
    }

    val color = when (signalLevel) {
        4 -> Color(0xFF4CAF50) // 绿色
        3 -> Color(0xFF8BC34A) // 浅绿
        2 -> Color(0xFFFFC107) // 黄色
        else -> Color(0xFFF44336) // 红色
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 4个信号格
        for (i in 1..4) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((i * 4 + 4).dp)
                    .background(
                        if (i <= signalLevel) color else Color(0xFF444444),
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        }
    }

    // 显示 RSSI 数值
    Text(
        text = "${rssi}dBm",
        color = color,
        fontSize = 10.sp,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun LatencyDisplay(latency: Int) {
    // 延迟等级
    // < 50ms: 优秀 (绿色)
    // 50-100ms: 良好 (浅绿)
    // 100-200ms: 一般 (黄色)
    // > 200ms: 差 (红色)
    val color = when {
        latency < 50 -> Color(0xFF4CAF50) // 绿色
        latency < 100 -> Color(0xFF8BC34A) // 浅绿
        latency < 200 -> Color(0xFFFFC107) // 黄色
        else -> Color(0xFFF44336) // 红色
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0xFF2A2A3A), shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // 延迟图标 (闪电形状用圆圈代替)
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, shape = RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${latency}ms",
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun saveLogsToFile(context: android.content.Context, messages: List<DebugMessage>) {
    try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "jremote_logs_$timestamp.txt"
        
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        
        val content = messages.joinToString("\n") { msg ->
            "[${msg.timestamp}] [${msg.level}] [${msg.tag}] ${msg.message}"
        }
        
        file.writeText(content)
        Toast.makeText(context, "日志已保存到: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}



@Composable
private fun LandscapeControlScreen(
    leftJoystickState: JoystickState,
    rightJoystickState: JoystickState,
    buttonConfigs: List<ButtonConfig>,
    buttonStates: Map<Int, Boolean>,
    connectionStatus: ConnectionStatus,
    debugMessages: List<DebugMessage>,
    isSending: Boolean,
    isEmergencyStopped: Boolean,
    showDebugPanel: Boolean,
    rssi: Int?,
    latency: Int?,
    onLeftJoystickChange: (JoystickState) -> Unit,
    onRightJoystickChange: (JoystickState) -> Unit,
    onButtonPressed: (Int, Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onConnectionClick: () -> Unit,
    onStartSending: () -> Unit,
    onStopSending: () -> Unit,
    onEmergencyStop: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF12121A))
    ) {
        // 顶部工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 信号强度图标
                if (connectionStatus.isConnected && rssi != null) {
                    SignalStrengthIcon(rssi = rssi)
                }

                // 延迟显示
                if (connectionStatus.isConnected && latency != null) {
                    LatencyDisplay(latency = latency)
                }

                Box(
                    modifier = Modifier
                        .background(
                            if (connectionStatus.isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onConnectionClick() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (connectionStatus.isConnected) "已连接" else "未连接",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onStopSending,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336)
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("退出", fontSize = 12.sp)
                }
            }
        }

        // 主控制区域
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧摇杆区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                // 摇杆上方：4个切换按钮（2x2布局）L1-L4
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        buttonConfigs.getOrNull(6)?.let { config ->
                            ControlButton(
                                config = config,
                                isPressed = buttonStates[config.id] ?: false,
                                isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                                size = 40.dp,
                                onPressed = { pressed -> onButtonPressed(config.id, pressed) }
                            )
                        }
                        buttonConfigs.getOrNull(7)?.let { config ->
                            ControlButton(
                                config = config,
                                isPressed = buttonStates[config.id] ?: false,
                                isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                                size = 40.dp,
                                onPressed = { pressed -> onButtonPressed(config.id, pressed) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        buttonConfigs.getOrNull(8)?.let { config ->
                            ControlButton(
                                config = config,
                                isPressed = buttonStates[config.id] ?: false,
                                isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                                size = 40.dp,
                                onPressed = { pressed -> onButtonPressed(config.id, pressed) }
                            )
                        }
                        buttonConfigs.getOrNull(9)?.let { config ->
                            ControlButton(
                                config = config,
                                isPressed = buttonStates[config.id] ?: false,
                                isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                                size = 40.dp,
                                onPressed = { pressed -> onButtonPressed(config.id, pressed) }
                            )
                        }
                    }
                }

                // 摇杆周围环绕3个按钮（LX、LY、LZ）- 在圆上，间距相等，偏向屏幕中心
                Box(
                    modifier = Modifier.size(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Joystick(
                        size = 120.dp,
                        onStateChanged = onLeftJoystickChange
                    )
                    // LX: 角度330°（右上）x=87, y=-50
                    buttonConfigs.getOrNull(0)?.let { config ->
                        ControlButton(
                            config = config,
                            isPressed = buttonStates[config.id] ?: false,
                            isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                            size = 45.dp,
                            onPressed = { pressed -> onButtonPressed(config.id, pressed) },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(x = 87.dp, y = (-50).dp)
                        )
                    }
                    // LY: 角度0°（正右）x=100, y=0
                    buttonConfigs.getOrNull(1)?.let { config ->
                        ControlButton(
                            config = config,
                            isPressed = buttonStates[config.id] ?: false,
                            isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                            size = 45.dp,
                            onPressed = { pressed -> onButtonPressed(config.id, pressed) },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(x = 100.dp, y = 0.dp)
                        )
                    }
                    // LZ: 角度30°（右下）x=87, y=50
                    buttonConfigs.getOrNull(2)?.let { config ->
                        ControlButton(
                            config = config,
                            isPressed = buttonStates[config.id] ?: false,
                            isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                            size = 45.dp,
                            onPressed = { pressed -> onButtonPressed(config.id, pressed) },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(x = 87.dp, y = 50.dp)
                        )
                    }
                }

                JoystickInfoPanel(
                    label = "L",
                    state = leftJoystickState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(top = 4.dp)
                )
            }

            // 中间区域：状态指示 + START/STOP按钮 + 日志
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 状态指示器
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val statusColor = when {
                        !connectionStatus.isConnected -> Color(0xFFF44336)
                        isEmergencyStopped -> Color(0xFFFF9800)
                        isSending -> Color(0xFF4CAF50)
                        else -> Color(0xFFF44336)
                    }
                    val statusText = when {
                        !connectionStatus.isConnected -> "遥控器断开连接"
                        isEmergencyStopped -> "设备急停中"
                        isSending -> "遥控正常"
                        else -> "遥控未就绪"
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(statusColor, shape = CircleShape)
                    )
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // START/STOP切换按钮
                Button(
                    onClick = {
                        if (isSending) {
                            onEmergencyStop()
                        } else {
                            onStartSending()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSending) Color(0xFFF44336) else Color(0xFF4CAF50)
                    ),
                    modifier = Modifier
                        .height(56.dp)
                        .fillMaxWidth(0.6f)
                ) {
                    Text(
                        text = if (isSending) "STOP" else "START",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (showDebugPanel) {
                    ExpandableLogPanel(
                        debugMessages = debugMessages,
                        modifier = Modifier
                            .height(220.dp)
                            .fillMaxWidth()
                    )
                }
            }

            // 右侧摇杆区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                // 摇杆上方：4个切换按钮（2x2布局）R1-R4
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        buttonConfigs.getOrNull(10)?.let { config ->
                            ControlButton(
                                config = config,
                                isPressed = buttonStates[config.id] ?: false,
                                isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                                size = 40.dp,
                                onPressed = { pressed -> onButtonPressed(config.id, pressed) }
                            )
                        }
                        buttonConfigs.getOrNull(11)?.let { config ->
                            ControlButton(
                                config = config,
                                isPressed = buttonStates[config.id] ?: false,
                                isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                                size = 40.dp,
                                onPressed = { pressed -> onButtonPressed(config.id, pressed) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        buttonConfigs.getOrNull(12)?.let { config ->
                            ControlButton(
                                config = config,
                                isPressed = buttonStates[config.id] ?: false,
                                isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                                size = 40.dp,
                                onPressed = { pressed -> onButtonPressed(config.id, pressed) }
                            )
                        }
                        buttonConfigs.getOrNull(13)?.let { config ->
                            ControlButton(
                                config = config,
                                isPressed = buttonStates[config.id] ?: false,
                                isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                                size = 40.dp,
                                onPressed = { pressed -> onButtonPressed(config.id, pressed) }
                            )
                        }
                    }
                }

                // 摇杆周围环绕3个按钮（RX、RY、RZ）- 在圆上，间距相等，偏向屏幕中心
                Box(
                    modifier = Modifier.size(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Joystick(
                        size = 120.dp,
                        onStateChanged = onRightJoystickChange
                    )
                    // RX: 角度150°（左下）x=-87, y=-50
                    buttonConfigs.getOrNull(3)?.let { config ->
                        ControlButton(
                            config = config,
                            isPressed = buttonStates[config.id] ?: false,
                            isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                            size = 45.dp,
                            onPressed = { pressed -> onButtonPressed(config.id, pressed) },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(x = (-87).dp, y = (-50).dp)
                        )
                    }
                    // RY: 角度180°（正左）x=-100, y=0
                    buttonConfigs.getOrNull(4)?.let { config ->
                        ControlButton(
                            config = config,
                            isPressed = buttonStates[config.id] ?: false,
                            isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                            size = 45.dp,
                            onPressed = { pressed -> onButtonPressed(config.id, pressed) },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(x = (-100).dp, y = 0.dp)
                        )
                    }
                    // RZ: 角度210°（左上）x=-87, y=50
                    buttonConfigs.getOrNull(5)?.let { config ->
                        ControlButton(
                            config = config,
                            isPressed = buttonStates[config.id] ?: false,
                            isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                            size = 45.dp,
                            onPressed = { pressed -> onButtonPressed(config.id, pressed) },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(x = (-87).dp, y = (50).dp)
                        )
                    }
                }

                JoystickInfoPanel(
                    label = "R",
                    state = rightJoystickState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ExpandableLogPanel(
    debugMessages: List<DebugMessage>,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }
    var autoScroll by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(debugMessages.size, autoScroll) {
        if (autoScroll && debugMessages.isNotEmpty()) {
            listState.animateScrollToItem(debugMessages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A2A))
    ) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2A2A3A))
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "调试日志 (${debugMessages.size})",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "滚动",
                    color = Color.White,
                    fontSize = 11.sp
                )
                Switch(
                    checked = autoScroll,
                    onCheckedChange = { autoScroll = it },
                    modifier = Modifier.height(24.dp),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4A90D9),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )

                IconButton(
                    onClick = { saveLogsToFile(context, debugMessages) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "保存日志",
                        tint = Color(0xFF4A90D9),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 日志内容
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(8.dp),
                reverseLayout = false
            ) {
                items(debugMessages) { message ->
                    val color = when (message.level) {
                        DebugLevel.ERROR -> Color(0xFFF44336)
                        DebugLevel.WARNING -> Color(0xFFFF9800)
                        DebugLevel.INFO -> Color(0xFF4CAF50)
                        else -> Color(0xFF888888)
                    }
                    val timeStr = java.text.SimpleDateFormat(
                        "HH:mm:ss.SSS",
                        java.util.Locale.getDefault()
                    )
                        .format(java.util.Date(message.timestamp))
                    Text(
                        text = "[$timeStr][${message.tag}] ${message.message}",
                        color = color,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        modifier = Modifier.padding(vertical = 1.dp),
                        softWrap = true,
                        maxLines = Int.MAX_VALUE
                    )
                }
            }
        }
    }
}




