package com.docwarp.scanner.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RotateRight
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
 * Displays page thumbnail, index badge (#01), and quick actions (Rotate 90°, Crop, Filter, Delete).
 */
@Composable
fun PageCardItem(
    page: ScannedPage,
    onCardClick: () -> Unit,
    onRotateClick: () -> Unit,
    onCropClick: () -> Unit,
    onFilterClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(14.dp)
    val file = if (page.processedImageFile.exists()) page.processedImageFile else page.rawImageFile

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(CharcoalGlass)
            .border(1.dp, CharcoalGlassBorder, cardShape)
            .clickable { onCardClick() }
            .padding(8.dp)
    ) {
        // Thumbnail Box with Page Badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f) // Standard document A4 portrait ratio
                .clip(RoundedCornerShape(8.dp))
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
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Quick Action Row: Rotate, Crop, Filter, Delete
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
                    imageVector = Icons.Default.RotateRight,
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
                    tint = TextSecondary,
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
    }
}
