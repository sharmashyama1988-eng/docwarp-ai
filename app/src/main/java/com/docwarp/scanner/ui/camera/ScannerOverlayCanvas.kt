package com.docwarp.scanner.ui.camera

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.docwarp.scanner.core.model.DocumentQuad
import com.docwarp.scanner.ui.theme.ElectricAmber
import com.docwarp.scanner.ui.theme.PrecisionEmerald
import kotlin.math.sqrt

/**
 * Smart Reticle Canvas:
 * Draws dynamic quadrilateral between 4 detected corners with L-shaped 24dp brackets,
 * smooth Amber-to-Emerald color transition when still, and a glowing vertical spine fold guide for dual-page mode.
 */
@Composable
fun ScannerOverlayCanvas(
    detectedQuad: DocumentQuad?,
    isStillAndLocked: Boolean,
    isDualPageMode: Boolean,
    modifier: Modifier = Modifier
) {
    val reticleColor by animateColorAsState(
        targetValue = if (isStillAndLocked) PrecisionEmerald else ElectricAmber,
        animationSpec = tween(durationMillis = 220),
        label = "ReticleColor"
    )

    val reticleFillColor = reticleColor.copy(alpha = if (isStillAndLocked) 0.18f else 0.08f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasW = size.width
        val canvasH = size.height

        val quad = detectedQuad?.toTargetResolution(canvasW.toInt(), canvasH.toInt())
            ?: DocumentQuad.defaultForDimensions(canvasW.toInt(), canvasH.toInt(), 0.08f)

        val tl = Offset(quad.topLeft.x, quad.topLeft.y)
        val tr = Offset(quad.topRight.x, quad.topRight.y)
        val br = Offset(quad.bottomRight.x, quad.bottomRight.y)
        val bl = Offset(quad.bottomLeft.x, quad.bottomLeft.y)

        // 1. Draw quadrilateral semi-transparent fill
        val quadPath = Path().apply {
            moveTo(tl.x, tl.y)
            lineTo(tr.x, tr.y)
            lineTo(br.x, br.y)
            lineTo(bl.x, bl.y)
            close()
        }
        drawPath(quadPath, color = reticleFillColor)

        // 2. Draw subtle connected boundary
        drawPath(
            quadPath,
            color = reticleColor.copy(alpha = 0.40f),
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
            )
        )

        // 3. Draw high-precision L-shaped 24dp brackets at each vertex
        val bracketLen = 24.dp.toPx()
        val strokeWidth = 3.5.dp.toPx()

        drawCornerBracket(this, tl, tr, bl, bracketLen, strokeWidth, reticleColor)
        drawCornerBracket(this, tr, br, tl, bracketLen, strokeWidth, reticleColor)
        drawCornerBracket(this, br, bl, tr, bracketLen, strokeWidth, reticleColor)
        drawCornerBracket(this, bl, tl, br, bracketLen, strokeWidth, reticleColor)

        // 4. Dual-Page Center Spine Guide
        if (isDualPageMode) {
            val topSpine = Offset((tl.x + tr.x) / 2f, (tl.y + tr.y) / 2f)
            val bottomSpine = Offset((bl.x + br.x) / 2f, (bl.y + br.y) / 2f)

            // Center spine glow line
            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        reticleColor.copy(alpha = 0.85f),
                        reticleColor.copy(alpha = 0.85f),
                        Color.Transparent
                    )
                ),
                start = topSpine,
                end = bottomSpine,
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun drawCornerBracket(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    vertex: Offset,
    adj1: Offset,
    adj2: Offset,
    length: Float,
    strokeWidth: Float,
    color: Color
) {
    fun getNormalizedArm(target: Offset): Offset {
        val dx = target.x - vertex.x
        val dy = target.y - vertex.y
        val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val l = length.coerceAtMost(dist * 0.45f)
        return Offset(vertex.x + (dx / dist) * l, vertex.y + (dy / dist) * l)
    }

    val arm1 = getNormalizedArm(adj1)
    val arm2 = getNormalizedArm(adj2)

    val path = Path().apply {
        moveTo(arm1.x, arm1.y)
        lineTo(vertex.x, vertex.y)
        lineTo(arm2.x, arm2.y)
    }

    scope.drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}
