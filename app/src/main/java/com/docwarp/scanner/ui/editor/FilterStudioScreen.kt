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
 * Features an interactive Before/After split-slider, preset filter carousel,
 * ML toggles (Erase Finger Shadows & Flatten Book Curvature), and batch apply options.
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
    val flattenCurvature by viewModel.flattenCurvature.collectAsState()
    val sliderPosition by viewModel.sliderPosition.collectAsState()
    val isGenerating by viewModel.isGeneratingPreview.collectAsState()

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
                    text = "Filter Studio",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
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
            }

            // Interactive Split-Slider Comparison Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F1115))
                    .border(1.dp, CharcoalGlassBorder, RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val newNorm = (sliderPosition + dragAmount / size.width).coerceIn(0.05f, 0.95f)
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

                        val scaleX = canvasW / orig.width.toFloat()
                        val scaleY = canvasH / orig.height.toFloat()
                        val scale = minOf(scaleX, scaleY)

                        val renderW = (orig.width * scale).toInt()
                        val renderH = (orig.height * scale).toInt()
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
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("ORIGINAL", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(selectedFilter.name, color = PrecisionEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // Bottom Filter & ML Controls Section
            FilterStudioControls(
                selectedFilter = selectedFilter,
                onSelectFilter = { viewModel.selectFilter(it) },
                eraseFingers = eraseFingers,
                onEraseFingersToggle = { viewModel.setEraseFingers(it) },
                flattenCurvature = flattenCurvature,
                onFlattenCurvatureToggle = { viewModel.setFlattenCurvature(it) },
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
    flattenCurvature: Boolean,
    onFlattenCurvatureToggle: (Boolean) -> Unit,
    onApplyToCurrent: () -> Unit,
    onApplyToAll: () -> Unit
) {
    val containerShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Preset Filter Carousel
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ScanFilter.entries.toTypedArray()) { filter ->
                    val isSelected = (filter == selectedFilter)
                    val pillShape = RoundedCornerShape(16.dp)

                    Box(
                        modifier = Modifier
                            .clip(pillShape)
                            .background(if (isSelected) PrecisionEmerald.copy(alpha = 0.20f) else GlassSurfaceMuted)
                            .border(
                                1.dp,
                                if (isSelected) PrecisionEmerald else CharcoalGlassBorder,
                                pillShape
                            )
                            .clickable { onSelectFilter(filter) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = filter.displayName,
                            color = if (isSelected) PrecisionEmerald else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // 2. ML Toggles Row (Erase Finger Shadows & Flatten Book Curvature)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Erase Fingers Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    Text("Erase Fingers", color = TextPrimary, fontSize = 12.sp)
                }

                // Flatten Curvature Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        checked = flattenCurvature,
                        onCheckedChange = onFlattenCurvatureToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CanvasBlack,
                            checkedTrackColor = PrecisionEmerald,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = CharcoalGlass
                        )
                    )
                    Text("Flatten Book", color = TextPrimary, fontSize = 12.sp)
                }
            }

            // 3. Action Buttons: [ Apply to All ] [ Apply Page ]
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
