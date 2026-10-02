package com.docwarp.scanner.ui.camera

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docwarp.scanner.core.cv.NativeCvEngine
import com.docwarp.scanner.core.cv.StillnessDetector
import com.docwarp.scanner.core.model.DocumentQuad
import com.docwarp.scanner.core.model.ScanFilter
import com.docwarp.scanner.core.model.ScannedPage
import com.docwarp.scanner.core.pipeline.DocumentPipelineWorker
import com.docwarp.scanner.core.pipeline.DocumentScanRepository
import com.docwarp.scanner.core.sensor.DeviceLeveler
import com.docwarp.scanner.ui.theme.HapticFeedbackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class FlashMode {
    OFF, AUTO, TORCH
}

class CameraScannerViewModel(
    application: Application,
    private val repository: DocumentScanRepository,
    private val pipelineWorker: DocumentPipelineWorker
) : AndroidViewModel(application) {

    private val haptics = HapticFeedbackManager(application)
    private val leveler = DeviceLeveler(application)

    private val _flashMode = MutableStateFlow(FlashMode.OFF)
    val flashMode: StateFlow<FlashMode> = _flashMode.asStateFlow()

    private val _isAutoShutter = MutableStateFlow(true)
    val isAutoShutter: StateFlow<Boolean> = _isAutoShutter.asStateFlow()

    private val _isDualPageMode = MutableStateFlow(false)
    val isDualPageMode: StateFlow<Boolean> = _isDualPageMode.asStateFlow()

    private val _detectedQuad = MutableStateFlow<DocumentQuad?>(null)
    val detectedQuad: StateFlow<DocumentQuad?> = _detectedQuad.asStateFlow()

    private val _feedbackState = MutableStateFlow<FeedbackState>(FeedbackState.PositionDocument)
    val feedbackState: StateFlow<FeedbackState> = _feedbackState.asStateFlow()

    private val _isStillAndLocked = MutableStateFlow(false)
    val isStillAndLocked: StateFlow<Boolean> = _isStillAndLocked.asStateFlow()

    private val _autoCaptureProgress = MutableStateFlow(0f)
    val autoCaptureProgress: StateFlow<Float> = _autoCaptureProgress.asStateFlow()

    // Leveler values
    val rollDegrees = leveler.rollDegrees
    val pitchDegrees = leveler.pitchDegrees
    val isLevel = leveler.isLevel

    // Repository states
    val documentState = repository.documentState

    private var imageCapture: ImageCapture? = null

    private val stillnessDetector = StillnessDetector(application) {
        if (_isAutoShutter.value) {
            triggerShutter()
        }
    }

    init {
        leveler.start()
        stillnessDetector.start()

        viewModelScope.launch {
            stillnessDetector.stabilityProgress.collect { progress ->
                _autoCaptureProgress.value = progress
                if (progress > 0.1f && progress < 1.0f) {
                    _feedbackState.value = FeedbackState.HoldingStill(progress)
                } else if (progress <= 0.1f && _feedbackState.value !is FeedbackState.Captured) {
                    _feedbackState.value = FeedbackState.PositionDocument
                }
            }
        }

        viewModelScope.launch {
            stillnessDetector.isLockedAndStill.collect { still ->
                _isStillAndLocked.value = still
                if (still) haptics.snap()
            }
        }
    }

    fun setImageCapture(capture: ImageCapture) {
        this.imageCapture = capture
    }

    fun cycleFlashMode() {
        haptics.modeToggle()
        _flashMode.value = when (_flashMode.value) {
            FlashMode.OFF -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.TORCH
            FlashMode.TORCH -> FlashMode.OFF
        }
    }

    fun toggleAutoShutter() {
        haptics.modeToggle()
        _isAutoShutter.value = !_isAutoShutter.value
    }

    fun setDualPageMode(enabled: Boolean) {
        haptics.modeToggle()
        _isDualPageMode.value = enabled
    }

    /**
     * Process CameraX ImageAnalysis frame for live quadrilateral tracking
     */
    fun processAnalysisFrame(bitmap: Bitmap) {
        stillnessDetector.processPreviewFrame(bitmap)

        viewModelScope.launch(Dispatchers.Default) {
            val quad = NativeCvEngine.findDocumentQuad(bitmap, minAreaRatio = 0.08f)
            if (quad != null) {
                // Normalize coordinates relative to preview analyzer frame
                val normQuad = quad.toNormalized(bitmap.width, bitmap.height)
                _detectedQuad.value = normQuad
            }
        }
    }

    fun triggerShutter() {
        val capture = imageCapture ?: return
        haptics.capture()

        _feedbackState.value = FeedbackState.Captured
        viewModelScope.launch {
            delay(800)
            _feedbackState.value = FeedbackState.PositionDocument
        }

        val cacheDir = getApplication<Application>().cacheDir
        val tempRawFile = File(cacheDir, "raw_${UUID.randomUUID()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempRawFile).build()

        capture.takePicture(
            outputOptions,
            androidx.core.content.ContextCompat.getMainExecutor(getApplication()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Instantly enqueue into background worker (Zero UI freeze)
                    pipelineWorker.enqueueCapture(
                        rawCaptureFile = tempRawFile,
                        detectedNormalizedQuad = _detectedQuad.value,
                        isDualPage = _isDualPageMode.value,
                        filter = ScanFilter.SAUVOLA_BINARIZED
                    )
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraScannerVM", "Photo capture failed: ${exception.message}", exception)
                }
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        leveler.stop()
        stillnessDetector.stop()
    }
}
