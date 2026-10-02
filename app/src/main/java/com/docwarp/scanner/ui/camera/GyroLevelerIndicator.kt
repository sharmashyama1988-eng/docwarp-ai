package com.docwarp.scanner.ui.camera

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.docwarp.scanner.ui.theme.ElectricAmber
import com.docwarp.scanner.ui.theme.PrecisionEmerald

/**
 * Gyroscope-backed horizontal level indicator.
 * Displays pitch & roll offset crosshairs that lock into Precision Emerald when level.
 */
@Composable
fun GyroLevelerIndicator(
    roll: Float,
    pitch: Float,
    isLevel: Boolean,
    modifier: Modifier = Modifier
) {
    val indicatorColor by animateColorAsState(
        targetValue = if (isLevel) PrecisionEmerald else ElectricAmber.copy(alpha = 0.75f),
        animationSpec = tween(durationMillis = 200),
        label = "LevelerColor"
    )

    Box(
        modifier = modifier.size(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(32.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            // Outer target reticle circle
            drawCircle(
                color = Color.White.copy(alpha = 0.25f),
                radius = 12.dp.toPx(),
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
            )

            // Dynamic bubble displacement based on tilt (clamped to circle bounds)
            val maxOffset = 10.dp.toPx()
            val dx = (-roll / 10f).coerceIn(-1f, 1f) * maxOffset
            val dy = (pitch / 10f).coerceIn(-1f, 1f) * maxOffset

            // Level bubble
            drawCircle(
                color = indicatorColor,
                radius = if (isLevel) 4.5.dp.toPx() else 3.5.dp.toPx(),
                center = Offset(cx + dx, cy + dy)
            )

            // Crosshair tick marks
            if (isLevel) {
                drawLine(
                    color = PrecisionEmerald,
                    start = Offset(cx - 14.dp.toPx(), cy),
                    end = Offset(cx - 8.dp.toPx(), cy),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = PrecisionEmerald,
                    start = Offset(cx + 8.dp.toPx(), cy),
                    end = Offset(cx + 14.dp.toPx(), cy),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
