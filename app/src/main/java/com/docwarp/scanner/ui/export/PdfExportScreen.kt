package com.docwarp.scanner.ui.export

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.docwarp.scanner.core.model.CompressionQuality
import com.docwarp.scanner.core.model.PageSizeOption
import com.docwarp.scanner.ui.theme.CanvasBlack
import com.docwarp.scanner.ui.theme.CharcoalGlass
import com.docwarp.scanner.ui.theme.CharcoalGlassBorder
import com.docwarp.scanner.ui.theme.GlassSurfaceMuted
import com.docwarp.scanner.ui.theme.PrecisionEmerald
import com.docwarp.scanner.ui.theme.TextPrimary
import com.docwarp.scanner.ui.theme.TextSecondary

/**
 * Screen 6: PDF Preview, Metadata & Compression Sheet.
 * Features an interactive vertical-scrolling page preview, metadata title & page-size customization,
 * multi-tier compression quality selector, and instant export/share actions.
 */
@Composable
fun PdfExportScreen(
    viewModel: PdfExportViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val document by viewModel.documentState.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val progress by viewModel.exportProgress.collectAsState()
    val exportedUri by viewModel.exportedUri.collectAsState()

    var docTitle by remember(document.title) { mutableStateOf(document.title) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
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
                    text = "PDF Preview & Export",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Metadata: Title Field
                item {
                    OutlinedTextField(
                        value = docTitle,
                        onValueChange = {
                            docTitle = it
                            viewModel.updateTitle(it)
                        },
                        label = { Text("Document Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrecisionEmerald,
                            unfocusedBorderColor = CharcoalGlassBorder,
                            focusedLabelColor = PrecisionEmerald,
                            unfocusedLabelColor = TextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 2. Page Size Selection Pills
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("PAGE SIZE", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PageSizeOption.entries.forEach { option ->
                                val isSelected = (option == document.pageSize)
                                val pillShape = RoundedCornerShape(12.dp)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(pillShape)
                                        .background(if (isSelected) PrecisionEmerald.copy(alpha = 0.20f) else GlassSurfaceMuted)
                                        .border(1.dp, if (isSelected) PrecisionEmerald else CharcoalGlassBorder, pillShape)
                                        .clickable { viewModel.updatePageSize(option) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option.name.replace("_", " "),
                                        color = if (isSelected) PrecisionEmerald else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Compression Profile Cards
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("COMPRESSION & QUALITY", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        CompressionQuality.entries.forEach { quality ->
                            val isSelected = (quality == document.compression)
                            val cardShape = RoundedCornerShape(14.dp)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(cardShape)
                                    .background(if (isSelected) PrecisionEmerald.copy(alpha = 0.15f) else CharcoalGlass)
                                    .border(1.dp, if (isSelected) PrecisionEmerald else CharcoalGlassBorder, cardShape)
                                    .clickable { viewModel.updateCompression(quality) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column {
                                    Text(
                                        text = quality.title,
                                        color = if (isSelected) PrecisionEmerald else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = quality.subtitle,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Interactive Page Previews
                item {
                    Text("PAGE PREVIEWS (${document.pages.size})", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                itemsIndexed(document.pages, key = { _, page -> page.id }) { index, page ->
                    val file = if (page.processedImageFile.exists()) page.processedImageFile else page.rawImageFile
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CharcoalGlass)
                            .border(1.dp, CharcoalGlassBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = file,
                            contentDescription = "Page ${index + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .matchParentSize()
                                .rotate(page.rotationDegrees.toFloat())
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.70f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "P.${index + 1}",
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Bottom Sticky Export Actions Dock
            PdfExportBottomDock(
                isExporting = isExporting,
                progress = progress,
                onSaveToStorage = {
                    viewModel.exportToStorage { uri ->
                        Toast.makeText(context, "PDF saved to Downloads/DocWarp!", Toast.LENGTH_LONG).show()
                    }
                },
                onShare = {
                    if (exportedUri != null) {
                        viewModel.sharePdf(exportedUri!!)
                    } else {
                        viewModel.exportToStorage { uri ->
                            viewModel.sharePdf(uri)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun PdfExportBottomDock(
    isExporting: Boolean,
    progress: Float,
    onSaveToStorage: () -> Unit,
    onShare: () -> Unit
) {
    val dockShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(dockShape)
            .background(CharcoalGlass)
            .border(1.dp, CharcoalGlassBorder, dockShape)
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (isExporting) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = PrecisionEmerald,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Direct Share Button
                OutlinedButton(
                    onClick = onShare,
                    enabled = !isExporting,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalGlassBorder)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = PrecisionEmerald, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Direct Share", color = TextPrimary, fontSize = 13.sp)
                }

                // Save to Storage Button
                Button(
                    onClick = onSaveToStorage,
                    enabled = !isExporting,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrecisionEmerald)
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(color = CanvasBlack, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Download, contentDescription = "Save", tint = CanvasBlack, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save to Storage", color = CanvasBlack, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
