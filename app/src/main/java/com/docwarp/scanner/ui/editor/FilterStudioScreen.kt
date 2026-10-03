package com.docwarp.scanner.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docwarp.scanner.core.model.ScanFilter
import com.docwarp.scanner.ui.theme.CanvasBlack
import com.docwarp.scanner.ui.theme.CharcoalGlass
import com.docwarp.scanner.ui.theme.CharcoalGlassBorder
import com.docwarp.scanner.ui.theme.GlassSurfaceMuted
import com.docwarp.scanner.ui.theme.PrecisionEmerald
import com.docwarp.scanner.ui.theme.TextPrimary
import com.docwarp.scanner.ui.theme.TextSecondary

/**
 * Screen 5: Filter Studio.
 * Features an interactive Before/After split-slider with zoom inspection,
 * preset filter carousel with OKEN color swatches, ML finger shadow erasure, and batch apply.
 */
@Composable
fun FilterStudioScreen(
    viewModel: FilterStudioViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val originalBmp by viewModel.originalBitmap.collectAsState()
    val filteredBmp by viewModel.filteredBitmap.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val eraseFingers by viewModel.eraseFingers.collectAsState()
    val sliderPosition by viewModel.sliderPosition.collectAsState()
    val isGenerating by viewModel.isGeneratingPreview.collectAsState()

    var zoomLevel by remember { mutableFloatStateOf(1f) }

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
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = "Filter Studio",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Drag center slider to inspect before & after",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (isGenerating) {
                    CircularProgressIndicator(
                        color = PrecisionEmerald,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 12.dp),
                        strokeWidth = 2.dp
                    )
                }

                // Zoom Toggle Pill (1x / 2.5x)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassSurfaceMuted)
                        .border(1.dp, CharcoalGlassBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            zoomLevel = if (zoomLevel == 1f) 2.2f else 1f
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (zoomLevel > 1f) Icons.Default.ZoomOut else Icons.Default.ZoomIn,
                            contentDescription = "Zoom",
                            tint = PrecisionEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (zoomLevel > 1f) "2.2x" else "1.0x",
                            color = PrecisionEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Interactive Split-Slider Comparison Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0D0F13))
                    .border(1.dp, CharcoalGlassBorder, RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val newNorm = (sliderPosition + dragAmount / size.width).coerceIn(0.04f, 0.96f)
                            viewModel.updateSliderPosition(newNorm)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val orig = originalBmp
                val filtered = filteredBmp

                if (orig != null && filtered != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasW = size.width
                        val canvasH = size.height
                        val splitX = canvasW * sliderPosition

                        val baseScaleX = canvasW / orig.width.toFloat()
                        val baseScaleY = canvasH / orig.height.toFloat()
                        val baseScale = minOf(baseScaleX, baseScaleY)
                        val effectiveScale = baseScale * zoomLevel

                        val renderW = (orig.width * effectiveScale).toInt()
                        val renderH = (orig.height * effectiveScale).toInt()
                        val dstOffset = IntOffset(((canvasW - renderW) / 2f).toInt(), ((canvasH - renderH) / 2f).toInt())
                        val dstSize = IntSize(renderW, renderH)

                        // 1. Draw Left half: Original "Before" image
                        val leftClipPath = Path().apply {
                            addRect(Rect(0f, 0f, splitX, canvasH))
                        }
                        clipPath(leftClipPath) {
                            drawImage(image = orig.asImageBitmap(), dstOffset = dstOffset, dstSize = dstSize)
                        }

                        // 2. Draw Right half: Filtered "After" image
                        val rightClipPath = Path().apply {
                            addRect(Rect(splitX, 0f, canvasW, canvasH))
                        }
                        clipPath(rightClipPath) {
                            drawImage(image = filtered.asImageBitmap(), dstOffset = dstOffset, dstSize = dstSize)
                        }

                        // 3. Draw vertical divider bar with glowing handle
                        drawLine(
                            color = PrecisionEmerald,
                            start = Offset(splitX, 0f),
                            end = Offset(splitX, canvasH),
                            strokeWidth = 2.dp.toPx()
                        )

                        // Center thumb disc
                        drawCircle(
                            color = Color.White,
                            radius = 14.dp.toPx(),
                            center = Offset(splitX, canvasH / 2f)
                        )
                        drawCircle(
                            color = PrecisionEmerald,
                            radius = 6.dp.toPx(),
                            center = Offset(splitX, canvasH / 2f)
                        )
                    }

                    // Before / After badges
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.70f))
                            .border(0.5.dp, CharcoalGlassBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("ORIGINAL", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.70f))
                            .border(0.5.dp, CharcoalGlassBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(selectedFilter.displayName.uppercase(), color = PrecisionEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // Bottom Filter & ML Controls Section
            FilterStudioControls(
                selectedFilter = selectedFilter,
                onSelectFilter = { viewModel.selectFilter(it) },
                eraseFingers = eraseFingers,
                onEraseFingersToggle = { viewModel.setEraseFingers(it) },
                onApplyToCurrent = { viewModel.applyToCurrentPage(onNavigateBack) },
                onApplyToAll = { viewModel.applyToAllPages(onNavigateBack) }
            )
        }
    }
}

