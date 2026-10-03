package com.docwarp.scanner.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docwarp.scanner.ui.theme.CanvasBlack
import com.docwarp.scanner.ui.theme.CharcoalGlass
import com.docwarp.scanner.ui.theme.CharcoalGlassBorder
import com.docwarp.scanner.ui.theme.PrecisionEmerald
import com.docwarp.scanner.ui.theme.TextPrimary
import com.docwarp.scanner.ui.theme.TextSecondary

/**
 * Screen 4: Precision Manual Crop & Corner Fine-Tuning.
 * Features 4 draggable corner handles, magnetic boundary snapping,
 * and an interactive 90dp 2x Magnifier Loupe floating above the dragging finger.
 */
@Composable
fun ManualCropScreen(
    viewModel: ManualCropViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rawBitmap by viewModel.rawBitmap.collectAsState()
    val currentQuad by viewModel.currentQuad.collectAsState()
    val activeCorner by viewModel.activeCorner.collectAsState()

    var touchOffset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "Fine-Tune Crop",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Interactive Image & Crop Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .onGloballyPositioned { coordinates ->
                        canvasSize = Offset(
                            coordinates.size.width.toFloat(),
                            coordinates.size.height.toFloat()
                        )
                    }
                    .pointerInput(rawBitmap, currentQuad) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val bmp = rawBitmap ?: return@detectDragGestures
                                val (scale, imgOffset) = calculateAspectFit(bmp.width, bmp.height, size.width, size.height)
                                val imgX = (offset.x - imgOffset.x) / scale
                                val imgY = (offset.y - imgOffset.y) / scale
                                if (viewModel.startCornerDrag(imgX, imgY, touchRadius = 70f / scale)) {
                                    touchOffset = offset
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val bmp = rawBitmap ?: return@detectDragGestures
                                touchOffset += dragAmount
                                val (scale, imgOffset) = calculateAspectFit(bmp.width, bmp.height, size.width, size.height)
                                val imgX = (touchOffset.x - imgOffset.x) / scale
                                val imgY = (touchOffset.y - imgOffset.y) / scale
                                viewModel.updateCornerDrag(imgX, imgY, bmp.width.toFloat(), bmp.height.toFloat())
                            },
                            onDragEnd = {
                                viewModel.endCornerDrag()
                            },
                            onDragCancel = {
                                viewModel.endCornerDrag()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val bmp = rawBitmap
                val quad = currentQuad

                if (bmp != null && quad != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val (scale, imgOffset) = calculateAspectFit(bmp.width, bmp.height, size.width.toInt(), size.height.toInt())

                        // 1. Draw source image aspect-fit
                        val dstW = (bmp.width * scale).toInt()
                        val dstH = (bmp.height * scale).toInt()
                        drawImage(
                            image = bmp.asImageBitmap(),
                            dstOffset = androidx.compose.ui.unit.IntOffset(imgOffset.x.toInt(), imgOffset.y.toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(dstW, dstH)
                        )

                        // 2. Map quad corners to screen coordinates
                        fun mapPoint(pt: android.graphics.PointF): Offset {
                            return Offset(imgOffset.x + pt.x * scale, imgOffset.y + pt.y * scale)
                        }

                        val tl = mapPoint(quad.topLeft)
                        val tr = mapPoint(quad.topRight)
                        val br = mapPoint(quad.bottomRight)
                        val bl = mapPoint(quad.bottomLeft)

                        // 3. Draw crop polygon boundary
                        val path = Path().apply {
                            moveTo(tl.x, tl.y)
                            lineTo(tr.x, tr.y)
                            lineTo(br.x, br.y)
                            lineTo(bl.x, bl.y)
                            close()
                        }

                        drawPath(path, color = PrecisionEmerald.copy(alpha = 0.20f))
                        drawPath(path, color = PrecisionEmerald, style = Stroke(width = 2.5.dp.toPx()))

                        // 4. Draw corner handle circles
                        val handleRadius = 14.dp.toPx()
                        val corners = listOf(tl, tr, br, bl)
                        for (corner in corners) {
                            drawCircle(color = Color.White, radius = handleRadius, center = corner)
                            drawCircle(color = PrecisionEmerald, radius = handleRadius, center = corner, style = Stroke(width = 3.dp.toPx()))
                        }
                    }

                    // 5. Floating Magnifier Loupe above thumb
                    if (activeCorner != null) {
                        val (scale, imgOffset) = calculateAspectFit(bmp.width, bmp.height, canvasSize.x.toInt(), canvasSize.y.toInt())
                        MagnifierLoupeOverlay(
                            sourceBitmap = bmp,
                            touchOffset = touchOffset,
                            imageDisplayOffset = imgOffset,
                            scaleRatio = scale
                        )
                    }
                }
            }

            // Bottom Control Dock: [ Reset ] [ AI Detect ] [ Full Image ] [ Confirm Crop ]
            CropBottomDock(
                onReset = { viewModel.resetToAuto() },
                onAutoDetect = { viewModel.autoDetectCorners() },
                onFullImage = { viewModel.selectFullImage() },
                onConfirm = { viewModel.confirmCrop(onNavigateBack) }
            )
        }
    }
}

@Composable
private fun CropBottomDock(
    onReset: () -> Unit,
    onAutoDetect: () -> Unit,
    onFullImage: () -> Unit,
    onConfirm: () -> Unit
) {
    val dockShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(dockShape)
            .background(CharcoalGlass)
            .border(1.dp, CharcoalGlassBorder, dockShape)
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reset Button
            OutlinedButton(
                onClick = onReset,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalGlassBorder),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset",
                    tint = TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text("Reset", color = TextPrimary, fontSize = 11.sp)
            }

            // AI Corner Auto-Detect Button
            OutlinedButton(
                onClick = onAutoDetect,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrecisionEmerald.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = PrecisionEmerald.copy(alpha = 0.08f)),
                modifier = Modifier.weight(1.1f)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Detect",
                    tint = PrecisionEmerald,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text("AI Detect", color = PrecisionEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Full Image Button
            OutlinedButton(
                onClick = onFullImage,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalGlassBorder),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.CropFree,
                    contentDescription = "Full",
                    tint = TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text("Full", color = TextPrimary, fontSize = 11.sp)
            }

            // Confirm Crop Button
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrecisionEmerald),
                modifier = Modifier.weight(1.2f)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Confirm",
                    tint = CanvasBlack,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text("Confirm", color = CanvasBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun calculateAspectFit(imgW: Int, imgH: Int, canvasW: Int, canvasH: Int): Pair<Float, Offset> {
    if (imgW <= 0 || imgH <= 0 || canvasW <= 0 || canvasH <= 0) return Pair(1f, Offset.Zero)

    val scaleX = canvasW.toFloat() / imgW.toFloat()
    val scaleY = canvasH.toFloat() / imgH.toFloat()
    val scale = minOf(scaleX, scaleY)

    val renderedW = imgW * scale
    val renderedH = imgH * scale

    val offsetX = (canvasW - renderedW) / 2f
    val offsetY = (canvasH - renderedH) / 2f

    return Pair(scale, Offset(offsetX, offsetY))
}
