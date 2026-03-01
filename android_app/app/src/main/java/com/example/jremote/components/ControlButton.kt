package com.example.jremote.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jremote.data.ButtonConfig

@Composable
fun ControlButton(
    config: ButtonConfig,
    isPressed: Boolean = false,
    isToggled: Boolean = false,
    size: Dp = 60.dp,
    onPressed: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val backgroundColor = when {
        !config.isEnabled -> Color(0xFF2A2A3A).copy(alpha = 0.3f)
        isPressed -> Color(0xFF4A90D9)
        isToggled -> Color(0xFF2E7D32)
        else -> Color(0xFF3A3A4A)
    }

    val borderColor = when {
        !config.isEnabled -> Color(0xFF3A3A4A).copy(alpha = 0.3f)
        isPressed -> Color(0xFF6AB0F9)
        isToggled -> Color(0xFF4CAF50)
        else -> Color(0xFF5A5A6A)
    }

    val textColor = if (config.isEnabled) Color.White else Color(0xFF555555)

    Box(
        modifier = modifier
            .size(size)
            .clip(if (config.isToggle) RoundedCornerShape(8.dp) else CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        backgroundColor.copy(alpha = 0.9f),
                        backgroundColor.copy(alpha = 0.7f)
                    )
                )
            )
            .border(
                width = 2.dp,
                color = borderColor,
                shape = if (config.isToggle) RoundedCornerShape(8.dp) else CircleShape
            )
            .then(
                if (config.isEnabled) {
                    Modifier.pointerInput(config.id, isToggled) {
                        detectTapGestures(
                            onPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (config.isToggle) {
                                    onPressed(!isToggled)
                                    tryAwaitRelease()
                                } else {
                                    onPressed(true)
                                    tryAwaitRelease()
                                    onPressed(false)
                                }
                            }
                        )
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = config.name,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