@Composable
private fun FilterStudioControls(
    selectedFilter: ScanFilter,
    onSelectFilter: (ScanFilter) -> Unit,
    eraseFingers: Boolean,
    onEraseFingersToggle: (Boolean) -> Unit,
    onApplyToCurrent: () -> Unit,
    onApplyToAll: () -> Unit
) {
    val containerShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(containerShape)
            .background(CharcoalGlass)
            .border(1.dp, CharcoalGlassBorder, containerShape)
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Preset Filter Carousel with OKEN swatches
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ScanFilter.entries.toTypedArray()) { filter ->
                    val isSelected = (filter == selectedFilter)
                    val pillShape = RoundedCornerShape(14.dp)

                    val swatchColor = when (filter) {
                        ScanFilter.NO_SHADOW -> Color(0xFF1B5E20)
                        ScanFilter.ORIGINAL -> Color(0xFF424242)
                        ScanFilter.LIGHTEN -> Color(0xFFF57F17)
                        ScanFilter.MAGIC_COLOR -> Color(0xFF00897B)
                        ScanFilter.EBOOK_CLEAN -> PrecisionEmerald
                        ScanFilter.GRAYSCALE_SMOOTH -> Color(0xFF757575)
                        ScanFilter.BW -> Color(0xFF212121)
                        ScanFilter.ECO -> Color(0xFF1E88E5)
                        else -> Color(0xFF00897B)
                    }

                    Box(
                        modifier = Modifier
                            .clip(pillShape)
                            .background(if (isSelected) PrecisionEmerald.copy(alpha = 0.18f) else GlassSurfaceMuted)
                            .border(
                                1.dp,
                                if (isSelected) PrecisionEmerald else CharcoalGlassBorder,
                                pillShape
                            )
                            .clickable { onSelectFilter(filter) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Mini Color Swatch
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                            )

                            Text(
                                text = filter.displayName,
                                color = if (isSelected) PrecisionEmerald else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )

                            if (filter.isBeta) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0xFFE53935))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "Beta",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Erase Fingers & Shadows Toggle Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Switch(
                        checked = eraseFingers,
                        onCheckedChange = onEraseFingersToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CanvasBlack,
                            checkedTrackColor = PrecisionEmerald,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = CharcoalGlass
                        )
                    )
                    Text("Erase Finger Shadows", color = TextPrimary, fontSize = 13.sp)
                }

                Text(
                    text = "Clean Dewarp Active",
                    color = PrecisionEmerald,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // 3. Action Buttons: [ Apply to All ] [ Save Page ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onApplyToAll,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalGlassBorder)
                ) {
                    Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = "Batch", tint = PrecisionEmerald, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply to All", color = TextPrimary, fontSize = 12.sp)
                }

                Button(
                    onClick = onApplyToCurrent,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrecisionEmerald)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = "Save", tint = CanvasBlack, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Page", color = CanvasBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
