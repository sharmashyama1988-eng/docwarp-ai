package com.docwarp.scanner.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docwarp.scanner.core.model.DocumentQuad
import com.docwarp.scanner.core.model.ScannedPage
import com.docwarp.scanner.core.pipeline.DocumentScanRepository
import com.docwarp.scanner.ui.theme.HapticFeedbackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

enum class CornerHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT
}

class ManualCropViewModel(
    application: Application,
    private val repository: DocumentScanRepository,
    private val pageId: String
) : AndroidViewModel(application) {

    private val haptics = HapticFeedbackManager(application)

    private val _page = MutableStateFlow<ScannedPage?>(null)
    val page: StateFlow<ScannedPage?> = _page.asStateFlow()

    private val _rawBitmap = MutableStateFlow<Bitmap?>(null)
    val rawBitmap: StateFlow<Bitmap?> = _rawBitmap.asStateFlow()

    private val _currentQuad = MutableStateFlow<DocumentQuad?>(null)
    val currentQuad: StateFlow<DocumentQuad?> = _currentQuad.asStateFlow()

    private val _initialQuad = MutableStateFlow<DocumentQuad?>(null)

    private val _activeCorner = MutableStateFlow<CornerHandle?>(null)
    val activeCorner: StateFlow<CornerHandle?> = _activeCorner.asStateFlow()

    init {
        loadPageData()
    }

    private fun loadPageData() {
        viewModelScope.launch {
            val p = repository.getPage(pageId)
            _page.value = p
            if (p != null && p.rawImageFile.exists()) {
                val bmp = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(p.rawImageFile.absolutePath)
                }
                _rawBitmap.value = bmp

                val initial = if (p.cropQuad != null) {
                    p.cropQuad.toTargetResolution(bmp.width, bmp.height)
                } else {
                    DocumentQuad.defaultForDimensions(bmp.width, bmp.height, 0.06f)
                }
                _currentQuad.value = initial
                _initialQuad.value = initial
            }
        }
    }

    fun startCornerDrag(x: Float, y: Float, touchRadius: Float = 60f): Boolean {
        val quad = _currentQuad.value ?: return false

        val corners = mapOf(
            CornerHandle.TOP_LEFT to quad.topLeft,
            CornerHandle.TOP_RIGHT to quad.topRight,
            CornerHandle.BOTTOM_RIGHT to quad.bottomRight,
            CornerHandle.BOTTOM_LEFT to quad.bottomLeft
        )

        for ((handle, pt) in corners) {
            val dist = distance(x, y, pt.x, pt.y)
            if (dist <= touchRadius) {
                _activeCorner.value = handle
                haptics.snap()
                return true
            }
        }
        return false
    }

    fun updateCornerDrag(newX: Float, newY: Float, imgWidth: Float, imgHeight: Float) {
        val handle = _activeCorner.value ?: return
        val quad = _currentQuad.value ?: return

        // Magnetic snap to image boundaries (within 16px of edge)
        val snapThreshold = 16f
        val clampedX = when {
            newX < snapThreshold -> 0f
            newX > imgWidth - snapThreshold -> imgWidth
            else -> newX.coerceIn(0f, imgWidth)
        }
        val clampedY = when {
            newY < snapThreshold -> 0f
            newY > imgHeight - snapThreshold -> imgHeight
            else -> newY.coerceIn(0f, imgHeight)
        }

        val updatedQuad = when (handle) {
            CornerHandle.TOP_LEFT -> quad.copy(topLeft = PointF(clampedX, clampedY))
            CornerHandle.TOP_RIGHT -> quad.copy(topRight = PointF(clampedX, clampedY))
            CornerHandle.BOTTOM_RIGHT -> quad.copy(bottomRight = PointF(clampedX, clampedY))
            CornerHandle.BOTTOM_LEFT -> quad.copy(bottomLeft = PointF(clampedX, clampedY))
        }

        _currentQuad.value = updatedQuad
    }

    fun endCornerDrag() {
        if (_activeCorner.value != null) {
            _activeCorner.value = null
            haptics.snap()
        }
    }

    fun resetToAuto() {
        haptics.modeToggle()
        _currentQuad.value = _initialQuad.value
    }

    fun selectFullImage() {
        val bmp = _rawBitmap.value ?: return
        haptics.modeToggle()
        _currentQuad.value = DocumentQuad(
            PointF(0f, 0f),
            PointF(bmp.width.toFloat(), 0f),
            PointF(bmp.width.toFloat(), bmp.height.toFloat()),
            PointF(0f, bmp.height.toFloat())
        )
    }

    fun confirmCrop(onDone: () -> Unit) {
        val quad = _currentQuad.value ?: return
        val bmp = _rawBitmap.value ?: return
        haptics.capture()

        val normalized = quad.toNormalized(bmp.width, bmp.height)
        repository.reprocessCrop(pageId, normalized)
        onDone()
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    override fun onCleared() {
        super.onCleared()
        _rawBitmap.value?.recycle()
    }
}
