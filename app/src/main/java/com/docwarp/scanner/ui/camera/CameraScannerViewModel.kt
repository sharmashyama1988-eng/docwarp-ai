package com.docwarp.scanner.ui.camera

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docwarp.scanner.DocWarpApplication
import com.docwarp.scanner.core.cv.NativeCvEngine
import com.docwarp.scanner.core.cv.StillnessDetector
import com.docwarp.scanner.core.model.CameraResolutionOption
import com.docwarp.scanner.core.model.DocumentFolder
import com.docwarp.scanner.core.model.DocumentQuad
import com.docwarp.scanner.core.model.ScanFilter
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
import java.io.InputStream
import java.util.UUID

enum class FlashMode {
    OFF, AUTO, TORCH
}

enum class ScannerMode(val title: String) {
    BOOK("Book"),
    TO_TEXT("To Text"),
    DOCS("Docs"),
    ID_CARD("ID Card"),
    QR_CODE("QR Code")
}

/**
 * Screen 1 & Core Engine: Camera Scanner Viewfinder Model.
 * Features:
 * - Rapid-Fire Zero-Wait Continuous Burst Shutter
 * - Dynamic HD Resolution Selection (OKEN Scanner match)
 * - Live Filter Palette Selection (Clean eBook, No Shadow, Magic Color, etc.)
 * - Folder Management for PDF organization
 * - File and Gallery Image Import (JPG, PNG, PDF)
 * - On-Device ML Kit OCR Text Extraction
 */
