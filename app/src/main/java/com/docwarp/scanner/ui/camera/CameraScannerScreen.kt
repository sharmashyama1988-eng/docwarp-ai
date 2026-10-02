package com.docwarp.scanner.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.docwarp.scanner.ui.theme.CanvasBlack
import com.docwarp.scanner.ui.theme.CharcoalGlassBorder
import com.docwarp.scanner.ui.theme.GlassSurfaceMuted
import com.docwarp.scanner.ui.theme.PrecisionEmerald
import com.docwarp.scanner.ui.theme.TextPrimary
import com.docwarp.scanner.ui.theme.TextSecondary
import java.util.concurrent.Executors

/**
 * Screen 1: Camera Scanner Viewfinder.
 * Continuous 1080p preview with smart reticle overlay, floating glass top bar,
 * live stillness detection, gyroscope leveler, ambient status chip, and bottom control dock.
 */
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

    val flashMode by viewModel.flashMode.collectAsState()
    val isAutoShutter by viewModel.isAutoShutter.collectAsState()
    val isDualPageMode by viewModel.isDualPageMode.collectAsState()
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

                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
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

            // Smart Reticle Canvas (Dynamic Quad, L-Brackets, Glowing Spine)
            ScannerOverlayCanvas(
                detectedQuad = detectedQuad,
                isStillAndLocked = isStillAndLocked,
                isDualPageMode = isDualPageMode,
                modifier = Modifier.fillMaxSize()
            )

            // Floating Glass Top Bar
            FloatingGlassTopBar(
                flashMode = flashMode,
                onFlashClick = { viewModel.cycleFlashMode() },
                isAutoShutter = isAutoShutter,
                onAutoShutterToggle = { viewModel.toggleAutoShutter() },
                roll = roll,
                pitch = pitch,
                isLevel = isLevel,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // Ambient Status Chip
            AmbientFeedbackChip(
                state = feedbackState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 170.dp)
            )

            // Thumb-Zone Bottom Control Dock
            ScannerBottomDock(
                isDualPageMode = isDualPageMode,
                onDualPageToggle = { viewModel.setDualPageMode(it) },
                lastScannedPage = lastPage,
                pageCount = pageCount,
                isAutoCaptureEnabled = isAutoShutter,
                autoCaptureProgress = autoCaptureProgress,
                onShutterClick = { viewModel.triggerShutter() },
                onGalleryClick = onNavigateToGallery,
                onDoneClick = onNavigateToGallery,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
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

@Composable
private fun FloatingGlassTopBar(
    flashMode: FlashMode,
    onFlashClick: () -> Unit,
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
            .background(GlassSurfaceMuted)
            .border(1.dp, CharcoalGlassBorder, barShape)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Flash cycle button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onFlashClick() },
                contentAlignment = Alignment.Center
            ) {
                val (icon, tint) = when (flashMode) {
                    FlashMode.OFF -> Pair(Icons.Default.FlashOff, TextSecondary)
                    FlashMode.AUTO -> Pair(Icons.Default.FlashAuto, TextPrimary)
                    FlashMode.TORCH -> Pair(Icons.Default.FlashOn, PrecisionEmerald)
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "Flash",
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Auto Shutter Beacon Badge
            AutoShutterBadge(
                isEnabled = isAutoShutter,
                onClick = onAutoShutterToggle
            )

            // Gyroscope Leveler
            GyroLevelerIndicator(
                roll = roll,
                pitch = pitch,
                isLevel = isLevel
            )
        }
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
        // Pulsing Emerald Beacon
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .alpha(if (isEnabled) beaconAlpha else 0.3f)
                .background(if (isEnabled) PrecisionEmerald else TextSecondary)
        )

        Text(
            text = if (isEnabled) "AUTO SHUTTER" else "MANUAL",
            color = if (isEnabled) PrecisionEmerald else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
