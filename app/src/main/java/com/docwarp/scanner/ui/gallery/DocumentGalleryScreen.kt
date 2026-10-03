package com.docwarp.scanner.ui.gallery

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docwarp.scanner.core.model.DocumentFolder
import com.docwarp.scanner.ui.theme.CanvasBlack
import com.docwarp.scanner.ui.theme.CharcoalGlass
import com.docwarp.scanner.ui.theme.CharcoalGlassBorder
import com.docwarp.scanner.ui.theme.CoralCrimson
import com.docwarp.scanner.ui.theme.EmeraldGradientEnd
import com.docwarp.scanner.ui.theme.EmeraldGradientStart
import com.docwarp.scanner.ui.theme.GlassSurfaceMuted
import com.docwarp.scanner.ui.theme.PrecisionEmerald
import com.docwarp.scanner.ui.theme.TextPrimary
import com.docwarp.scanner.ui.theme.TextSecondary

/**
 * Screen 3: Multi-Page Gallery & Document Management.
 * Features folder tabs, batch selection, file imports (JPG/PDF),
 * and sticky bottom bar for quick PDF compilation.
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
    val context = LocalContext.current
    val documentState by viewModel.documentState.collectAsState()
    val estSize by viewModel.estimatedPdfSize.collectAsState()
    val availableFolders by viewModel.availableFolders.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedPageIds by viewModel.selectedPageIds.collectAsState()

    var showTitleDialog by remember { mutableStateOf(false) }
    var tempTitle by remember { mutableStateOf(documentState.title) }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    // Multiple Images Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importImagesFromUris(uris)
            Toast.makeText(context, "Importing ${uris.size} image(s)...", Toast.LENGTH_SHORT).show()
        }
    }

    // PDF & All Documents Picker Launcher
    val filesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importImagesFromUris(uris)
            Toast.makeText(context, "Importing ${uris.size} document(s)...", Toast.LENGTH_SHORT).show()
        }
    }

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
                folderName = selectedFolder.name,
                folderEmoji = selectedFolder.iconEmoji,
                isSelectionMode = isSelectionMode,
                selectedCount = selectedPageIds.size,
                totalCount = documentState.pages.size,
                onTitleClick = {
                    tempTitle = documentState.title
                    showTitleDialog = true
                },
                onBackClick = onNavigateBackToCamera,
                onAddPageClick = onNavigateBackToCamera,
                onToggleSelectionMode = { viewModel.toggleSelectionMode() },
                onImportClick = { filesPickerLauncher.launch(arrayOf("image/*", "application/pdf")) }
            )

            // Horizontal Folder Selection Tabs Row
            FolderTabsRow(
                folders = availableFolders,
                selectedFolder = selectedFolder,
                onSelectFolder = { viewModel.selectFolder(it) },
                onAddFolderClick = { showNewFolderDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            // Main Content Area: Grid or Empty State
            if (documentState.pages.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(
                        items = documentState.pages,
                        key = { it.id }
                    ) { page ->
                        val isSelected = selectedPageIds.contains(page.id)
                        PageCardItem(
                            page = page,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onCardClick = {
                                if (isSelectionMode) {
                                    viewModel.togglePageSelection(page.id)
                                } else {
                                    onNavigateToFilter(page.id)
                                }
                            },
                            onCardLongClick = {
                                viewModel.togglePageSelection(page.id)
                            },
                            onRotateClick = { viewModel.rotatePage(page.id) },
                            onCropClick = { onNavigateToCrop(page.id) },
                            onFilterClick = { onNavigateToFilter(page.id) },
                            onDeleteClick = { viewModel.deletePage(page.id) }
                        )
                    }
                }
            } else {
                // Empty state illustration
                GalleryEmptyState(
                    onScanClick = onNavigateBackToCamera,
                    onImportClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            // Bottom Sticky Bar (Adapts between Export mode & Multi-Select Batch mode)
            if (isSelectionMode) {
                BatchSelectionBottomBar(
                    selectedCount = selectedPageIds.size,
                    totalCount = documentState.pages.size,
                    onSelectAll = { viewModel.selectAll() },
                    onRotateSelected = { viewModel.rotateSelected() },
                    onDeleteSelected = { viewModel.deleteSelected() },
                    onCancelSelection = { viewModel.clearSelection() }
                )
            } else {
                GalleryStickyBottomBar(
                    totalCount = documentState.pages.size,
                    estSize = estSize,
                    onExportClick = onNavigateToExport
                )
            }
        }

        // Title rename dialog
        if (showTitleDialog) {
            AlertDialog(
                onDismissRequest = { showTitleDialog = false },
                title = { Text("Rename Document", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = tempTitle,
                        onValueChange = { tempTitle = it },
                        singleLine = true,
                        label = { Text("Document Title") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrecisionEmerald,
                            unfocusedBorderColor = CharcoalGlassBorder
                        )
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
                        Text("Save", color = CanvasBlack, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTitleDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = Color(0xFF14171D)
            )
        }

        // New folder creation dialog
        if (showNewFolderDialog) {
            AlertDialog(
                onDismissRequest = { showNewFolderDialog = false },
                title = { Text("New Document Folder", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        singleLine = true,
                        placeholder = { Text("e.g. Invoices, College Notes") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrecisionEmerald,
                            unfocusedBorderColor = CharcoalGlassBorder
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                viewModel.createFolder(newFolderName)
                                newFolderName = ""
                                showNewFolderDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrecisionEmerald)
                    ) {
                        Text("Create", color = CanvasBlack, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewFolderDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = Color(0xFF14171D)
            )
        }
    }
}

/**
 * Top App Bar with folder indication and selection controls
 */
