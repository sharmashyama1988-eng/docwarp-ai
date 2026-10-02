package com.docwarp.scanner.core.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.docwarp.scanner.core.cv.NativeCvEngine
import com.docwarp.scanner.core.ml.MediaPipeHandMasker
import com.docwarp.scanner.core.ml.OnnxModelManager
import com.docwarp.scanner.core.model.DocumentQuad
import com.docwarp.scanner.core.model.ScanFilter
import com.docwarp.scanner.core.model.ScannedPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Screen 2: Batch Processing Pipeline Worker.
 * Ensures zero UI freeze during heavy ML execution:
 * Camera shutter snaps immediately, persists raw frame, and queues to this worker.
 * Executes:
 * 1. Perspective Crop (OpenCV C++ cv::warpPerspective)
 * 2. Dual-Page Center Split (if enabled)
 * 3. MediaPipe Hand Landmark mask generation
 * 4. LaMa Inpainting (finger erasure)
 * 5. Sauvola Adaptive Binarization (shadow removal & sharp text)
 * 6. Cache persistence and Repository update
 */
class DocumentPipelineWorker(
    private val context: Context,
    private val repository: DocumentScanRepository,
    private val mediaPipeHandMasker: MediaPipeHandMasker,
    private val onnxModelManager: OnnxModelManager
) {
    private val workerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val workChannel = Channel<CaptureTask>(Channel.UNLIMITED)

    private val _pendingTaskCount = MutableStateFlow(0)
    val pendingTaskCount: StateFlow<Int> = _pendingTaskCount.asStateFlow()

    private val _isWorkerActive = MutableStateFlow(false)
    val isWorkerActive: StateFlow<Boolean> = _isWorkerActive.asStateFlow()

    data class CaptureTask(
        val rawCaptureFile: File,
        val detectedNormalizedQuad: DocumentQuad?,
        val isDualPage: Boolean,
        val filter: ScanFilter = ScanFilter.SAUVOLA_BINARIZED
    )

    init {
        startWorkerLoop()
    }

    private fun startWorkerLoop() {
        workerScope.launch {
            for (task in workChannel) {
                _isWorkerActive.value = true
                try {
                    processTask(task)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process capture task: ${e.message}", e)
                } finally {
                    _pendingTaskCount.value = (_pendingTaskCount.value - 1).coerceAtLeast(0)
                    _isWorkerActive.value = _pendingTaskCount.value > 0
                }
            }
        }
    }

    fun enqueueCapture(
        rawCaptureFile: File,
        detectedNormalizedQuad: DocumentQuad?,
        isDualPage: Boolean,
        filter: ScanFilter = ScanFilter.SAUVOLA_BINARIZED
    ) {
        _pendingTaskCount.value += 1
        workChannel.trySend(CaptureTask(rawCaptureFile, detectedNormalizedQuad, isDualPage, filter))
    }

    private suspend fun processTask(task: CaptureTask) {
        val rawFile = task.rawCaptureFile
        if (!rawFile.exists() || rawFile.length() == 0L) {
            Log.w(TAG, "Raw capture file does not exist or empty: ${rawFile.absolutePath}")
            return
        }

        val cacheDir = File(context.cacheDir, "processed_scans").apply { if (!exists()) mkdirs() }

        // Fast native C++ file-to-file execution (Zero Java Heap OOM)
        val normCoords = task.detectedNormalizedQuad?.toFloatArray()
        val doBinarize = (task.filter == ScanFilter.SAUVOLA_BINARIZED)

        val nativeResults = NativeCvEngine.processDocumentFileNative(
            inputFilePath = rawFile.absolutePath,
            outputDir = cacheDir.absolutePath,
            normalizedQuadCoords = normCoords,
            doDewarp = true,
            doDualSplit = task.isDualPage,
            doBinarize = doBinarize,
            sauvolaK = 0.20,
            sauvolaWindow = 35
        )

        val currentCount = repository.documentState.value.pages.size

        if (nativeResults != null && nativeResults.isNotEmpty()) {
            for ((index, item) in nativeResults.withIndex()) {
                val processedFile = File(item.outputPath)

                // Hand Landmark check on the processed output
                val processedBmp = BitmapFactory.decodeFile(processedFile.absolutePath)
                if (processedBmp != null) {
                    val mask = mediaPipeHandMasker.generateFingerMask(processedBmp)
                    if (mask != null) {
                        val cleaned = onnxModelManager.inpaint(processedBmp, mask)
                        mask.recycle()
                        if (cleaned !== processedBmp) {
                            FileOutputStream(processedFile).use { out ->
                                cleaned.compress(Bitmap.CompressFormat.JPEG, 92, out)
                            }
                            cleaned.recycle()
                        }
                    }
                    processedBmp.recycle()
                }

                // Add to repository
                val scannedPage = ScannedPage(
                    pageNumber = currentCount + index + 1,
                    rawImageFile = rawFile,
                    processedImageFile = processedFile,
                    cropQuad = task.detectedNormalizedQuad,
                    filter = task.filter,
                    width = item.width,
                    height = item.height
                )

                repository.addPage(scannedPage)
            }
        } else {
            // Fallback: copy raw directly as processed
            val fallbackFile = File(cacheDir, "fallback_${UUID.randomUUID()}.jpg")
            rawFile.copyTo(fallbackFile, overwrite = true)
            val page = ScannedPage(
                pageNumber = currentCount + 1,
                rawImageFile = rawFile,
                processedImageFile = fallbackFile,
                cropQuad = task.detectedNormalizedQuad,
                filter = task.filter
            )
            repository.addPage(page)
        }
    }

    companion object {
        private const val TAG = "DocumentPipelineWorker"
    }
}
