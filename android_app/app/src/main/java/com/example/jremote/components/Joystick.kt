package com.example.jremote.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.jremote.data.JoystickState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun Joystick(
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    knobRatio: Float = 0.4f,
    deadZone: Float = 0.1f,
    onStateChanged: (JoystickState) -> Unit = {}
) {
    var knobPosition by remember { mutableStateOf(Offset.Zero) }
    var isPressed by remember { mutableStateOf(false) }
    
    val knobRadius = size * knobRatio / 2
    val outerRadius = size / 2 - knobRadius
    
    Box(
        modifier = modifier
            .size(size)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isPressed = true
                        val centerOffset = Offset(this.size.width / 2f, this.size.height / 2f)
                        updateKnobPosition(
                            offset = offset,
                            center = centerOffset,
                            outerRadius = outerRadius.toPx(),
                            deadZone = deadZone
                        ) { position, state ->
                            knobPosition = position
                            onStateChanged(state)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val centerOffset = Offset(this.size.width / 2f, this.size.height / 2f)
                        updateKnobPosition(
                            offset = change.position,
                            center = centerOffset,
                            outerRadius = outerRadius.toPx(),
                            deadZone = deadZone
                        ) { position, state ->
                            knobPosition = position
                            onStateChanged(state)
                        }
                    },
                    onDragEnd = {
                        isPressed = false
                        knobPosition = Offset.Zero
                        onStateChanged(JoystickState())
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = this.size.center
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2A2A3A),
                        Color(0xFF1A1A2A)
                    ),
                    center = center,
                    radius = size.toPx() / 2
                ),
                radius = size.toPx() / 2
            )
            
            drawCircle(
                color = Color(0xFF3A3A4A),
                radius = outerRadius.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            
            for (i in 0..3) {
                val angle = i * 90f
                drawLine(
                    color = Color(0xFF3A3A4A).copy(alpha = 0.5f),
                    start = Offset(
                        center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * outerRadius.toPx() * 0.3f,
                        center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * outerRadius.toPx() * 0.3f
                    ),
                    end = Offset(
                        center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * outerRadius.toPx() * 0.9f,
                        center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * outerRadius.toPx() * 0.9f
                    ),
                    strokeWidth = 1.dp.toPx()
                )
            }
            
            val knobCenter = Offset(center.x + knobPosition.x, center.y + knobPosition.y)
            val knobColor = if (isPressed) Color(0xFF4A90D9) else Color(0xFF5A5A6A)
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        knobColor.copy(alpha = 0.9f),
                        knobColor.copy(alpha = 0.7f)
                    ),
                    center = knobCenter,
                    radius = knobRadius.toPx()
                ),
                radius = knobRadius.toPx(),
                center = knobCenter
            )
            
            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = knobRadius.toPx() * 0.3f,
                center = Offset(knobCenter.x - knobRadius.toPx() * 0.2f, knobCenter.y - knobRadius.toPx() * 0.2f)
            )
        }
    }
}

private fun updateKnobPosition(
    offset: Offset,
    center: Offset,
    outerRadius: Float,
    deadZone: Float,
    onResult: (Offset, JoystickState) -> Unit
) {
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    val distance = sqrt(dx * dx + dy * dy)
    
    val clampedDistance = min(distance, outerRadius)
    val angle = atan2(dy, dx)
    
    val normalizedDistance = if (distance < outerRadius * deadZone) {
        0f
    } else {
        (clampedDistance - outerRadius * deadZone) / (outerRadius * (1 - deadZone))
    }
    
    val knobX = cos(angle) * clampedDistance
    val knobY = sin(angle) * clampedDistance
    
    val normalizedX = (knobX / outerRadius).coerceIn(-1f, 1f)
    val normalizedY = (knobY / outerRadius).coerceIn(-1f, 1f)
    
    val state = JoystickState(
        x = normalizedX,
        y = normalizedY,
        angle = ((angle * 180 / Math.PI).toFloat() + 360) % 360,
        distance = normalizedDistance.coerceIn(0f, 1f)
    )
    
    onResult(Offset(knobX, knobY), state)
}
