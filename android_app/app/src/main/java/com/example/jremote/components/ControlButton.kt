package com.example.jremote.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
    hapticFeedbackEnabled: Boolean = true,
    onPressed: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val backgroundColor = when {
        !config.isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        isPressed -> MaterialTheme.colorScheme.primary
        isToggled -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val borderColor = when {
        !config.isEnabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        isPressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        isToggled -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.outline
    }

    val textColor = if (config.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

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
                                if (hapticFeedbackEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
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
