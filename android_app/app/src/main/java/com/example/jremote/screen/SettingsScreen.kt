package com.example.jremote.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.jremote.data.ButtonConfig

@Composable
fun SettingsScreen(
    buttonConfigs: List<ButtonConfig>,
    onUpdateButtonConfig: (ButtonConfig) -> Unit,
    onAddButtonConfig: (ButtonConfig) -> Unit,
    onRemoveButtonConfig: (Int) -> Unit,
    onNavigateBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<ButtonConfig?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2A))
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
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
                Text(
                    text = "按键设置",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            IconButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加按键",
                    tint = Color.White
                )
            }
        }
        
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(buttonConfigs) { config ->
                ButtonConfigItem(
                    config = config,
                    onEdit = { editingConfig = config },
                    onDelete = { onRemoveButtonConfig(config.id) },
                    onToggleMode = {
                        onUpdateButtonConfig(config.copy(isToggle = !config.isToggle))
                    }
                )
            }
        }
        
        if (showAddDialog) {
            AddButtonDialog(
                existingIds = buttonConfigs.map { it.id },
                onDismiss = { showAddDialog = false },
                onAdd = { config ->
                    onAddButtonConfig(config)
                    showAddDialog = false
                }
            )
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
private fun ButtonConfigItem(
    config: ButtonConfig,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleMode: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A3A)
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
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (config.isToggle) Color(0xFF2E7D32) else Color(0xFF4A90D9),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = config.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = "KeyCode: 0x${config.keyCode.toString(16).uppercase()}",
                        color = Color(0xFF888888),
                        fontSize = 12.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = config.isToggle,
                        onCheckedChange = { onToggleMode() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF4A90D9),
                            uncheckedColor = Color(0xFF888888)
                        )
                    )
                    Text(
                        text = "切换模式",
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp
                    )
                }
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = Color(0xFFF44336)
                )
            }
        }
    }
}

@Composable
private fun AddButtonDialog(
    existingIds: List<Int>,
    onDismiss: () -> Unit,
    onAdd: (ButtonConfig) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var keyCode by remember { mutableStateOf("0") }
    var isToggle by remember { mutableStateOf(false) }
    
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
                containerColor = Color(0xFF2A2A3A)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "添加按键",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("按键名称", color = Color(0xFF888888)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF4A90D9),
                        unfocusedBorderColor = Color(0xFF5A5A6A)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                OutlinedTextField(
                    value = keyCode,
                    onValueChange = { keyCode = it.filter { c -> c.isDigit() || c in "xXabcdefABCDEF" } },
                    label = { Text("KeyCode (十六进制)", color = Color(0xFF888888)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF4A90D9),
                        unfocusedBorderColor = Color(0xFF5A5A6A)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isToggle,
                        onCheckedChange = { isToggle = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF4A90D9),
                            uncheckedColor = Color(0xFF888888)
                        )
                    )
                    Text(
                        text = "切换模式",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3A3A4A)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    
                    Button(
                        onClick = {
                            val newId = (existingIds.maxOrNull() ?: -1) + 1
                            val code = keyCode.removePrefix("0x").removePrefix("0X").toIntOrNull(16) ?: 0
                            onAdd(ButtonConfig(
                                id = newId,
                                name = name.ifEmpty { "BTN$newId" },
                                keyCode = code.toByte(),
                                isToggle = isToggle
                            ))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4A90D9)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("添加")
                    }
                }
            }
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
                containerColor = Color(0xFF2A2A3A)
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
                
                Spacer(modifier = Modifier.width(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("按键名称", color = Color(0xFF888888)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF4A90D9),
                        unfocusedBorderColor = Color(0xFF5A5A6A)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                OutlinedTextField(
                    value = keyCode,
                    onValueChange = { keyCode = it.filter { c -> c.isDigit() || c in "xXabcdefABCDEF" } },
                    label = { Text("KeyCode (十六进制)", color = Color(0xFF888888)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF4A90D9),
                        unfocusedBorderColor = Color(0xFF5A5A6A)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isToggle,
                        onCheckedChange = { isToggle = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF4A90D9),
                            uncheckedColor = Color(0xFF888888)
                        )
                    )
                    Text(
                        text = "切换模式",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3A3A4A)
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
                            containerColor = Color(0xFF4A90D9)
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
