package com.example.jremote.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jremote.data.ConnectionStatus
import com.example.jremote.data.DebugLevel
import com.example.jremote.data.DebugMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DebugPanel(
    modifier: Modifier = Modifier,
    connectionStatus: ConnectionStatus,
    debugMessages: List<DebugMessage>,
    maxLines: Int = 8
) {
    val listState = rememberLazyListState()
    
    LaunchedEffect(debugMessages.size) {
        if (debugMessages.isNotEmpty()) {
            listState.animateScrollToItem(debugMessages.size - 1)
        }
    }
    
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A2A))
            .padding(8.dp)
    ) {
        ConnectionStatusRow(connectionStatus)
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((maxLines * 18).dp)
                .padding(top = 4.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(debugMessages) { message ->
                    DebugMessageRow(message)
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusRow(status: ConnectionStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .background(
                        if (status.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (status.isConnected) "已连接" else "未连接",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (status.isConnected) {
                Text(
                    text = status.deviceName,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        if (status.isConnected && status.latency > 0) {
            Text(
                text = "${status.latency}ms",
                color = if (status.latency < 50) MaterialTheme.colorScheme.primary
                       else if (status.latency < 100) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun DebugMessageRow(message: DebugMessage) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    
    val levelColor = when (message.level) {
        DebugLevel.INFO -> MaterialTheme.colorScheme.primary
        DebugLevel.WARNING -> MaterialTheme.colorScheme.primary
        DebugLevel.ERROR -> MaterialTheme.colorScheme.primary
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Text(
            text = timeFormat.format(Date(message.timestamp)),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 4.dp)
        )
        
        Text(
            text = message.level.name.take(1),
            color = levelColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 4.dp)
        )
        
        Text(
            text = "[${message.tag}]",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 4.dp)
        )
        
        Text(
            text = message.message,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
