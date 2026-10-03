package com.docwarp.scanner.ui.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
import com.docwarp.scanner.ui.theme.CoralCrimson
import com.docwarp.scanner.ui.theme.PrecisionEmerald
import com.docwarp.scanner.ui.theme.TextPrimary
import com.docwarp.scanner.ui.theme.TextSecondary

/**
 * Screen 3 Page Card Component:
 * Displays page thumbnail, index badge (#01), filter badge, selection checkbox,
 * and quick actions (Rotate 90°, Crop, Filter, Delete).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageCardItem(
    page: ScannedPage,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onCardClick: () -> Unit,
    onCardLongClick: () -> Unit = {},
    onRotateClick: () -> Unit,
    onCropClick: () -> Unit,
    onFilterClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(16.dp)
    val file = if (page.processedImageFile.exists()) page.processedImageFile else page.rawImageFile

    val borderColor = when {
        isSelected -> PrecisionEmerald
        isSelectionMode -> CharcoalGlassBorder
        else -> CharcoalGlassBorder
    }

    val backgroundColor = if (isSelected) {
        PrecisionEmerald.copy(alpha = 0.08f)
    } else {
        CharcoalGlass
    }

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(backgroundColor)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, cardShape)
            .combinedClickable(
                onClick = onCardClick,
                onLongClick = onCardLongClick
            )
            .padding(8.dp)
    ) {
        // Thumbnail Box with Page Badge & Selection Indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f) // Standard document A4 portrait ratio
                .clip(RoundedCornerShape(10.dp))
                .background(CanvasBlack)
        ) {
            AsyncImage(
                model = file,
                contentDescription = "Page ${page.pageNumber}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .matchParentSize()
                    .rotate(page.rotationDegrees.toFloat())
            )

            // Index badge: #01, #02
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(0.5.dp, CharcoalGlassBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "#%02d".format(page.pageNumber),
                    color = PrecisionEmerald,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Filter badge bottom start
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.70f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = page.filter.displayName,
                    color = TextPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Selection Circle Indicator
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) PrecisionEmerald else Color.Black.copy(alpha = 0.65f))
                        .border(1.5.dp, if (isSelected) PrecisionEmerald else Color.White.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = CanvasBlack,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Quick Action Row: Rotate, Crop, Filter, Delete (hidden during selection mode for clean look)
        if (!isSelectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onRotateClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.RotateRight,
                        contentDescription = "Rotate 90",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onCropClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Crop,
                        contentDescription = "Manual Crop",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onFilterClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Filter Studio",
                        tint = PrecisionEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = CoralCrimson,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else {
            // In selection mode, show simple title
            Text(
                text = "Page ${page.pageNumber}",
                color = if (isSelected) PrecisionEmerald else TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            )
        }
    }
}