@Composable
private fun GalleryTopAppBar(
    title: String,
    folderName: String,
    folderEmoji: String,
    isSelectionMode: Boolean,
    selectedCount: Int,
    totalCount: Int,
    onTitleClick: () -> Unit,
    onBackClick: () -> Unit,
    onAddPageClick: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onImportClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
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
                .padding(horizontal = 6.dp)
        ) {
            Text(
                text = if (isSelectionMode) "$selectedCount of $totalCount Selected" else title,
                color = if (isSelectionMode) PrecisionEmerald else TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$folderEmoji $folderName",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = " • Tap to rename",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        }

        // Import Button
        IconButton(onClick = onImportClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.FileUpload,
                contentDescription = "Import Files",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Selection Toggle Button
        IconButton(onClick = onToggleSelectionMode, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (isSelectionMode) Icons.Default.Close else Icons.Default.SelectAll,
                contentDescription = "Select Mode",
                tint = if (isSelectionMode) PrecisionEmerald else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Add page (+) camera button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(GlassSurfaceMuted)
                .border(1.dp, CharcoalGlassBorder, RoundedCornerShape(16.dp))
                .clickable { onAddPageClick() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Scan More",
                    tint = PrecisionEmerald,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Scan",
                    color = PrecisionEmerald,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Horizontal scrolling folder tabs
 */
@Composable
private fun FolderTabsRow(
    folders: List<DocumentFolder>,
    selectedFolder: DocumentFolder,
    onSelectFolder: (DocumentFolder) -> Unit,
    onAddFolderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(folders) { folder ->
            val isSelected = (folder.id == selectedFolder.id)
            val tabShape = RoundedCornerShape(14.dp)

            Row(
                modifier = Modifier
                    .clip(tabShape)
                    .background(if (isSelected) PrecisionEmerald.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
                    .border(
                        1.dp,
                        if (isSelected) PrecisionEmerald else CharcoalGlassBorder,
                        tabShape
                    )
                    .clickable { onSelectFolder(folder) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(text = folder.iconEmoji, fontSize = 12.sp)
                Text(
                    text = folder.name,
                    color = if (isSelected) PrecisionEmerald else TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        // Add Folder Pill
        item {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(1.dp, CharcoalGlassBorder, RoundedCornerShape(14.dp))
                    .clickable { onAddFolderClick() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Folder",
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text("Folder", color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

/**
 * Empty State when no document pages are present
 */
@Composable
private fun GalleryEmptyState(
    onScanClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, CharcoalGlassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = PrecisionEmerald,
                    modifier = Modifier.size(36.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No Pages Scanned Yet",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Capture documents with the camera or import images and PDF files directly.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onScanClick,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrecisionEmerald)
                ) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Scan", tint = CanvasBlack, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start Scanning", color = CanvasBlack, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = onImportClick,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GlassSurfaceMuted)
                ) {
                    Icon(imageVector = Icons.Default.FileUpload, contentDescription = "Import", tint = TextPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Files", color = TextPrimary, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Batch Selection Multi-Action Bottom Bar
 */
@Composable
private fun BatchSelectionBottomBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onRotateSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCancelSelection: () -> Unit
) {
    val barShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(barShape)
            .background(CharcoalGlass)
            .border(1.dp, CharcoalGlassBorder, barShape)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSelectAll) {
                    Text(if (selectedCount == totalCount) "Deselect All" else "Select All", color = PrecisionEmerald, fontSize = 12.sp)
                }
                TextButton(onClick = onCancelSelection) {
                    Text("Done", color = TextSecondary, fontSize = 12.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRotateSelected,
                    enabled = selectedCount > 0,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GlassSurfaceMuted)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate", tint = TextPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rotate", color = TextPrimary, fontSize = 12.sp)
                }

                Button(
                    onClick = onDeleteSelected,
                    enabled = selectedCount > 0,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CoralCrimson)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete ($selectedCount)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
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
            .padding(horizontal = 20.dp, vertical = 14.dp)
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
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(
                        if (totalCount > 0) {
                            Brush.horizontalGradient(
                                colors = listOf(EmeraldGradientStart, EmeraldGradientEnd)
                            )
                        } else {
                            Brush.horizontalGradient(
                                colors = listOf(Color.DarkGray, Color.Gray)
                            )
                        }
                    )
                    .clickable(enabled = totalCount > 0) { onExportClick() }
                    .padding(horizontal = 22.dp),
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
