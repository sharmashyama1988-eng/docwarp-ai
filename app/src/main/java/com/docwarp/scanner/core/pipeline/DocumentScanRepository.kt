package com.docwarp.scanner.core.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import com.docwarp.scanner.core.cv.NativeCvEngine
import com.docwarp.scanner.core.ml.MediaPipeHandMasker
import com.docwarp.scanner.core.ml.OnnxModelManager
import com.docwarp.scanner.core.model.CompressionQuality
import com.docwarp.scanner.core.model.DocumentQuad
import com.docwarp.scanner.core.model.PageSizeOption
import com.docwarp.scanner.core.model.ScanDocument
import com.docwarp.scanner.core.model.ScanFilter
import com.docwarp.scanner.core.model.ScannedPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.UUID

/**
 * Central State Repository managing scanned pages, active document session,
 * re-ordering, crop adjustments, and filter reprocessing.
 */
class DocumentScanRepository(
    private val context: Context,
    private val mediaPipeHandMasker: MediaPipeHandMasker,
    private val onnxModelManager: OnnxModelManager
) {
    private val repositoryScope = CoroutineScope(Dispatchers.Default)

    private val _documentState = MutableStateFlow(ScanDocument())
    val documentState: StateFlow<ScanDocument> = _documentState.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    fun addPage(page: ScannedPage) {
        val current = _documentState.value
        val updatedPages = current.pages.toMutableList().apply { add(page) }
        _documentState.value = current.copy(pages = updatedPages)
    }

    fun removePage(pageId: String) {
        val current = _documentState.value
        val updatedPages = current.pages.filter { it.id != pageId }
            .mapIndexed { index, p -> p.copy(pageNumber = index + 1) }
        _documentState.value = current.copy(pages = updatedPages)
    }

    fun reorderPages(fromIndex: Int, toIndex: Int) {
        val current = _documentState.value
        val list = current.pages.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            Collections.swap(list, fromIndex, toIndex)
            val reindexed = list.mapIndexed { index, p -> p.copy(pageNumber = index + 1) }
            _documentState.value = current.copy(pages = reindexed)
        }
    }

    fun rotatePage(pageId: String, deltaDegrees: Int = 90) {
        val current = _documentState.value
        val updated = current.pages.map { page ->
            if (page.id == pageId) {
                val newRot = (page.rotationDegrees + deltaDegrees) % 360
                page.copy(rotationDegrees = newRot)
            } else page
        }
        _documentState.value = current.copy(pages = updated)
    }

    fun getPage(pageId: String): ScannedPage? {
        return _documentState.value.pages.find { it.id == pageId }
    }

    fun updateDocumentMetadata(
        title: String? = null,
        pageSize: PageSizeOption? = null,
        compression: CompressionQuality? = null,
        folder: String? = null
    ) {
        val current = _documentState.value
        _documentState.value = current.copy(
            title = title ?: current.title,
            pageSize = pageSize ?: current.pageSize,
            compression = compression ?: current.compression,
            folder = folder ?: current.folder
        )
    }

    fun clearDocument() {
        _documentState.value = ScanDocument()
    }

    /**
     * Reprocess a page when user updates crop quad in ManualCropScreen
     */
    fun reprocessCrop(pageId: String, newQuad: DocumentQuad) {
        repositoryScope.launch {
            _isProcessing.value = true
            try {
                val page = getPage(pageId) ?: return@launch
                if (!page.rawImageFile.exists()) return@launch

                val rawBitmap = BitmapFactory.decodeFile(page.rawImageFile.absolutePath) ?: return@launch
                val cropped = NativeCvEngine.cropAndWarpQuad(rawBitmap, newQuad) ?: rawBitmap

                // Apply filter
                val filtered = applyFilterToBitmap(cropped, page.filter, page.eraseFingers, page.flattenCurvature)

                // Overwrite processed file
                FileOutputStream(page.processedImageFile).use { out ->
                    filtered.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }

                // Update state
                val current = _documentState.value
                val updated = current.pages.map {
                    if (it.id == pageId) {
                        it.copy(
                            cropQuad = newQuad,
                            width = filtered.width,
                            height = filtered.height
                        )
                    } else it
                }
                _documentState.value = current.copy(pages = updated)

                if (rawBitmap !== cropped) rawBitmap.recycle()
                if (cropped !== filtered) cropped.recycle()
                filtered.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Error reprocessing crop: ${e.message}", e)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * Reprocess page when user updates filter in FilterStudioScreen
     */
    fun updatePageFilter(
        pageId: String,
        newFilter: ScanFilter,
        eraseFingers: Boolean,
        flattenCurvature: Boolean
    ) {
        repositoryScope.launch {
            _isProcessing.value = true
            try {
                val page = getPage(pageId) ?: return@launch
                if (!page.rawImageFile.exists()) return@launch

                val rawBitmap = BitmapFactory.decodeFile(page.rawImageFile.absolutePath) ?: return@launch
                val cropped = if (page.cropQuad != null) {
                    NativeCvEngine.cropAndWarpQuad(rawBitmap, page.cropQuad) ?: rawBitmap
                } else {
                    rawBitmap
                }

                val filtered = applyFilterToBitmap(cropped, newFilter, eraseFingers, flattenCurvature)

                FileOutputStream(page.processedImageFile).use { out ->
                    filtered.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }

                val current = _documentState.value
                val updated = current.pages.map {
                    if (it.id == pageId) {
                        it.copy(
                            filter = newFilter,
                            eraseFingers = eraseFingers,
                            flattenCurvature = flattenCurvature,
                            width = filtered.width,
                            height = filtered.height
                        )
                    } else it
                }
                _documentState.value = current.copy(pages = updated)

                if (rawBitmap !== cropped) rawBitmap.recycle()
                if (cropped !== filtered) cropped.recycle()
                filtered.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating page filter: ${e.message}", e)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * Batch apply filter settings across every page in document
     */
    fun applyFilterToAll(newFilter: ScanFilter, eraseFingers: Boolean, flattenCurvature: Boolean) {
        repositoryScope.launch {
            _isProcessing.value = true
            try {
                val pages = _documentState.value.pages
                for (p in pages) {
                    if (!p.rawImageFile.exists()) continue
                    val rawBitmap = BitmapFactory.decodeFile(p.rawImageFile.absolutePath) ?: continue
                    val cropped = if (p.cropQuad != null) {
                        NativeCvEngine.cropAndWarpQuad(rawBitmap, p.cropQuad) ?: rawBitmap
                    } else rawBitmap

                    val filtered = applyFilterToBitmap(cropped, newFilter, eraseFingers, flattenCurvature)

                    FileOutputStream(p.processedImageFile).use { out ->
                        filtered.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }

                    if (rawBitmap !== cropped) rawBitmap.recycle()
                    if (cropped !== filtered) cropped.recycle()
                    filtered.recycle()
                }

                val current = _documentState.value
                val updated = current.pages.map {
                    it.copy(
                        filter = newFilter,
                        eraseFingers = eraseFingers,
                        flattenCurvature = flattenCurvature
                    )
                }
                _documentState.value = current.copy(pages = updated)
            } catch (e: Exception) {
                Log.e(TAG, "Error applying filter to all: ${e.message}", e)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    suspend fun applyFilterToBitmap(
        bitmap: Bitmap,
        filter: ScanFilter,
        eraseFingers: Boolean,
        flattenCurvature: Boolean
    ): Bitmap = withContext(Dispatchers.Default) {
        var current = bitmap

        // Curvature dewarping
        if (flattenCurvature) {
            val dewarped = onnxModelManager.dewarp(current)
            if (dewarped !== current) {
                if (current !== bitmap) current.recycle()
                current = dewarped
            }
        }

        // Finger inpainting
        if (eraseFingers) {
            val mask = mediaPipeHandMasker.generateFingerMask(current)
            if (mask != null) {
                val inpainted = onnxModelManager.inpaint(current, mask)
                mask.recycle()
                if (inpainted !== current) {
                    if (current !== bitmap) current.recycle()
                    current = inpainted
                }
            }
        }

        // Color / Binarization Filter
        val targetFilter = if (filter == ScanFilter.AUTO_BEST) {
            AutoFilterSelector.determineOptimalFilter(current)
        } else {
            filter
        }

        val result = if (targetFilter == ScanFilter.ORIGINAL) {
            current
        } else {
            NativeCvEngine.applyFilter(current, targetFilter) ?: current
        }

        if (result !== current && current !== bitmap) {
            current.recycle()
        }

        return@withContext result
    }

    companion object {
        private const val TAG = "DocumentScanRepository"
    }
}
