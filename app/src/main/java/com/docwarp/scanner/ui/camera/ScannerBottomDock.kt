package com.docwarp.scanner.ui.camera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.docwarp.scanner.core.model.ScannedPage
import com.docwarp.scanner.ui.theme.CanvasBlack
import com.docwarp.scanner.ui.theme.CharcoalGlass
import com.docwarp.scanner.ui.theme.CharcoalGlassBorder
import com.docwarp.scanner.ui.theme.GlassSurfaceMuted
import com.docwarp.scanner.ui.theme.PrecisionEmerald
import com.docwarp.scanner.ui.theme.TextPrimary
import com.docwarp.scanner.ui.theme.TextSecondary

/**
 * Screen 1 Thumb-Zone Bottom Control Dock:
 * - Animated Capsule toggle: Single Page vs Dual Page
 * - Stacked thumbnail of last scanned page with emerald count badge
 * - 82dp Shutter with 500ms sweeping circular progress ring for auto-capture
 * - High-contrast Done button
 */
@Composable
fun ScannerBottomDock(
    isDualPageMode: Boolean,
    onDualPageToggle: (Boolean) -> Unit,
    lastScannedPage: ScannedPage?,
    pageCount: Int,
    isAutoCaptureEnabled: Boolean,
    autoCaptureProgress: Float, // 0.0 to 1.0
    onShutterClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Mode Segmented Capsule: [ Single Page ] | [ Dual Page (Split) ]
        SegmentedModeCapsule(
            isDualPage = isDualPageMode,
            onToggle = onDualPageToggle
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 2. Control Row: [ Last Thumbnail ] --- [ Shutter Ring ] --- [ Done (N) > ]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Stacked thumbnail + count badge
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassSurfaceMuted)
                    .border(1.dp, CharcoalGlassBorder, RoundedCornerShape(12.dp))
                    .clickable { onGalleryClick() },
                contentAlignment = Alignment.Center
            ) {
                if (lastScannedPage != null) {
                    val file = if (lastScannedPage.processedImageFile.exists()) {
                        lastScannedPage.processedImageFile
                    } else lastScannedPage.rawImageFile

                    AsyncImage(
                        model = file,
                        contentDescription = "Recent Page",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )

                    // Emerald page count badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(PrecisionEmerald)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "$pageCount",
                            color = CanvasBlack,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Text(
                        text = "0",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Center: Shutter with 500ms sweeping circular progress ring
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onShutterClick() },
                contentAlignment = Alignment.Center
            ) {
                // Outer ring Canvas with SVG progress sweep
                Canvas(modifier = Modifier.size(82.dp)) {
                    val strokeW = 4.dp.toPx()
                    val radius = (size.width - strokeW) / 2f

                    // Base circle
                    drawCircle(
                        color = Color.White.copy(alpha = 0.35f),
                        radius = radius,
                        style = Stroke(width = strokeW)
                    )

                    // Auto-capture sweep ring
                    if (isAutoCaptureEnabled && autoCaptureProgress > 0f) {
                        drawArc(
                            color = PrecisionEmerald,
                            startAngle = -90f,
                            sweepAngle = autoCaptureProgress * 360f,
                            useCenter = false,
                            style = Stroke(width = strokeW + 1.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Inner 66dp Shutter Button
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }

            // Right: High-contrast Done pill
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (pageCount > 0) PrecisionEmerald else GlassSurfaceMuted)
                    .border(
                        1.dp,
                        if (pageCount > 0) PrecisionEmerald else CharcoalGlassBorder,
                        RoundedCornerShape(22.dp)
                    )
                    .clickable(enabled = pageCount > 0) { onDoneClick() }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Done ($pageCount)",
                        color = if (pageCount > 0) CanvasBlack else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Done",
                        tint = if (pageCount > 0) CanvasBlack else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedModeCapsule(
    isDualPage: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val capsuleShape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .clip(capsuleShape)
            .background(CharcoalGlass.copy(alpha = 0.85f))
            .border(1.dp, CharcoalGlassBorder, capsuleShape)
            .padding(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Single Page Segment
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (!isDualPage) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onToggle(false) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Single Page",
                    color = if (!isDualPage) TextPrimary else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (!isDualPage) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Dual Page Segment
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDualPage) PrecisionEmerald.copy(alpha = 0.20f) else Color.Transparent)
                    .clickable { onToggle(true) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Dual Page (Split)",
                    color = if (isDualPage) PrecisionEmerald else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isDualPage) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
