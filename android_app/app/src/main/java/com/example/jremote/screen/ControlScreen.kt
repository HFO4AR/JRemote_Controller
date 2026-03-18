package com.example.jremote.screen

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
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
import com.example.jremote.data.FrameFormat
import com.example.jremote.data.JoystickState
import com.example.jremote.data.ToggleButtonLayout
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
    toggleButtonLayout: ToggleButtonLayout,
    hapticFeedback: Boolean,
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
    onExitControlMode: () -> Unit,
    frameFormat: FrameFormat,
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
            toggleButtonLayout = toggleButtonLayout,
            hapticFeedback = hapticFeedback,
            rssi = rssi,
            latency = latency,
            onLeftJoystickChange = onLeftJoystickChange,
            onRightJoystickChange = onRightJoystickChange,
            onButtonPressed = onButtonPressed,
            onSettingsClick = onSettingsClick,
            onConnectionClick = onConnectionClick,
            onStartSending = onStartSending,
            onStopSending = onExitControlMode,
            onEmergencyStop = onEmergencyStop,
            frameFormat = frameFormat
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
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "JRemote Controller",
            color = MaterialTheme.colorScheme.onSurface,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        if (connectionStatus.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
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
                            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = if (connectionStatus.isConnected) "已连接: ${connectionStatus.deviceName}" else "未连接 - 点击连接",
                        color = MaterialTheme.colorScheme.surface,
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
                containerColor = MaterialTheme.colorScheme.primary
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
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "设置",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color=MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (connectionStatus.isConnected) "点击启动进入横屏控制模式" else "未连接设备",
            color = if (connectionStatus.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
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
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = String.format("X:%+.2f", state.x),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = String.format("Y:%+.2f", state.y),
                color = MaterialTheme.colorScheme.onSurface,
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

    val signalColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant

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
                        if (i <= signalLevel) signalColor else inactiveColor,
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

@Composable
private fun LatencyDisplay(latency: Int) {
    // 延迟等级
    // < 50ms: 优秀
    // 50-100ms: 良好
    // 100-200ms: 一般
    // > 200ms: 差
    val latencyColor = when {
        latency < 50 -> MaterialTheme.colorScheme.primary
        latency < 100 -> MaterialTheme.colorScheme.secondary
        latency < 200 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // 延迟图标
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(latencyColor, shape = RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${latency}ms",
            color = latencyColor,
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
    toggleButtonLayout: ToggleButtonLayout,
    hapticFeedback: Boolean,
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
    frameFormat: FrameFormat,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    tint = MaterialTheme.colorScheme.onSurface,
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
                            if (connectionStatus.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onConnectionClick() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (connectionStatus.isConnected) "已连接" else "未连接",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Button(
                    onClick = onStopSending,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
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
                // 摇杆上方：4个切换按钮 L1-L4
                Box(
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    ToggleButtonsLayout(
                        buttonConfigs = buttonConfigs,
                        buttonStates = buttonStates,
                        startIndex = 6,
                        layout = toggleButtonLayout,
                        hapticFeedback = hapticFeedback,
                        onButtonPressed = onButtonPressed
                    )
                }

                // 摇杆周围环绕3个按钮（LX、LY、LZ）- 在圆上，间距相等，偏向屏幕中心
                // 最简帧（6字节，8按钮）模式下隐藏这些按钮
                if (frameFormat != FrameFormat.MIN) {
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
                                hapticFeedbackEnabled = hapticFeedback,
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
                                hapticFeedbackEnabled = hapticFeedback,
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
                                hapticFeedbackEnabled = hapticFeedback,
                                onPressed = { pressed -> onButtonPressed(config.id, pressed) },
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(x = 87.dp, y = 50.dp)
                            )
                        }
                    }
                } else {
                    Joystick(
                        size = 120.dp,
                        onStateChanged = onLeftJoystickChange
                    )
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
                        !connectionStatus.isConnected -> MaterialTheme.colorScheme.error
                        isEmergencyStopped -> MaterialTheme.colorScheme.tertiary
                        isSending -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    }
                    val statusText = when {
                        !connectionStatus.isConnected -> "遥控器断开连接"
                        isEmergencyStopped -> "设备急停中"
                        isSending -> "遥控正常"
                        else -> "遥控未就绪"
                    }

                    // 状态指示器带脉冲动画
                    val pulseScale by animateFloatAsState(
                        targetValue = if (isSending) 1f else 0.8f,
                        animationSpec = tween(300),
                        label = "pulse"
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .scale(pulseScale)
                            .background(statusColor, shape = CircleShape)
                    )
                    AnimatedContent(
                        targetState = statusText,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(150)) togetherWith
                                    fadeOut(animationSpec = tween(150))
                        },
                        label = "statusText"
                    ) { text ->
                        Text(
                            text = text,
                            color = statusColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                        containerColor = if (isSending) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .height(56.dp)
                        .fillMaxWidth(0.6f)
                ) {
                    Text(
                        text = if (isSending) "E-STOP" else "RESTART",
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
                // 摇杆上方：4个切换按钮 R1-R4
                Box(
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    ToggleButtonsLayout(
                        buttonConfigs = buttonConfigs,
                        buttonStates = buttonStates,
                        startIndex = 10,
                        layout = toggleButtonLayout,
                        hapticFeedback = hapticFeedback,
                        onButtonPressed = onButtonPressed
                    )
                }

                // 摇杆周围环绕3个按钮（RX、RY、RZ）- 在圆上，间距相等，偏向屏幕中心
                // 最简帧（6字节，8按钮）模式下隐藏这些按钮
                if (frameFormat != FrameFormat.MIN) {
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
                                hapticFeedbackEnabled = hapticFeedback,
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
                                hapticFeedbackEnabled = hapticFeedback,
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
                                hapticFeedbackEnabled = hapticFeedback,
                                onPressed = { pressed -> onButtonPressed(config.id, pressed) },
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(x = (-87).dp, y = (50).dp)
                            )
                        }
                    }
                } else {
                    Joystick(
                        size = 120.dp,
                        onStateChanged = onRightJoystickChange
                    )
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
private fun ToggleButtonsLayout(
    buttonConfigs: List<ButtonConfig>,
    buttonStates: Map<Int, Boolean>,
    startIndex: Int,
    layout: ToggleButtonLayout,
    hapticFeedback: Boolean,
    onButtonPressed: (Int, Boolean) -> Unit
) {
    when (layout) {
        ToggleButtonLayout.HORIZONTAL -> {
            Row(
                modifier = Modifier.padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (i in startIndex until startIndex + 4) {
                    buttonConfigs.getOrNull(i)?.let { config ->
                        ControlButton(
                            config = config,
                            isPressed = buttonStates[config.id] ?: false,
                            isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                            size = 50.dp,
                            hapticFeedbackEnabled = hapticFeedback,
                            onPressed = { pressed -> onButtonPressed(config.id, pressed) }
                        )
                    }
                }
            }
        }
        ToggleButtonLayout.GRID_2X2 -> {
            Column(
                modifier = Modifier.padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    buttonConfigs.getOrNull(startIndex)?.let { config ->
                        ControlButton(
                            config = config,
                            isPressed = buttonStates[config.id] ?: false,
                            isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                            size = 40.dp,
                            hapticFeedbackEnabled = hapticFeedback,
                            onPressed = { pressed -> onButtonPressed(config.id, pressed) }
                        )
                    }
                    buttonConfigs.getOrNull(startIndex + 1)?.let { config ->
                        ControlButton(
                            config = config,
                            isPressed = buttonStates[config.id] ?: false,
                            isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                            size = 40.dp,
                            hapticFeedbackEnabled = hapticFeedback,
                            onPressed = { pressed -> onButtonPressed(config.id, pressed) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    buttonConfigs.getOrNull(startIndex + 2)?.let { config ->
                        ControlButton(
                            config = config,
                            isPressed = buttonStates[config.id] ?: false,
                            isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                            size = 40.dp,
                            hapticFeedbackEnabled = hapticFeedback,
                            onPressed = { pressed -> onButtonPressed(config.id, pressed) }
                        )
                    }
                    buttonConfigs.getOrNull(startIndex + 3)?.let { config ->
                        ControlButton(
                            config = config,
                            isPressed = buttonStates[config.id] ?: false,
                            isToggled = if (config.isToggle) buttonStates[config.id] ?: false else false,
                            size = 40.dp,
                            hapticFeedbackEnabled = hapticFeedback,
                            onPressed = { pressed -> onButtonPressed(config.id, pressed) }
                        )
                    }
                }
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
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
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
                    color = MaterialTheme.colorScheme.onSurface,
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
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = autoScroll,
                    onCheckedChange = { autoScroll = it },
                    modifier = Modifier.height(24.dp),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
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
                        tint = MaterialTheme.colorScheme.primary,
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
                    // MUC 消息使用特殊颜色（绿色），其他消息根据级别着色
                    val logColor = when {
                        message.tag == "MUC" -> Color(0xFF4CAF50)  // 绿色表示来自 ESP32
                        message.level == DebugLevel.ERROR -> MaterialTheme.colorScheme.error
                        message.level == DebugLevel.WARNING -> MaterialTheme.colorScheme.tertiary
                        message.level == DebugLevel.INFO -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val timeStr = java.text.SimpleDateFormat(
                        "HH:mm:ss.SSS",
                        java.util.Locale.getDefault()
                    )
                        .format(java.util.Date(message.timestamp))
                    Text(
                        text = "[$timeStr][${message.tag}] ${message.message}",
                        color = logColor,
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




