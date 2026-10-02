package com.docwarp.scanner.ui.editor

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.docwarp.scanner.ui.theme.PrecisionEmerald
import kotlin.math.roundToInt

/**
 * 90dp circular zoom loupe (2x magnification with crosshairs)
 * Floats above the user's thumb during manual corner dragging.
 */
@Composable
fun MagnifierLoupeOverlay(
    sourceBitmap: Bitmap?,
    touchOffset: Offset,
    imageDisplayOffset: Offset,
    scaleRatio: Float, // scale between displayed image and original bitmap
    modifier: Modifier = Modifier
) {
    if (sourceBitmap == null) return

    val loupeSize = 92.dp
    val zoomFactor = 2.0f

    // Float 80dp above the finger so the touch contact point is completely unobstructed
    val loupeX = (touchOffset.x - 46.dp.value * 2.5f).roundToInt()
    val loupeY = (touchOffset.y - 120.dp.value * 2.5f).roundToInt()

    Box(
        modifier = modifier
            .offset { IntOffset(loupeX, loupeY) }
            .size(loupeSize)
            .shadow(12.dp, CircleShape)
            .clip(CircleShape)
            .border(2.5.dp, PrecisionEmerald, CircleShape)
    ) {
        Canvas(modifier = Modifier.size(loupeSize)) {
            val center = Offset(size.width / 2f, size.height / 2f)

            // Calculate sample coordinate in source bitmap
            val normX = (touchOffset.x - imageDisplayOffset.x) / scaleRatio
            val normY = (touchOffset.y - imageDisplayOffset.y) / scaleRatio

            val srcX = normX.coerceIn(0f, sourceBitmap.width.toFloat())
            val srcY = normY.coerceIn(0f, sourceBitmap.height.toFloat())

            val sampleW = (size.width / zoomFactor).toInt()
            val sampleH = (size.height / zoomFactor).toInt()

            val cropLeft = (srcX - sampleW / 2).toInt().coerceIn(0, sourceBitmap.width - sampleW)
            val cropTop = (srcY - sampleH / 2).toInt().coerceIn(0, sourceBitmap.height - sampleH)

            if (sampleW > 0 && sampleH > 0 && cropLeft >= 0 && cropTop >= 0 &&
                cropLeft + sampleW <= sourceBitmap.width && cropTop + sampleH <= sourceBitmap.height
            ) {
                val subBmp = Bitmap.createBitmap(sourceBitmap, cropLeft, cropTop, sampleW, sampleH)
                val scaledBmp = Bitmap.createScaledBitmap(subBmp, size.width.toInt(), size.height.toInt(), true)

                drawImage(image = scaledBmp.asImageBitmap())
                subBmp.recycle()
                scaledBmp.recycle()
            }

            // Draw precision crosshairs
            val crossHairLen = 14.dp.toPx()
            drawLine(
                color = PrecisionEmerald,
                start = Offset(center.x - crossHairLen, center.y),
                end = Offset(center.x + crossHairLen, center.y),
                strokeWidth = 1.5.dp.toPx()
            )
            drawLine(
                color = PrecisionEmerald,
                start = Offset(center.x, center.y - crossHairLen),
                end = Offset(center.x, center.y + crossHairLen),
                strokeWidth = 1.5.dp.toPx()
            )

            // Center target dot
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = center
            )
        }
    }
}
