package com.docwarp.scanner.ui.camera

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.docwarp.scanner.DocWarpApplication
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.docwarp.scanner.core.model.CameraResolutionOption
import com.docwarp.scanner.core.model.DocumentFolder
import com.docwarp.scanner.core.model.ScanFilter
import com.docwarp.scanner.core.model.ScannedPage
import com.docwarp.scanner.ui.theme.CanvasBlack
import com.docwarp.scanner.ui.theme.CharcoalGlass
import com.docwarp.scanner.ui.theme.CharcoalGlassBorder
import com.docwarp.scanner.ui.theme.GlassSurfaceMuted
import com.docwarp.scanner.ui.theme.PrecisionEmerald
import com.docwarp.scanner.ui.theme.TextPrimary
import com.docwarp.scanner.ui.theme.TextSecondary
import java.util.concurrent.Executors

@Composable
fun CameraScannerScreen(
    viewModel: CameraScannerViewModel,
    onNavigateToGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // State collections
    val flashMode by viewModel.flashMode.collectAsState()
    val isAutoShutter by viewModel.isAutoShutter.collectAsState()
    val isDualPageMode by viewModel.isDualPageMode.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val selectedResolution by viewModel.selectedResolution.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val availableFolders by viewModel.availableFolders.collectAsState()

    val isResolutionDialogVisible by viewModel.isResolutionDialogVisible.collectAsState()
    val isFilterPaletteVisible by viewModel.isFilterPaletteVisible.collectAsState()
    val isFolderDialogVisible by viewModel.isFolderDialogVisible.collectAsState()
    val isGridEnabled by viewModel.isGridEnabled.collectAsState()

    val extractedOcrText by viewModel.extractedOcrText.collectAsState()
    val isOcrLoading by viewModel.isOcrLoading.collectAsState()
    val shutterFlashCounter by viewModel.shutterFlashCounter.collectAsState()

    val detectedQuad by viewModel.detectedQuad.collectAsState()
    val feedbackState by viewModel.feedbackState.collectAsState()
    val isStillAndLocked by viewModel.isStillAndLocked.collectAsState()
    val autoCaptureProgress by viewModel.autoCaptureProgress.collectAsState()

    val roll by viewModel.rollDegrees.collectAsState()
    val pitch by viewModel.pitchDegrees.collectAsState()
    val isLevel by viewModel.isLevel.collectAsState()

    val documentState by viewModel.documentState.collectAsState()
    val lastPage = documentState.pages.lastOrNull()
    val pageCount = documentState.pages.size

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Gallery Multiple Images Picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importImagesFromUris(uris)
            Toast.makeText(context, "Importing ${uris.size} image(s)...", Toast.LENGTH_SHORT).show()
        }
    }

    // Files Document Picker (PDF & Images)
    val filesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importImagesFromUris(uris)
            Toast.makeText(context, "Importing ${uris.size} file(s)...", Toast.LENGTH_SHORT).show()
        }
    }

    // Google Play Services ML Kit Document Scanner Launcher (Hardware-accelerated neural edge detection & dewarping)
    val activity = context as? Activity
    val mlKitScannerClient = remember {
        (context.applicationContext as? DocWarpApplication)?.googleMlKitScanner?.getScannerClient(100)
    }

    val mlKitLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pages?.let { pages ->
                val uris = pages.mapNotNull { it.imageUri }
                if (uris.isNotEmpty()) {
                    viewModel.importImagesFromUris(uris)
                    Toast.makeText(context, "Scanned ${uris.size} page(s) with Google AI!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val onLaunchAiScanner: () -> Unit = {
        if (activity != null && mlKitScannerClient != null) {
            mlKitScannerClient.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    mlKitLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener { e ->
                    Log.e("CameraScanner", "ML Kit intent failed: ${e.message}")
                    Toast.makeText(context, "Could not launch Google AI Scanner: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(context, "AI Scanner initializing...", Toast.LENGTH_SHORT).show()
        }
    }

    // Shutter flash animation (60ms camera pulse)
    val flashAlpha = remember { Animatable(0f) }
    LaunchedEffect(shutterFlashCounter) {
        if (shutterFlashCounter > 0) {
            flashAlpha.snapTo(0.75f)
            flashAlpha.animateTo(0f, animationSpec = tween(70))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBlack)
    ) {
        if (hasCameraPermission) {
            // CameraX Viewfinder
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val targetSize = selectedResolution.size
                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .setTargetResolution(targetSize)
                            .build()
                        viewModel.setImageCapture(imageCapture)

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            val bitmap = imageProxy.toBitmap()
                            viewModel.processAnalysisFrame(bitmap)
                            imageProxy.close()
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            Log.e("CameraScanner", "Camera bind failed: ${e.message}", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Rule of Thirds 3x3 Grid Overlay (Optional)
            if (isGridEnabled) {
                RuleOfThirdsGrid(modifier = Modifier.fillMaxSize())
            }

            // Real-Time Smart Reticle Canvas (Bounding Quad, L-Brackets, Glowing Spine)
            ScannerOverlayCanvas(
                detectedQuad = detectedQuad,
                isStillAndLocked = isStillAndLocked,
                isDualPageMode = isDualPageMode,
                modifier = Modifier.fillMaxSize()
            )

            // Optical Shutter Flash Pulse
            if (flashAlpha.value > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(flashAlpha.value)
                        .background(Color.White)
                )
            }

            // Floating Top Navigation Bar (OKEN Scanner Style: Flash | HD | Filter | Grid | Auto)
            OkenTopBar(
                flashMode = flashMode,
                onFlashClick = { viewModel.cycleFlashMode() },
                resolutionTag = selectedResolution.tag,
                onResolutionClick = { viewModel.toggleResolutionDialog() },
                onFilterPaletteClick = { viewModel.toggleFilterPalette() },
                onAiScanClick = onLaunchAiScanner,
                isGridEnabled = isGridEnabled,
                onGridToggle = { viewModel.toggleGrid() },
                isAutoShutter = isAutoShutter,
                onAutoShutterToggle = { viewModel.toggleAutoShutter() },
                roll = roll,
                pitch = pitch,
                isLevel = isLevel,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )

            // Ambient Status Chip
            AmbientFeedbackChip(
                state = feedbackState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 195.dp)
            )

            // Bottom Mode Selector & Control Dock
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                // Folder Quick Chip (e.g. 📁 "General" - tap to select/create folder)
                FolderIndicatorChip(
                    folder = selectedFolder,
                    onClick = { viewModel.toggleFolderDialog() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Mode Selector Carousel ("Book" | "To Text" | "Docs" | "ID Card" | "QR Code")
                ModeSelectorRow(
                    selectedMode = selectedMode,
                    onSelectMode = { viewModel.setScannerMode(it) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Control Dock (Gallery/Import | Rapid Burst Shutter | Import Files | Done)
                ProBottomDock(
                    lastScannedPage = lastPage,
                    pageCount = pageCount,
                    isAutoCaptureEnabled = isAutoShutter,
                    autoCaptureProgress = autoCaptureProgress,
                    onShutterClick = { viewModel.triggerShutter() },
                    onGalleryClick = onNavigateToGallery,
                    onImportImagesClick = { imagePickerLauncher.launch("image/*") },
                    onImportFilesClick = { filesPickerLauncher.launch(arrayOf("application/pdf", "image/*")) },
                    onDoneClick = onNavigateToGallery
                )
            }

            // Resolution Selection Dialog (OKEN Screenshot 1 style - floating left panel)
            AnimatedVisibility(
                visible = isResolutionDialogVisible,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                ResolutionPickerSidebar(
                    selectedResolution = selectedResolution,
                    onSelectResolution = { viewModel.setResolution(it) },
                    onDismiss = { viewModel.closeResolutionDialog() }
                )
            }

            // Filter Palette Dialog (OKEN Screenshot 2 style - floating top card)
            AnimatedVisibility(
                visible = isFilterPaletteVisible,
                enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp, start = 16.dp, end = 16.dp)
            ) {
                FilterPalettePopup(
                    activeFilter = activeFilter,
                    onSelectFilter = { viewModel.setActiveFilter(it) },
                    onDismiss = { viewModel.closeFilterPalette() }
                )
            }

            // Folder Selection Dialog
            AnimatedVisibility(
                visible = isFolderDialogVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier.align(Alignment.Center)
            ) {
                FolderSelectionModal(
                    selectedFolder = selectedFolder,
                    availableFolders = availableFolders,
                    onSelectFolder = { viewModel.selectFolder(it) },
                    onCreateFolder = { viewModel.createFolder(it) },
                    onDismiss = { viewModel.closeFolderDialog() }
                )
            }

            // OCR Extraction Result Sheet ("To Text" Mode)
            if (extractedOcrText != null || isOcrLoading) {
                OcrResultSheet(
                    text = extractedOcrText,
                    isLoading = isOcrLoading,
                    onDismiss = { viewModel.clearOcrText() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        } else {
            // Permission fallback message
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Camera permission required to scan documents.",
                    color = TextPrimary,
                    fontSize = 14.sp
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}

/**
 * Top Navigation Bar matching OKEN Scanner:
 * [ Flash ] [ HD Badge ] [ 3-Circle Palette ] [ 3x3 Grid ] [ Auto/Manual Shutter ]
 */
@Composable
private fun OkenTopBar(
    flashMode: FlashMode,
    onFlashClick: () -> Unit,
    resolutionTag: String,
    onResolutionClick: () -> Unit,
    onFilterPaletteClick: () -> Unit,
    onAiScanClick: () -> Unit,
    isGridEnabled: Boolean,
    onGridToggle: () -> Unit,
    isAutoShutter: Boolean,
    onAutoShutterToggle: () -> Unit,
    roll: Float,
    pitch: Float,
    isLevel: Boolean,
    modifier: Modifier = Modifier
) {
    val barShape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(barShape)
            .background(CharcoalGlass)
            .border(1.dp, CharcoalGlassBorder, barShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Flash Toggle
            IconButton(onClick = onFlashClick, modifier = Modifier.size(36.dp)) {
                val (icon, tint) = when (flashMode) {
                    FlashMode.OFF -> Pair(Icons.Default.FlashOff, TextSecondary)
                    FlashMode.AUTO -> Pair(Icons.Default.FlashAuto, TextPrimary)
                    FlashMode.TORCH -> Pair(Icons.Default.FlashOn, PrecisionEmerald)
                }
                Icon(imageVector = icon, contentDescription = "Flash", tint = tint, modifier = Modifier.size(20.dp))
            }

            // 2. HD Resolution Selector Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { onResolutionClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = resolutionTag,
                        color = PrecisionEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 3. AI Scanner Neural Edge Detection Button (Google ML Kit Document Scanner)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(PrecisionEmerald.copy(alpha = 0.16f))
                    .border(1.dp, PrecisionEmerald, RoundedCornerShape(10.dp))
                    .clickable { onAiScanClick() }
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Scanner",
                        tint = PrecisionEmerald,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "AI SCAN",
                        color = PrecisionEmerald,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 4. 3-Circle Filter Palette Button (OKEN style)
            IconButton(onClick = onFilterPaletteClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = "Filters",
                    tint = PrecisionEmerald,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 5. 3x3 Grid Toggle
            IconButton(onClick = onGridToggle, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.GridOn,
                    contentDescription = "Grid",
                    tint = if (isGridEnabled) PrecisionEmerald else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 6. Auto / Manual Shutter Beacon
            AutoShutterBadge(
                isEnabled = isAutoShutter,
                onClick = onAutoShutterToggle
            )
        }
    }
}

/**
 * Resolution Selection Sidebar matching OKEN Scanner Screenshot 1:
 * "Unit: pixel" with radio buttons for all resolutions
 */
@Composable
private fun ResolutionPickerSidebar(
    selectedResolution: CameraResolutionOption,
    onSelectResolution: (CameraResolutionOption) -> Unit,
    onDismiss: () -> Unit
) {
    val panelShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)

    Box(
        modifier = Modifier
            .fillMaxHeight(0.78f)
            .width(260.dp)
            .clip(panelShape)
            .background(Color(0xFF13151A))
            .border(1.dp, CharcoalGlassBorder, panelShape)
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Unit: pixel",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = CharcoalGlassBorder)
            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(CameraResolutionOption.ALL_OPTIONS) { option ->
                    val isSelected = (option.title == selectedResolution.title)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) PrecisionEmerald.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { onSelectResolution(option) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = option.title,
                            color = if (isSelected) PrecisionEmerald else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectResolution(option) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = PrecisionEmerald,
                                unselectedColor = TextSecondary
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Filter Palette Popup matching OKEN Scanner Screenshot 2:
 * Floating card with filter tiles: No shadow (Beta), Original, Lighten, Magic Color, Clean eBook, Grayscale, B&W, Eco
 */
@Composable
private fun FilterPalettePopup(
    activeFilter: ScanFilter,
    onSelectFilter: (ScanFilter) -> Unit,
    onDismiss: () -> Unit
) {
    val cardShape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Color(0xFF16181E))
            .border(1.dp, CharcoalGlassBorder, cardShape)
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Live Enhancement Filters",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val displayFilters = listOf(
                ScanFilter.NO_SHADOW,
                ScanFilter.ORIGINAL,
                ScanFilter.LIGHTEN,
                ScanFilter.MAGIC_COLOR,
                ScanFilter.EBOOK_CLEAN,
                ScanFilter.GRAYSCALE_SMOOTH,
                ScanFilter.BW,
                ScanFilter.ECO
            )

            // 4 columns grid
            val rows = displayFilters.chunked(4)
            for (row in rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (filter in row) {
                        val isSelected = (filter == activeFilter)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) PrecisionEmerald.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                                .border(
                                    1.5.dp,
                                    if (isSelected) PrecisionEmerald else CharcoalGlassBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectFilter(filter) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Filter icon illustration
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when (filter) {
                                            ScanFilter.NO_SHADOW -> Color(0xFF1B5E20)
                                            ScanFilter.ORIGINAL -> Color(0xFF424242)
                                            ScanFilter.LIGHTEN -> Color(0xFFF57F17)
                                            ScanFilter.MAGIC_COLOR -> Color(0xFF00897B)
                                            ScanFilter.EBOOK_CLEAN -> Color(0xFF00E599)
                                            ScanFilter.GRAYSCALE_SMOOTH -> Color(0xFF757575)
                                            ScanFilter.BW -> Color(0xFF212121)
                                            ScanFilter.ECO -> Color(0xFF1E88E5)
                                            else -> Color.DarkGray
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = filter.displayName.take(2).uppercase(),
                                    color = if (filter == ScanFilter.EBOOK_CLEAN) CanvasBlack else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = filter.displayName,
                                color = if (isSelected) PrecisionEmerald else TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )

                            if (filter.isBeta) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0xFFE53935))
                                        .padding(horizontal = 3.dp, vertical = 0.dp)
                                ) {
                                    Text("Beta", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Bottom Mode Selector Row matching OKEN Scanner:
 * "Book" | "To Text" | "Docs" (Active in Emerald) | "ID Card" | "QR Code"
 */
@Composable
private fun ModeSelectorRow(
    selectedMode: ScannerMode,
    onSelectMode: (ScannerMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScannerMode.entries.forEach { mode ->
            val isSelected = (mode == selectedMode)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onSelectMode(mode) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = mode.title,
                    color = if (isSelected) PrecisionEmerald else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(3.dp))
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(PrecisionEmerald)
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.5.dp))
                }
            }
        }
    }
}

/**
 * Folder Indicator Chip above shutter
 */
@Composable
private fun FolderIndicatorChip(
    folder: DocumentFolder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, CharcoalGlassBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(folder.iconEmoji, fontSize = 12.sp)
        Text(
            text = folder.name,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Thumb-Zone Bottom Control Dock with non-stop rapid burst shutter,
 * gallery import, files import, and batch page count.
 */
@Composable
private fun ProBottomDock(
    lastScannedPage: ScannedPage?,
    pageCount: Int,
    isAutoCaptureEnabled: Boolean,
    autoCaptureProgress: Float,
    onShutterClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onImportImagesClick: () -> Unit,
    onImportFilesClick: () -> Unit,
    onDoneClick: () -> Unit
) {
    val dockShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(dockShape)
            .background(CharcoalGlass)
            .border(1.dp, CharcoalGlassBorder, dockShape)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Gallery Thumbnail / Import Images
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (pageCount > 0) onGalleryClick() else onImportImagesClick()
                    }
                    .padding(6.dp)
            ) {
                if (lastScannedPage != null && lastScannedPage.processedImageFile.exists()) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.5.dp, PrecisionEmerald, RoundedCornerShape(10.dp))
                    ) {
                        AsyncImage(
                            model = lastScannedPage.processedImageFile,
                            contentDescription = "Last Scan",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GlassSurfaceMuted)
                            .border(1.dp, CharcoalGlassBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = "Import", tint = TextPrimary, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text("Import", color = TextSecondary, fontSize = 11.sp)
            }

            // Center: Rapid-Fire Continuous Shutter Button
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(2.dp, if (isAutoCaptureEnabled) PrecisionEmerald else Color.White, CircleShape)
                    .clickable { onShutterClick() },
                contentAlignment = Alignment.Center
            ) {
                // Outer ring
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )

                // Page count badge on shutter
                if (pageCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(PrecisionEmerald),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pageCount.toString(),
                            color = CanvasBlack,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Right: Import Files (PDF/Images) or Done button
            if (pageCount > 0) {
                Button(
                    onClick = onDoneClick,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrecisionEmerald)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = "Done", tint = CanvasBlack, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Done ($pageCount)",
                        color = CanvasBlack,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onImportFilesClick() }
                        .padding(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GlassSurfaceMuted)
                            .border(1.dp, CharcoalGlassBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = "Import Files", tint = PrecisionEmerald, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Import Files", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Folder Selection & Creation Modal
 */
@Composable
private fun FolderSelectionModal(
    selectedFolder: DocumentFolder,
    availableFolders: List<DocumentFolder>,
    onSelectFolder: (DocumentFolder) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newFolderName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    val modalShape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth(0.90f)
            .clip(modalShape)
            .background(Color(0xFF13151A))
            .border(1.dp, CharcoalGlassBorder, modalShape)
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Select Document Folder",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxHeight(0.35f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(availableFolders) { folder ->
                    val isSelected = (folder.id == selectedFolder.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) PrecisionEmerald.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
                            .border(1.dp, if (isSelected) PrecisionEmerald else CharcoalGlassBorder, RoundedCornerShape(10.dp))
                            .clickable { onSelectFolder(folder) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(folder.iconEmoji, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = folder.name,
                            color = if (isSelected) PrecisionEmerald else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = "Selected", tint = PrecisionEmerald, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isCreating) {
                Button(
                    onClick = { isCreating = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GlassSurfaceMuted)
                ) {
                    Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = "Add", tint = PrecisionEmerald, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Folder", color = TextPrimary, fontSize = 12.sp)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        placeholder = { Text("Folder Name", color = TextSecondary, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrecisionEmerald,
                            unfocusedBorderColor = CharcoalGlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Button(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                onCreateFolder(newFolderName)
                                newFolderName = ""
                                isCreating = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrecisionEmerald)
                    ) {
                        Text("Add", color = CanvasBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * On-Device OCR Result Bottom Sheet ("To Text" Mode)
 */
@Composable
private fun OcrResultSheet(
    text: String?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.50f)
            .clip(sheetShape)
            .background(Color(0xFF111317))
            .border(1.dp, CharcoalGlassBorder, sheetShape)
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "OCR", tint = PrecisionEmerald, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Extracted Text (On-Device OCR)",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrecisionEmerald)
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (!text.isNullOrBlank()) text else "No text detected on scanned page.",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (!text.isNullOrBlank()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Scanned Text", text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = GlassSurfaceMuted)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = PrecisionEmerald, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Text", color = TextPrimary, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (!text.isNullOrBlank()) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Scanned Text"))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PrecisionEmerald)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = CanvasBlack, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", color = CanvasBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 3x3 Rule of Thirds Grid Overlay
 */
@Composable
private fun RuleOfThirdsGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val gridColor = Color.White.copy(alpha = 0.18f)
        val strokeWidth = 1.dp.toPx()

        // Vertical lines at 1/3 and 2/3
        drawLine(color = gridColor, start = Offset(w / 3f, 0f), end = Offset(w / 3f, h), strokeWidth = strokeWidth)
        drawLine(color = gridColor, start = Offset(2f * w / 3f, 0f), end = Offset(2f * w / 3f, h), strokeWidth = strokeWidth)

        // Horizontal lines at 1/3 and 2/3
        drawLine(color = gridColor, start = Offset(0f, h / 3f), end = Offset(w, h / 3f), strokeWidth = strokeWidth)
        drawLine(color = gridColor, start = Offset(0f, 2f * h / 3f), end = Offset(w, 2f * h / 3f), strokeWidth = strokeWidth)
    }
}

@Composable
private fun AutoShutterBadge(
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "BeaconPulse")
    val beaconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BeaconAlpha"
    )

    val badgeShape = RoundedCornerShape(14.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(badgeShape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .alpha(if (isEnabled) beaconAlpha else 0.3f)
                .background(if (isEnabled) PrecisionEmerald else TextSecondary)
        )

        Text(
            text = if (isEnabled) "AUTO" else "MANUAL",
            color = if (isEnabled) PrecisionEmerald else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