class CameraScannerViewModel(
    application: Application,
    private val repository: DocumentScanRepository,
    private val pipelineWorker: DocumentPipelineWorker
) : AndroidViewModel(application) {

    private val haptics = HapticFeedbackManager(application)
    private val leveler = DeviceLeveler(application)
    private val googleMlKit = (application as? DocWarpApplication)?.googleMlKitScanner

    private val _flashMode = MutableStateFlow(FlashMode.OFF)
    val flashMode: StateFlow<FlashMode> = _flashMode.asStateFlow()

    private val _isAutoShutter = MutableStateFlow(false)
    val isAutoShutter: StateFlow<Boolean> = _isAutoShutter.asStateFlow()

    private val _isDualPageMode = MutableStateFlow(false)
    val isDualPageMode: StateFlow<Boolean> = _isDualPageMode.asStateFlow()

    private val _selectedMode = MutableStateFlow(ScannerMode.DOCS)
    val selectedMode: StateFlow<ScannerMode> = _selectedMode.asStateFlow()

    private val _selectedResolution = MutableStateFlow(CameraResolutionOption.DEFAULT)
    val selectedResolution: StateFlow<CameraResolutionOption> = _selectedResolution.asStateFlow()

    private val _activeFilter = MutableStateFlow(ScanFilter.EBOOK_CLEAN)
    val activeFilter: StateFlow<ScanFilter> = _activeFilter.asStateFlow()

    private val _selectedFolder = MutableStateFlow(DocumentFolder.DEFAULT)
    val selectedFolder: StateFlow<DocumentFolder> = _selectedFolder.asStateFlow()

    private val _availableFolders = MutableStateFlow(DocumentFolder.DEFAULT_FOLDERS)
    val availableFolders: StateFlow<List<DocumentFolder>> = _availableFolders.asStateFlow()

    // Dialog & overlay visibility states
    private val _isResolutionDialogVisible = MutableStateFlow(false)
    val isResolutionDialogVisible: StateFlow<Boolean> = _isResolutionDialogVisible.asStateFlow()

    private val _isFilterPaletteVisible = MutableStateFlow(false)
    val isFilterPaletteVisible: StateFlow<Boolean> = _isFilterPaletteVisible.asStateFlow()

    private val _isFolderDialogVisible = MutableStateFlow(false)
    val isFolderDialogVisible: StateFlow<Boolean> = _isFolderDialogVisible.asStateFlow()

    private val _isGridEnabled = MutableStateFlow(false)
    val isGridEnabled: StateFlow<Boolean> = _isGridEnabled.asStateFlow()

    // OCR result
    private val _extractedOcrText = MutableStateFlow<String?>(null)
    val extractedOcrText: StateFlow<String?> = _extractedOcrText.asStateFlow()

    private val _isOcrLoading = MutableStateFlow(false)
    val isOcrLoading: StateFlow<Boolean> = _isOcrLoading.asStateFlow()

    // Viewfinder visual shutter pulse trigger
    private val _shutterFlashCounter = MutableStateFlow(0)
    val shutterFlashCounter: StateFlow<Int> = _shutterFlashCounter.asStateFlow()

    private val _detectedQuad = MutableStateFlow<DocumentQuad?>(null)
    val detectedQuad: StateFlow<DocumentQuad?> = _detectedQuad.asStateFlow()

    private val _feedbackState = MutableStateFlow<FeedbackState>(FeedbackState.PositionDocument)
    val feedbackState: StateFlow<FeedbackState> = _feedbackState.asStateFlow()

    private val _isStillAndLocked = MutableStateFlow(false)
    val isStillAndLocked: StateFlow<Boolean> = _isStillAndLocked.asStateFlow()

    private val _autoCaptureProgress = MutableStateFlow(0f)
    val autoCaptureProgress: StateFlow<Float> = _autoCaptureProgress.asStateFlow()

    // Gyroscope leveler values
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

    fun setScannerMode(mode: ScannerMode) {
        haptics.modeToggle()
        _selectedMode.value = mode
        _isDualPageMode.value = (mode == ScannerMode.BOOK)
    }

    fun setResolution(option: CameraResolutionOption) {
        haptics.modeToggle()
        _selectedResolution.value = option
        _isResolutionDialogVisible.value = false
    }

    fun toggleResolutionDialog() {
        haptics.modeToggle()
        _isResolutionDialogVisible.value = !_isResolutionDialogVisible.value
        if (_isResolutionDialogVisible.value) {
            _isFilterPaletteVisible.value = false
            _isFolderDialogVisible.value = false
        }
    }

    fun closeResolutionDialog() {
        _isResolutionDialogVisible.value = false
    }

    fun setActiveFilter(filter: ScanFilter) {
        haptics.modeToggle()
        _activeFilter.value = filter
        _isFilterPaletteVisible.value = false
    }

    fun toggleFilterPalette() {
        haptics.modeToggle()
        _isFilterPaletteVisible.value = !_isFilterPaletteVisible.value
        if (_isFilterPaletteVisible.value) {
            _isResolutionDialogVisible.value = false
            _isFolderDialogVisible.value = false
        }
    }

    fun closeFilterPalette() {
        _isFilterPaletteVisible.value = false
    }

    fun toggleGrid() {
        haptics.modeToggle()
        _isGridEnabled.value = !_isGridEnabled.value
    }

    fun toggleFolderDialog() {
        haptics.modeToggle()
        _isFolderDialogVisible.value = !_isFolderDialogVisible.value
        if (_isFolderDialogVisible.value) {
            _isResolutionDialogVisible.value = false
            _isFilterPaletteVisible.value = false
        }
    }

    fun closeFolderDialog() {
        _isFolderDialogVisible.value = false
    }

    fun selectFolder(folder: DocumentFolder) {
        haptics.modeToggle()
        _selectedFolder.value = folder
        repository.updateDocumentMetadata(folder = folder.name)
        _isFolderDialogVisible.value = false
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        val newFolder = DocumentFolder(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            iconEmoji = "📁"
        )
        _availableFolders.value = _availableFolders.value + newFolder
        selectFolder(newFolder)
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

    fun processAnalysisFrame(bitmap: Bitmap) {
        stillnessDetector.processPreviewFrame(bitmap)

        viewModelScope.launch(Dispatchers.Default) {
            val quad = NativeCvEngine.findDocumentQuad(bitmap, minAreaRatio = 0.08f)
            if (quad != null) {
                val normQuad = quad.toNormalized(bitmap.width, bitmap.height)
                _detectedQuad.value = normQuad
            }
        }
    }

    /**
     * RAPID-FIRE CONTINUOUS BURST SHUTTER:
     * Non-blocking, zero wait time!
     * Snaps instantly with haptic feedback, triggers optical viewfinder pulse,
     * writes raw image file and enqueues to the background pipeline worker.
     * Viewfinder is immediately ready for subsequent shots without delay!
     */
    fun triggerShutter() {
        val capture = imageCapture ?: return
        haptics.capture()

        // 1. Trigger instant shutter pulse animation
        _shutterFlashCounter.value = _shutterFlashCounter.value + 1
        _feedbackState.value = FeedbackState.Captured

        viewModelScope.launch {
            delay(400)
            _feedbackState.value = FeedbackState.PositionDocument
        }

        val cacheDir = getApplication<Application>().cacheDir
        val tempRawFile = File(cacheDir, "raw_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempRawFile).build()

        val capturedQuad = _detectedQuad.value
        val isDual = _isDualPageMode.value
        val filterToApply = _activeFilter.value

        // Execute capture asynchronously - UI thread never blocks
        capture.takePicture(
            outputOptions,
            androidx.core.content.ContextCompat.getMainExecutor(getApplication()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Instantly enqueue into background queue with selected filter
                    pipelineWorker.enqueueCapture(
                        rawCaptureFile = tempRawFile,
                        detectedNormalizedQuad = capturedQuad,
                        isDualPage = isDual,
                        filter = filterToApply
                    )

                    // If in "To Text" mode, also trigger OCR extraction on the captured image
                    if (_selectedMode.value == ScannerMode.TO_TEXT) {
                        extractOcrFromFile(tempRawFile)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                }
            }
        )
    }

    /**
     * Batch import multiple images (JPG, PNG) from Device Gallery or Scoped Storage
     */
    fun importImagesFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        haptics.capture()

        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val cacheDir = File(context.cacheDir, "imported_scans").apply { if (!exists()) mkdirs() }

            for (uri in uris) {
                try {
                    val rawFile = File(cacheDir, "import_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(rawFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (rawFile.exists() && rawFile.length() > 0) {
                        pipelineWorker.enqueueCapture(
                            rawCaptureFile = rawFile,
                            detectedNormalizedQuad = null, // Auto-detect corners in native worker
                            isDualPage = false,
                            filter = _activeFilter.value
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error importing image URI $uri: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Run high-speed on-device ML Kit OCR on a file
     */
    fun extractOcrFromFile(file: File) {
        val scanner = googleMlKit ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isOcrLoading.value = true
            try {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) {
                    val text = scanner.extractTextFromBitmap(bmp)
                    _extractedOcrText.value = text
                    bmp.recycle()
                }
            } catch (e: Exception) {
                Log.e(TAG, "OCR extraction failed: ${e.message}", e)
            } finally {
                _isOcrLoading.value = false
            }
        }
    }

    fun clearOcrText() {
        _extractedOcrText.value = null
    }

    override fun onCleared() {
        super.onCleared()
        leveler.stop()
        stillnessDetector.stop()
    }

    companion object {
        private const val TAG = "CameraScannerVM"
    }
}
