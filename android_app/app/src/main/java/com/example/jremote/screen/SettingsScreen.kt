package com.example.jremote.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jremote.data.AppSettings
import com.example.jremote.data.ButtonConfig
import com.example.jremote.data.FrameFormat
import com.example.jremote.data.ToggleButtonLayout

@Composable
fun SettingsScreen(
    buttonConfigs: List<ButtonConfig>,
    settings: AppSettings,
    onUpdateButtonConfig: (ButtonConfig) -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
    onNavigateBack: () -> Unit,
    onSerialTerminalClick: () -> Unit = {}
) {
    var editingConfig by remember { mutableStateOf<ButtonConfig?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "设置",
                    color=MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsSection(title = "数据发送")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("发送间隔", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            Text(
                                "${settings.sendIntervalMs}ms",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = settings.sendIntervalMs.toFloat(),
                            onValueChange = { 
                                onUpdateSettings(settings.copy(sendIntervalMs = it.toLong()))
                            },
                            valueRange = 10f..100f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "较低间隔可提高响应速度，但会增加功耗",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            item {
                SettingsSection(title = "帧格式")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "选择帧格式",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "决定发送数据的格式和精度",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FrameFormatButton(
                                title = "最简",
                                subtitle = "6字节",
                                description = "8按钮",
                                isSelected = settings.frameFormat == FrameFormat.MIN,
                                onClick = { onUpdateSettings(settings.copy(frameFormat = FrameFormat.MIN)) },
                                modifier = Modifier.weight(1f)
                            )
                            FrameFormatButton(
                                title = "标准",
                                subtitle = "9字节",
                                description = "32按钮",
                                isSelected = settings.frameFormat == FrameFormat.STANDARD,
                                onClick = { onUpdateSettings(settings.copy(frameFormat = FrameFormat.STANDARD)) },
                                modifier = Modifier.weight(1f)
                            )
                            FrameFormatButton(
                                title = "16位",
                                subtitle = "17字节",
                                description = "高精度",
                                isSelected = settings.frameFormat == FrameFormat.BIT16,
                                onClick = { onUpdateSettings(settings.copy(frameFormat = FrameFormat.BIT16)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            item {
                SettingsSection(title = "界面设置")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingSwitch(
                            title = "显示调试面板",
                            description = "在控制界面显示日志信息",
                            checked = settings.showDebugPanel,
                            onCheckedChange = { onUpdateSettings(settings.copy(showDebugPanel = it)) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SettingSwitch(
                            title = "触觉反馈",
                            description = "按下按钮时震动反馈",
                            checked = settings.hapticFeedback,
                            onCheckedChange = { onUpdateSettings(settings.copy(hapticFeedback = it)) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "切换按钮布局",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onUpdateSettings(settings.copy(toggleButtonLayout = ToggleButtonLayout.HORIZONTAL)) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (settings.toggleButtonLayout == ToggleButtonLayout.HORIZONTAL)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("一字排开", color=MaterialTheme.colorScheme.onSurface,fontSize = 12.sp)
                            }
                            Button(
                                onClick = { onUpdateSettings(settings.copy(toggleButtonLayout = ToggleButtonLayout.GRID_2X2)) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (settings.toggleButtonLayout == ToggleButtonLayout.GRID_2X2)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("2x2 网格", color=MaterialTheme.colorScheme.onSurface,fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "主题模式",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onUpdateSettings(settings.copy(themeMode = com.example.jremote.data.ThemeMode.SYSTEM)) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (settings.themeMode == com.example.jremote.data.ThemeMode.SYSTEM)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("跟随系统", color=MaterialTheme.colorScheme.onSurface,fontSize = 12.sp)
                            }
                            Button(
                                onClick = { onUpdateSettings(settings.copy(themeMode = com.example.jremote.data.ThemeMode.DARK)) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (settings.themeMode == com.example.jremote.data.ThemeMode.DARK)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("深色", color=MaterialTheme.colorScheme.onSurface,fontSize = 12.sp)
                            }
                            Button(
                                onClick = { onUpdateSettings(settings.copy(themeMode = com.example.jremote.data.ThemeMode.LIGHT)) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (settings.themeMode == com.example.jremote.data.ThemeMode.LIGHT)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("浅色", color=MaterialTheme.colorScheme.onSurface,fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        SettingSwitch(
                            title = "跟随主题色",
                            description = "跟随手机壁纸颜色（仅 Android 12+）",
                            checked = settings.dynamicColor,
                            onCheckedChange = { onUpdateSettings(settings.copy(dynamicColor = it)) }
                        )
                    }
                }
            }
            
            item {
                SettingsSection(title = "蓝牙设置")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingSwitch(
                            title = "自动重连",
                            description = "连接断开时自动尝试重新连接",
                            checked = settings.autoReconnect,
                            onCheckedChange = { onUpdateSettings(settings.copy(autoReconnect = it)) }
                        )
                    }
                }
            }

            item {
                SettingsSection(title = "工具")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSerialTerminalClick() }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("串口终端", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            Text("发送和接收自定义数据", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "进入",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SettingsSection(title = "按键配置")
            }
            
            items(buttonConfigs) { config ->
                ButtonConfigItem(
                    config = config,
                    onEdit = { editingConfig = config },
                    onToggleEnabled = {
                        onUpdateButtonConfig(config.copy(isEnabled = !config.isEnabled))
                    },
                    onToggleMode = {
                        onUpdateButtonConfig(config.copy(isToggle = !config.isToggle))
                    }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        if (editingConfig != null) {
            EditButtonDialog(
                config = editingConfig!!,
                onDismiss = { editingConfig = null },
                onSave = { config ->
                    onUpdateButtonConfig(config)
                    editingConfig = null
                }
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun FrameFormatButton(
    title: String,
    subtitle: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp),
        border = if (!isSelected)
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Text(
                text = description,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun ButtonConfigItem(
    config: ButtonConfig,
    onEdit: () -> Unit,
    onToggleEnabled: () -> Unit,
    onToggleMode: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        colors = CardDefaults.cardColors(
            containerColor = if (config.isEnabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(
                                when {
                                    !config.isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    config.isToggle -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = config.name,
                            color = if (config.isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = "KeyCode: 0x${config.keyCode.toString(16).uppercase()}",
                        color = if (config.isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = config.isToggle,
                        onCheckedChange = { onToggleMode() },
                        enabled = config.isEnabled,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = "切换模式",
                        color = if (config.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }
            
            Switch(
                checked = config.isEnabled,
                onCheckedChange = { onToggleEnabled() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    uncheckedThumbColor = MaterialTheme.colorScheme.error,
                    uncheckedTrackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
private fun EditButtonDialog(
    config: ButtonConfig,
    onDismiss: () -> Unit,
    onSave: (ButtonConfig) -> Unit
) {
    var name by remember { mutableStateOf(config.name) }
    var keyCode by remember { mutableStateOf("0x${config.keyCode.toString(16).uppercase()}") }
    var isToggle by remember { mutableStateOf(config.isToggle) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .clickable(enabled = false) { },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "编辑按键",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("按键名称", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = keyCode,
                    onValueChange = { keyCode = it.filter { c -> c.isDigit() || c in "xXabcdefABCDEF" } },
                    label = { Text("KeyCode (十六进制)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isToggle,
                        onCheckedChange = { isToggle = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = "切换模式",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    
                    Button(
                        onClick = {
                            val code = keyCode.removePrefix("0x").removePrefix("0X").toIntOrNull(16) ?: config.keyCode.toInt()
                            onSave(config.copy(
                                name = name,
                                keyCode = code.toByte(),
                                isToggle = isToggle
                            ))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
