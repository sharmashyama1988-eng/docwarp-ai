package com.docwarp.scanner.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docwarp.scanner.ui.theme.CanvasBlack
import com.docwarp.scanner.ui.theme.CharcoalGlass
import com.docwarp.scanner.ui.theme.CharcoalGlassBorder
import com.docwarp.scanner.ui.theme.EmeraldGradientEnd
import com.docwarp.scanner.ui.theme.EmeraldGradientStart
import com.docwarp.scanner.ui.theme.GlassSurfaceMuted
import com.docwarp.scanner.ui.theme.PrecisionEmerald
import com.docwarp.scanner.ui.theme.TextPrimary
import com.docwarp.scanner.ui.theme.TextSecondary

/**
 * Screen 3: Multi-Page Gallery & Reorder Sheet.
 * Displays pages in a 2-column grid with quick rotation, crop, and filter access,
 * and a sticky bottom bar showing page count, estimated PDF size, and Export action.
 */
@Composable
fun DocumentGalleryScreen(
    viewModel: DocumentGalleryViewModel,
    onNavigateBackToCamera: () -> Unit,
    onNavigateToCrop: (pageId: String) -> Unit,
    onNavigateToFilter: (pageId: String) -> Unit,
    onNavigateToExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val documentState by viewModel.documentState.collectAsState()
    val estSize by viewModel.estimatedPdfSize.collectAsState()

    var showTitleDialog by remember { mutableStateOf(false) }
    var tempTitle by remember { mutableStateOf(documentState.title) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBlack)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar
            GalleryTopAppBar(
                title = documentState.title,
                onTitleClick = {
                    tempTitle = documentState.title
                    showTitleDialog = true
                },
                onBackClick = onNavigateBackToCamera,
                onAddPageClick = onNavigateBackToCamera
            )

            // 2-Column Grid Canvas
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(
                    items = documentState.pages,
                    key = { it.id }
                ) { page ->
                    PageCardItem(
                        page = page,
                        onCardClick = { onNavigateToFilter(page.id) },
                        onRotateClick = { viewModel.rotatePage(page.id) },
                        onCropClick = { onNavigateToCrop(page.id) },
                        onFilterClick = { onNavigateToFilter(page.id) },
                        onDeleteClick = { viewModel.deletePage(page.id) }
                    )
                }
            }

            // Bottom Sticky Bar
            GalleryStickyBottomBar(
                totalCount = documentState.pages.size,
                estSize = estSize,
                onExportClick = onNavigateToExport
            )
        }

        // Title rename dialog
        if (showTitleDialog) {
            AlertDialog(
                onDismissRequest = { showTitleDialog = false },
                title = { Text("Rename Document", color = TextPrimary) },
                text = {
                    OutlinedTextField(
                        value = tempTitle,
                        onValueChange = { tempTitle = it },
                        singleLine = true,
                        label = { Text("Document Title") }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateTitle(tempTitle)
                            showTitleDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrecisionEmerald)
                    ) {
                        Text("Save", color = CanvasBlack)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTitleDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = CharcoalGlass
            )
        }
    }
}

@Composable
private fun GalleryTopAppBar(
    title: String,
    onTitleClick: () -> Unit,
    onBackClick: () -> Unit,
    onAddPageClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onTitleClick() }
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = "Tap to rename",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        // Add page (+) button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(GlassSurfaceMuted)
                .border(1.dp, CharcoalGlassBorder, RoundedCornerShape(18.dp))
                .clickable { onAddPageClick() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Page",
                    tint = PrecisionEmerald,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Add",
                    color = PrecisionEmerald,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun GalleryStickyBottomBar(
    totalCount: Int,
    estSize: String,
    onExportClick: () -> Unit
) {
    val barShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(barShape)
            .background(CharcoalGlass)
            .border(1.dp, CharcoalGlassBorder, barShape)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Stats column
            Column {
                Text(
                    text = "$totalCount Pages",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Est. Size: $estSize",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Primary Emerald Gradient Export Pill
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(EmeraldGradientStart, EmeraldGradientEnd)
                        )
                    )
                    .clickable(enabled = totalCount > 0) { onExportClick() }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "Export PDF",
                        tint = CanvasBlack,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Export to PDF",
                        color = CanvasBlack,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
